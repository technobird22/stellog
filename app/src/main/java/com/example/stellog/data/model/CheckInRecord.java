package com.example.stellog.data.model;

import java.util.Calendar;

/**
 * 单次打卡记录。
 *
 * Habit 表示一个活动，CheckInRecord 表示该活动在某一天的一次完成记录。
 */
public class CheckInRecord {
    public static final String SOURCE_NORMAL = "\u6b63\u5e38\u6253\u5361";
    public static final String SOURCE_PATCH = "\u8865\u6253\u5361";

    public final long id;
    public final long habitId;
    public final long userId;
    public final RecordDate date;
    public long value; // 用户在记录详细是自行填写的数字
    public final String source;
    public final long createdAt;
    public long updatedAt;

    public CheckInRecord(
            long id,
            long habitId,
            long userId,
            RecordDate date,
            long value,
            String source,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.habitId = habitId;
        this.userId = userId;
        this.date = date;
        this.value = value;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 只记录年月日，避免直接用毫秒时间戳比较时受到时分秒影响。
     */
    public static class RecordDate {
        public final int year;
        public final int month;
        public final int day;

        public RecordDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        public boolean isSameDay(RecordDate other) {
            return other != null
                    && year == other.year
                    && month == other.month
                    && day == other.day;
        }

        public static RecordDate today() {
            Calendar calendar = Calendar.getInstance();
            return fromCalendar(calendar);
        }

        public static RecordDate fromCalendar(Calendar calendar) {
            return new RecordDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
        }
    }
}
