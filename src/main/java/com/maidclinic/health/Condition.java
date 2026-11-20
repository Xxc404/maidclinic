package com.maidclinic.health;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 一种疾病/不适的静态定义（不可变）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>病程、体温、心率等“生理数值”按严重程度分级累加，便于统一计算；</li>
 *     <li>用 {@link Treatment} 集合描述“对症 / 辅助 / 禁忌”，使药物与疾病解耦；</li>
 *     <li>症状与建议只存翻译键，文本放在语言文件里，便于本地化与校对。</li>
 * </ul>
 *
 * <p>数值参考见 {@code docs/MEDICAL_REFERENCES.md}，仅用于游戏模拟，不构成医疗建议。</p>
 */
public final class Condition {

    /** 疾病分类，主要用于症状旁白与提示语。 */
    public enum Category {
        INFECTION, INFLAMMATION, METABOLIC, TRAUMA, MENTAL, IATROGENIC, CHRONIC
    }

    private final String id;
    /** 覆盖默认的显示名翻译键；为 null 时由 id 派生。 */
    private final String nameKeyOverride;
    private final Category category;
    private final boolean natural;
    /** 自限概率：0 表示非自限，1 表示必定自限，0–1 表示概率自限。 */
    private final float selfLimitingChance;
    private final boolean recoveryNeedsSupport;
    private final boolean infectious;
    private final float contagion;
    private final int minSeconds;
    private final int maxSeconds;
    private final float feverPerSeverity;
    private final int heartRatePerSeverity;
    private final int systolicPerSeverity;
    private final int diastolicPerSeverity;
    private final int respiratoryPerSeverity;
    private final int oxygenPerSeverity;
    private final float glucosePerSeverity;
    private final int maxSeverity;
    private final int requiredDoses;
    private final int doseIntervalSeconds;
    private final String complicationId;
    private final float complicationChance;
    /** 重度未治疗时的相对危重程度（1.0 为基准）；仅在启用疾病致死/危重掉血时生效。 */
    private final float mortality;
    /** 毒力：重度未治疗时每次危重伤害的倍率，1.0 为基准。 */
    private final float virulence;
    private final List<String> symptoms;
    private final List<String> redFlags;
    private final Set<Treatment> primary;
    private final Set<Treatment> supportive;
    private final Set<Treatment> harmful;

