package com.maidclinic.health;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.Locale;
import java.util.Set;

/**
 * 女仆的“初始身体属性”。
 * <p>
 * 分成两类，对应现实里的“不可变因素”和“可干预因素”：
 * <ul>
 *     <li><b>固定（不可改动）</b>：ABO/Rh 血型、身高、青霉素过敏、家族史、体质。
 *         这些由出生决定，游戏内不会随照顾改变；</li>
 *     <li><b>可变（会变）</b>：体重、运动量、盐摄入、糖摄入、睡眠质量、压力。
 *         会随着“是否有人照顾、是否经常生病”按游戏日缓慢变化。</li>
 * </ul>
 * 目的不是做体检软件，而是让每只女仆有可解释的个体差异：
 * 胖一点更容易高血压/糖尿病，运动少更容易骨质疏松，压力大更容易失眠。
 */
public final class MaidProfile {

    public enum BloodType {
        A, B, AB, O
    }

    public enum Constitution {
        FRAIL, NORMAL, ROBUST
    }

    public enum BmiCategory {
        UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE
    }

    // ------------------------------------------------------------------ 固定属性

    private final BloodType bloodType;
    private final boolean rhPositive;
    private final int heightCm;
    private final boolean penicillinAllergy;
    private final boolean familyHypertension;
    private final boolean familyDiabetes;
    private final Constitution constitution;

    // ------------------------------------------------------------------ 可变属性

    private float weightKg;
    private int exercise;
    private int saltIntake;
    private int sugarIntake;
    private int sleepQuality;
    private int stress;

    private long lastUpdateDay = Long.MIN_VALUE;

    private MaidProfile(BloodType bloodType, boolean rhPositive, int heightCm, boolean penicillinAllergy,
                        boolean familyHypertension, boolean familyDiabetes, Constitution constitution,
                        float weightKg, int exercise, int saltIntake, int sugarIntake, int sleepQuality, int stress) {
        this.bloodType = bloodType;
        this.rhPositive = rhPositive;
        this.heightCm = heightCm;
        this.penicillinAllergy = penicillinAllergy;
        this.familyHypertension = familyHypertension;
        this.familyDiabetes = familyDiabetes;
        this.constitution = constitution;
        this.weightKg = weightKg;
        this.exercise = exercise;
        this.saltIntake = saltIntake;
        this.sugarIntake = sugarIntake;
        this.sleepQuality = sleepQuality;
        this.stress = stress;
    }

    /**
     * 首次建档：按中国成人常见分布随机生成。
     * <ul>
     *     <li>ABO：O 约 34%、A 约 28%、B 约 29%、AB 约 9%；Rh 阴性约 0.3%；</li>
     *     <li>身高：成年女性 150–172 cm；体重由 18.5–27 的目标 BMI 反推；</li>
     *     <li>青霉素过敏约 8%，高血压家族史约 30%，糖尿病家族史约 12%。</li>
     * </ul>
     */
    public static MaidProfile random(RandomSource random) {
        BloodType bloodType;
        int roll = random.nextInt(100);
        if (roll < 34) {
            bloodType = BloodType.O;
        } else if (roll < 62) {
            bloodType = BloodType.A;
        } else if (roll < 91) {
            bloodType = BloodType.B;
        } else {
            bloodType = BloodType.AB;
        }

        boolean rhPositive = random.nextFloat() > 0.003F;
        int height = 150 + random.nextInt(23);
        float targetBmi = 18.5F + random.nextFloat() * 8.5F;
        float weight = targetBmi * (height / 100.0F) * (height / 100.0F);
        weight = Math.max(40.0F, Math.min(90.0F, weight));

        boolean penicillinAllergy = random.nextFloat() < 0.08F;
        boolean familyHypertension = random.nextFloat() < 0.30F;
        boolean familyDiabetes = random.nextFloat() < 0.12F;
        float constitutionRoll = random.nextFloat();
        Constitution constitution = constitutionRoll < 0.20F
                ? Constitution.FRAIL
                : (constitutionRoll > 0.80F ? Constitution.ROBUST : Constitution.NORMAL);

        int exercise = 25 + random.nextInt(51);
        int salt = 35 + random.nextInt(41);
        int sugar = 25 + random.nextInt(41);
        int sleep = 40 + random.nextInt(46);
        int stress = 20 + random.nextInt(46);

        return new MaidProfile(bloodType, rhPositive, height, penicillinAllergy, familyHypertension,
                familyDiabetes, constitution, weight, exercise, salt, sugar, sleep, stress);
    }

