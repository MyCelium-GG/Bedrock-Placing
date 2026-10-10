package my.celium.org.bedrockplacing;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client {@code ->} server request: "right-clicked with a block, assist me".
 *
 * <p>Sent from the client tick the moment the use key goes down (and then at
 * most every {@code repeatTicks} while held), so assisted placement no longer
 * waits for vanilla's 4-tick use repeat or the client-predict-then-confirm
 * round trip. The server re-validates everything in
 * {@link BedrockPlacingBrain#tryPlace} and drops same-tick duplicates against
 * the vanilla air-click event (see {@link BedrockPlacingMod#tryAssist}), so a
 * click can never place twice.
 */
public record AssistPlacePacket(boolean offhand) implements CustomPacketPayload {
    public static final Type<AssistPlacePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BedrockPlacingMod.ID, "assist"));
    public static final StreamCodec<FriendlyByteBuf, AssistPlacePacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, AssistPlacePacket::offhand, AssistPlacePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
