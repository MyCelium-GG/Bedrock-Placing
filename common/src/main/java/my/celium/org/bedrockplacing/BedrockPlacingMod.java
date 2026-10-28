package my.celium.org.bedrockplacing;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.metadata.ModMetadata;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * BedrockPlacing: right-click the air with a block to extend the floor you
 * stand on in the direction you face — Bedrock Education style.
 *
 * <p>Two paths feed the same server-side assist ({@link #tryAssist}):
 * <ul>
 *   <li>the fast path — the modded client sends {@link AssistPlacePacket} the
 *   tick the use key goes down (and at most every {@code repeatTicks} while
 *   held), bypassing vanilla's sluggish 4-tick use repeat;</li>
 *   <li>the fallback path — each loader's native "right-clicked air" event,
 *   which also keeps vanilla clients working against a modded server.</li>
 * </ul>
 * Same-tick duplicates between the two paths are dropped, so a click never
 * places twice. Client calls are a no-op {@code PASS} so vanilla's packet
 * still reaches the server exactly once.
 *
 * <p>The whole mod is this file plus {@link BedrockPlacingBrain} (placement),
 * {@link AssistPlacePacket} (fast path) and the client preview: metadata,
 * config, and entry points. File IO, loader events, platform detection,
 * networking and diagnostics all come from Mycel.
 */
public final class BedrockPlacingMod {
    public static final String ID = "bedrockplacing";

    public static final ModMetadata META = ModMetadata.builder(ID, "BedrockPlacing", "1.1.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/bedrock-placing")
            .updateUrl("https://wiki.mycelium-gg.org/updates/bedrockplacing.json")
            .build();

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<Boolean> sneakDisables;
    public static ConfigValue<Boolean> requireSupport;
    public static ConfigValue<Double> reach;
    public static ConfigValue<Boolean> preview;
    public static ConfigValue<Integer> repeatTicks;
    public static MycelConfig config;

    public static PacketDefinition<AssistPlacePacket> assistPacket;

    /**
     * Last server tick each player was assisted on. A click reaches the server
     * twice (fast packet + vanilla air-click event); the second arrival in the
     * same tick is a duplicate, not a second click.
     */
    private static final ConcurrentHashMap<UUID, Integer> LAST_ASSIST_TICK = new ConcurrentHashMap<>();

    private BedrockPlacingMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        ConfigBuilder builder = Mycel.config(META);
        enabled = builder.booleanValue("enabled", true,
                "Whether Bedrock-style air-click placement is active.");
        sneakDisables = builder.booleanValue("sneakDisables", true,
                "Whether sneaking skips the assist (hold sneak to right-click air normally).");
        requireSupport = builder.booleanValue("requireSupport", true,
                "Whether the anchor block under you must have a sturdy side face.");
        reach = builder.doubleValue("reach", 4.5, 2.0, 6.0,
                "Maximum eye-to-target distance in blocks for assisted placement.");
        preview = builder.booleanValue("preview", true,
                "Whether to outline the block cell that would be placed (client side).");
        repeatTicks = builder.intValue("repeatTicks", 2, 1, 4,
                "Client ticks between assisted placements while holding right-click (vanilla uses 4).");
        config = builder.build();

        assistPacket = PacketDefinition.toServer(AssistPlacePacket.TYPE, AssistPlacePacket.CODEC,
                (packet, ctx) -> {
                    ServerPlayer sender = ctx.sender();
                    if (sender != null) {
                        tryAssist(sender,
                                packet.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    }
                });
        // Optional on every loader (Mycel 1.4.0+): on a server without the
        // mod the client simply never sends it.
        MycelNetwork.register(META, assistPacket);
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID) && enabled.get();
    }

    /** Whether {@link #init()} has run (config available). The client tick checks this first. */
    public static boolean isReady() {
        return enabled != null;
    }

    public static boolean sneakDisables() {
        return sneakDisables.get();
    }

    public static boolean requireSupport() {
        return requireSupport.get();
    }

    /** Reads the reach config defensively clamped to its documented range. */
    public static double effectiveReach() {
        return Math.max(2.0, Math.min(6.0, reach.get()));
    }

    public static boolean previewEnabled() {
        return preview.get();
    }

    /** Reads the repeat config defensively clamped to its documented range. */
    public static int effectiveRepeatTicks() {
        return Math.max(1, Math.min(4, repeatTicks.get()));
    }

    /**
     * Loader-agnostic air-click hook. Only the logical server may place;
     * every other case returns {@code PASS} so vanilla keeps handling the
     * click.
     */
    public static InteractionResult onRightClickAir(Player player, Level level, InteractionHand hand) {
        if (!isModEnabled()) {
            return InteractionResult.PASS;
        }
        if (player == null || level == null || hand == null || level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        boolean placed = tryAssist(serverPlayer, hand);
        return placed ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Central server-side assist with same-tick duplicate suppression. Both
     * the fast packet path and the vanilla air-click event funnel through
     * here; whichever arrives second in a tick is ignored.
     *
     * @return {@code true} when a block was placed
     */
    public static boolean tryAssist(ServerPlayer player, InteractionHand hand) {
        if (!isModEnabled() || player == null || hand == null) {
            return false;
        }
        if (!BedrockPlacingBrain.wantsAssist(player, player.level(), hand)) {
            return false;
        }
        int tick = player.level().getServer().getTickCount();
        Integer last = LAST_ASSIST_TICK.put(player.getUUID(), tick);
        if (LAST_ASSIST_TICK.size() > 512) {
            LAST_ASSIST_TICK.entrySet().removeIf(entry -> tick - entry.getValue() > 100);
        }
        if (last != null && last == tick) {
            return false;
        }
        return BedrockPlacingBrain.tryPlace(player, player.level(), hand);
    }
}
