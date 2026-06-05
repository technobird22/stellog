package com.example.stellog.data.repository;

import android.content.Context;

import com.example.stellog.data.database.AchievementDao;
import com.example.stellog.data.database.AchievementEntity;
import com.example.stellog.data.database.CheckInRecordDao;
import com.example.stellog.data.database.CheckInDateCount;
import com.example.stellog.data.database.CheckInRecordEntity;
import com.example.stellog.data.database.HabitDao;
import com.example.stellog.data.database.HabitEntity;
import com.example.stellog.data.database.StellogDatabase;
import com.example.stellog.data.model.Achievement;
import com.example.stellog.data.model.CheckInRecord;
import com.example.stellog.data.model.Habit;
import com.example.stellog.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * 习惯数据仓库。
 *
 * Repository 对上层 UI 提供稳定的业务方法，对下层通过 Room DAO 读写数据库。
 * 当前仍保留 habits 内存列表作为 ViewPager2 的数据源；Repository 创建时会先从 Room 加载已有数据。
 */
public class HabitRepository {
    private static final long DEFAULT_CHECK_IN_VALUE = 0L;

    private final HabitDao habitDao;
    private final CheckInRecordDao checkInRecordDao;
    private final AchievementDao achievementDao;
    private final List<Habit> habits = new ArrayList<>();

    public HabitRepository(Context context) {
        // 使用 applicationContext 创建数据库，避免 Repository 间接持有 Activity。
        StellogDatabase database = StellogDatabase.getInstance(context);
        habitDao = database.habitDao();
        checkInRecordDao = database.checkInRecordDao();
        achievementDao = database.achievementDao();
        seedDefaultAchievementsIfNeeded();
        reloadHabits();
    }

    public List<Habit> getHabits() {
        return habits;
    }

    public List<Achievement> getAchievements() {
        List<Achievement> achievements = new ArrayList<>();
        List<AchievementEntity> entities = achievementDao.getAll();
        for (AchievementEntity entity : entities) {
            achievements.add(entity.toModel());
        }
        return achievements;
    }

    public Habit addHabit(String name, String unit) {
        long now = System.currentTimeMillis();
        // id 由数据库当前最大 id 推导，保证重启应用后继续递增。
        long newId = habitDao.nextId();

        Habit newHabit = new Habit(
                newId,
                0,
                name,
                unit,
                0,
                false,
                1,
                0,
                now,
                now
        );

        // Room 只认识 Entity，UI 和业务层继续使用 Habit 模型。
        habitDao.insert(HabitEntity.fromModel(newHabit));
        habits.add(newHabit);
        return newHabit;
    }

    public CheckInRecord getTodayRecord(long habitId) {
        return getRecordOnDate(habitId, CheckInRecord.RecordDate.today());
    }

    public boolean checkInToday(Habit habit) {
        return checkInOnDate(habit, CheckInRecord.RecordDate.today(), CheckInRecord.SOURCE_NORMAL);
    }

    public boolean checkInOnDate(Habit habit, CheckInRecord.RecordDate date, String source) {
        if (getRecordOnDate(habit.id, date) != null) {
            return false;
        }

        long now = System.currentTimeMillis();
        CheckInRecord record = new CheckInRecord(
                checkInRecordDao.nextId(),
                habit.id,
                habit.userId,
                date,
                DEFAULT_CHECK_IN_VALUE,
                source,
                now,
                now
        );

        // 新增打卡记录后，同步更新 Habit 统计；补打卡的 record.date 是历史日期，createdAt 仍是当前创建时间。
        checkInRecordDao.insert(CheckInRecordEntity.fromModel(record));
        habit.recordNum += 1;
        habit.totalValue += record.value;
        habit.updatedAt = now;
        habitDao.update(HabitEntity.fromModel(habit));
        return true;
    }

    public boolean cancelTodayCheckIn(Habit habit) {
        CheckInRecord todayRecord = getTodayRecord(habit.id);
        if (todayRecord == null) {
            return false;
        }

        checkInRecordDao.delete(CheckInRecordEntity.fromModel(todayRecord));
        habit.recordNum = Math.max(0, habit.recordNum - 1);
        habit.totalValue = Math.max(0, habit.totalValue - todayRecord.value);
        habit.updatedAt = System.currentTimeMillis();
        habitDao.update(HabitEntity.fromModel(habit));
        return true;
    }

