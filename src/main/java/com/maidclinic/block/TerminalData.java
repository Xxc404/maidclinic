package com.maidclinic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 智能终端 → 多张病床 的绑定关系。存档级保存，不依赖方块实体。 */
public final class TerminalData extends SavedData {
    public static final int MAX_BEDS = 16;

    private static final String FILE_ID = "maidclinic_terminals";
    private static final SavedData.Factory<TerminalData> FACTORY =
            new SavedData.Factory<>(TerminalData::new, TerminalData::load, DataFixTypes.LEVEL);

    public enum BindResult {
        ADDED,
        ALREADY_BOUND,
        FULL
    }

    private final Map<BlockPos, List<BlockPos>> bindings = new HashMap<>();

    public static TerminalData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** 返回该终端绑定的病床（最多 {@link #MAX_BEDS} 张），不会返回可变列表。 */
    public List<BlockPos> bedsFor(BlockPos terminal) {
        List<BlockPos> beds = bindings.get(terminal);
        return beds == null ? List.of() : List.copyOf(beds);
    }

    public BindResult bind(BlockPos terminal, BlockPos bed) {
        List<BlockPos> beds = bindings.computeIfAbsent(terminal.immutable(), key -> new ArrayList<>());
        if (beds.contains(bed)) {
            return BindResult.ALREADY_BOUND;
        }
        if (beds.size() >= MAX_BEDS) {
            return BindResult.FULL;
        }
        beds.add(bed.immutable());
        setDirty();
        return BindResult.ADDED;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, List<BlockPos>> entry : bindings.entrySet()) {
            for (BlockPos bed : entry.getValue()) {
                CompoundTag binding = new CompoundTag();
                binding.putLong("terminal", entry.getKey().asLong());
                binding.putLong("bed", bed.asLong());
                list.add(binding);
            }
        }
        tag.put("bindings", list);
        return tag;
    }

    public static TerminalData load(CompoundTag tag, HolderLookup.Provider registries) {
        TerminalData data = new TerminalData();
        ListTag list = tag.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag binding = list.getCompound(i);
            if (!binding.contains("terminal") || !binding.contains("bed")) {
                continue;
            }
            BlockPos terminal = BlockPos.of(binding.getLong("terminal"));
            BlockPos bed = BlockPos.of(binding.getLong("bed"));
            List<BlockPos> beds = data.bindings.computeIfAbsent(terminal, key -> new ArrayList<>());
            if (!beds.contains(bed) && beds.size() < MAX_BEDS) {
                beds.add(bed);
            }
        }
        return data;
    }
}
