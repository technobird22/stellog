package com.example.stellog.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
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
    public static final String KEY_API_URL = "api_url";
    public static final String KEY_MODEL = "model";
    public static final String DEFAULT_API_URL = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    private EditText apiUrlInput;
    private EditText modelInput;
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

        apiUrlInput = findViewById(R.id.api_url_input);
        modelInput = findViewById(R.id.model_input);
        apiKeyInput = findViewById(R.id.api_key_input);
        saveButton = findViewById(R.id.save_api_key_button);

        findViewById(R.id.ai_settings_close).setOnClickListener(v -> finish());

        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        apiUrlInput.setText(preferences.getString(KEY_API_URL, DEFAULT_API_URL));
        modelInput.setText(preferences.getString(KEY_MODEL, DEFAULT_MODEL));
        apiKeyInput.setText(preferences.getString(KEY_API_KEY, ""));

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String apiUrl = apiUrlInput.getText().toString().trim();
        String model = modelInput.getText().toString().trim();
        String apiKey = apiKeyInput.getText().toString().trim();

        // 地址和模型留空时回退到默认值；API Key 允许为空（本地模型不需要）。
        if (apiUrl.isEmpty()) {
            apiUrl = DEFAULT_API_URL;
        }
        if (model.isEmpty()) {
            model = DEFAULT_MODEL;
        }

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_API_URL, apiUrl)
                .putString(KEY_MODEL, model)
                .putString(KEY_API_KEY, apiKey)
                .apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
