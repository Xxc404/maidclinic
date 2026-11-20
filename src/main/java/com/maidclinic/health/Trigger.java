package com.maidclinic.health;

/**
 * 疾病诱因。
 * <p>
 * 本模组的疾病不再只是“随机发生”，而是由女仆在游戏里的<b>行为与处境</b>触发：
 * 淋雨受寒、高温暴晒、火焰灼伤、战斗受伤、过度劳累、熬夜缺觉、饥饿、精神压力、
 * 花粉过敏原、不洁饮食，以及女仆之间的交叉感染。
 * </p>
 * <p>
 * 诱因会随疾病一起记录在健康档案里，问诊与查看档案时都会显示，
 * 让玩家能看懂“她为什么会得这个病”。
 * </p>
 */
public enum Trigger {
    /** 没有任何明确诱因的自然发病（权重很低）。 */
    NATURAL("natural"),
    /** 淋雨、泡水、寒冷环境。 */
    CHILL("chill"),
    /** 高温环境、暴晒、闷热。 */
    HEAT("heat"),
    /** 火焰、岩浆、着火。 */
    FIRE("fire"),
    /** 战斗、被怪物攻击。 */
    COMBAT("combat"),
    /** 摔伤、仙人掌、甜浆果丛等外伤。 */
    INJURY("injury"),
    /** 长时间劳作、体力透支。 */
    OVERWORK("overwork"),
    /** 熬夜、夜间工作、缺乏睡眠。 */
    SLEEP("sleep"),
    /** 饥饿、营养不足。 */
    HUNGER("hunger"),
    /** 压力、低好感度、长期紧张。 */
    STRESS("stress"),
    /** 花粉、花草、蜂箱等过敏原。 */
    ALLERGEN("allergen"),
    /** 吃了腐肉、生肉、河豚等不洁食物。 */
    FOOD("food"),
    /** 被附近生病的女仆传染。 */
    CONTAGION("contagion"),
    /** 药物不良反应（如退热药过量）。 */
    IATROGENIC("iatrogenic"),
    /** 其它疾病发展出的并发症。 */
    COMPLICATION("complication");

    private final String id;

    Trigger(String id) {
        this.id = id;
    }

    /** 存档/网络使用的稳定 id，不随枚举顺序变化。 */
    public String id() {
        return id;
    }

    /** 诱因名称的翻译键。 */
    public String nameKey() {
        return "cause.maidclinic." + id;
    }

    /** 健康档案里“近期状态”的短名称翻译键。 */
    public String exposureKey() {
        return "exposure.maidclinic." + id;
    }

    /** 行为预警文本的翻译键。 */
    public String warningKey() {
        return "message.maidclinic.warning." + id;
    }

    public static Trigger byId(String id) {
        if (id != null) {
            for (Trigger trigger : values()) {
                if (trigger.id.equals(id)) {
                    return trigger;
                }
            }
        }
        return NATURAL;
    }
}
