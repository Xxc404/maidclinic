package com.maidclinic.health;

import java.util.Locale;

/**
 * 可对女仆使用的治疗/护理手段。
 * <p>
 * 每个枚举值对应一个物品（物品注册名见 {@link #itemName()}），
 * 疾病定义通过 {@link #primary()} / {@link #supportive()} / {@link #harmful()}
 * 声明“什么病该用什么、什么药不能用”。
 */
public enum Treatment {
    /** 对乙酰氨基酚：解热镇痛，仅对症。 */
    ANTIPYRETIC("antipyretic"),
    /** 阿莫西林：只对细菌感染有效，需足疗程。 */
    ANTIBIOTIC("antibiotic"),
    /** 阿奇霉素：青霉素过敏者的替代选择。 */
    AZITHROMYCIN("azithromycin"),
    /** 口服补液盐（ORS）：腹泻/中暑脱水的一线处理。 */
    ORS("ors"),
    /** 氯雷他定：抗组胺，用于过敏性鼻炎。 */
    ANTIHISTAMINE("antihistamine"),
    /** 铁剂（配维生素 C）：缺铁性贫血。 */
    IRON("iron_supplement"),
    /** 褪黑素：短期助眠，不替代睡眠卫生。 */
    MELATONIN("melatonin"),
    /** 绷带：清创包扎。 */
    BANDAGE("bandage"),
    /** 清洁敷料：覆盖伤口/烫伤创面。 */
    DRESSING("dressing"),
    /** 冷敷贴：物理降温，用于发热、中暑、烫伤早期。 */
    COOLING("cooling_patch"),
    /** 葡萄糖片：低血糖快速补糖。 */
    GLUCOSE("glucose"),
    /** 降压药：长期控制血压。 */
    ANTIHYPERTENSIVE("antihypertensive"),
    /** 降糖药（二甲双胍）：2 型糖尿病长期管理。 */
    ANTIDIABETIC("antidiabetic"),
    /** 抑酸药：慢性胃炎对症。 */
    ANTACID("antacid"),
    /** 钙+维生素D：骨质疏松长期管理。 */
    CALCIUM("calcium_vitamin_d");

    private final String itemName;

    Treatment(String itemName) {
        this.itemName = itemName;
    }

    /** 对应物品的注册名（不含命名空间）。 */
    public String itemName() {
        return itemName;
    }

    public String translationKey() {
        return "treatment.maidclinic." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * 是否属于“急救/护理”类操作：这类操作不要求先问诊，
     * 避免玩家在突发情况下被流程卡住。
     */
    public boolean firstAid() {
        return this == BANDAGE || this == DRESSING || this == COOLING || this == ORS || this == GLUCOSE;
    }
}
