package com.maidclinic;

/**
 * 全局可调参数。
 * <p>
 * 之所以集中在一个类里，是为了让整合包作者/维护者只改一处就能调整整体节奏；
 * 所有涉及医学的“数值”都在 {@link com.maidclinic.health.Conditions} 与
 * {@code docs/MEDICAL_REFERENCES.md} 中，这里只放玩法节奏参数。
 */
public final class ClinicSettings {
    private ClinicSettings() {
    }

    /** 女仆健康逻辑的检查间隔（tick）。20 tick = 1 秒。 */
    public static final int TICK_INTERVAL = 20;

    /** 同时最多存在的疾病数量，避免状态栏爆炸。 */
    public static final int MAX_ACTIVE_CONDITIONS = 2;

    /** 女仆每秒自然发病的基础概率默认值（实际以 {@link ClinicConfig#BASE_ONSET_CHANCE} 为准）。 */
    public static final float BASE_ONSET_CHANCE = 0.001F;

    /** 开启行为诱因后，纯自然发病只保留基础概率的这个比例，其余交给游戏内行为。 */
    public static final float NATURAL_ONSET_SHARE = 0.25F;

    /** 行为暴露低于该值时不增加发病概率（需要持续一段时间的行为才会真正致病）。 */
    public static final float BEHAVIOR_THRESHOLD = 30.0F;

    /** 每 1 点“超过阈值的行为暴露”每秒增加的发病概率。 */
    public static final float BEHAVIOR_HAZARD_SCALE = 0.00005F;

    /** 行为带来的发病概率上限（每秒）。 */
    public static final float MAX_BEHAVIOR_HAZARD = 0.0025F;

    /** 发病概率总上限（每秒），避免多个行为叠加后“必病”。 */
    public static final float MAX_ONSET_CHANCE = 0.02F;

    /** 行为暴露超过该值时给主人一次预警。 */
    public static final float BEHAVIOR_WARNING_THRESHOLD = 60.0F;

    /** 健康女仆工作效率加成：基础 +5%，医生经验最多 +20%，连续健康最多 +15%，封顶 +40%。 */
    public static final float HEALTHY_BONUS_BASE = 0.05F;
    public static final float HEALTHY_BONUS_PER_XP = 0.01F;
    public static final float HEALTHY_BONUS_MAX = 0.40F;

    /** 连续健康 3/7/14 天的额外加成。 */
    public static final float HEALTHY_STREAK_3 = 0.05F;
    public static final float HEALTHY_STREAK_7 = 0.10F;
    public static final float HEALTHY_STREAK_14 = 0.15F;

    /** 体质不适合当前工作时的效率惩罚。 */
    public static final float CONSTITUTION_PENALTY = 0.20F;

    /** 强迫生病女仆工作的反馈间隔（tick）。 */
    public static final int FORCED_WORK_FEEDBACK_INTERVAL = 20 * 20;

    /** 生病睡着时偶尔发出难受声音的间隔（tick）。 */
    public static final int SICK_SLEEP_SOUND_INTERVAL = 20 * 8;

    /** 同一只女仆两次行为预警的最短间隔（tick）。 */
    public static final int BEHAVIOR_WARNING_COOLDOWN_TICKS = 20 * 90;

    /** 问诊结果的有效期（tick）：5 分钟内可以直接用药，超时需重新问诊。 */
    public static final int EXAM_VALID_TICKS = 20 * 300;

    /** 退热药在游戏内一天的推荐上限（对乙酰氨基酚成人每日不超过 4 次）。 */
    public static final int MAX_ANTIPYRETIC_DOSES_PER_DAY = 4;

    /** 两次服药之间的最短间隔，用于模拟“按时服药”。 */
    public static final int MIN_DOSE_INTERVAL_TICKS = 20 * 45;

    /** 签约家庭医生所需的信任值。 */
    public static final int CONTRACT_TRUST = 600;

    /** 签约家庭医生所需的治愈次数。 */
    public static final int CONTRACT_CURES = 8;

    /** 签约后发病概率的倍率。 */
    public static final float CONTRACT_ONSET_MULTIPLIER = 0.6F;

    /** 佩戴口罩的有效时长（tick）：3 个游戏日。 */
    public static final int MASK_DURATION_TICKS = 3 * 24000;

    /** 佩戴口罩后发病/传染概率的倍率。 */
    public static final float MASK_ONSET_MULTIPLIER = 0.5F;

    /** 症状旁白的最短间隔（tick）。 */
    public static final int SYMPTOM_SPEAK_INTERVAL_TICKS = 20 * 90;

    /** 主人距离女仆多远时才能听到/看到症状旁白（方块）。 */
    public static final double SYMPTOM_SPEAK_RANGE = 16.0D;

    /** 睡梦猝死的随机时间范围（tick）：1–4.5 分钟。现实中重度感染/脱水可在数小时内致命，这里按游戏时间压缩。 */
    public static final int SLEEP_DEATH_MIN_TICKS = 20 * 60;
    public static final int SLEEP_DEATH_MAX_TICKS = 20 * 270;

    /** 睡眠猝死计时允许的短暂醒来宽限：醒来不超过该时长则续上剩余计时。 */
    public static final int SLEEP_DEATH_WAKE_GRACE_TICKS = 20 * 20;

    /** 疾病到病程终点仍未自限时，延长的时间（tick）：2 分钟。未治疗的感染/慢性病不会自行结束。 */
    public static final int NON_SELF_LIMITING_EXTENSION_TICKS = 20 * 120;

    /** 每次成功问诊获得的信任。 */
    public static final int TRUST_EXAM = 5;

    /** 对症治疗一次获得的信任。 */
    public static final int TRUST_TREAT = 10;

    /** 对症护理（辅助治疗）获得的信任。 */
    public static final int TRUST_SUPPORT = 4;

    /** 治愈一次获得的信任。 */
    public static final int TRUST_CURE = 20;

    /** 用药不当扣除的信任。 */
    public static final int TRUST_WRONG = 12;
}
