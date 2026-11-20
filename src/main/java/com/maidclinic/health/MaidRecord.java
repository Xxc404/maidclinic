package com.maidclinic.health;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 一只女仆的长期健康档案。
 * <p>
 * 该对象由 {@link ClinicData}（存档级 SavedData）持有并以 UUID 索引，
 * 因此女仆被收起、换维度再放出来时，档案仍然能找回来（同一维度内）。
 */
public final class MaidRecord {
    private final UUID maidId;
    private String maidName = "";
    private int trust;
    private int cured;
    private int exams;
    private int wrongDoses;
    private boolean contracted;
    private long lastExamTick = Long.MIN_VALUE;
    private long lastSeenTick;
    private float lastHealth = 20.0F;
    private long doseDay = Long.MIN_VALUE;
    private int antipyreticDoses;
    private long maskedUntil;
    /** 睡梦猝死的计划死亡时刻（绝对游戏刻，0 表示未安排）。 */
    private long sleepDeathAt;
    /** 本段睡眠是否已经尝试过猝死，避免死亡被拦截后每秒重复 die()。 */
    private boolean sleepDeathAttempted;
    /** 短暂醒来时记录醒来时刻，用于 20 秒内续上剩余计时（0 表示未暂停）。 */
    private long sleepWakeAt;
    private long lastSymptomTick;
    /** 是否被诊所安排卧床/坐着休息（用于生病时自动休息与唤醒）。 */
    private boolean resting;
    /** 玩家是否强制她带病工作，直到再次要求休息。 */
    private boolean forcedWork;
    /** 医师经验：治疗成功累积，决定医生等级（徽章等级为上限）。 */
    private int doctorXp;
    /** 连续健康天数（生病/后遗症清零）。 */
    private int healthyDays;
    /** 护理积分，用于一次性健康奖励。 */
    private int carePoints;
    /** 已领取的护理奖励层级：0 未领，1 补给，2 最大生命，3 攻击伤害。 */
    private int careTier;
    /** 已经留下的后遗症（id 集合，去重）。 */
    private final Set<String> sequelae = new LinkedHashSet<>();
    private final List<ActiveCondition> conditions = new ArrayList<>();
    private MaidProfile profile;
    /** 最近一段时间的行为暴露（淋雨、劳累、熬夜等），决定疾病诱因。 */
    private MaidBehavior behavior = new MaidBehavior();

    public MaidRecord(UUID maidId) {
        this.maidId = maidId;
    }

    // ------------------------------------------------------------------ 基础信息

    public UUID maidId() {
        return maidId;
    }

    public String maidName() {
        return maidName;
    }

    public void setMaidName(String maidName) {
        this.maidName = maidName;
    }

    // ------------------------------------------------------------------ 信任与统计

    public int trust() {
        return trust;
    }

    public void setTrust(int trust) {
        this.trust = Math.max(0, Math.min(1000, trust));
    }

    public void addTrust(int amount) {
        setTrust(this.trust + amount);
    }

    public int cured() {
        return cured;
    }

    public void setCured(int cured) {
        this.cured = cured;
    }

    public void addCured() {
        this.cured++;
    }

    public int exams() {
        return exams;
    }

    public void setExams(int exams) {
        this.exams = exams;
    }

    public int wrongDoses() {
        return wrongDoses;
    }

    public void setWrongDoses(int wrongDoses) {
        this.wrongDoses = wrongDoses;
    }

    public void addWrongDose() {
        this.wrongDoses++;
    }

    public boolean contracted() {
        return contracted;
    }

    public void setContracted(boolean contracted) {
        this.contracted = contracted;
    }

    public TrustLevel trustLevel() {
        return TrustLevel.of(trust);
    }

    // ------------------------------------------------------------------ 时间与状态

    public long lastExamTick() {
        return lastExamTick;
    }

    public void setLastExamTick(long lastExamTick) {
        this.lastExamTick = lastExamTick;
    }

    public long lastSeenTick() {
        return lastSeenTick;
    }

    public void setLastSeenTick(long lastSeenTick) {
        this.lastSeenTick = lastSeenTick;
    }

    public float lastHealth() {
        return lastHealth;
    }

    public void setLastHealth(float lastHealth) {
        this.lastHealth = lastHealth;
    }

    public long doseDay() {
        return doseDay;
    }

    public void setDoseDay(long doseDay) {
        this.doseDay = doseDay;
    }

    public int antipyreticDoses() {
        return antipyreticDoses;
    }

    public void setAntipyreticDoses(int antipyreticDoses) {
        this.antipyreticDoses = Math.max(0, antipyreticDoses);
    }

    public long maskedUntil() {
        return maskedUntil;
    }

    public void setMaskedUntil(long maskedUntil) {
        this.maskedUntil = maskedUntil;
    }

