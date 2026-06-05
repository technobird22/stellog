package com.example.stellog.data.model;

public class Achievement {
    public final String code;
    public final String name;
    public final String condition;
    public final String iconKey;
    public final String countText;
    public boolean unlocked;
    public long completedAt;
    public final int sortOrder;

    public Achievement(
            String code,
            String name,
            String condition,
            String iconKey,
            String countText,
            boolean unlocked,
            long completedAt,
            int sortOrder
    ) {
        this.code = code;
        this.name = name;
        this.condition = condition;
        this.iconKey = iconKey;
        this.countText = countText;
        this.unlocked = unlocked;
        this.completedAt = completedAt;
        this.sortOrder = sortOrder;
    }
}
