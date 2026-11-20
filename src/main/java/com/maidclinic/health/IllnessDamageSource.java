package com.maidclinic.health;

import com.maidclinic.util.Msg;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 疾病致死伤害：死亡提示带上具体病名，而不是原版“魔法”击杀提示。
 * <p>{@code condition} 允许为 null（找不到对应病种定义时），此时退回通用病亡提示，
 * 保证所有疾病伤害都用同一个伤害来源类型，方便按类型识别。</p>
 */
final class IllnessDamageSource extends DamageSource {
    private final Condition condition;

    IllnessDamageSource(Holder<DamageType> type, Condition condition) {
        super(type);
        this.condition = condition;
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        if (condition == null) {
            return Component.translatable("death.attack.maidclinic.illness", victim.getDisplayName());
        }
        return Component.translatable("death.maidclinic.illness",
                victim.getDisplayName(), Msg.tr(condition.nameKey()));
    }
}
