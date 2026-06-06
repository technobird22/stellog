package com.example.stellog.ui;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.stellog.R;

import java.util.Locale;

/**
 * 提醒编辑页面。
 *
 * 为当前活动设置提醒标题、开始和结束时间，保存到 SharedPreferences。
 */
public class ReminderEditActivity extends AppCompatActivity {
    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String EXTRA_HABIT_NAME = "habit_name";
    public static final String PREF_NAME = "reminders";

    private static final int DEFAULT_START_MINUTES = 8 * 60;
    private static final int DEFAULT_END_MINUTES = 8 * 60 + 30;

    private long habitId;
    private int startMinutes = DEFAULT_START_MINUTES;
    private int endMinutes = DEFAULT_END_MINUTES;

    private EditText titleInput;
    private TextView startValue;
    private TextView endValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reminder_edit);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reminder_edit_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L);
        String habitName = intent.getStringExtra(EXTRA_HABIT_NAME);
        if (habitName == null) {
            habitName = "";
        }

        titleInput = findViewById(R.id.reminder_title_input);
        startValue = findViewById(R.id.reminder_start_value);
        endValue = findViewById(R.id.reminder_end_value);

        loadSavedReminder(habitName);

        findViewById(R.id.reminder_close_button).setOnClickListener(v -> finish());
        findViewById(R.id.reminder_cancel_button).setOnClickListener(v -> finish());
        startValue.setOnClickListener(v -> pickTime(true));
        endValue.setOnClickListener(v -> pickTime(false));
        findViewById(R.id.reminder_save_button).setOnClickListener(v -> saveReminder());
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE);
    }

    private void loadSavedReminder(String habitName) {
        SharedPreferences prefs = prefs();
        // 默认带入活动名称，便于快速设置
        String savedTitle = prefs.getString(keyTitle(habitId), habitName);
        startMinutes = prefs.getInt(keyStart(habitId), DEFAULT_START_MINUTES);
        endMinutes = prefs.getInt(keyEnd(habitId), DEFAULT_END_MINUTES);

        titleInput.setText(savedTitle);
        titleInput.setSelection(titleInput.getText().length());
        updateTimeLabels();
    }

    private void pickTime(boolean isStart) {
        int current = isStart ? startMinutes : endMinutes;
        new TimePickerDialog(
                this,
                (view, hour, minute) -> {
                    if (isStart) {
                        startMinutes = hour * 60 + minute;
                    } else {
                        endMinutes = hour * 60 + minute;
                    }
                    updateTimeLabels();
                },
                current / 60,
                current % 60,
                true
        ).show();
    }

    private void updateTimeLabels() {
        startValue.setText(formatTime(startMinutes));
        endValue.setText(formatTime(endMinutes));
    }

    private String formatTime(int minutesOfDay) {
        return String.format(Locale.CHINA, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    private void saveReminder() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("提醒标题不能为空");
            return;
        }
        if (endMinutes < startMinutes) {
            Toast.makeText(this, "结束时间不能早于开始时间", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs().edit()
                .putBoolean(keyEnabled(habitId), true)
                .putString(keyTitle(habitId), title)
                .putInt(keyStart(habitId), startMinutes)
                .putInt(keyEnd(habitId), endMinutes)
                .apply();

        Toast.makeText(this, "提醒已保存", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private static String keyEnabled(long habitId) {
        return "reminder_" + habitId + "_enabled";
    }

    private static String keyTitle(long habitId) {
        return "reminder_" + habitId + "_title";
    }

    private static String keyStart(long habitId) {
        return "reminder_" + habitId + "_start";
    }

    private static String keyEnd(long habitId) {
        return "reminder_" + habitId + "_end";
    }
}
