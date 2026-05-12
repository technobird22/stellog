package com.example.stellog.data.repository;

import android.content.Context;

import com.example.stellog.data.database.CheckInRecordDao;
import com.example.stellog.data.database.CheckInDateCount;
import com.example.stellog.data.database.CheckInRecordEntity;
import com.example.stellog.data.database.HabitDao;
import com.example.stellog.data.database.HabitEntity;
import com.example.stellog.data.database.StellogDatabase;
import com.example.stellog.data.model.CheckInRecord;
import com.example.stellog.data.model.Habit;
import com.example.stellog.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final List<Habit> habits = new ArrayList<>();

    public HabitRepository(Context context) {
        // 使用 applicationContext 创建数据库，避免 Repository 间接持有 Activity。
        StellogDatabase database = StellogDatabase.getInstance(context);
        habitDao = database.habitDao();
        checkInRecordDao = database.checkInRecordDao();
        reloadHabits();
    }

    public List<Habit> getHabits() {
        return habits;
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
}
