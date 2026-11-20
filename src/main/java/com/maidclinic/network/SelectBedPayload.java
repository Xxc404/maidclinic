package com.maidclinic.network;

import com.maidclinic.MaidClinic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectBedPayload(BlockPos terminal, BlockPos bed) implements CustomPacketPayload {
    public static final Type<SelectBedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidClinic.MOD_ID, "select_bed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectBedPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeBlockPos(payload.terminal());
                buffer.writeBlockPos(payload.bed());
            }, buffer -> new SelectBedPayload(buffer.readBlockPos(), buffer.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
