package my.celium.org.bedrockplacing.fabric;

import my.celium.org.bedrockplacing.BedrockPlacingMod;
import my.celium.org.fabric.FabricNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

/**
 * Fabric entrypoint: forwards air right-clicks with an item into the common
 * Bedrock-style placement hook. The hook itself only places on the logical
 * server, so the client's {@code PASS} just lets the packet travel.
 */
public final class BedrockPlacingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mycel")) {
            my.celium.org.bedrockplacing.MissingMycel.logOnce();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                    (handler, sender, server) -> my.celium.org.bedrockplacing.MissingMycel.tell(handler.getPlayer()));
            return;
        }
        my.celium.org.bedrockplacing.ModGate.open();
        BedrockPlacingMod.init();
        // The packet may be registered after Mycel's own entrypoint ran.
        FabricNetworking.flush();
        UseItemCallback.EVENT.register(
                (player, level, hand) -> BedrockPlacingMod.onRightClickAir(player, level, hand));
    }
}
