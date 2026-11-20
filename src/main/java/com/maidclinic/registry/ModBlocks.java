package com.maidclinic.registry;

import com.maidclinic.MaidClinic;
import com.maidclinic.block.FirstAidKitBlock;
import com.maidclinic.block.HospitalBedBlock;
import com.maidclinic.block.SmartTerminalBlock;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MaidClinic.MOD_ID);

    public static final DeferredBlock<Block> FIRST_AID_KIT = BLOCKS.register("first_aid_kit",
            () -> new FirstAidKitBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD)));

    /** 病床：原版床的两格结构，但白天也能睡。 */
    public static final DeferredBlock<HospitalBedBlock> HOSPITAL_BED = BLOCKS.register("hospital_bed",
            () -> new HospitalBedBlock(DyeColor.WHITE, BlockBehaviour.Properties.of()
                    .strength(0.2F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 智能终端：绑定病床并显示床上女仆的完整状态。 */
    public static final DeferredBlock<SmartTerminalBlock> SMART_TERMINAL = BLOCKS.register("smart_terminal",
            () -> new SmartTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    private ModBlocks() {
    }
}
