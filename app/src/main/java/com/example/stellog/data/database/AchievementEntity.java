package com.example.stellog.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.example.stellog.data.model.Achievement;

@Entity(tableName = "achievements")
public class AchievementEntity {
    @PrimaryKey
    @NonNull
    public String code;
    public String name;
    public String condition;
    public String iconKey;
    public String countText;
    public boolean unlocked;
    public long completedAt;
    public int sortOrder;

    public static AchievementEntity fromModel(Achievement achievement) {
        AchievementEntity entity = new AchievementEntity();
        entity.code = achievement.code;
        entity.name = achievement.name;
        entity.condition = achievement.condition;
        entity.iconKey = achievement.iconKey;
        entity.countText = achievement.countText;
        entity.unlocked = achievement.unlocked;
        entity.completedAt = achievement.completedAt;
        entity.sortOrder = achievement.sortOrder;
        return entity;
    }

    public Achievement toModel() {
        return new Achievement(
                code,
                name,
                condition,
                iconKey,
                countText,
                unlocked,
                completedAt,
                sortOrder
        );
    }
}
