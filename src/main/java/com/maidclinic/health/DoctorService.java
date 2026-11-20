package com.maidclinic.health;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidclinic.registry.ModEffects;
import com.maidclinic.util.Msg;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;

/**
 * “医生女仆”的行为：装备医师徽章后，定期治疗 8 格内其它女仆。
 * <p>
 * 徽章等级是**能力上限**，实际等级由医疗经验决定：
 * 每次成功治疗获得 1 + 病情严重度 点经验，经验达到 4/10/20 时晋升为 2/3/4 级。
 * 等级越高：可处理的严重程度越高、成功率越高、冷却越短。
 */
public final class DoctorService {
    private static final int[] COOLDOWNS = {600, 400, 250, 150};
    private static final float[] SUCCESS_RATES = {0.55F, 0.70F, 0.85F, 0.95F};
    private static final double RANGE = 8.0D;
    private static final int CURE_DELAY_TICKS = 20 * 20;
    private static final int AURA_INTERVAL = 20 * 10;
    private static final double AURA_RANGE = 16.0D;

    private DoctorService() {
    }

    public static void tick(EntityMaid doctor, int badgeLevel) {
        if (!(doctor.level() instanceof ServerLevel level)) {
            return;
        }

        applyBadgeAura(doctor, badgeLevel);

        ClinicData data = ClinicData.get(level);
        MaidRecord doctorRecord = data.recordOf(doctor.getUUID(), doctor.getName().getString());
        int skill = Math.min(badgeLevel, doctorRecord.doctorLevel());
        int levelIndex = Math.max(0, Math.min(COOLDOWNS.length - 1, skill - 1));
        if (doctor.tickCount % COOLDOWNS[levelIndex] != 0) {
            return;
        }

        EntityMaid target = null;
        ActiveCondition targetCondition = null;
        Condition targetDefinition = null;

        List<EntityMaid> candidates = level.getEntities(EntityTypeTest.forClass(EntityMaid.class),
                doctor.getBoundingBox().inflate(RANGE), Entity::isAlive);
        for (EntityMaid candidate : candidates) {
            if (candidate == doctor) {
                continue;
            }
            MaidRecord record = data.find(candidate.getUUID());
            if (record == null) {
                continue;
            }
            for (ActiveCondition active : record.conditions()) {
                if (active.treated()) {
                    continue;
                }
                Condition definition = Conditions.byId(active.conditionId());
                if (definition == null || definition.primary().isEmpty()) {
                    continue;
                }
                if (active.severity() > maxSeverity(skill)) {
                    continue;
                }
                if (targetCondition == null || active.severity() > targetCondition.severity()) {
                    target = candidate;
                    targetCondition = active;
                    targetDefinition = definition;
                }
            }
        }

        if (target == null || targetCondition == null || targetDefinition == null) {
            return;
        }

        float chance = SUCCESS_RATES[levelIndex];
        if (!doctorRecord.conditions().isEmpty()) {
            chance -= 0.15F;
        }
        if (level.random.nextFloat() > chance) {
            notifyOwner(doctor, Msg.yellow("message.maidclinic.doctor.fail",
                    doctor.getName().getString(), levelText(skill), target.getName().getString()));
            level.playSound(null, target.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6F, 0.9F);
            return;
        }

        targetCondition.setTreated(true);
        targetCondition.setDurationTicks(targetCondition.elapsedTicks() + CURE_DELAY_TICKS);
        data.setDirty();

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + 1.2D, target.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, target.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.7F, 1.4F);
        notifyOwner(doctor, Msg.green("message.maidclinic.doctor.success",
                doctor.getName().getString(), levelText(skill),
                target.getName().getString(), Msg.tr(targetDefinition.nameKey())));

        int experience = 1 + targetCondition.severity();
        doctorRecord.addDoctorXp(experience);
        notifyOwner(doctor, Msg.gray("message.maidclinic.doctor.xp",
                doctor.getName().getString(), experience,
                doctorRecord.doctorXp(), doctorRecord.doctorXpForNextLevel()));
        data.setDirty();

        int newSkill = Math.min(badgeLevel, doctorRecord.doctorLevel());
        if (newSkill > skill) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    doctor.getX(), doctor.getY() + 1.4D, doctor.getZ(), 24, 0.4D, 0.5D, 0.4D, 0.0D);
            level.playSound(null, doctor.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.5F);
            notifyOwner(doctor, Msg.gold("message.maidclinic.doctor.levelup",
                    doctor.getName().getString(), levelText(newSkill)));
        }
    }

    /**
     * 徽章光环：装备医师徽章的女仆定期给自己和附近的主人回血/护盾。
     * <p>这样玩家也能直接获得正反馈，而不只是看女仆自动治疗别人。</p>
     */
    private static void applyBadgeAura(EntityMaid doctor, int badgeLevel) {
        if (doctor.tickCount % AURA_INTERVAL != 0) {
            return;
        }
        int regeneration = Math.max(0, Math.min(1, badgeLevel - 1));
        int absorption = badgeLevel >= 4 ? 1 : 0;
        // 生病的医生不再给自己回血/护盾，否则会抵消疾病致死伤害。
        if (doctor.getEffect(ModEffects.UNWELL) == null) {
            doctor.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    20 * 10, regeneration, true, true, true));
            doctor.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                    20 * 30, absorption, true, false, true));
        }

        if (doctor.getOwner() instanceof ServerPlayer owner
                && owner.level() == doctor.level()
                && owner.distanceToSqr(doctor) <= AURA_RANGE * AURA_RANGE) {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    20 * 10, regeneration, true, true, true));
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                    20 * 30, absorption, true, false, true));
        }
    }

    private static int maxSeverity(int skill) {
        return switch (skill) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    private static net.minecraft.network.chat.Component levelText(int skill) {
        return Msg.tr("doctor.maidclinic.level." + Math.max(1, Math.min(4, skill)));
    }

    private static void notifyOwner(EntityMaid doctor, net.minecraft.network.chat.Component message) {
        if (doctor.getOwner() instanceof ServerPlayer owner
                && owner.level() == doctor.level()
                && owner.distanceToSqr(doctor) <= 32.0D * 32.0D) {
            Msg.chat(owner, message);
        }
    }
}