    public boolean hasRecordOnDate(long habitId, CheckInRecord.RecordDate date) {
        return getRecordOnDate(habitId, date) != null;
    }

    public Map<Integer, Integer> getCheckInCountByDateRange(
            Calendar startDate,
            Calendar endDate,
            Set<Long> selectedHabitIds
    ) {
        if (selectedHabitIds == null || selectedHabitIds.isEmpty()) {
            return new HashMap<>();
        }

        int startDateKey = DateUtils.toDateKey(startDate);
        int endDateKey = DateUtils.toDateKey(endDate);
        List<CheckInDateCount> counts = checkInRecordDao.countByDateRange(
                startDateKey,
                endDateKey,
                new ArrayList<>(selectedHabitIds)
        );

        Map<Integer, Integer> countByDateKey = new HashMap<>();
        for (CheckInDateCount count : counts) {
            countByDateKey.put(count.dateKey, count.count);
        }
        return countByDateKey;
    }

    public Map<Long, CheckInRecord> getRecordsByDate(CheckInRecord.RecordDate date, Set<Long> selectedHabitIds) {
        if (selectedHabitIds == null || selectedHabitIds.isEmpty()) {
            return new HashMap<>();
        }

        int dateKey = DateUtils.toDateKey(date);
        List<CheckInRecordEntity> entities = checkInRecordDao.findByDate(
                dateKey,
                new ArrayList<>(selectedHabitIds)
        );

        Map<Long, CheckInRecord> recordByHabitId = new HashMap<>();
        for (CheckInRecordEntity entity : entities) {
            CheckInRecord record = entity.toModel();
            recordByHabitId.put(record.habitId, record);
        }
        return recordByHabitId;
    }

    public boolean applyRecordDetailValue(long habitId, long newValue) {
        return applyRecordDetailValue(habitId, CheckInRecord.RecordDate.today(), newValue);
    }

    public boolean applyRecordDetailValue(long habitId, CheckInRecord.RecordDate date, long newValue) {
        int habitPosition = findHabitPosition(habitId);
        if (habitPosition < 0) {
            return false;
        }

        Habit habit = habits.get(habitPosition);
        CheckInRecord record = getRecordOnDate(habitId, date);
        if (record == null) {
            return false;
        }

        long oldValue = record.value;
        if (newValue == oldValue) {
            return false;
        }

        long now = System.currentTimeMillis();
        record.value = newValue;
        record.updatedAt = now;
        habit.totalValue = habit.totalValue - oldValue + newValue;
        habit.updatedAt = now;
        checkInRecordDao.update(CheckInRecordEntity.fromModel(record));
        habitDao.update(HabitEntity.fromModel(habit));
        return true;
    }

    public int findHabitPosition(long habitId) {
        for (int i = 0; i < habits.size(); i++) {
            if (habits.get(i).id == habitId) {
                return i;
            }
        }
        return -1;
    }

    private CheckInRecord getRecordOnDate(long habitId, CheckInRecord.RecordDate date) {
        // 单日查询走 dateKey 索引，避免逐字段比较时无法复用日历范围查询的索引策略。
        CheckInRecordEntity entity = checkInRecordDao.findOnDate(
                habitId,
                DateUtils.toDateKey(date)
        );
        if (entity == null) {
            return null;
        }
        return entity.toModel();
    }

    private void reloadHabits() {
        // 从 Room 读取持久化数据，并转换成 UI 层使用的 Habit 模型。
        habits.clear();
        List<HabitEntity> entities = habitDao.getAll();
        for (HabitEntity entity : entities) {
            habits.add(entity.toModel());
        }
    }

    // 成就解锁逻辑：每次创建习惯或打卡后检查是否满足成就条件，解锁后更新数据库并返回新解锁的成就列表。
    public List<Achievement> checkAchievementsAfterHabitCreated() {
        List<Achievement> unlocked = new ArrayList<>();
        unlockIfEligible("diverse_5", habitDao.countAll() >= 5, unlocked);
        return unlocked;
    }

