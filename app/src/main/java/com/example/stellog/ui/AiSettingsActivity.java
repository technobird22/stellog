package com.example.stellog.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.stellog.R;

public class AiSettingsActivity extends AppCompatActivity {

    public static final String PREF_NAME = "ai_settings";
    public static final String KEY_API_KEY = "api_key";

    private EditText apiKeyInput;
    private TextView saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ai_settings_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        apiKeyInput = findViewById(R.id.api_key_input);
        saveButton = findViewById(R.id.save_api_key_button);

        findViewById(R.id.ai_settings_close).setOnClickListener(v -> finish());

        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedApiKey = preferences.getString(KEY_API_KEY, "");
        apiKeyInput.setText(savedApiKey);

        saveButton.setOnClickListener(v -> saveApiKey());
    }

    private void saveApiKey() {
        String apiKey = apiKeyInput.getText().toString().trim();

        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_API_KEY, apiKey)
                .apply();

        Toast.makeText(this, "API Key 已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}