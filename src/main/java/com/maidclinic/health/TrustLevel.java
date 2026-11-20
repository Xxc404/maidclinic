package com.maidclinic.health;

/**
 * 信任等级。数值 0～1000，等级只影响提示文案与签约门槛。
 */
public enum TrustLevel {
    STRANGER(0, "stranger"),
    ACQUAINTED(150, "acquainted"),
    TRUSTED(350, "trusted"),
    TACIT(600, "tacit"),
    CLOSE(850, "close");

    private final int threshold;
    private final String key;

    TrustLevel(int threshold, String key) {
        this.threshold = threshold;
        this.key = key;
    }

    public int threshold() {
        return threshold;
    }

    public String translationKey() {
        return "trust.maidclinic." + key;
    }

    public static TrustLevel of(int trust) {
        TrustLevel result = STRANGER;
        for (TrustLevel level : values()) {
            if (trust >= level.threshold) {
                result = level;
            }
        }
        return result;
    }
}
