package com.maidclinic.health;

/**
 * 危重期的掉血方式。
 * <p>
 * {@link #VIRULENCE} 只按毒力持续扣血；{@link #SLEEP} 在扣血之外，
 * 额外让危重女仆在睡梦中按随机时间猝死（仅在开启疾病致死时生效）。
 * </p>
 */
public enum DeathStyle {
    /** 按毒力扣血。 */
    VIRULENCE("virulence"),
    /** 按毒力扣血，并且睡梦中可能猝死。 */
    SLEEP("sleep");

    private final String id;

    DeathStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "deathstyle.maidclinic." + id;
    }

    /** 解析 id；未知值退回 {@link #VIRULENCE}。 */
    public static DeathStyle byId(String id) {
        for (DeathStyle style : values()) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        return VIRULENCE;
    }
}
