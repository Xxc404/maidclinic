package com.maidclinic.health;

/**
 * 单条“正在发生”的疾病状态。
 * <p>
 * 与 {@link Condition}（静态定义）相对：这里保存严重程度、病程进度、
 * 是否已经对因治疗、抗生素疗程次数等会随时间变化的量。
 */
public final class ActiveCondition {
    private final String conditionId;
    private int severity = 1;
    private int elapsedTicks;
    private int durationTicks;
    private boolean treated;
    private boolean supported;
    private int doses;
    private long lastDoseTick = Long.MIN_VALUE;
    /** 这次发病的诱因（行为/事件），会写进健康档案。 */
    private Trigger cause = Trigger.NATURAL;

    public ActiveCondition(String conditionId, int severity, int durationTicks) {
        this.conditionId = conditionId;
        this.severity = Math.max(1, severity);
        this.durationTicks = Math.max(20, durationTicks);
    }

    public String conditionId() {
        return conditionId;
    }

    public int severity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = Math.max(1, severity);
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = Math.max(this.elapsedTicks + 20, durationTicks);
    }

    /** 让病情继续发展，并返回剩余 tick。 */
    public int progress(int ticks) {
        this.elapsedTicks += ticks;
        return remainingTicks();
    }

    public int remainingTicks() {
        return Math.max(0, durationTicks - elapsedTicks);
    }

    public float progressRatio() {
        return Math.min(1.0F, (float) elapsedTicks / (float) durationTicks);
    }

    public boolean finished() {
        return elapsedTicks >= durationTicks;
    }

    public boolean treated() {
        return treated;
    }

    public void setTreated(boolean treated) {
        this.treated = treated;
    }

    /** 是否接受过对症护理（补液、退热、冷敷等），用于区分“有人照顾”和“完全不管”。 */
    public boolean supported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public int doses() {
        return doses;
    }

    public void setDoses(int doses) {
        this.doses = doses;
    }

    public long lastDoseTick() {
        return lastDoseTick;
    }

    public void setLastDoseTick(long lastDoseTick) {
        this.lastDoseTick = lastDoseTick;
    }

    /** 本次发病的诱因。 */
    public Trigger cause() {
        return cause;
    }

    public void setCause(Trigger cause) {
        this.cause = cause == null ? Trigger.NATURAL : cause;
    }
}
