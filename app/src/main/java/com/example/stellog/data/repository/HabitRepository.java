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
import java.util.Locale;

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

    public static class HabitPrioritySnapshot {
        public final long habitId;
        public final double score;
        public final String hint;

        public HabitPrioritySnapshot(long habitId, double score, String hint) {
            this.habitId = habitId;
            this.score = score;
            this.hint = hint;
        }
    }

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

    // 编辑活动名称和单位。name/unit 是 final，用拷贝其余字段的新 Habit 替换原对象。
    public Habit updateHabit(long habitId, String name, String unit) {
        int index = findHabitPosition(habitId);
        if (index < 0) {
            return null;
        }

        Habit old = habits.get(index);
        Habit updated = new Habit(
                old.id,
                old.userId,
                name,
                unit,
                old.recordNum,
                old.reminderEnabled,
                old.sortWeight,
                old.totalValue,
                old.createdAt,
                System.currentTimeMillis()
        );

        habitDao.update(HabitEntity.fromModel(updated));
        habits.set(index, updated);
        return updated;
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

    public List<HabitPrioritySnapshot> buildHabitPrioritySnapshots() {
        List<HabitPrioritySnapshot> snapshots = new ArrayList<>();
        if (habits.isEmpty()) {
            return snapshots;
        }

        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);
        Calendar startDate = (Calendar) today.clone();
        startDate.add(Calendar.DAY_OF_MONTH, -6);

        int startDateKey = DateUtils.toDateKey(startDate);
        int endDateKey = DateUtils.toDateKey(today);

        List<CheckInRecordEntity> recentRecords = checkInRecordDao.findByDateRange(startDateKey, endDateKey);
        Map<Long, HashSet<Integer>> checkedDateKeysByHabit = new HashMap<>();
        for (CheckInRecordEntity record : recentRecords) {
            HashSet<Integer> checkedDates = checkedDateKeysByHabit.get(record.habitId);
            if (checkedDates == null) {
                checkedDates = new HashSet<>();
                checkedDateKeysByHabit.put(record.habitId, checkedDates);
            }
            checkedDates.add(record.dateKey);
        }

        for (Habit habit : habits) {
            HashSet<Integer> checkedDates = checkedDateKeysByHabit.get(habit.id);
            if (checkedDates == null) {
                checkedDates = new HashSet<>();
            }

            int recentCheckInDays = checkedDates.size();
            int recentGapDays = Math.max(0, 7 - recentCheckInDays);
            int streakDays = countRecentStreakDays(checkedDates, today);

            double completionRate = recentCheckInDays / 7.0;
            double interruptionRisk = recentGapDays / 7.0;
            double streakScore = Math.min(streakDays, 14) / 14.0;

            // TODO: 接入提醒时间后，可增加“临近提醒时间”因子并参与加权。
            double riskAdjustedByActivity = interruptionRisk * (0.4 + 0.6 * completionRate);
            double score = riskAdjustedByActivity * 0.45
                    + completionRate * 0.35
                    + streakScore * 0.20;

            snapshots.add(new HabitPrioritySnapshot(habit.id, score, buildPriorityHint(
                    recentCheckInDays,
                    recentGapDays,
                    streakDays
            )));
        }

        return snapshots;
    }

    private int countRecentStreakDays(HashSet<Integer> checkedDateKeys, Calendar today) {
        int streak = 0;
        Calendar cursor = (Calendar) today.clone();
        while (checkedDateKeys.contains(DateUtils.toDateKey(cursor))) {
            streak++;
            cursor.add(Calendar.DAY_OF_MONTH, -1);
        }
        return streak;
    }

    private String buildPriorityHint(int recentCheckInDays, int recentGapDays, int streakDays) {
        if (recentGapDays >= 4 && recentCheckInDays >= 2) {
            return "中断风险较高，建议优先关注";
        }
        if (streakDays >= 3) {
            return "连续坚持表现较好";
        }
        if (recentCheckInDays >= 5) {
            return "近期活跃度较高";
        }
        if (recentCheckInDays <= 1) {
            return "建议从今天的小目标开始";
        }
        return "保持当前节奏";
    }

    public String buildCurrentWeekAiContextPrompt() {
        List<CheckInRecord.RecordDate> weekDates = DateUtils.getCurrentWeekDates();
        if (weekDates.isEmpty()) {
            return "本周数据暂时不可用。";
        }

        CheckInRecord.RecordDate startDate = weekDates.get(0);
        CheckInRecord.RecordDate endDate = weekDates.get(weekDates.size() - 1);

        int startDateKey = DateUtils.toDateKey(startDate);
        int endDateKey = DateUtils.toDateKey(endDate);

        List<HabitEntity> habitEntities = habitDao.getAll();
        List<CheckInRecordEntity> recordEntities = checkInRecordDao.findByDateRange(startDateKey, endDateKey);

        Map<Long, HabitEntity> habitById = new HashMap<>();
        for (HabitEntity habit : habitEntities) {
            habitById.put(habit.id, habit);
        }

        Map<Long, Integer> weeklyCountByHabit = new HashMap<>();
        Map<Long, Long> weeklyValueByHabit = new HashMap<>();
        Map<Long, Integer> normalCountByHabit = new HashMap<>();
        Map<Long, Integer> patchCountByHabit = new HashMap<>();

        for (CheckInRecordEntity record : recordEntities) {
            long habitId = record.habitId;

            weeklyCountByHabit.put(
                    habitId,
                    weeklyCountByHabit.getOrDefault(habitId, 0) + 1
            );

            weeklyValueByHabit.put(
                    habitId,
                    weeklyValueByHabit.getOrDefault(habitId, 0L) + record.value
            );

            if (CheckInRecord.SOURCE_NORMAL.equals(record.source)) {
                normalCountByHabit.put(
                        habitId,
                        normalCountByHabit.getOrDefault(habitId, 0) + 1
                );
            } else if (CheckInRecord.SOURCE_PATCH.equals(record.source)) {
                patchCountByHabit.put(
                        habitId,
                        patchCountByHabit.getOrDefault(habitId, 0) + 1
                );
            }
        }

        int totalHabitCount = habitEntities.size();
        int totalRecordCount = recordEntities.size();

        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是用户本周习惯数据，请你基于这些数据回答用户问题。");
        prompt.append("如果用户问题与数据无关，也可以正常回答，但不要编造不存在的打卡记录。\n\n");

        prompt.append("本周范围：")
                .append(formatDate(startDate))
                .append(" 至 ")
                .append(formatDate(endDate))
                .append("\n");

        prompt.append("用户当前活动数量：")
                .append(totalHabitCount)
                .append(" 个\n");

        prompt.append("本周总打卡次数：")
                .append(totalRecordCount)
                .append(" 次\n\n");

        prompt.append("各活动本周情况：\n");

        if (habitEntities.isEmpty()) {
            prompt.append("- 用户还没有创建活动。\n");
        } else {
            for (HabitEntity habit : habitEntities) {
                int weekCount = weeklyCountByHabit.getOrDefault(habit.id, 0);
                long weekValue = weeklyValueByHabit.getOrDefault(habit.id, 0L);
                int normalCount = normalCountByHabit.getOrDefault(habit.id, 0);
                int patchCount = patchCountByHabit.getOrDefault(habit.id, 0);

                prompt.append("- ")
                        .append(habit.name)
                        .append("：本周打卡 ")
                        .append(weekCount)
                        .append(" 次");

                if (weekValue > 0) {
                    prompt.append("，记录值合计 ")
                            .append(weekValue)
                            .append(habit.unit == null ? "" : habit.unit);
                }

                prompt.append("，正常打卡 ")
                        .append(normalCount)
                        .append(" 次，补打卡 ")
                        .append(patchCount)
                        .append(" 次");

                if (weekCount == 0) {
                    prompt.append("，本周尚未完成");
                }

                prompt.append("\n");
            }
        }

        prompt.append("\n回答要求：");
        prompt.append("1. 使用中文。");
        prompt.append("2. 回答要简洁、具体、鼓励用户继续行动。");
        prompt.append("3. 如果发现某个活动本周 0 次打卡，可以温和提醒。");
        prompt.append("4. 不要声称知道用户没有提供的数据。");

        return prompt.toString();
    }

    private String formatDate(CheckInRecord.RecordDate date) {
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", date.year, date.month, date.day);
    }

}
