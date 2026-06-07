package com.example.stellog.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * habits 表的数据访问接口。
 *
 * DAO 只声明数据库操作方法，Room 会在编译期生成具体实现。
 */
@Dao
public interface HabitDao {
    /**
     * 按 id 升序读取所有习惯，用于初始化首页卡片列表。
     */
    @Query("SELECT * FROM habits ORDER BY id ASC")
    List<HabitEntity> getAll();

    /**
     * 根据 id 查找单个习惯。
     */
    @Query("SELECT * FROM habits WHERE id = :habitId LIMIT 1")
    HabitEntity findById(long habitId);

    /**
     * 生成下一个习惯 id。
     */
    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM habits")
    long nextId();

    /**
     * 插入新习惯。
     */
    @Insert
    void insert(HabitEntity habit);

    /**
     * 更新已有习惯。
     */
    @Update
    void update(HabitEntity habit);

    // 查询数量，用于解锁成就
    @Query("SELECT COUNT(*) FROM habits")
    int countAll();

    @Query("UPDATE habits SET reminderEnabled = :enabled, reminderTimeMinutes = :minutes, updatedAt = :updatedAt WHERE id = :habitId")
    void updateReminder(long habitId, boolean enabled, int minutes, long updatedAt);

    // 删除活动。
    @Query("DELETE FROM habits WHERE id = :habitId")
    void deleteById(long habitId);

    // 清空全部活动。
    @Query("DELETE FROM habits")
    void deleteAll();
}
