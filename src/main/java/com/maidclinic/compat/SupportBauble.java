package com.maidclinic.compat;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.health.SupportService;
import net.minecraft.world.item.ItemStack;

/** 支援兵战备的饰品行为。 */
public final class SupportBauble implements IMaidBauble {
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        SupportService.tick(maid);
    }
}
