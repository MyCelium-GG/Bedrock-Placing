package my.celium.org.bedrockplacing;

import my.celium.org.stats.ModStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Anonymous stats for the FastStats project {@code bedrock-placing}, sent through Mycel under its
 * consent: how many blocks get placed by bridging, which ones, whether the client has the mod
 * (fast packet) or not (vanilla air-click), and the settings.
 */
public final class PlacingStats {
    private static final String TOKEN = "71577de4a85580cebb552b5b6d76f494";

    private static volatile ModStats stats;

    private PlacingStats() {
    }

    static void start() {
        stats = ModStats.builder(BedrockPlacingMod.META, TOKEN)
                .bool("enabled", () -> BedrockPlacingMod.enabled.get())
                .bool("sneak_disables", () -> BedrockPlacingMod.sneakDisables.get())
                .bool("require_support", () -> BedrockPlacingMod.requireSupport.get())
                .bool("preview", () -> BedrockPlacingMod.preview.get())
                .number("reach", () -> BedrockPlacingMod.reach.get())
                .number("repeat_ticks", () -> BedrockPlacingMod.repeatTicks.get())
                .counters("placed_blocks")
                .errors("my.celium.org.bedrockplacing.")
                .start();
    }

    /** A block was placed by the assist. */
    static void placed(Block block) {
        ModStats current = stats;
        if (current == null) {
            return;
        }
        current.count("place");
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        current.count("placed_blocks", "minecraft".equals(key.getNamespace()) ? key.getPath() : key.toString());
    }

    /** Which path a placement came through: {@code via_packet} (mod on the client) or {@code via_click}. */
    static void path(String what) {
        ModStats current = stats;
        if (current != null) {
            current.count(what);
        }
    }
}
