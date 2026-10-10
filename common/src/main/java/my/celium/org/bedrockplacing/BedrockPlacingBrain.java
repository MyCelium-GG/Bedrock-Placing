package my.celium.org.bedrockplacing;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import my.celium.org.logging.MycelLog;

/**
 * Bedrock-style bridging brain: no loader APIs, just placement logic plus a
 * server-side entry point.
 *
 * <p>Java Edition only places a block when the crosshair targets an existing
 * block face, which forces bridge-builders to aim down at the edge they stand
 * on. Bedrock (and Bedrock Education) lets you look straight ahead and extend
 * the floor you stand on. This brain reproduces that: when right-clicking the
 * <em>air</em> with a block in hand, the block under (or just behind under)
 * the player's feet becomes the anchor, and the new block is placed on the
 * anchor's side facing the player's horizontal look direction — going through
 * vanilla's own {@link Block#getStateForPlacement} path so orientation,
 * waterlogging and shape logic behave exactly like a normal click.
 *
 * <p>Geometry ({@link #resolveBridgingTarget}) is pure and headless-testable;
 * {@link #tryPlace} does the world-mutating part on the server thread only.
 * Placement is locked to one horizontal layer via {@link LayerLock}: grounded
 * anchors always work and re-lock the layer, airborne anchors (walked off the
 * edge) only work on the locked layer, so jumping or falling can never place
 * a block above or below the floor being built.
 */
public final class BedrockPlacingBrain {
    private BedrockPlacingBrain() {
    }

    /** One build-layer lock per player (server thread only, map is thread-safe anyway). */
    private static final ConcurrentHashMap<UUID, LayerLock> LAYER_LOCKS = new ConcurrentHashMap<>();

