package my.celium.org.bedrockplacing.fabric;

import java.util.HashSet;
import java.util.Set;

import my.celium.org.bedrockplacing.AssistPlacePacket;
import my.celium.org.bedrockplacing.BedrockPlacingMod;
import my.celium.org.logging.MycelLog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Fabric wiring for the {@link AssistPlacePacket}, kept manual and
 * idempotent.
 *
 * <p>Mycel 1.2.1's {@code FabricNetworking} registers serverbound payload
 * types without a duplicate guard, so a second {@code flushClient()} crashes
 * the game once any {@code TO_SERVER} packet exists. This class registers our
 * packet exactly once no matter how many entrypoints call it, and the common
 * module skips {@code MycelNetwork} registration on Fabric accordingly.
 */
final class BedrockPlacingFabricNetworking {
    private static final Set<Identifier> REGISTERED = new HashSet<>();

    private BedrockPlacingFabricNetworking() {
    }

    /** Registers type + server receiver. Safe to call from every entrypoint. */
    static synchronized void registerAssist() {
        if (!REGISTERED.add(AssistPlacePacket.TYPE.id())) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(AssistPlacePacket.TYPE, AssistPlacePacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AssistPlacePacket.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> {
                try {
                    BedrockPlacingMod.tryAssist(sender,
                            payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                } catch (RuntimeException e) {
                    MycelLog.warnMod(BedrockPlacingMod.ID, "Assist packet handler threw: {}", e.toString());
                }
            });
        });
    }
}
