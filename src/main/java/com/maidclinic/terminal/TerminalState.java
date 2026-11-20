package com.maidclinic.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/** 服务端生成、客户端只读渲染的终端快照。 */
public record TerminalState(
        BlockPos terminal,
        List<BedSummary> beds,
        BlockPos selected,
        boolean detailReady,
        List<Component> detail) {

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalState> STREAM_CODEC =
            StreamCodec.of(TerminalState::write, TerminalState::read);

    public record BedSummary(BlockPos bed, String name, int statusColor, boolean occupied, boolean valid) {
    }

    public TerminalState {
        beds = List.copyOf(beds);
        detail = List.copyOf(detail);
    }

    private static void write(RegistryFriendlyByteBuf buffer, TerminalState state) {
        buffer.writeBlockPos(state.terminal);
        buffer.writeVarInt(state.beds.size());
        for (BedSummary bed : state.beds) {
            buffer.writeBlockPos(bed.bed());
            buffer.writeUtf(bed.name(), 64);
            buffer.writeInt(bed.statusColor());
            buffer.writeBoolean(bed.occupied());
            buffer.writeBoolean(bed.valid());
        }
        buffer.writeBoolean(state.selected != null);
        if (state.selected != null) {
            buffer.writeBlockPos(state.selected);
        }
        buffer.writeBoolean(state.detailReady);
        buffer.writeVarInt(state.detail.size());
        for (Component line : state.detail) {
            ComponentSerialization.STREAM_CODEC.encode(buffer, line);
        }
    }

    private static TerminalState read(RegistryFriendlyByteBuf buffer) {
        BlockPos terminal = buffer.readBlockPos();
        int bedCount = buffer.readVarInt();
        List<BedSummary> beds = new ArrayList<>(bedCount);
        for (int i = 0; i < bedCount; i++) {
            beds.add(new BedSummary(buffer.readBlockPos(), buffer.readUtf(64),
                    buffer.readInt(), buffer.readBoolean(), buffer.readBoolean()));
        }
        BlockPos selected = buffer.readBoolean() ? buffer.readBlockPos() : null;
        boolean ready = buffer.readBoolean();
        int lineCount = buffer.readVarInt();
        List<Component> detail = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            detail.add(ComponentSerialization.STREAM_CODEC.decode(buffer));
        }
        return new TerminalState(terminal, beds, selected, ready, detail);
    }
}
