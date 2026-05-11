package com.example.stellog.util;

import com.example.stellog.data.model.CheckInRecord;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 日期工具类。
 *
 * 当前主要服务于“今日打卡”和“本周打卡圆点”的日期计算。
 */
public final class DateUtils {
    private DateUtils() {
    }

    public static List<CheckInRecord.RecordDate> getCurrentWeekDates() {
        List<CheckInRecord.RecordDate> weekDates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int mondayOffset = dayOfWeek == Calendar.SUNDAY ? -6 : Calendar.MONDAY - dayOfWeek;
        calendar.add(Calendar.DAY_OF_MONTH, mondayOffset);

        for (int i = 0; i < 7; i++) {
            weekDates.add(CheckInRecord.RecordDate.fromCalendar(calendar));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        return weekDates;
    }

    public static String getTodayDateString() {
        CheckInRecord.RecordDate today = CheckInRecord.RecordDate.today();
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", today.year, today.month, today.day);
    }
}
