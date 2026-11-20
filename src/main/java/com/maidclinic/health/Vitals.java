package com.maidclinic.health;

import java.util.Locale;

/**
 * 一次测量得到的生命体征。
 * <p>
 * 参考范围（成人静息状态）：
 * 体温 36.0～37.2 ℃、心率 60～100 次/分、呼吸 12～20 次/分、
 * 血压 90～120/60～80 mmHg、血氧饱和度 95%～100%。
 * 详见 {@code docs/MEDICAL_REFERENCES.md}。
 */
public record Vitals(
        float temperature,
        int heartRate,
        int systolic,
        int diastolic,
        int respiratoryRate,
        int oxygen,
        float glucose) {

    public static final float NORMAL_TEMPERATURE = 36.5F;
    public static final int NORMAL_HEART_RATE = 78;
    public static final int NORMAL_SYSTOLIC = 112;
    public static final int NORMAL_DIASTOLIC = 72;
    public static final int NORMAL_RESPIRATORY_RATE = 16;
    public static final int NORMAL_OXYGEN = 98;
    /** 空腹血糖参考值约 3.9–6.1 mmol/L。 */
    public static final float NORMAL_GLUCOSE = 5.0F;

    /** 发热阈值（腋温 37.3 ℃）。 */
    public static final float FEVER_THRESHOLD = 37.3F;

    public static Vitals of(MaidRecord record) {
        float temperature = NORMAL_TEMPERATURE;
        int heartRate = NORMAL_HEART_RATE;
        int systolic = NORMAL_SYSTOLIC;
        int diastolic = NORMAL_DIASTOLIC;
        int respiratoryRate = NORMAL_RESPIRATORY_RATE;
        int oxygen = NORMAL_OXYGEN;
        float glucose = NORMAL_GLUCOSE;

        for (ActiveCondition active : record.conditions()) {
            Condition condition = Conditions.byId(active.conditionId());
            if (condition == null) {
                continue;
            }
            int severity = active.severity();
            temperature += condition.feverPerSeverity() * severity;
            heartRate += condition.heartRatePerSeverity() * severity;
            systolic += condition.systolicPerSeverity() * severity;
            diastolic += condition.diastolicPerSeverity() * severity;
            respiratoryRate += condition.respiratoryPerSeverity() * severity;
            oxygen += condition.oxygenPerSeverity() * severity;
            glucose += condition.glucosePerSeverity() * severity;
        }

        // 体质与体重会轻微影响静息读数
        MaidProfile profile = record.profileOrNull();
        if (profile != null) {
            switch (profile.bmiCategory()) {
                case UNDERWEIGHT -> systolic -= 3;
                case OVERWEIGHT -> {
                    heartRate += 2;
                    systolic += 3;
                    diastolic += 2;
                }
                case OBESE -> {
                    heartRate += 5;
                    systolic += 6;
                    diastolic += 4;
                }
                default -> {
                }
            }
            if (profile.constitution() == MaidProfile.Constitution.FRAIL) {
                heartRate += 4;
            } else if (profile.constitution() == MaidProfile.Constitution.ROBUST) {
                heartRate -= 2;
            }
        }

        // 体力/饥饿也会轻微影响心率，让读数不至于“太整齐”
        if (record.lastHealth() < 10.0F) {
            heartRate += 6;
        }

        return new Vitals(
                clamp(temperature, 34.0F, 42.0F),
                clamp(heartRate, 40, 180),
                clamp(systolic, 70, 200),
                clamp(diastolic, 40, 130),
                clamp(respiratoryRate, 6, 45),
                clamp(oxygen, 70, 100),
                clamp(glucose, 2.0F, 25.0F));
    }

    public boolean hasFever() {
        return temperature >= FEVER_THRESHOLD;
    }

    public boolean tachycardic() {
        return heartRate > 100;
    }

    public boolean hypotensive() {
        return systolic < 90 || diastolic < 60;
    }

    public boolean hypoxic() {
        return oxygen < 95;
    }

    public boolean hyperglycemic() {
        return glucose > 6.1F;
    }

    public boolean hypoglycemic() {
        return glucose < 3.9F;
    }

    public String glucoseText() {
        return String.format(Locale.ROOT, "%.1f", glucose);
    }

    public String glucoseLabelKey() {
        if (hypoglycemic()) {
            return "vitals.maidclinic.low";
        }
        if (hyperglycemic()) {
            return "vitals.maidclinic.high";
        }
        return "vitals.maidclinic.normal";
    }

    public String temperatureText() {
        return String.format(Locale.ROOT, "%.1f", temperature);
    }

    public String pressureText() {
        return systolic + "/" + diastolic;
    }

    /** 用于“是否异常”的简短提示。 */
    public String summaryKey() {
        if (temperature >= 39.0F || hypoxic() || hypotensive()) {
            return "vitals.maidclinic.critical";
        }
        if (hasFever() || tachycardic()) {
            return "vitals.maidclinic.abnormal";
        }
        return "vitals.maidclinic.normal";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
