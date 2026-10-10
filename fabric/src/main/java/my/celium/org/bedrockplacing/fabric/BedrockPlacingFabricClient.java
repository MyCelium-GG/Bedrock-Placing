package my.celium.org.bedrockplacing.fabric;

import my.celium.org.bedrockplacing.client.BedrockPlacingClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric client entrypoint: preview outline + fast assist path live here, so
 * dedicated servers never touch client classes.
 */
public final class BedrockPlacingFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Idempotent: the main entrypoint already registered on integrated
        // servers/clients; the guard makes the second call a no-op.
        BedrockPlacingFabricNetworking.registerAssist();
        ClientTickEvents.END_CLIENT_TICK.register(BedrockPlacingClient::clientTick);
    }
}