    private Condition(Builder builder) {
        this.id = builder.id;
        this.nameKeyOverride = builder.nameKey;
        this.category = builder.category;
        this.natural = builder.natural;
        this.selfLimitingChance = builder.selfLimitingChance;
        this.recoveryNeedsSupport = builder.recoveryNeedsSupport;
        this.infectious = builder.infectious;
        this.contagion = builder.contagion;
        this.minSeconds = builder.minSeconds;
        this.maxSeconds = builder.maxSeconds;
        this.feverPerSeverity = builder.feverPerSeverity;
        this.heartRatePerSeverity = builder.heartRatePerSeverity;
        this.systolicPerSeverity = builder.systolicPerSeverity;
        this.diastolicPerSeverity = builder.diastolicPerSeverity;
        this.respiratoryPerSeverity = builder.respiratoryPerSeverity;
        this.oxygenPerSeverity = builder.oxygenPerSeverity;
        this.glucosePerSeverity = builder.glucosePerSeverity;
        this.maxSeverity = builder.maxSeverity;
        this.requiredDoses = builder.requiredDoses;
        this.doseIntervalSeconds = builder.doseIntervalSeconds;
        this.complicationId = builder.complicationId;
        this.complicationChance = builder.complicationChance;
        this.mortality = builder.mortality;
        this.virulence = builder.virulence;
        this.symptoms = List.copyOf(builder.symptoms);
        this.redFlags = List.copyOf(builder.redFlags);
        this.primary = copyOf(builder.primary);
        this.supportive = copyOf(builder.supportive);
        this.harmful = copyOf(builder.harmful);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    private static Set<Treatment> copyOf(Set<Treatment> source) {
        EnumSet<Treatment> copy = EnumSet.noneOf(Treatment.class);
        copy.addAll(source);
        return Collections.unmodifiableSet(copy);
    }

    // ------------------------------------------------------------------ 访问器

    public String id() {
        return id;
    }

    public Category category() {
        return category;
    }

    /** 是否会自然发病（并发症/医源性问题为 false）。 */
    public boolean natural() {
        return natural;
    }

    /** 是否有自限可能（概率 > 0）。具体是否自限由 {@link #selfLimitingChance()} 掷骰决定。 */
    public boolean selfLimiting() {
        return selfLimitingChance > 0.0F;
    }

    /** 自限概率：0 非自限，1 必定自限。 */
    public float selfLimitingChance() {
        return selfLimitingChance;
    }

    /**
     * 自限性疾病是否仍需要基础护理（补液/降温）才能平稳恢复。
     * <p>例如急性胃肠炎本身多为自限性，但不补液会明显增加脱水风险；
     * 为 true 时，“完全不管”的结局不再是无代价自愈。</p>
     */
    public boolean recoveryNeedsSupport() {
        return recoveryNeedsSupport;
    }

    public boolean infectious() {
        return infectious;
    }

    public float contagion() {
        return contagion;
    }

    public int minSeconds() {
        return minSeconds;
    }

    public int maxSeconds() {
        return maxSeconds;
    }

    public float feverPerSeverity() {
        return feverPerSeverity;
    }

    public int heartRatePerSeverity() {
        return heartRatePerSeverity;
    }

    public int systolicPerSeverity() {
        return systolicPerSeverity;
    }

    public int diastolicPerSeverity() {
        return diastolicPerSeverity;
    }

    public int respiratoryPerSeverity() {
        return respiratoryPerSeverity;
    }

    public int oxygenPerSeverity() {
        return oxygenPerSeverity;
    }

    /** 每级严重程度对血糖（mmol/L）的影响。 */
    public float glucosePerSeverity() {
        return glucosePerSeverity;
    }

    public int maxSeverity() {
        return maxSeverity;
    }

    /** 完成疗程所需的给药次数，1 表示一次即可。 */
    public int requiredDoses() {
        return requiredDoses;
    }

    /** 两次给药的最短间隔（秒）；慢性病通常更长。 */
    public int doseIntervalSeconds() {
        return doseIntervalSeconds;
    }

    public String complicationId() {
        return complicationId;
    }

    public float complicationChance() {
        return complicationChance;
    }

    /** 重度未治疗时的相对危重程度（1.0 为基准）。死亡仍由配置/个人开关控制。 */
    public float mortality() {
        return mortality;
    }

    /** 毒力：危重伤害的倍率，1.0 为基准。 */
    public float virulence() {
        return virulence;
    }

    public List<String> symptoms() {
        return symptoms;
    }

    public List<String> redFlags() {
        return redFlags;
    }

    public Set<Treatment> primary() {
        return primary;
    }

    public Set<Treatment> supportive() {
        return supportive;
    }

    public Set<Treatment> harmful() {
        return harmful;
    }

    /**
     * 显示名翻译键：默认由 id 派生，可用 {@link Builder#nameKey(String)} 覆盖，
     * 让同一临床表现的不同诱因共用同一个名称。
     */
    public String nameKey() {
        return nameKeyOverride != null ? nameKeyOverride : "condition.maidclinic." + id;
    }

    public String adviceKey() {
        return "advice.maidclinic." + id;
    }

    // ------------------------------------------------------------------ Builder

    public static final class Builder {
        private final String id;
        private String nameKey;
        private Category category = Category.INFECTION;
        private boolean natural = true;
        private float selfLimitingChance;
        private boolean recoveryNeedsSupport;
        private boolean infectious;
        private float contagion;
        private int minSeconds = 300;
        private int maxSeconds = 420;
        private float feverPerSeverity;
        private int heartRatePerSeverity;
        private int systolicPerSeverity;
        private int diastolicPerSeverity;
        private int respiratoryPerSeverity;
        private int oxygenPerSeverity;
        private float glucosePerSeverity;
        private int maxSeverity = 2;
        private int requiredDoses = 1;
        private int doseIntervalSeconds = 45;
        private String complicationId;
        private float complicationChance;
        private float mortality = 0.5F;
        private float virulence = 1.0F;
        private List<String> symptoms = List.of();
        private List<String> redFlags = List.of();
        private final Set<Treatment> primary = EnumSet.noneOf(Treatment.class);
        private final Set<Treatment> supportive = EnumSet.noneOf(Treatment.class);
        private final Set<Treatment> harmful = EnumSet.noneOf(Treatment.class);

        private Builder(String id) {
            this.id = id;
        }

        public Builder category(Category category) {
            this.category = category;
            return this;
        }

        public Builder natural(boolean natural) {
            this.natural = natural;
            return this;
        }

        public Builder selfLimiting(boolean selfLimiting) {
            this.selfLimitingChance = selfLimiting ? 1.0F : 0.0F;
            return this;
        }

        /** 概率自限：0 非自限，1 必定自限。 */
        public Builder selfLimiting(float chance) {
            this.selfLimitingChance = Math.max(0.0F, Math.min(1.0F, chance));
            return this;
        }

        public Builder recoveryNeedsSupport(boolean recoveryNeedsSupport) {
            this.recoveryNeedsSupport = recoveryNeedsSupport;
            return this;
        }

        public Builder infectious(boolean infectious, float contagion) {
            this.infectious = infectious;
            this.contagion = contagion;
            return this;
        }

        public Builder duration(int minSeconds, int maxSeconds) {
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
            return this;
        }

        public Builder fever(float perSeverity) {
            this.feverPerSeverity = perSeverity;
            return this;
        }

        public Builder heartRate(int perSeverity) {
            this.heartRatePerSeverity = perSeverity;
            return this;
        }

        public Builder bloodPressure(int systolic, int diastolic) {
            this.systolicPerSeverity = systolic;
            this.diastolicPerSeverity = diastolic;
            return this;
        }

        public Builder respiratoryRate(int perSeverity) {
            this.respiratoryPerSeverity = perSeverity;
            return this;
        }

        public Builder oxygen(int perSeverity) {
            this.oxygenPerSeverity = perSeverity;
            return this;
        }

        public Builder glucose(float perSeverity) {
            this.glucosePerSeverity = perSeverity;
            return this;
        }

        public Builder maxSeverity(int maxSeverity) {
            this.maxSeverity = maxSeverity;
            return this;
        }

        public Builder requiredDoses(int requiredDoses) {
            this.requiredDoses = requiredDoses;
            return this;
        }

        public Builder doseInterval(int seconds) {
            this.doseIntervalSeconds = Math.max(5, seconds);
            return this;
        }

        public Builder complication(String conditionId, float chance) {
            this.complicationId = conditionId;
            this.complicationChance = chance;
            return this;
        }

        /** 重度未治疗时的相对危重程度；默认 1.0，死亡仍由配置控制。 */
        public Builder mortality(float mortality) {
            this.mortality = Math.max(0.05F, mortality);
            return this;
        }

        /** 毒力：危重伤害的倍率；默认 1.0。 */
        public Builder virulence(float virulence) {
            this.virulence = virulence;
            return this;
        }

        public Builder symptoms(String... keys) {
            this.symptoms = List.of(keys);
            return this;
        }

        public Builder redFlags(String... keys) {
            this.redFlags = List.of(keys);
            return this;
        }

        public Builder primary(Treatment... treatments) {
            this.primary.addAll(List.of(treatments));
            return this;
        }

        public Builder supportive(Treatment... treatments) {
            this.supportive.addAll(List.of(treatments));
            return this;
        }

        public Builder harmful(Treatment... treatments) {
            this.harmful.addAll(List.of(treatments));
            return this;
        }

        /** 覆盖默认的显示名翻译键（默认 {@code condition.maidclinic.<id>}）。 */
        public Builder nameKey(String nameKey) {
            this.nameKey = nameKey;
            return this;
        }

        public Condition build() {
            return new Condition(this);
        }
    }
}
