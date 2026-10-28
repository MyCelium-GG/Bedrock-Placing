package my.celium.org.bedrockplacing.client;

import java.util.Optional;

import my.celium.org.bedrockplacing.AssistPlacePacket;
import my.celium.org.bedrockplacing.BedrockPlacingBrain;
import my.celium.org.bedrockplacing.BedrockPlacingMod;
import my.celium.org.bedrockplacing.LayerLock;
import my.celium.org.client.MycelScreens;
import my.celium.org.logging.MycelLog;
import my.celium.org.network.MycelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/**
 * Client side of BedrockPlacing: live preview outline plus the fast assist
 * path.
 *
 * <p>Every client tick {@link #clientTick} mirrors the server's read-only
 * checks ({@link BedrockPlacingBrain#resolveBridgingTarget}) against the
 * client world. When a target exists a white box gizmo is drawn over the
 * candidate cell through vanilla's per-tick gizmo flow
 * ({@code Minecraft.collectPerTickGizmos} + {@link Gizmos#cuboid}, merged
 * into every frame by the level extractor — no per-loader render code), and
 * while the use key is held an {@link AssistPlacePacket} is sent — instantly
 * on the first press, then at most every {@code repeatTicks} — so bridging no
 * longer stutters on vanilla's 4-tick use repeat.
 *
 * <p>The shown outline is <em>sticky</em>: a single tick where the checks
 * flicker off (crosshair raycast edge, reach boundary, chunk state) keeps the
 * last cell for a few ticks instead of strobing. It still drops instantly when
 * the cell stops being replaceable, so after a placement the outline advances
 * to the next cell instead of lingering on the new block.
 *
 * <p>Client-only: only ever referenced from client entrypoints and
 * dist-guarded loader code, never from server paths.
 */
public final class BedrockPlacingClient {
    /** Opaque white preview outline. */
    private static final int PREVIEW_COLOR = 0xFFFFFFFF;
    /** How many ticks a stale preview survives transient check failures. */
    private static final int STICKY_TICKS = 4;

    private static BlockPos currentPreview;
    private static Preview stickyPreview;
    private static long stickyUntilTick;
    private static long clientTicks;
    private static long lastSendTick = -100;
    private static boolean wasDown;
    /** Client mirror of the server's build-layer lock (single local player). */
    private static final LayerLock LAYER_LOCK = new LayerLock();

    private BedrockPlacingClient() {
    }

    /** Last shown candidate cell, or {@code null} when no assist is available. */
    public static BlockPos currentPreview() {
        return currentPreview;
    }

    /** Called once per client tick from each loader's client tick hook. Never throws. */
    public static void clientTick(Minecraft minecraft) {
        clientTicks++;
        boolean down = false;
        Preview preview = null;
        try {
            if (minecraft != null) {
                down = minecraft.options.keyUse.isDown();
                preview = computePreview(minecraft);
            }
        } catch (RuntimeException e) {
            MycelLog.warnMod(BedrockPlacingMod.ID, "Preview computation failed; skipping: {}", e.toString());
            preview = null;
        }
        boolean fresh = down && !wasDown;
        wasDown = down;

        Preview shown = resolveShown(minecraft, preview);
        currentPreview = shown == null ? null : shown.candidate();

        if (shown == null || minecraft == null) {
            return;
        }
        drawGizmo(minecraft, shown.candidate());
        if (!down) {
            return;
        }
        if (!fresh && clientTicks - lastSendTick < BedrockPlacingMod.effectiveRepeatTicks()) {
            return;
        }
        lastSendTick = clientTicks;
        try {
            MycelNetwork.sendToServer(BedrockPlacingMod.assistPacket,
                    new AssistPlacePacket(shown.hand() == InteractionHand.OFF_HAND));
        } catch (RuntimeException e) {
            MycelLog.warnMod(BedrockPlacingMod.ID, "Assist packet send failed; skipping: {}", e.toString());
        }
    }