    // ------------------------------------------------------------------ 派生值

    public float bmi() {
        float heightM = heightCm / 100.0F;
        return weightKg / (heightM * heightM);
    }

    /** 中国成人 BMI 分级：<18.5 偏瘦，18.5–23.9 正常，24–27.9 超重，≥28 肥胖。 */
    public BmiCategory bmiCategory() {
        float bmi = bmi();
        if (bmi < 18.5F) {
            return BmiCategory.UNDERWEIGHT;
        }
        if (bmi < 24.0F) {
            return BmiCategory.NORMAL;
        }
        if (bmi < 28.0F) {
            return BmiCategory.OVERWEIGHT;
        }
        return BmiCategory.OBESE;
    }

    // ------------------------------------------------------------------ 疾病风险（1.0 为基准）

    public float hypertensionRisk() {
        float risk = 1.0F + saltIntake / 200.0F + stress / 300.0F + bmiPenalty(24.0F, 10.0F);
        if (familyHypertension) {
            risk += 0.5F;
        }
        if (constitution == Constitution.FRAIL) {
            risk += 0.1F;
        }
        return clampRisk(risk);
    }

    public float diabetesRisk() {
        float risk = 1.0F + sugarIntake / 200.0F + bmiPenalty(24.0F, 8.0F) * 1.5F
                + (100 - exercise) / 300.0F;
        if (familyDiabetes) {
            risk += 0.6F;
        }
        return clampRisk(risk);
    }

    public float osteoporosisRisk() {
        float risk = 1.0F + (100 - exercise) / 250.0F;
        if (bmiCategory() == BmiCategory.UNDERWEIGHT) {
            risk += 0.6F;
        }
        if (constitution == Constitution.FRAIL) {
            risk += 0.3F;
        }
        return clampRisk(risk);
    }

    public float anemiaRisk() {
        float risk = 1.0F;
        if (bmiCategory() == BmiCategory.UNDERWEIGHT) {
            risk += 0.5F;
        }
        if (constitution == Constitution.FRAIL) {
            risk += 0.2F;
        }
        return clampRisk(risk);
    }

    public float insomniaRisk() {
        return clampRisk(1.0F + stress / 150.0F + (100 - sleepQuality) / 200.0F);
    }

    public float infectionRisk() {
        float risk = 1.0F;
        if (constitution == Constitution.FRAIL) {
            risk += 0.3F;
        } else if (constitution == Constitution.ROBUST) {
            risk -= 0.2F;
        }
        if (bmiCategory() == BmiCategory.UNDERWEIGHT) {
            risk += 0.1F;
        }
        return clampRisk(risk);
    }

    private float bmiPenalty(float threshold, float divisor) {
        return Math.max(0.0F, (bmi() - threshold) / divisor);
    }

    private static float clampRisk(float risk) {
        return Math.max(0.5F, Math.min(2.5F, risk));
    }

    // ------------------------------------------------------------------ 每日变化

