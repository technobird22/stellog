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
    private long habitId;

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
        habitId = intent.getLongExtra("habit_id", -1L);
        String habitName = intent.getStringExtra("habit_name");
        String habitUnit = intent.getStringExtra("habit_unit");
        long oldValue = intent.getLongExtra("record_value", 0L);

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
        findViewById(R.id.record_detail_save_button).setOnClickListener(v -> saveRecordValue());
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

        Intent resultIntent = new Intent();
        resultIntent.putExtra("habit_id", habitId);
        resultIntent.putExtra("record_value", value);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
