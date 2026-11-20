package com.maidclinic.health;

/**
 * 玩家个人的玩法开关（按 UUID 保存在世界存档里）。
 * <p>字段为 {@code null} 表示“跟随配置文件”，不是“关闭”。</p>
 */
public final class PlayerSettings {
    private Boolean hardcore;
    private Boolean chronic;
    private Boolean efficiency;
    private Boolean sequela;
    private DeathStyle deathStyle;

    public Boolean hardcore() {
        return hardcore;
    }

    public void setHardcore(Boolean hardcore) {
        this.hardcore = hardcore;
    }

    public Boolean chronic() {
        return chronic;
    }

    public void setChronic(Boolean chronic) {
        this.chronic = chronic;
    }

    public Boolean efficiency() {
        return efficiency;
    }

    public void setEfficiency(Boolean efficiency) {
        this.efficiency = efficiency;
    }

    public Boolean sequela() {
        return sequela;
    }

    public void setSequela(Boolean sequela) {
        this.sequela = sequela;
    }

    /** 危重掉血方式；null 表示跟随配置文件。 */
    public DeathStyle deathStyle() {
        return deathStyle;
    }

    public void setDeathStyle(DeathStyle deathStyle) {
        this.deathStyle = deathStyle;
    }

    public boolean isEmpty() {
        return hardcore == null && chronic == null && efficiency == null && sequela == null
                && deathStyle == null;
    }
}