    /**
     * 每游戏日推进一次：有人照顾、身体健康时，生活方式会缓慢改善；
     * 长期生病或无人照顾时会变差。体重向 BMI 22 缓慢回归。
     *
     * @return 是否发生了变化
     */
    public boolean tickDaily(long day, boolean healthy, int trust) {
        if (day == lastUpdateDay) {
            return false;
        }
        lastUpdateDay = day;

        int direction = healthy && trust >= 300 ? 1 : (healthy ? 0 : -1);
        exercise = clampPercent(exercise + direction);
        sleepQuality = clampPercent(sleepQuality + direction);
        stress = clampPercent(stress - direction * 2);
        saltIntake = clampPercent(saltIntake - direction);
        sugarIntake = clampPercent(sugarIntake - direction);

        float heightM = heightCm / 100.0F;
        float targetWeight = 22.0F * heightM * heightM;
        float delta = (targetWeight - weightKg) * 0.02F;
        delta += (sugarIntake - 50) * 0.002F;
        delta -= (exercise - 50) * 0.001F;
        weightKg = Math.max(35.0F, Math.min(120.0F, weightKg + Math.max(-0.2F, Math.min(0.2F, delta))));
        return true;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ------------------------------------------------------------------ 访问器

    public BloodType bloodType() {
        return bloodType;
    }

    public boolean rhPositive() {
        return rhPositive;
    }

    public int heightCm() {
        return heightCm;
    }

    public boolean penicillinAllergy() {
        return penicillinAllergy;
    }

    public boolean familyHypertension() {
        return familyHypertension;
    }

    public boolean familyDiabetes() {
        return familyDiabetes;
    }

    public Constitution constitution() {
        return constitution;
    }

    /** 体质影响最大生命值：偏弱 -4，强健 +6。 */
    public float maxHealthBonus() {
        return switch (constitution) {
            case FRAIL -> -4.0F;
            case ROBUST -> 6.0F;
            default -> 0.0F;
        };
    }

    /** 偏弱体质不适合战斗与重体力农活。 */
    public boolean isSuitableFor(String taskPath) {
        if (constitution != Constitution.FRAIL) {
            return true;
        }
        return !Set.of("attack", "ranged_attack", "crossbow_attack", "danmaku_attack",
                "trident_attack", "gun_attack", "farm", "sugar_cane", "melon", "cocoa",
                "snow", "honey", "extinguishing").contains(taskPath);
    }

    public float weightKg() {
        return weightKg;
    }

    public int exercise() {
        return exercise;
    }

    public int saltIntake() {
        return saltIntake;
    }

    public int sugarIntake() {
        return sugarIntake;
    }

    public int sleepQuality() {
        return sleepQuality;
    }

    public int stress() {
        return stress;
    }

    public String bloodTypeKey() {
        return "profile.maidclinic.blood." + bloodType.name().toLowerCase(Locale.ROOT);
    }

    public String rhKey() {
        return rhPositive ? "profile.maidclinic.rh.positive" : "profile.maidclinic.rh.negative";
    }

    public String bmiCategoryKey() {
        return "profile.maidclinic.bmi." + bmiCategory().name().toLowerCase(Locale.ROOT);
    }

    public String constitutionKey() {
        return "profile.maidclinic.constitution." + constitution.name().toLowerCase(Locale.ROOT);
    }

    public String weightText() {
        return String.format(Locale.ROOT, "%.1f", weightKg);
    }

    public String bmiText() {
        return String.format(Locale.ROOT, "%.1f", bmi());
    }

    // ------------------------------------------------------------------ 存档

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("blood", bloodType.name());
        tag.putBoolean("rh", rhPositive);
        tag.putInt("height", heightCm);
        tag.putBoolean("penicillin_allergy", penicillinAllergy);
        tag.putBoolean("family_hypertension", familyHypertension);
        tag.putBoolean("family_diabetes", familyDiabetes);
        tag.putString("constitution", constitution.name());
        tag.putFloat("weight", weightKg);
        tag.putInt("exercise", exercise);
        tag.putInt("salt", saltIntake);
        tag.putInt("sugar", sugarIntake);
        tag.putInt("sleep", sleepQuality);
        tag.putInt("stress", stress);
        tag.putLong("last_day", lastUpdateDay);
        return tag;
    }

    public static MaidProfile load(CompoundTag tag) {
        BloodType bloodType;
        try {
            bloodType = BloodType.valueOf(tag.getString("blood"));
        } catch (IllegalArgumentException exception) {
            bloodType = BloodType.O;
        }
        Constitution constitution;
        try {
            constitution = Constitution.valueOf(tag.getString("constitution"));
        } catch (IllegalArgumentException exception) {
            constitution = Constitution.NORMAL;
        }
        MaidProfile profile = new MaidProfile(
                bloodType,
                tag.getBoolean("rh"),
                Math.max(140, tag.getInt("height")),
                tag.getBoolean("penicillin_allergy"),
                tag.getBoolean("family_hypertension"),
                tag.getBoolean("family_diabetes"),
                constitution,
                Math.max(35.0F, tag.getFloat("weight")),
                tag.getInt("exercise"),
                tag.getInt("salt"),
                tag.getInt("sugar"),
                tag.getInt("sleep"),
                tag.getInt("stress"));
        profile.lastUpdateDay = tag.getLong("last_day");
        return profile;
    }
}
