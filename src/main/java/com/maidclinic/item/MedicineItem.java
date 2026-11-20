package com.maidclinic.item;

import com.maidclinic.health.Treatment;
import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * 药品/护理耗材。
 * <p>
 * 物品本身不包含疗效逻辑，疗效由 {@link com.maidclinic.health.Condition} 的
 * primary / supportive / harmful 声明决定，避免“药罐子里写死规则”。
 */
public class MedicineItem extends AbstractCareItem {
    private final Treatment treatment;

    public MedicineItem(Properties properties, Treatment treatment) {
        super(properties);
        this.treatment = treatment;
    }

    public Treatment treatment() {
        return treatment;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = treatment.name().toLowerCase(Locale.ROOT);
        tooltip.add(Msg.tr("tooltip.maidclinic.treatment." + key).withStyle(ChatFormatting.GRAY));
        tooltip.add(Msg.tr("tooltip.maidclinic.caution." + key).withStyle(ChatFormatting.DARK_GRAY));
        addUsageLine(tooltip);
    }
}
