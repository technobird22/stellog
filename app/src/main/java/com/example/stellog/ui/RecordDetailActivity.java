package com.example.stellog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.stellog.R;

/**
 * 记录详细页面。
 *
 * 只负责收集“今天完成的数量”，保存后通过 ActivityResult 返回给 MainActivity。
 */
public class RecordDetailActivity extends AppCompatActivity {
    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String EXTRA_HABIT_NAME = "habit_name";
    public static final String EXTRA_HABIT_UNIT = "habit_unit";
    public static final String EXTRA_RECORD_VALUE = "record_value";
    public static final String EXTRA_RECORD_YEAR = "record_year";
    public static final String EXTRA_RECORD_MONTH = "record_month";
    public static final String EXTRA_RECORD_DAY = "record_day";

    private long habitId;
    private int recordYear = -1;
    private int recordMonth = -1;
    private int recordDay = -1;
    private TextView saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_record_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.record_detail_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L);
        String habitName = intent.getStringExtra(EXTRA_HABIT_NAME);
        String habitUnit = intent.getStringExtra(EXTRA_HABIT_UNIT);
        long oldValue = intent.getLongExtra(EXTRA_RECORD_VALUE, 0L);
        recordYear = intent.getIntExtra(EXTRA_RECORD_YEAR, -1);
        recordMonth = intent.getIntExtra(EXTRA_RECORD_MONTH, -1);
        recordDay = intent.getIntExtra(EXTRA_RECORD_DAY, -1);

        TextView subtitle = findViewById(R.id.record_detail_subtitle);
        EditText valueInput = findViewById(R.id.record_value_input);

        if (habitName == null) {
            habitName = "";
        }
        if (habitUnit == null) {
            habitUnit = "";
        }

        subtitle.setText(getString(R.string.record_detail_subtitle, habitName, habitUnit));
        if (oldValue > 0) {
            valueInput.setText(String.valueOf(oldValue));
            valueInput.setSelection(valueInput.getText().length());
        }

        findViewById(R.id.record_detail_close_button).setOnClickListener(v -> finish());
        findViewById(R.id.record_detail_cancel_button).setOnClickListener(v -> finish());
        findViewById(R.id.record_minus_button).setOnClickListener(v -> adjustValue(-1));
        findViewById(R.id.record_plus_button).setOnClickListener(v -> adjustValue(1));
        saveButton = findViewById(R.id.record_detail_save_button);
        saveButton.setOnClickListener(v -> saveRecordValue());
    }

    // -1 / +1 步进，最小为 0。
    private void adjustValue(int delta) {
        EditText valueInput = findViewById(R.id.record_value_input);
        long current;
        String text = valueInput.getText().toString().trim();
        try {
            current = text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            current = 0;
        }
        long next = Math.max(0, current + delta);
        valueInput.setText(String.valueOf(next));
        valueInput.setSelection(valueInput.getText().length());
    }

    private void saveRecordValue() {
        EditText valueInput = findViewById(R.id.record_value_input);
        String valueText = valueInput.getText().toString().trim();

        if (valueText.isEmpty()) {
            valueInput.setError("\u6570\u91cf\u4e0d\u80fd\u4e3a\u7a7a");
            return;
        }

        long value;
        try {
            value = Long.parseLong(valueText);
        } catch (NumberFormatException e) {
            valueInput.setError("\u8bf7\u8f93\u5165\u6709\u6548\u6570\u5b57");
            return;
        }

        if (value < 0) {
            valueInput.setError("\u6570\u91cf\u4e0d\u80fd\u5c0f\u4e8e 0");
            return;
        }

        setSaveButtonLoading(true);

        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_HABIT_ID, habitId);
        resultIntent.putExtra(EXTRA_RECORD_VALUE, value);
        if (recordYear > 0 && recordMonth > 0 && recordDay > 0) {
            resultIntent.putExtra(EXTRA_RECORD_YEAR, recordYear);
            resultIntent.putExtra(EXTRA_RECORD_MONTH, recordMonth);
            resultIntent.putExtra(EXTRA_RECORD_DAY, recordDay);
        }
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void setSaveButtonLoading(boolean loading) {
        if (saveButton == null) {
            return;
        }
        saveButton.setEnabled(!loading);
        saveButton.setAlpha(loading ? 0.65f : 1f);
        saveButton.setText(loading ? "\u4fdd\u5b58\u4e2d..." : "\u4fdd\u5b58");
    }
}
