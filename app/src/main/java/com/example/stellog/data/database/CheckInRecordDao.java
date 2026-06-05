package com.example.stellog.data.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * check_in_records 表的数据访问接口。
 */
@Dao
public interface CheckInRecordDao {
    /**
     * 查询某个习惯在指定日期是否已有打卡记录。
     */
    @Query("SELECT * FROM check_in_records WHERE habitId = :habitId AND dateKey = :dateKey LIMIT 1")
    CheckInRecordEntity findOnDate(long habitId, int dateKey);

    /**
     * 查询日历当前 42 个格子日期范围内，每一天共有多少条打卡记录。
     */
    @Query("SELECT dateKey, COUNT(*) AS count FROM check_in_records WHERE dateKey BETWEEN :startDateKey AND :endDateKey AND habitId IN (:habitIds) GROUP BY dateKey")
    List<CheckInDateCount> countByDateRange(int startDateKey, int endDateKey, List<Long> habitIds);

    /**
     * 查询某一天的所有打卡记录，日历详情区按 habitId 顺序展示。
     */
    @Query("SELECT * FROM check_in_records WHERE dateKey = :dateKey AND habitId IN (:habitIds) ORDER BY habitId ASC")
    List<CheckInRecordEntity> findByDate(int dateKey, List<Long> habitIds);

    /**
     * 生成下一条打卡记录 id。
     */
    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM check_in_records")
    long nextId();

    /**
     * 插入新打卡记录。
     */
    @Insert
    void insert(CheckInRecordEntity record);

    /**
     * 更新打卡记录，例如记录详情页修改完成数量。
     */
    @Update
    void update(CheckInRecordEntity record);

    /**
     * 删除打卡记录，例如取消今日打卡。
     */
    @Delete
    void delete(CheckInRecordEntity record);

    @Query("SELECT COUNT(*) FROM check_in_records")
    int countAll();

    // 查询某个习惯在某个来源（如日历详情页）已打卡的日期列表，用于展示已打卡的日期标签。
    @Query("SELECT dateKey FROM check_in_records WHERE habitId = :habitId AND source = :source")
    List<Integer> findDateKeysByHabitAndSource(long habitId, String source);
    }
