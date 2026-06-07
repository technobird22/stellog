package com.example.stellog.ui;

import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.stellog.R;
import com.example.stellog.data.database.HabitDao;
import com.example.stellog.data.database.StellogDatabase;
import com.example.stellog.data.model.Habit;
import com.example.stellog.util.ReminderScheduler;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderEditActivity extends AppCompatActivity {
    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String EXTRA_HABIT_NAME = "habit_name";
    public static final String EXTRA_REMINDER_ENABLED = "reminder_enabled";
    public static final String EXTRA_REMINDER_TIME = "reminder_time";

    private long habitId;
    private String habitName;
    private boolean reminderEnabled;
    private int reminderTimeMinutes;
    private HabitDao habitDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Switch enableSwitch;
    private TextView timeText;
    private android.view.View timePanel;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> { }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_edit);

        habitDao = StellogDatabase.getInstance(getApplicationContext()).habitDao();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        habitId = getIntent().getLongExtra(EXTRA_HABIT_ID, -1L);
        habitName = getIntent().getStringExtra(EXTRA_HABIT_NAME);
        reminderEnabled = getIntent().getBooleanExtra(EXTRA_REMINDER_ENABLED, false);
        reminderTimeMinutes = getIntent().getIntExtra(EXTRA_REMINDER_TIME, -1);

        TextView habitNameText = findViewById(R.id.reminder_habit_name);
        habitNameText.setText(habitName != null ? habitName : "");

        enableSwitch = findViewById(R.id.reminder_enable_switch);
        timeText = findViewById(R.id.reminder_time_text);
        timePanel = findViewById(R.id.reminder_time_panel);

        enableSwitch.setChecked(reminderEnabled);
        updateTimeDisplay();
        timePanel.setVisibility(reminderEnabled ? android.view.View.VISIBLE : android.view.View.GONE);

        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            reminderEnabled = isChecked;
            timePanel.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
            if (isChecked && reminderTimeMinutes < 0) {
                java.util.Calendar now = java.util.Calendar.getInstance();
                reminderTimeMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
                updateTimeDisplay();
            }
        });

        timePanel.setOnClickListener(v -> showTimePicker());

        findViewById(R.id.reminder_cancel_button).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.reminder_save_button).setOnClickListener(v -> saveReminder());
    }

    private void showTimePicker() {
        int hour = reminderTimeMinutes >= 0 ? reminderTimeMinutes / 60 : 8;
        int minute = reminderTimeMinutes >= 0 ? reminderTimeMinutes % 60 : 0;

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minuteOfDay) -> {
                    reminderTimeMinutes = hourOfDay * 60 + minuteOfDay;
                    updateTimeDisplay();
                },
                hour,
                minute,
                true
        );
        dialog.show();
    }

    private void updateTimeDisplay() {
        if (reminderTimeMinutes >= 0) {
            int hour = reminderTimeMinutes / 60;
            int minute = reminderTimeMinutes % 60;
            timeText.setText(String.format(Locale.CHINA, "%02d:%02d", hour, minute));
        } else {
            timeText.setText("--:--");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void saveReminder() {
        findViewById(R.id.reminder_save_button).setEnabled(false);
        long now = System.currentTimeMillis();
        int minutes = reminderEnabled ? reminderTimeMinutes : -1;

        executor.execute(() -> {
            habitDao.updateReminder(habitId, reminderEnabled, minutes, now);
            Habit habit = habitDao.findById(habitId).toModel();
            ReminderScheduler.scheduleReminder(getApplicationContext(), habit);

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_HABIT_ID, habitId);
            resultIntent.putExtra(EXTRA_REMINDER_ENABLED, reminderEnabled);
            resultIntent.putExtra(EXTRA_REMINDER_TIME, minutes);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}
