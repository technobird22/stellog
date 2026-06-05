package com.example.stellog.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY sortOrder ASC")
    List<AchievementEntity> getAll();

    @Query("SELECT COUNT(*) FROM achievements")
    int count();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<AchievementEntity> achievements);

    @Update
    void update(AchievementEntity achievement);

    @Query("SELECT * FROM achievements WHERE code = :code LIMIT 1")
    AchievementEntity findByCode(String code);
}
