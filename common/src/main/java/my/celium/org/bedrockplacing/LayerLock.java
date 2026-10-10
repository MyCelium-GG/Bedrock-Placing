package my.celium.org.bedrockplacing;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Build-layer memory: the assist may only ever extend the layer the player
 * stands on, never a layer above or below it.
 *
 * <p>The candidate cell always sits at anchor height, and the anchor is either
 * the block directly under the feet (grounded case) or one cell behind under
 * them (airborne case: walked off the edge). Accidents happen exactly in the
 * airborne case when the feet already left the build height — jumping (a layer
 * up) or falling over lower terrain (a layer down). So:
 * <ul>
 *   <li>a grounded anchor is always allowed and (re)locks the layer;</li>
 *   <li>an airborne anchor is allowed only while it targets the locked layer
 *   in the same dimension; walking off an edge at floor height keeps bridging,
 *   jumping or dropping a full block places nothing;</li>
 *   <li>with no fresh memory (first touch, expiry, dimension change) the
 *   anchor is allowed once and immediately locks the new layer, so the mod
 *   never goes mysteriously silent.</li>
 * </ul>
 *
 * <p>Pure logic, no world access — headless-testable. The server keeps one
 * instance per player; the client keeps one for the local player and mirrors
 * the same rule for its preview.
 */
public final class LayerLock {
    /** How long a locked layer survives without a grounded anchor (ticks). */
    static final long LOCK_TICKS = 100;

    private Integer layerY;
    private ResourceKey<Level> dimension;
    private long lockTick;
    private long lastSeenTick;

    /**
     * Checks an anchor against the locked layer.
     *
     * @param groundedAnchor whether the anchor is the block directly under the feet
     * @param anchorY the anchor's Y (candidate height)
     * @param dimension the level the anchor is in
     * @param nowTick current tick (server or client counter, monotonic)
     * @return {@code true} when the anchor may be used
     */
    public synchronized boolean check(boolean groundedAnchor, int anchorY, ResourceKey<Level> dimension,
            long nowTick) {
        lastSeenTick = nowTick;
        if (groundedAnchor) {
            lock(anchorY, dimension, nowTick);
            return true;
        }
        if (layerY == null || this.dimension == null || dimension == null
                || !this.dimension.equals(dimension) || nowTick - lockTick > LOCK_TICKS) {
            // No usable memory: allow once and lock the new layer.
            lock(anchorY, dimension, nowTick);
            return true;
        }
        return anchorY == layerY;
    }

    /** Last tick this lock was consulted; used to evict stale entries. */
    synchronized long lastSeenTick() {
        return lastSeenTick;
    }

    private void lock(int anchorY, ResourceKey<Level> dimension, long nowTick) {
        this.layerY = anchorY;
        this.dimension = dimension;
        this.lockTick = nowTick;
    }
}