    /** The caller's layer lock, creating and occasionally evicting entries. */
    static LayerLock layerLockFor(ServerPlayer player) {
        LayerLock lock = LAYER_LOCKS.computeIfAbsent(player.getUUID(), id -> new LayerLock());
        if (LAYER_LOCKS.size() > 512) {
            long now = player.level().getServer().getTickCount();
            LAYER_LOCKS.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick() > LayerLock.LOCK_TICKS);
        }
        return lock;
    }

    /** Anchor block that was clicked "virtually" plus the cell to fill. */
    public record Target(BlockPos anchor, Direction face, BlockPos candidate) {
    }

    /** Fall distance above which an anchor no longer counts as standing. */
    static final double GROUNDED_FALL_EPSILON = 0.5;
    /** How exactly the feet must rest on a collision top to count as standing on it. */
    static final double STOOD_UPON_EPSILON = 0.03;

    /**
     * Whether an anchor counts as standing: it is the feet cell or the one
     * below, and the player has not fallen onto it. Dipping into a gap or
     * landing in a pit below the build layer fails this even when the cell
     * under the feet happens to be solid, so those anchors go through the
     * layer lock instead of re-locking it. Headless-testable.
     */
    public static boolean isGroundedAnchor(BlockPos anchor, BlockPos feetPos, double fallDistance) {
        if (anchor == null || feetPos == null) {
            return false;
        }
        if (!(anchor.equals(feetPos) || anchor.equals(feetPos.below()))) {
            return false;
        }
        return fallDistance < GROUNDED_FALL_EPSILON;
    }

    /**
     * Whether the player stands on {@code pos}: the cell's collision shape
     * has a top face exactly at {@code feetY}. True for full blocks, slabs,
     * paths and farmland underfoot; false for air, vegetation and liquids, and
     * for solid cells the player is merely inside or falling past. Read-only;
     * safe on either side.
     */
    public static boolean isStoodUpon(Level level, BlockPos pos, double feetY) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        try {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return false;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return false;
            }
            double top = pos.getY();
            for (AABB box : shape.toAabbs()) {
                if (Math.abs(feetY - (top + box.maxY)) < STOOD_UPON_EPSILON) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Pure bridging geometry: standing at {@code feetPos} and facing
     * {@code dir}, find the support block the player stands on — the feet
     * cell itself (slabs, paths and other partial floors), the block under
     * it, or one cell behind under it when the player walked past the edge —
     * and the air cell in front of it.
     *
     * @param feetPos block containing the player's feet
     * @param dir player's horizontal facing (up/down are flattened to north)
     * @param isStoodUpon whether the player stands on that cell (feet cell only)
     * @param isSolid whether a cell can support a neighbour (sturdy face)
     * @param isReplaceable whether the candidate cell may be overwritten
     * @param minY lowest placeable Y (world min build height)
     * @param maxY highest placeable Y (world max build height, exclusive)
     * @return anchor + face + candidate, or empty when nothing sensible exists
     */
    public static Optional<Target> resolveBridgingTarget(BlockPos feetPos, Direction dir,
            Predicate<BlockPos> isStoodUpon, Predicate<BlockPos> isSolid, Predicate<BlockPos> isReplaceable,
            int minY, int maxY) {
        if (feetPos == null || dir == null || isStoodUpon == null || isSolid == null || isReplaceable == null) {
            return Optional.empty();
        }
        Direction horizontal = dir.getAxis() == Direction.Axis.Y ? Direction.NORTH : dir;
        BlockPos underFeet = feetPos.below();
        BlockPos behindUnder = underFeet.relative(horizontal.getOpposite());

        BlockPos anchor = null;
        if (isStoodUpon.test(feetPos)) {
            // Partial floor (slab, path, farmland): the feet cell itself is
            // the layer being built, not the ground beneath it.
            anchor = feetPos;
        } else if (isSolid.test(underFeet)) {
            anchor = underFeet;
        } else if (isSolid.test(behindUnder)) {
            // Player already walked past the edge and is falling: reach back.
            anchor = behindUnder;
        } else {
            return Optional.empty();
        }

        BlockPos candidate = anchor.relative(horizontal);
        if (candidate.getY() < minY || candidate.getY() >= maxY) {
            return Optional.empty();
        }
        if (!isReplaceable.test(candidate)) {
            return Optional.empty();
        }
        // Never replace the cell the player's body occupies.
        if (candidate.equals(feetPos) || candidate.equals(feetPos.above())) {
            return Optional.empty();
        }
        return Optional.of(new Target(anchor.immutable(), horizontal, candidate.immutable()));
    }

    /**
     * Whether the candidate is close enough to the player's eyes to place.
     * Headless-testable: no registries, no world, just vectors.
     */
    public static boolean isWithinReach(Vec3 eyePos, BlockPos candidate, double reach) {
        if (eyePos == null || candidate == null || reach < 0) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(candidate);
        return eyePos.distanceToSqr(center) <= (reach + 1.0) * (reach + 1.0);
    }

    /**
     * Server-side assist: right-clicking the air with a {@link BlockItem} in
     * {@code hand} places one block Bedrock-style. Returns {@code true} when a
     * block was placed (callers should report success / swing).
     *
     * <p>Must be called on the server thread with a {@link ServerPlayer}; the
     * client intentionally does nothing so placement stays authoritative and
     * single (no ghost blocks, no double consume).
     */
    public static boolean tryPlace(ServerPlayer player, ServerLevel level, InteractionHand hand) {
        if (player == null || level == null || hand == null) {
            return false;
        }
        if (player.isSpectator() || player.isRemoved() || !player.isAlive()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (player.isCrouching() && BedrockPlacingMod.sneakDisables()) {
            return false;
        }

        double reach = BedrockPlacingMod.effectiveReach();
        Direction dir = player.getDirection();
        BlockPos feet = player.blockPosition();

        Optional<Target> target = resolveBridgingTarget(feet, dir,
                pos -> isStoodUpon(level, pos, player.getY()),
                pos -> isSupport(level, pos, dir),
                pos -> isReplaceable(level, pos),
                level.getMinY(), level.getMaxY());
        if (target.isEmpty()) {
            return false;
        }
        Target t = target.get();

        // Same-layer lock: extending the floor you stand on is always fine;
        // an airborne anchor (walked off the edge) must target the locked
        // build layer, so jumping or falling can never place a block above or
        // below it.
        boolean groundedAnchor = t.anchor().equals(feet.below());
        if (!layerLockFor(player).check(groundedAnchor, t.anchor().getY(), level.dimension(),
                level.getServer().getTickCount())) {
            return false;
        }

        if (!isWithinReach(player.getEyePosition(), t.candidate(), reach)) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(t.candidate())) {
            return false;
        }
        if (!player.mayUseItemAt(t.candidate(), t.face(), stack)) {
            return false;
        }

        Block block = blockItem.getBlock();
        Vec3 faceCenter = Vec3.atCenterOf(t.anchor()).add(
                t.face().getStepX() * 0.5, t.face().getStepY() * 0.5, t.face().getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(faceCenter, t.face(), t.anchor(), false);
        BlockPlaceContext ctx = new BlockPlaceContext(player, hand, stack, hit);
        BlockState toPlace;
        try {
            toPlace = block.getStateForPlacement(ctx);
        } catch (RuntimeException e) {
            MycelLog.warnMod(BedrockPlacingMod.ID, "State-for-placement failed; skipping assist: {}", e.toString());
            return false;
        }
        if (toPlace == null || !toPlace.canSurvive(level, t.candidate())) {
            return false;
        }

        if (!level.setBlock(t.candidate(), toPlace, Block.UPDATE_ALL)) {
            return false;
        }
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        level.playSound(null, t.candidate(), toPlace.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS, 1.0F, 0.8F);
        // No server-side swing: the clicking client already predicted its own
        // arm swing when right-clicking, and other viewers see the new block.
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        return true;
    }

    /** Whether {@code pos} can anchor a neighbour in {@code dir}. Read-only; safe on either side. */
    public static boolean isSupport(Level level, BlockPos pos, Direction dir) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (!BedrockPlacingMod.requireSupport()) {
            return true;
        }
        try {
            return state.isFaceSturdy(level, pos, dir);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether {@code pos} may be overwritten by assisted placement. Read-only; safe on either side. */
    public static boolean isReplaceable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        try {
            return level.getBlockState(pos).canBeReplaced();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Shared guard for loader entrypoints deciding whether to even try. */
    public static boolean wantsAssist(Player player, Level level, InteractionHand hand) {
        if (player == null || level == null || hand == null || level.isClientSide()) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }
}
