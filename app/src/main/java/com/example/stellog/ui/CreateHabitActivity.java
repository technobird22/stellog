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
 * 创建/编辑活动页面。
 *
 * 收集活动名称和单位，通过 ActivityResult 返回给 MainActivity。
 * 传入 habit_id 时进入编辑模式，预填并在返回时带上 id。
 */
public class CreateHabitActivity extends AppCompatActivity {
    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String EXTRA_HABIT_NAME = "habit_name";
    public static final String EXTRA_HABIT_UNIT = "habit_unit";

    private long editHabitId = -1L;
    private TextView saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_habit);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_habit_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText nameInput = findViewById(R.id.habit_name_input);
        EditText unitInput = findViewById(R.id.habit_unit_input);

        // 编辑模式：带入活动 id 时预填名称和单位。
        editHabitId = getIntent().getLongExtra(EXTRA_HABIT_ID, -1L);
        if (editHabitId > 0) {
            ((TextView) findViewById(R.id.create_title)).setText("编辑活动");
            String name = getIntent().getStringExtra(EXTRA_HABIT_NAME);
            String unit = getIntent().getStringExtra(EXTRA_HABIT_UNIT);
            nameInput.setText(name == null ? "" : name);
            nameInput.setSelection(nameInput.getText().length());
            unitInput.setText(unit == null ? "" : unit);
        }

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
            nameInput.setError("活动名称不能为空");
            return;
        }

        setSaveButtonLoading(true);

        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_HABIT_NAME, name);
        resultIntent.putExtra(EXTRA_HABIT_UNIT, unit);
        if (editHabitId > 0) {
            resultIntent.putExtra(EXTRA_HABIT_ID, editHabitId);
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
        saveButton.setText(loading ? "保存中..." : "保存");
    }
}
