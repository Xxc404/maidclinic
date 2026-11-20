package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 医师徽章：装备在女仆饰品栏后，该女仆会定期治疗附近的其它女仆。
 * <p>4 个等级对应不同的可处理严重程度、成功率与冷却。</p>
 */
public class DoctorBadgeItem extends Item {
    private final int level;

    public DoctorBadgeItem(Properties properties, int level) {
        super(properties);
        this.level = level;
    }

    public int level() {
        return level;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Msg.tr("tooltip.maidclinic.doctor_badge.level." + level).withStyle(ChatFormatting.GRAY));
        tooltip.add(Msg.tr("tooltip.maidclinic.doctor_badge.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
