package com.example.stellog.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Stellog 的 Room 数据库入口。
 *
 * 这里声明数据库包含哪些表，并提供 DAO 访问入口。
 * 数据库文件名为 stellog.db，存放在应用私有数据目录中。
 */
@Database(
        entities = {HabitEntity.class, CheckInRecordEntity.class},
        version = 2,
        exportSchema = false
)
public abstract class StellogDatabase extends RoomDatabase {
    private static volatile StellogDatabase instance;

    /**
     * 习惯表 DAO。
     */
    public abstract HabitDao habitDao();

    /**
     * 打卡记录表 DAO。
     */
    public abstract CheckInRecordDao checkInRecordDao();

    public static StellogDatabase getInstance(Context context) {
        // 双重检查锁：保证整个应用进程中只创建一个数据库实例。
        if (instance == null) {
            synchronized (StellogDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    StellogDatabase.class,
                                    "stellog.db"
                            )
                            // 开发阶段表结构变化时直接重建数据库；正式版本应改为 Migration。
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return instance;
    }
}
