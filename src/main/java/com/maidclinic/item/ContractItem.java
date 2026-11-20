package com.maidclinic.item;

import com.maidclinic.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 家庭医生签约书。
 * <p>
 * 这是模组里“关系”的最高阶段，但它是<b>职业关系</b>：
 * 玩家成为女仆的签约家庭医生，负责长期健康管理，不涉及任何恋爱内容。
 */
public class ContractItem extends AbstractCareItem {
    public ContractItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Msg.tr("tooltip.maidclinic.contract").withStyle(ChatFormatting.GRAY));
        addUsageLine(tooltip);
    }
}
