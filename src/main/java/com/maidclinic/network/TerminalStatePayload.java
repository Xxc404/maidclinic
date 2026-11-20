package com.maidclinic.network;

import com.maidclinic.MaidClinic;
import com.maidclinic.terminal.TerminalState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalStatePayload(TerminalState state) implements CustomPacketPayload {
    public static final Type<TerminalStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidClinic.MOD_ID, "terminal_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalStatePayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> TerminalState.STREAM_CODEC.encode(buffer, payload.state()),
                    buffer -> new TerminalStatePayload(TerminalState.STREAM_CODEC.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
