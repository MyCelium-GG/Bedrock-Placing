package my.celium.org.bedrockplacing.forge;

import my.celium.org.bedrockplacing.BedrockPlacingMod;
import my.celium.org.bedrockplacing.client.BedrockPlacingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint: forwards air right-clicks with an item into the common
 * Bedrock-style placement hook, and (client only) runs the preview + fast
 * assist tick.
 */
@Mod(BedrockPlacingMod.ID)
public final class BedrockPlacingForge {
    public BedrockPlacingForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        BedrockPlacingMod.init();
        PlayerInteractEvent.RightClickItem.BUS.addListener(this::onRightClickItem);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Method body only ever runs on the client, so the dedicated
            // server never loads client classes.
            TickEvent.ClientTickEvent.Post.BUS.addListener(event -> onClientTick());
        }
    }

    /**
     * Returning {@code true} cancels the event (Forge 26.x cancellable-bus
     * style): vanilla item use is skipped and our
     * {@link InteractionResult#SUCCESS} becomes the click result.
     */
    private boolean onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return false;
        }
        InteractionResult result =
                BedrockPlacingMod.onRightClickAir(event.getEntity(), event.getLevel(), event.getHand());
        if (result == InteractionResult.SUCCESS) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        return false;
    }

    private static void onClientTick() {
        BedrockPlacingClient.clientTick(Minecraft.getInstance());
    }
}
