package my.celium.org.bedrockplacing.neoforge;

import my.celium.org.bedrockplacing.BedrockPlacingMod;
import my.celium.org.bedrockplacing.client.BedrockPlacingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * NeoForge entrypoint: forwards air right-clicks with an item into the common
 * Bedrock-style placement hook, and (client only) runs the preview + fast
 * assist tick.
 */
@Mod(BedrockPlacingMod.ID)
public final class BedrockPlacingNeoForge {
    public BedrockPlacingNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        BedrockPlacingMod.init();
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            // Listener body only ever runs on the client, so the dedicated
            // server never loads client classes.
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTick());
        }
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        InteractionResult result =
                BedrockPlacingMod.onRightClickAir(event.getEntity(), event.getLevel(), event.getHand());
        if (result == InteractionResult.SUCCESS) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static void onClientTick() {
        BedrockPlacingClient.clientTick(Minecraft.getInstance());
    }
}
