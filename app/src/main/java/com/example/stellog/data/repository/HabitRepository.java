package com.example.stellog.data.repository;

import com.example.stellog.data.model.CheckInRecord;
import com.example.stellog.data.model.Habit;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动数据仓库。
 *
 * 当前仍然使用内存列表保存数据；后续接入 Room/SQLite 时，UI 层只需要继续调用这里的方法。
 */
public class HabitRepository {
    private static final long DEFAULT_CHECK_IN_VALUE = 0L;

    private final List<Habit> habits = new ArrayList<>();
    private final List<CheckInRecord> records = new ArrayList<>();

    public List<Habit> getHabits() {
        return habits;
    }

    public Habit addHabit(String name, String unit) {
        long now = System.currentTimeMillis();
        long newId = habits.isEmpty() ? 1L : habits.get(habits.size() - 1).id + 1L;

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

        habits.add(newHabit);
        return newHabit;
    }

    public CheckInRecord getTodayRecord(long habitId) {
        CheckInRecord.RecordDate today = CheckInRecord.RecordDate.today();
        for (CheckInRecord record : records) {
            if (record.habitId == habitId && record.date.isSameDay(today)) {
                return record;
            }
        }
        return null;
    }

    public boolean checkInToday(Habit habit) {
        if (getTodayRecord(habit.id) != null) {
            return false;
        }

        long now = System.currentTimeMillis();
        CheckInRecord record = new CheckInRecord(
                generateRecordId(),
                habit.id,
                habit.userId,
                CheckInRecord.RecordDate.today(),
                DEFAULT_CHECK_IN_VALUE,
                CheckInRecord.SOURCE_NORMAL,
                now,
                now
        );

        records.add(record);
        habit.recordNum += 1;
        habit.totalValue += record.value;
        habit.updatedAt = now;
        return true;
    }

    public boolean cancelTodayCheckIn(Habit habit) {
        CheckInRecord todayRecord = getTodayRecord(habit.id);
        if (todayRecord == null) {
            return false;
        }

        records.remove(todayRecord);
        habit.recordNum = Math.max(0, habit.recordNum - 1);
        habit.totalValue = Math.max(0, habit.totalValue - todayRecord.value);
        habit.updatedAt = System.currentTimeMillis();
        return true;
    }

    public boolean hasRecordOnDate(long habitId, CheckInRecord.RecordDate date) {
        for (CheckInRecord record : records) {
            if (record.habitId == habitId && record.date.isSameDay(date)) {
                return true;
            }
        }
        return false;
    }

    public boolean applyRecordDetailValue(long habitId, long newValue) {
        int habitPosition = findHabitPosition(habitId);
        if (habitPosition < 0) {
            return false;
        }

        Habit habit = habits.get(habitPosition);
        CheckInRecord todayRecord = getTodayRecord(habitId);
        if (todayRecord == null) {
            return false;
        }

        long oldValue = todayRecord.value;
        if (newValue == oldValue) {
            return false;
        }

        long now = System.currentTimeMillis();
        todayRecord.value = newValue;
        todayRecord.updatedAt = now;
        habit.totalValue = habit.totalValue - oldValue + newValue;
        habit.updatedAt = now;
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

    private long generateRecordId() {
        if (records.isEmpty()) {
            return 1L;
        }
        return records.get(records.size() - 1).id + 1L;
    }
}