    private record Preview(BlockPos candidate, InteractionHand hand) {
    }

    /**
     * Fresh preview wins; otherwise the sticky one survives a few ticks as
     * long as its cell is still placeable. A placed block is never
     * replaceable, so the outline can never linger on it.
     */
    private static Preview resolveShown(Minecraft minecraft, Preview fresh) {
        if (fresh != null) {
            stickyPreview = fresh;
            stickyUntilTick = clientTicks + STICKY_TICKS;
            return fresh;
        }
        if (stickyPreview != null && clientTicks <= stickyUntilTick && stillReplaceable(minecraft, stickyPreview)) {
            return stickyPreview;
        }
        stickyPreview = null;
        return null;
    }

    private static boolean stillReplaceable(Minecraft minecraft, Preview preview) {
        try {
            if (minecraft == null || minecraft.level == null || preview == null) {
                return false;
            }
            return BedrockPlacingBrain.isReplaceable(minecraft.level, preview.candidate());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Read-only mirror of the server's placement checks. Returns the candidate
     * cell plus the hand holding the block, or {@code null} when the assist
     * would not fire (so no preview and no packet).
     */
    static Preview computePreview(Minecraft minecraft) {
        if (!BedrockPlacingMod.isReady() || !BedrockPlacingMod.isModEnabled()
                || !BedrockPlacingMod.previewEnabled()) {
            return null;
        }
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || MycelScreens.current() != null) {
            return null;
        }
        // No outline for a server that could not act on it (vanilla or without the mod).
        if (!MycelNetwork.canSendToServer(BedrockPlacingMod.assistPacket)) {
            return null;
        }
        HitResult hit = minecraft.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.MISS) {
            // Vanilla handles aimed-at blocks itself; only preview air clicks.
            return null;
        }
        InteractionHand hand = handWithBlock(player);
        if (hand == null || player.isSpectator() || !player.isAlive()) {
            return null;
        }
        if (player.isCrouching() && BedrockPlacingMod.sneakDisables()) {
            return null;
        }
        Direction dir = player.getDirection();
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                player.blockPosition(), dir,
                pos -> BedrockPlacingBrain.isStoodUpon(level, pos, player.getY()),
                pos -> BedrockPlacingBrain.isSupport(level, pos, dir),
                pos -> BedrockPlacingBrain.isReplaceable(level, pos),
                level.getMinY(), level.getMaxY());
        if (target.isEmpty()) {
            return null;
        }
        BedrockPlacingBrain.Target resolved = target.get();
        // Same mirror of the server's layer lock: grounded anchors re-lock,
        // airborne ones must target the locked layer.
        boolean groundedAnchor = resolved.anchor().equals(player.blockPosition().below());
        if (!LAYER_LOCK.check(groundedAnchor, resolved.anchor().getY(), level.dimension(), clientTicks)) {
            return null;
        }
        if (!level.getWorldBorder().isWithinBounds(resolved.candidate())) {
            return null;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!player.mayUseItemAt(resolved.candidate(), resolved.face(), stack)) {
            return null;
        }
        if (!BedrockPlacingBrain.isWithinReach(player.getEyePosition(), resolved.candidate(),
                BedrockPlacingMod.effectiveReach())) {
            return null;
        }
        return new Preview(resolved.candidate(), hand);
    }

    private static InteractionHand handWithBlock(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BlockItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** Submits one outline box through vanilla's per-tick gizmo flow. Never throws. */
    private static void drawGizmo(Minecraft minecraft, BlockPos candidate) {
        try (var ignored = minecraft.collectPerTickGizmos()) {
            Gizmos.cuboid(candidate, GizmoStyle.stroke(PREVIEW_COLOR));
        } catch (RuntimeException e) {
            MycelLog.warnMod(BedrockPlacingMod.ID, "Preview outline failed; skipping: {}", e.toString());
        }
    }
}
