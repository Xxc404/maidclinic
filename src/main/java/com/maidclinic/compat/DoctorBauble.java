package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.health.DoctorService;
import net.minecraft.world.item.ItemStack;

/**
 * 医师徽章的饰品行为：每个 tick 交给 {@link DoctorService} 处理（内部自带冷却）。
 */
public final class DoctorBauble implements IMaidBauble {
    private final int level;

    public DoctorBauble(int level) {
        this.level = level;
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        DoctorService.tick(maid, level);
    }
}
