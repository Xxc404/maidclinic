package com.maidclinic;

import com.maidclinic.health.SpiritAttribution;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 通用配置（生成在 {@code config/maidclinic-common.toml}）。
 * <p>
 * 只放“节奏/难度”开关，不放医学数值；所有默认值都对应 README 里描述的默认体验。
 * 默认<b>不会因疾病死亡</b>，只有把 {@link #ILLNESS_CAN_KILL} 打开才会进入硬核模式。
 */
public final class ClinicConfig {
    private ClinicConfig() {
    }

    public static final ModConfigSpec SPEC;

    /** 疾病是否可能致死（默认关闭）。 */
    public static final ModConfigSpec.BooleanValue ILLNESS_CAN_KILL;

    /** 危重（重度且未治疗）时是否持续掉血。 */
    public static final ModConfigSpec.BooleanValue CRITICAL_DAMAGE;

    /** 危重掉血的间隔（秒）。 */
    public static final ModConfigSpec.IntValue CRITICAL_INTERVAL_SECONDS;

    /** 非致死模式下，危重掉血保留的最低生命值。 */
    public static final ModConfigSpec.DoubleValue CRITICAL_MIN_HEALTH;

    /** 危重期的掉血方式：virulence（按毒力扣血）或 sleep（额外睡梦猝死）。 */
    public static final ModConfigSpec.ConfigValue<String> DEATH_STYLE;

    /** 女仆每秒自然发病的基础概率。 */
    public static final ModConfigSpec.DoubleValue BASE_ONSET_CHANCE;

    /** 疾病是否主要由女仆的游戏内行为触发。 */
    public static final ModConfigSpec.BooleanValue ENABLE_BEHAVIOR_TRIGGERS;

    /** 是否要求先问诊才能使用处方类药物。 */
    public static final ModConfigSpec.BooleanValue REQUIRE_EXAM;

    /** 是否启用慢性病（高血压、2 型糖尿病、慢性胃炎、骨质疏松）。 */
    public static final ModConfigSpec.BooleanValue ENABLE_CHRONIC;

    /** 疾病是否降低女仆的工作效率（移动/攻击）。 */
    public static final ModConfigSpec.BooleanValue ENABLE_WORK_EFFICIENCY;

    /** 死亡后复活是否保留永久后遗症。 */
    public static final ModConfigSpec.BooleanValue KEEP_SEQUELA_ON_DEATH;

    /** 健康女仆是否获得工作效率加成（正反馈）。 */
    public static final ModConfigSpec.BooleanValue ENABLE_HEALTHY_BONUS;

    /** 灵体归因档位：决定“非玩家伤害但可能是玩家造成”的死亡是否留下灵体。 */
    public static final ModConfigSpec.EnumValue<SpiritAttribution.Mode> SPIRIT_ATTRIBUTION;

    /** 主人就在附近的环境死亡是否也算玩家的责任（默认关闭，避免误伤正常跟随）。 */
    public static final ModConfigSpec.BooleanValue SPIRIT_BLOCK_ON_OWNER_NEGLIGENCE;

    /** 接触证据窗口（tick）：死前多久内被该玩家打过才算关联。 */
    public static final ModConfigSpec.IntValue SPIRIT_CONTACT_WINDOW_TICKS;

    /** 爆炸归因缓存寿命（tick）。 */
    public static final ModConfigSpec.IntValue SPIRIT_EXPLOSION_CACHE_TICKS;

    /** 玩家放置的危险方块/岩浆/火的归因寿命（tick）。 */
    public static final ModConfigSpec.IntValue SPIRIT_PLACED_BLOCK_TTL_TICKS;

    /** 玩家碰撞箱（含载具）把后代推下去后，多久内的环境死亡仍算玩家责任（tick）。 */
    public static final ModConfigSpec.IntValue SPIRIT_PUSH_WINDOW_TICKS;

    /** 推挤必须让女仆的行进方向偏转多少度才算“被推下去”。 */
    public static final ModConfigSpec.DoubleValue SPIRIT_PUSH_DEVIATION_DEGREES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Difficulty switches. Default experience: illness never kills, but can drop a maid to 1 HP.",
                "难度开关。默认体验：疾病不会致死，但危重时可能把女仆打到 1 点生命。");
        ILLNESS_CAN_KILL = builder
                .comment("Allow illness to kill a maid. Enable for hardcore packs.",
                        "疾病是否可能致死。硬核整合包可开启。")
                .define("illnessCanKill", false);

        CRITICAL_DAMAGE = builder
                .comment("Severe untreated illness drains health over time.",
                        "重度且未治疗的疾病是否持续掉血。")
                .define("criticalDamage", true);

        CRITICAL_INTERVAL_SECONDS = builder
                .comment("Seconds between critical damage ticks.",
                        "危重掉血的间隔（秒）。")
                .defineInRange("criticalIntervalSeconds", 15, 5, 120);

        CRITICAL_MIN_HEALTH = builder
                .comment("Health floor when illnessCanKill is false.",
                        "未开启致死时，疾病掉血保留的最低生命值。")
                .defineInRange("criticalMinHealth", 1.0D, 0.5D, 10.0D);

        DEATH_STYLE = builder
                .comment("Critical damage style: 'virulence' = HP drain by virulence;",
                        "'sleep' = HP drain plus a random sudden death while sleeping (only when illnessCanKill is on).",
                        "危重掉血方式：virulence 按毒力扣血；sleep 额外在睡梦中随机猝死（仅 illnessCanKill 开启时生效）。")
                .define("deathStyle", "virulence");

        BASE_ONSET_CHANCE = builder
                .comment("Base chance per second for a maid to fall ill (0.001 ≈ once per 17 minutes).",
                        "When behavior triggers are enabled, natural onset is only 25% of this value.",
                        "女仆每秒自然发病的基础概率（0.001 约等于 17 分钟一次）。",
                        "开启行为诱因时，纯自然发病只按这个值的 25% 计算。")
                .defineInRange("baseOnsetChance", 0.001D, 0.0D, 0.1D);

        ENABLE_BEHAVIOR_TRIGGERS = builder
                .comment("Illness is mainly triggered by what the maid does in game: rain, heat, fire, combat, injury,",
                        "overwork, sleep loss, hunger, stress, allergens, contaminated food and cross-infection.",
                        "Set to false to restore the old purely random onset model.",
                        "疾病是否主要由女仆的游戏内行为触发：淋雨、受热、火焰、战斗、受伤、劳累、熬夜、饥饿、",
                        "压力、过敏原、不洁饮食与交叉感染。关闭后恢复旧版的纯随机发病模型。")
                .define("enableBehaviorTriggers", true);

        REQUIRE_EXAM = builder
                .comment("Require a stethoscope exam before prescription medicine can be used.",
                        "是否要求先用听诊器问诊才能使用处方药。")
                .define("requireExamBeforeMedicine", true);

        ENABLE_CHRONIC = builder
                .comment("Enable chronic conditions (hypertension, type 2 diabetes, chronic gastritis, osteoporosis).",
                        "Some take 25-50 minutes and need repeated doses.",
                        "是否启用慢性病（高血压、2 型糖尿病、慢性胃炎、骨质疏松）。",
                        "慢性病病程约 25–50 分钟，需要多次、按间隔给药。")
                .define("enableChronicConditions", false);

        ENABLE_WORK_EFFICIENCY = builder
                .comment("Illness slows down a maid's work (movement speed and attack damage).",
                        "Severe untreated illness can leave a permanent penalty.",
                        "疾病是否降低女仆的工作效率（移动速度与攻击伤害）。",
                        "重度且未治疗的疾病可能留下永久惩罚。")
                .define("enableWorkEfficiency", true);

        KEEP_SEQUELA_ON_DEATH = builder
                .comment("Keep the full health record (bond, cures, constitution, sequelae, etc.) when a maid dies and is revived.",
                        "Players can override with /maidclinic sequela on|off|default.",
                        "女仆死亡后复活时是否保留完整健康档案（信赖、治愈次数、体质、后遗症等）。",
                        "玩家可用 /maidclinic sequela on|off|default 按个人覆盖。")
                .define("keepSequelaOnDeath", true);

        ENABLE_HEALTHY_BONUS = builder
                .comment("Healthy maids (no illness, no sequelae) get a small work efficiency bonus.",
                        "健康的女仆（无疾病、无后遗症）获得少量工作效率加成。")
                .define("enableHealthyBonus", true);

        SPIRIT_ATTRIBUTION = builder
                .comment("How hard the clinic tries to attribute a death to a player before allowing a spirit to spawn.",
                        "OFF = direct player kills only (old behaviour).",
                        "LENIENT = + explosions and player-placed/ignited hazards (lava, fire, TNT, campfire...).",
                        "BALANCED = + \"hit by that player recently\" evidence for fire/fall/suffocation deaths (default).",
                        "STRICT = + \"only one player nearby\" evidence for otherwise natural deaths.",
                        "灵体归因档位：决定非玩家伤害的死亡是否追溯到玩家。",
                        "OFF=只认直接玩家击杀（旧行为）；LENIENT=加爆炸与玩家放置/引燃的危险方块；",
                        "BALANCED=再加“死前 5 秒内被该玩家打过”的接触证据（默认）；",
                        "STRICT=再加“现场只有一名玩家”的在场证据。")
                .defineEnum("spiritAttribution", SpiritAttribution.Mode.BALANCED);

        SPIRIT_BLOCK_ON_OWNER_NEGLIGENCE = builder
                .comment("Also block the spirit when the owner was within 16 blocks of an environmental death (negligence).",
                        "Default off: a child following its owner into lava still leaves a spirit.",
                        "主人就在 16 格内的环境死亡也算玩家责任（过失）。",
                        "默认关闭：孩子跟着主人掉进岩浆仍会留下灵体。")
                .define("spiritBlockOnOwnerNegligence", false);

        SPIRIT_CONTACT_WINDOW_TICKS = builder
                .comment("How recently (in ticks) a player must have damaged the victim for contact evidence.",
                        "接触证据窗口（tick）：死前多久内被该玩家或其宠物打过才算关联。")
                .defineInRange("spiritContactWindowTicks", 100, 20, 1200);

        SPIRIT_EXPLOSION_CACHE_TICKS = builder
                .comment("How long (in ticks) an explosion site is remembered for attribution.",
                        "爆炸归因缓存寿命（tick）。")
                .defineInRange("spiritExplosionCacheTicks", 5, 1, 40);

        SPIRIT_PLACED_BLOCK_TTL_TICKS = builder
                .comment("How long (in ticks) a player-placed hazard block stays attributable.",
                        "玩家放置的岩浆/火/TNT 等危险方块的归因寿命（tick）。")
                .defineInRange("spiritPlacedBlockTtlTicks", 6000, 200, 24000);

        SPIRIT_PUSH_WINDOW_TICKS = builder
                .comment("How long (in ticks) a player pushing the maid off a ledge/lava counts as the cause.",
                        "Requires the maid's velocity to change direction abruptly, the player to face her,",
                        "and a hazard (drop / lava / water / void) right in the push direction.",
                        "玩家碰撞箱（含船/矿车等载具）把后代推下去后，多久内的环境死亡仍算玩家责任。",
                        "需要同时满足：速度突变、玩家朝向女仆、推挤方向前方确有落差/岩浆/水/虚空。")
                .defineInRange("spiritPushWindowTicks", 100, 20, 600);

        SPIRIT_PUSH_DEVIATION_DEGREES = builder
                .comment("Minimum heading change (degrees) caused by the push to count as 'pushed off'.",
                        "Same-direction pushes are still detected separately: when the maid's own movement",
                        "intent (speed/input/yaw) is unchanged on land, an impulse can only come from outside.",
                        "Lower = catches softer nudges; higher = only clear shoves. Default 20.",
                        "推挤必须让女仆行进方向偏转的最小角度。越小越灵敏，越大越严格。默认 20 度。",
                        "同向推另有判据：陆地上移动意图（speed/输入/朝向）完全未变时，速度突变只可能来自外部。")
                .defineInRange("spiritPushDeviationDegrees", 20.0D, 0.0D, 90.0D);

        SPEC = builder.build();
    }
}
