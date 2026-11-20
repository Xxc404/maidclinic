package com.maidclinic.health;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;
import java.util.Set;

/**
 * 支援兵战备：每 20 tick 给 16 格内攻击模式女仆提供支援。
 */
public final class SupportService {
    private static final Set<String> ATTACK_TASKS = Set.of(
            "attack", "ranged_attack", "crossbow_attack", "danmaku_attack",
            "trident_attack", "gun_attack");
    private static final double RANGE = 16.0D;

    private SupportService() {
    }

    public static void tick(EntityMaid support) {
        if (!(support.level() instanceof ServerLevel level) || support.tickCount % 20 != 0) {
            return;
        }
        List<EntityMaid> targets = level.getEntities(EntityTypeTest.forClass(EntityMaid.class),
                support.getBoundingBox().inflate(RANGE), Entity::isAlive);
        for (EntityMaid target : targets) {
            if (!isAttackMode(target)) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    20 * 60, 1, true, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    20 * 60, 1, true, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                    20 * 60, 0, true, false, true));

            if (target.getHealth() >= target.getMaxHealth() * 0.15F) {
                continue;
            }
            boolean hasDoctorBadge = target.getMaidBauble()
                    .containsItem(ModItems.DOCTOR_BADGE_4.get());
            if (hasDoctorBadge) {
                target.setHealth(target.getMaxHealth());
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                        20 * 20, 2, true, true, true));
            } else {
                target.setHealth(target.getMaxHealth() * 0.5F);
            }
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    20 * 30, 0, true, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    20 * 30, 0, true, true, true));
        }
    }

    private static boolean isAttackMode(EntityMaid maid) {
        if (maid.getTask() == null || maid.getTask().getUid() == null) {
            return false;
        }
        return ATTACK_TASKS.contains(maid.getTask().getUid().getPath());
    }
}
