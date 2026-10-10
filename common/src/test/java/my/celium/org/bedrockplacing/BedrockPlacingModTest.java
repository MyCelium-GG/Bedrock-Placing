package my.celium.org.bedrockplacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.netty.buffer.Unpooled;
import my.celium.org.Mycel;
import my.celium.org.internal.PlatformHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionResult;

/** The mod boots on Mycel with a tiny, well-defaulted config. */
class BedrockPlacingModTest {
    @BeforeAll
    static void initOnce(@TempDir Path tempDir) {
        PlatformHolder.setForTesting(new BedrockPlacingTestPlatform(tempDir));
        BedrockPlacingMod.init();
    }

    @Test
    void registersWithMycel() {
        assertTrue(Mycel.getRegisteredMods().contains(BedrockPlacingMod.META));
        assertEquals("bedrockplacing", BedrockPlacingMod.META.id());
    }

    @Test
    void configDefaults() {
        assertTrue(BedrockPlacingMod.enabled.get());
        assertTrue(BedrockPlacingMod.sneakDisables.get());
        assertTrue(BedrockPlacingMod.requireSupport.get());
        assertEquals(4.5, BedrockPlacingMod.reach.get());
        assertEquals(4.5, BedrockPlacingMod.effectiveReach());
        assertTrue(BedrockPlacingMod.preview.get());
        assertTrue(BedrockPlacingMod.previewEnabled());
        assertEquals(2, BedrockPlacingMod.repeatTicks.get());
        assertEquals(2, BedrockPlacingMod.effectiveRepeatTicks());
        assertTrue(BedrockPlacingMod.isReady());
    }

    @Test
    void enableToggleFlowsThrough() {
        assertTrue(BedrockPlacingMod.isModEnabled());
        Mycel.setEnabled(BedrockPlacingMod.ID, false);
        try {
            assertTrue(!BedrockPlacingMod.isModEnabled());
        } finally {
            Mycel.setEnabled(BedrockPlacingMod.ID, true);
        }
        assertTrue(BedrockPlacingMod.isModEnabled());
    }

    @Test
    void airClickHookToleratesNulls() {
        // Loader hooks must never crash the game.
        assertEquals(InteractionResult.PASS, BedrockPlacingMod.onRightClickAir(null, null, null));
    }

    @Test
    void assistPacketRegistered() {
        assertTrue(BedrockPlacingMod.assistPacket != null);
        assertEquals("bedrockplacing:assist", BedrockPlacingMod.assistPacket.id().toString());
    }

    @Test
    void assistPacketRoundTrips() {
        for (boolean offhand : new boolean[] {false, true}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            AssistPlacePacket.CODEC.encode(buf, new AssistPlacePacket(offhand));
            assertEquals(new AssistPlacePacket(offhand), AssistPlacePacket.CODEC.decode(buf));
            buf.release();
        }
    }

    @Test
    void tryAssistToleratesNulls() {
        assertFalse(BedrockPlacingMod.tryAssist(null, null));
    }
}
