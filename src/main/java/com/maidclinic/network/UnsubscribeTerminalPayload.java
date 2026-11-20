package com.maidclinic.network;

import com.maidclinic.MaidClinic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UnsubscribeTerminalPayload(BlockPos terminal) implements CustomPacketPayload {
    public static final Type<UnsubscribeTerminalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidClinic.MOD_ID, "unsubscribe_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnsubscribeTerminalPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeBlockPos(payload.terminal()),
                    buffer -> new UnsubscribeTerminalPayload(buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
