package com.example.stellog.data.model;

/**
 * 习惯活动的数据模型。
 *
 * 目前数据只保存在内存中，后续接入数据库时可以直接按这个结构建表。
 */
public class Habit {
    public final long id;
    public final long userId;
    public final String name;
    public final String unit;

    // 已打卡次数，用于卡片上的“多少天收获”展示。
    public int recordNum;

    public boolean reminderEnabled;
    public int reminderTimeMinutes; // minutes since midnight, -1 means not set
    public final int sortWeight;

    // 累计完成值，目前每次打卡默认增加 1。
    public long totalValue;

    public final long createdAt;

    // 更新时间会在打卡或取消打卡时刷新。
    public long updatedAt;

    public Habit(
            long id,
            long userId,
            String name,
            String unit,
            int recordNum,
            boolean reminderEnabled,
            int reminderTimeMinutes,
            int sortWeight,
            long totalValue,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.unit = unit;
        this.recordNum = recordNum;
        this.reminderEnabled = reminderEnabled;
        this.reminderTimeMinutes = reminderTimeMinutes;
        this.sortWeight = sortWeight;
        this.totalValue = totalValue;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
