package com.example.stellog.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.stellog.R;
import com.example.stellog.ui.MainActivity;

public class ReminderBroadcastReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "habit_reminder";
    private static final int NOTIFICATION_ID_BASE = 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        long habitId = intent.getLongExtra("habit_id", -1L);
        String habitName = intent.getStringExtra("habit_name");
        if (habitId < 0 || habitName == null) {
            return;
        }

        createNotificationChannel(context);

        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent.putExtra("focus_habit_id", habitId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) habitId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle("活动提醒")
                .setContentText("该打卡 \"" + habitName + "\" 了")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) (NOTIFICATION_ID_BASE + habitId), builder.build());
        }
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "习惯打卡提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("每天提醒你打卡习惯活动");
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
