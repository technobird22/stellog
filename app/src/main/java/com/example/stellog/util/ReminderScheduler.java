package com.example.stellog.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.example.stellog.data.model.Habit;
import com.example.stellog.receiver.ReminderBroadcastReceiver;

import java.util.Calendar;

public final class ReminderScheduler {
    private ReminderScheduler() {
    }

    public static void scheduleReminder(Context context, Habit habit) {
        if (!habit.reminderEnabled || habit.reminderTimeMinutes < 0) {
            cancelReminder(context, habit.id);
            return;
        }

        int hour = habit.reminderTimeMinutes / 60;
        int minute = habit.reminderTimeMinutes % 60;

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        intent.putExtra("habit_id", habit.id);
        intent.putExtra("habit_name", habit.name);

        int requestCode = (int) (habit.id % 100000);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // cancel any existing alarm for this habit first
        alarmManager.cancel(pendingIntent);

        // set the next upcoming alarm time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // use setAlarmClock for maximum reliability – system treats this as a user-set alarm
        AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                calendar.getTimeInMillis(), pendingIntent
        );
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
    }

    public static void cancelReminder(Context context, long habitId) {
        int requestCode = (int) (habitId % 100000);
        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        pendingIntent.cancel();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
