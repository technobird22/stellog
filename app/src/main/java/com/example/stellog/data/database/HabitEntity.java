package com.example.stellog.data.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.example.stellog.data.model.Habit;

/**
 * Room 中的习惯表结构。
 *
 * Entity 只负责描述数据库字段；业务层和 UI 层仍然使用 Habit 模型。
 */
@Entity(tableName = "habits")
public class HabitEntity {
    @PrimaryKey
    public long id;
    public long userId;
    public String name;
    public String unit;
    public int recordNum;
    public boolean reminderEnabled;
    public int reminderTimeMinutes;
    public int sortWeight;
    public long totalValue;
    public long createdAt;
    public long updatedAt;

    /**
     * 将业务模型转换成数据库 Entity，用于插入或更新 Room。
     */
    public static HabitEntity fromModel(Habit habit) {
        HabitEntity entity = new HabitEntity();
        entity.id = habit.id;
        entity.userId = habit.userId;
        entity.name = habit.name;
        entity.unit = habit.unit;
        entity.recordNum = habit.recordNum;
        entity.reminderEnabled = habit.reminderEnabled;
        entity.reminderTimeMinutes = habit.reminderTimeMinutes;
        entity.sortWeight = habit.sortWeight;
        entity.totalValue = habit.totalValue;
        entity.createdAt = habit.createdAt;
        entity.updatedAt = habit.updatedAt;
        return entity;
    }

    /**
     * 将数据库 Entity 转回业务模型，用于 UI 展示和业务计算。
     */
    public Habit toModel() {
        return new Habit(
                id,
                userId,
                name,
                unit,
                recordNum,
                reminderEnabled,
                reminderTimeMinutes,
                sortWeight,
                totalValue,
                createdAt,
                updatedAt
        );
    }
}
