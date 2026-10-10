package my.celium.org.bedrockplacing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Headless tests for the build-layer lock. Pure logic, no world needed:
 * grounded anchors lock, airborne anchors must match, stale or foreign
 * memory re-locks leniently so the mod never goes silent.
 */
class LayerLockTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("test", "overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("test", "nether"));

    @Test
    void groundedAnchorLocksLayer() {
        LayerLock lock = new LayerLock();
        assertTrue(lock.check(true, 63, OVERWORLD, 10));
        // Airborne on the same layer still works (walked off the edge).
        assertTrue(lock.check(false, 63, OVERWORLD, 11));
    }

    @Test
    void airborneOffLayerDenied() {
        LayerLock lock = new LayerLock();
        assertTrue(lock.check(true, 63, OVERWORLD, 10));
        // Jumped (layer up) or fell (layer down): no placement.
        assertFalse(lock.check(false, 64, OVERWORLD, 11));
        assertFalse(lock.check(false, 62, OVERWORLD, 12));
        // The lock still holds the original layer afterwards.
        assertTrue(lock.check(false, 63, OVERWORLD, 13));
    }

    @Test
    void groundedRelocksNewLayer() {
        LayerLock lock = new LayerLock();
        assertTrue(lock.check(true, 63, OVERWORLD, 10));
        // Stepped up a block and stands there now: new layer, works at once.
        assertTrue(lock.check(true, 64, OVERWORLD, 20));
        assertTrue(lock.check(false, 64, OVERWORLD, 21));
        assertFalse(lock.check(false, 63, OVERWORLD, 22));
    }

    @Test
    void staleMemoryRelocksLeniently() {
        LayerLock lock = new LayerLock();
        assertTrue(lock.check(true, 63, OVERWORLD, 10));
        // Long idle: next airborne anchor is allowed once and becomes the layer.
        assertTrue(lock.check(false, 70, OVERWORLD, 10 + LayerLock.LOCK_TICKS + 1));
        assertTrue(lock.check(false, 70, OVERWORLD, 10 + LayerLock.LOCK_TICKS + 2));
        assertFalse(lock.check(false, 63, OVERWORLD, 10 + LayerLock.LOCK_TICKS + 3));
    }

    @Test
    void dimensionChangeRelocks() {
        LayerLock lock = new LayerLock();
        assertTrue(lock.check(true, 63, OVERWORLD, 10));
        // Same Y in another dimension is a different build: allow + re-lock.
        assertTrue(lock.check(false, 63, NETHER, 11));
        assertTrue(lock.check(false, 63, NETHER, 12));
    }

    @Test
    void firstTouchIsLenient() {
        LayerLock lock = new LayerLock();
        assertTrue(lock.check(false, 5, OVERWORLD, 0));
        assertTrue(lock.check(false, 5, OVERWORLD, 1));
        assertFalse(lock.check(false, 6, OVERWORLD, 2));
    }
}
