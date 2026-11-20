package com.maidclinic.registry;

import com.maidclinic.MaidClinic;
import com.maidclinic.health.Treatment;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidClinic.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.maidclinic.main"))
                    .icon(() -> new ItemStack(ModItems.STETHOSCOPE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.STETHOSCOPE.get());
                        output.accept(ModItems.THERMOMETER.get());
                        output.accept(ModItems.SPHYGMOMANOMETER.get());
                        output.accept(ModItems.OXIMETER.get());
                        output.accept(ModItems.GLUCOMETER.get());
                        output.accept(ModItems.HEALTH_RECORD.get());
                        output.accept(ModItems.CARE_MANUAL.get());
                        output.accept(ModItems.DOCTOR_CONTRACT.get());
                        output.accept(ModItems.MEDICAL_MASK.get());
                        output.accept(ModItems.DOCTOR_BADGE_4.get());
                        output.accept(ModItems.SUPPORT_KIT.get());
                        for (Treatment treatment : Treatment.values()) {
                            output.accept(ModItems.MEDICINES.get(treatment).get());
                        }
                        output.accept(ModItems.FIRST_AID_KIT.get());
                        output.accept(ModItems.HOSPITAL_BED.get());
                        output.accept(ModItems.SMART_TERMINAL.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