    public List<Achievement> checkAchievementsAfterCheckIn(
            long habitId,
            CheckInRecord.RecordDate recordDate,
            String source
    ) {
        List<Achievement> unlocked = new ArrayList<>();

        int totalRecords = checkInRecordDao.countAll();
        unlockIfEligible("beginner", totalRecords >= 1, unlocked);
        unlockIfEligible("persistence_25", totalRecords >= 25, unlocked);
        unlockIfEligible("persistence_100", totalRecords >= 100, unlocked);
        unlockIfEligible("persistence_500", totalRecords >= 500, unlocked);

        if (CheckInRecord.SOURCE_NORMAL.equals(source)) {
            int consecutiveDays = countConsecutiveNormalDays(habitId, recordDate);
            unlockIfEligible("attendance_7", consecutiveDays >= 7, unlocked);
            unlockIfEligible("attendance_30", consecutiveDays >= 30, unlocked);
            unlockIfEligible("attendance_180", consecutiveDays >= 180, unlocked);
        }

        return unlocked;
    }

    private int countConsecutiveNormalDays(long habitId, CheckInRecord.RecordDate recordDate) {
        List<Integer> dateKeys = checkInRecordDao.findDateKeysByHabitAndSource(
                habitId,
                CheckInRecord.SOURCE_NORMAL
        );
        HashSet<Integer> dateKeySet = new HashSet<>(dateKeys);

        Calendar cursor = Calendar.getInstance();
        cursor.set(Calendar.YEAR, recordDate.year);
        cursor.set(Calendar.MONTH, recordDate.month - 1);
        cursor.set(Calendar.DAY_OF_MONTH, recordDate.day);
        DateUtils.clearTime(cursor);

        int consecutiveDays = 0;
        while (dateKeySet.contains(DateUtils.toDateKey(cursor))) {
            consecutiveDays++;
            cursor.add(Calendar.DAY_OF_MONTH, -1);
        }
        return consecutiveDays;
    }

    private void unlockIfEligible(String code, boolean eligible, List<Achievement> unlocked) {
        if (!eligible) {
            return;
        }

        AchievementEntity entity = achievementDao.findByCode(code);
        if (entity == null || entity.unlocked) {
            return;
        }

        entity.unlocked = true;
        entity.completedAt = System.currentTimeMillis();
        achievementDao.update(entity);
        unlocked.add(entity.toModel());
    }

    private void seedDefaultAchievementsIfNeeded() {
        if (achievementDao.count() > 0) {
            return;
        }

        long now = System.currentTimeMillis();
        List<Achievement> achievements = new ArrayList<>();
        achievements.add(new Achievement("beginner", "\u521d\u5fc3\u8005", "\u5b8c\u6210\u7b2c\u4e00\u6b21\u6253\u5361", "beginner", "1", false, 0, 0));
        achievements.add(new Achievement("attendance_7", "\u5168\u52e4 I", "\u67d0\u4e2a\u6d3b\u52a8\u8fde\u7eed\u6253\u5361 7 \u5929", "attendance_7", "7", false, 0, 1));
        achievements.add(new Achievement("attendance_30", "\u5168\u52e4 II", "\u67d0\u4e2a\u6d3b\u52a8\u8fde\u7eed\u6253\u5361 30 \u5929", "attendance_30", "30", false, 0, 2));
        achievements.add(new Achievement("attendance_180", "\u5168\u52e4 III", "\u67d0\u4e2a\u6d3b\u52a8\u8fde\u7eed\u6253\u5361 180 \u5929", "attendance_180", "180", false, 0, 3));
        achievements.add(new Achievement("persistence_25", "\u6301\u4e4b\u4ee5\u6052 I", "\u7d2f\u8ba1\u6253\u5361 25 \u6b21", "persistence_25", "25", false, 0, 4));
        achievements.add(new Achievement("persistence_100", "\u6301\u4e4b\u4ee5\u6052 II", "\u7d2f\u8ba1\u6253\u5361 100 \u6b21", "persistence_100", "100", false, 0, 5));
        achievements.add(new Achievement("persistence_500", "\u6301\u4e4b\u4ee5\u6052 III", "\u7d2f\u8ba1\u6253\u5361 500 \u6b21", "persistence_500", "500", false, 0, 6));
        achievements.add(new Achievement("diverse_5", "\u5168\u9762\u53d1\u5c55", "\u540c\u65f6\u62e5\u6709 5 \u4e2a\u6d3b\u52a8", "diverse_5", "5", false, 0, 7));

        List<AchievementEntity> entities = new ArrayList<>();
        for (Achievement achievement : achievements) {
            entities.add(AchievementEntity.fromModel(achievement));
        }
        achievementDao.insertAll(entities);
    }

}