    public boolean masked(long now) {
        return maskedUntil > now;
    }

    public long lastSymptomTick() {
        return lastSymptomTick;
    }

    public void setLastSymptomTick(long lastSymptomTick) {
        this.lastSymptomTick = lastSymptomTick;
    }

    public long sleepDeathAt() {
        return sleepDeathAt;
    }

    public void setSleepDeathAt(long sleepDeathAt) {
        this.sleepDeathAt = sleepDeathAt;
    }

    public boolean sleepDeathAttempted() {
        return sleepDeathAttempted;
    }

    public void setSleepDeathAttempted(boolean sleepDeathAttempted) {
        this.sleepDeathAttempted = sleepDeathAttempted;
    }

    public long sleepWakeAt() {
        return sleepWakeAt;
    }

    public void setSleepWakeAt(long sleepWakeAt) {
        this.sleepWakeAt = sleepWakeAt;
    }

    public boolean resting() {
        return resting;
    }

    public void setResting(boolean resting) {
        this.resting = resting;
    }

    public boolean forcedWork() {
        return forcedWork;
    }

    public void setForcedWork(boolean forcedWork) {
        this.forcedWork = forcedWork;
    }

    // ------------------------------------------------------------------ 疾病

    /** 直接返回可变列表：调用方（ClinicService）负责在修改后标记存档脏数据。 */
    public List<ActiveCondition> conditions() {
        return conditions;
    }

    public boolean hasCondition(String conditionId) {
        for (ActiveCondition active : conditions) {
            if (active.conditionId().equals(conditionId)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyCondition() {
        return !conditions.isEmpty();
    }

    public int doctorXp() {
        return doctorXp;
    }

    public void setDoctorXp(int doctorXp) {
        this.doctorXp = Math.max(0, doctorXp);
    }

    public void addDoctorXp(int amount) {
        this.doctorXp = Math.max(0, this.doctorXp + amount);
    }

    public int healthyDays() {
        return healthyDays;
    }

    public void setHealthyDays(int healthyDays) {
        this.healthyDays = Math.max(0, Math.min(14, healthyDays));
    }

    public void advanceHealthyDay(boolean healthy) {
        if (healthy) {
            healthyDays = Math.min(14, healthyDays + 1);
            addCarePoints(1);
        } else {
            healthyDays = 0;
        }
    }

    public int carePoints() {
        return carePoints;
    }

    public void setCarePoints(int carePoints) {
        this.carePoints = Math.max(0, carePoints);
    }

    public void addCarePoints(int amount) {
        setCarePoints(this.carePoints + amount);
    }

    public int careTier() {
        return careTier;
    }

    public void setCareTier(int careTier) {
        this.careTier = Math.max(0, Math.min(3, careTier));
    }

    /** 由经验推导的医生等级（1–4）。 */
    public int doctorLevel() {
        if (doctorXp >= 20) {
            return 4;
        }
        if (doctorXp >= 10) {
            return 3;
        }
        if (doctorXp >= 4) {
            return 2;
        }
        return 1;
    }

    public int doctorXpForNextLevel() {
        return switch (doctorLevel()) {
            case 1 -> 4;
            case 2 -> 10;
            case 3 -> 20;
            default -> 20;
        };
    }

    /** 全部后遗症对工作效率的合计惩罚（0–0.15）。 */
    public float efficiencyPenalty() {
        float total = 0.0F;
        for (String id : sequelae) {
            Sequela sequela = Sequela.byId(id);
            if (sequela != null) {
                total += sequela.penalty();
            }
        }
        return Math.min(0.15F, total);
    }

    public Set<String> sequelae() {
        return sequelae;
    }

    public boolean hasSequela(Sequela sequela) {
        return sequelae.contains(sequela.id());
    }

    /** 添加后遗症；已存在则忽略，返回是否新增。 */
    public boolean addSequela(Sequela sequela) {
        return sequelae.add(sequela.id());
    }

    /** 取得（必要时首次生成）身体属性档案。 */
    public MaidProfile profile(RandomSource random) {
        if (profile == null) {
            profile = MaidProfile.random(random);
        }
        return profile;
    }

    public MaidProfile profileOrNull() {
        return profile;
    }

    public void setProfile(MaidProfile profile) {
        this.profile = profile;
    }

    /** 行为暴露记录；永远非 null。 */
    public MaidBehavior behavior() {
        return behavior;
    }

    public void setBehavior(MaidBehavior behavior) {
        this.behavior = behavior == null ? new MaidBehavior() : behavior;
    }

    public String describeConditions() {
        StringBuilder builder = new StringBuilder();
        for (ActiveCondition active : conditions) {
            Condition definition = Conditions.byId(active.conditionId());
            if (definition == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(definition.nameKey());
        }
        return builder.toString();
    }
}
