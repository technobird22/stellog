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
 * 创建活动页面。
 *
 * 目前只负责收集活动名称和单位，并通过 ActivityResult 返回给 MainActivity。
 */
public class CreateHabitActivity extends AppCompatActivity {
    private TextView saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_habit);

        // 处理沉浸式系统栏，避免内容被状态栏或导航栏遮挡。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_habit_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.create_close_button).setOnClickListener(v -> finish());
        findViewById(R.id.create_cancel_button).setOnClickListener(v -> finish());
        saveButton = findViewById(R.id.create_save_button);
        saveButton.setOnClickListener(v -> saveHabitInput());
    }

    private void saveHabitInput() {
        EditText nameInput = findViewById(R.id.habit_name_input);
        EditText unitInput = findViewById(R.id.habit_unit_input);

        // 名称必须填写；单位允许为空。
        String name = nameInput.getText().toString().trim();
        String unit = unitInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("\u6d3b\u52a8\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
            return;
        }

        setSaveButtonLoading(true);

        // 通过 Intent 返回输入结果，真正创建 Habit 的逻辑由 MainActivity 统一处理。
        Intent resultIntent = new Intent();
        resultIntent.putExtra("habit_name", name);
        resultIntent.putExtra("habit_unit", unit);

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
