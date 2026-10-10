package my.celium.org.bedrockplacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Headless tests for the bridging geometry. Pure positions and predicates —
 * no registries, no world needed.
 */
class BedrockPlacingBrainTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static Predicate<BlockPos> solid(BlockPos... blocks) {
        Set<BlockPos> set = Set.of(blocks);
        return set::contains;
    }

    private static Predicate<BlockPos> replaceableExcept(BlockPos... occupied) {
        Set<BlockPos> set = Set.of(occupied);
        return pos -> !set.contains(pos);
    }

    @Test
    void standingOnBlockExtendsForward() {
        BlockPos feet = new BlockPos(0, 64, 0);
        BlockPos support = new BlockPos(0, 63, 0);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.SOUTH, solid(), solid(support), replaceableExcept(), MIN_Y, MAX_Y);
        assertTrue(target.isPresent());
        assertEquals(support, target.get().anchor());
        assertEquals(Direction.SOUTH, target.get().face());
        assertEquals(new BlockPos(0, 63, 1), target.get().candidate());
    }

    @Test
    void walkedPastEdgeReachesBack() {
        // Player moved one block past the edge but hasn't fallen a full block:
        // anchor behind under the feet, candidate at bridge level.
        BlockPos feet = new BlockPos(0, 64, 1);
        BlockPos support = new BlockPos(0, 63, 0);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.SOUTH, solid(), solid(support), replaceableExcept(), MIN_Y, MAX_Y);
        assertTrue(target.isPresent());
        assertEquals(support, target.get().anchor());
        assertEquals(new BlockPos(0, 63, 1), target.get().candidate());
    }

    @Test
    void partialFloorAnchorsFeetCell() {
        // Standing on a slab/path inside the feet cell: the feet cell itself
        // is the layer being built, not the ground beneath it.
        BlockPos feet = new BlockPos(0, 64, 0);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.SOUTH, solid(feet), solid(new BlockPos(0, 63, 0)),
                replaceableExcept(), MIN_Y, MAX_Y);
        assertTrue(target.isPresent());
        assertEquals(feet, target.get().anchor());
        assertEquals(new BlockPos(0, 64, 1), target.get().candidate());
    }

    @Test
    void noSupportMeansNoTarget() {
        BlockPos feet = new BlockPos(0, 100, 0);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.EAST, solid(), solid(), replaceableExcept(), MIN_Y, MAX_Y);
        assertTrue(target.isEmpty());
    }

    @Test
    void occupiedCandidateIsRejected() {
        BlockPos feet = new BlockPos(0, 64, 0);
        BlockPos support = new BlockPos(0, 63, 0);
        BlockPos candidate = new BlockPos(0, 63, 1);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.SOUTH, solid(), solid(support), replaceableExcept(candidate), MIN_Y, MAX_Y);
        assertTrue(target.isEmpty());
    }

    @Test
    void neverPlacesIntoOwnBody() {
        // Degenerate predicate world: everything solid and replaceable.
        BlockPos feet = new BlockPos(0, 64, 0);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.NORTH, pos -> false, pos -> true, pos -> true, MIN_Y, MAX_Y);
        assertTrue(target.isPresent());
        assertFalse(target.get().candidate().equals(feet));
        assertFalse(target.get().candidate().equals(feet.above()));
    }

    @Test
    void verticalFacingFlattensToNorth() {
        BlockPos feet = new BlockPos(5, 64, 5);
        BlockPos support = new BlockPos(5, 63, 5);
        Optional<BedrockPlacingBrain.Target> target = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.UP, solid(), solid(support), replaceableExcept(), MIN_Y, MAX_Y);
        assertTrue(target.isPresent());
        assertEquals(Direction.NORTH, target.get().face());
        assertEquals(new BlockPos(5, 63, 4), target.get().candidate());
    }

    @Test
    void buildHeightIsRespected() {
        // Anchor (and therefore the candidate, always at anchor height) would
        // sit below the world floor.
        Optional<BedrockPlacingBrain.Target> below = BedrockPlacingBrain.resolveBridgingTarget(
                new BlockPos(0, -64, 0), Direction.SOUTH, solid(), solid(new BlockPos(0, -65, 0)),
                replaceableExcept(), -64, MAX_Y);
        assertTrue(below.isEmpty());
        // Sanity: same setup inside limits works.
        BlockPos feet = new BlockPos(0, 63, 0);
        BlockPos support = new BlockPos(0, 62, 0);
        Optional<BedrockPlacingBrain.Target> ok = BedrockPlacingBrain.resolveBridgingTarget(
                feet, Direction.SOUTH, solid(), solid(support), replaceableExcept(), MIN_Y, MAX_Y);
        assertTrue(ok.isPresent());
    }

    @Test
    void reachCheckUsesCandidateCenter() {
        BlockPos candidate = new BlockPos(0, 63, 1);
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        assertTrue(BedrockPlacingBrain.isWithinReach(eye, candidate, 4.5));
        assertFalse(BedrockPlacingBrain.isWithinReach(new Vec3(30.5, 65.62, 0.5), candidate, 4.5));
    }

    @Test
    void rejectsBadInput() {
        BlockPos feet = new BlockPos(0, 64, 0);
        assertTrue(BedrockPlacingBrain
                .resolveBridgingTarget(null, Direction.SOUTH, solid(), solid(feet.below()), replaceableExcept(), MIN_Y, MAX_Y)
                .isEmpty());
        assertTrue(BedrockPlacingBrain
                .resolveBridgingTarget(feet, null, solid(), solid(feet.below()), replaceableExcept(), MIN_Y, MAX_Y)
                .isEmpty());
        assertFalse(BedrockPlacingBrain.isWithinReach(null, feet, 4.5));
        assertFalse(BedrockPlacingBrain.isWithinReach(Vec3.ZERO, null, 4.5));
        assertFalse(BedrockPlacingBrain.isWithinReach(Vec3.ZERO, feet, -1));
    }

    @Test
    void tryPlaceToleratesNulls() {
        assertFalse(BedrockPlacingBrain.tryPlace(null, null, null));
        assertFalse(BedrockPlacingBrain.wantsAssist(null, null, null));
    }
}
