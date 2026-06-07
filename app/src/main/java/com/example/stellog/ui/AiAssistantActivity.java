package com.example.stellog.ui;
import com.example.stellog.data.repository.HabitRepository;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.stellog.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAssistantActivity extends AppCompatActivity {

    private EditText messageInput;
    private TextView sendButton;
    private LinearLayout chatMessages;


    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final JSONArray conversationInput = new JSONArray();

    private HabitRepository habitRepository;
    private String weeklyContextPrompt = "本周数据正在读取中。";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_assistant);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ai_assistant_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        messageInput = findViewById(R.id.ai_message_input);
        sendButton = findViewById(R.id.ai_send_button);
        chatMessages = findViewById(R.id.ai_chat_messages);

        findViewById(R.id.ai_assistant_close_button).setOnClickListener(v -> finish());

        sendButton.setOnClickListener(v -> sendMessage());

        addAssistantMessage("你好，我是你的习惯助手。你可以问我关于习惯养成、活动安排或打卡复盘的问题。");
        loadWeeklyContextPrompt();
    }

    private void loadWeeklyContextPrompt() {
        aiExecutor.execute(() -> {
            try {
                habitRepository = new HabitRepository(getApplicationContext());
                String contextPrompt = habitRepository.buildCurrentWeekAiContextPrompt();
                weeklyContextPrompt = contextPrompt;
                runOnUiThread(() ->
                        Toast.makeText(this, "本周数据读取完成", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                weeklyContextPrompt = "本周数据读取失败。回答时不要假设用户本周数据。";
                runOnUiThread(() ->
                        Toast.makeText(this, "正在读取本周数据，请稍后再试", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void sendMessage() {
        String userText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(userText)) {
            Toast.makeText(this, "请输入问题", Toast.LENGTH_SHORT).show();
            return;
        }

        String apiKey = getApiKey();
        String apiUrl = getApiUrl();
        String model = getModel();
        // 默认 DeepSeek 地址需要 API Key；自定义（如本地）地址允许留空。
        if (apiUrl.equals(AiSettingsActivity.DEFAULT_API_URL) && TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "请先在 AI 设置中填写 API Key", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AiSettingsActivity.class));
            return;
        }

        messageInput.setText("");
        addUserMessage(userText);

        TextView aiBubble = addAssistantMessage("");
        sendButton.setEnabled(false);
        sendButton.setText("发送中");

        aiExecutor.execute(() -> {
            try {
                requestAiReply(apiUrl, model, apiKey, userText, aiBubble);

                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    sendButton.setText("发送");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    aiBubble.setText("请求失败了，请检查网络、API Key、模型名称或账户余额后重试。");
                    sendButton.setEnabled(true);
                    sendButton.setText("发送");
                });
            }
        });
    }

    private String getApiKey() {
        SharedPreferences preferences = getSharedPreferences(AiSettingsActivity.PREF_NAME, MODE_PRIVATE);
        return preferences.getString(AiSettingsActivity.KEY_API_KEY, "");
    }

    private String getApiUrl() {
        SharedPreferences preferences = getSharedPreferences(AiSettingsActivity.PREF_NAME, MODE_PRIVATE);
        return preferences.getString(AiSettingsActivity.KEY_API_URL, AiSettingsActivity.DEFAULT_API_URL);
    }

    private String getModel() {
        SharedPreferences preferences = getSharedPreferences(AiSettingsActivity.PREF_NAME, MODE_PRIVATE);
        return preferences.getString(AiSettingsActivity.KEY_MODEL, AiSettingsActivity.DEFAULT_MODEL);
    }

    private String requestAiReply(String apiUrl, String model, String apiKey, String userText, TextView aiBubble) throws Exception {
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", userText);
        conversationInput.put(userMessage);

        JSONArray messages = new JSONArray();

        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildSystemPrompt());
        messages.put(systemMessage);

        for (int i = 0; i < conversationInput.length(); i++) {
            messages.put(conversationInput.getJSONObject(i));
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        // 本地模型可不需要 API Key，留空时不发送 Authorization 头。
        if (!TextUtils.isEmpty(apiKey)) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream(),
                StandardCharsets.UTF_8
        ));

        if (code < 200 || code >= 300) {
            StringBuilder errorBuilder = new StringBuilder();
            String errorLine;
            while ((errorLine = reader.readLine()) != null) {
                errorBuilder.append(errorLine);
            }
            throw new RuntimeException(errorBuilder.toString());
        }

        StringBuilder fullReply = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring("data:".length()).trim();
            if ("[DONE]".equals(data)) {
                break;
            }

            JSONObject chunk = new JSONObject(data);
            JSONArray choices = chunk.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                continue;
            }

            JSONObject delta = choices
                    .getJSONObject(0)
                    .optJSONObject("delta");

            if (delta == null) {
                continue;
            }

            String piece = delta.optString("content", "");
            if (TextUtils.isEmpty(piece)) {
                continue;
            }

            fullReply.append(piece);

            runOnUiThread(() -> aiBubble.setText(fullReply.toString()));
        }

        String reply = fullReply.toString();

        JSONObject assistantMessage = new JSONObject();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", reply);
        conversationInput.put(assistantMessage);

        return reply;
    }

    private String buildSystemPrompt() {
        return "你是 Stellog 应用内的 AI 习惯助手。"
                + "你的任务是围绕习惯养成、活动安排、打卡复盘给出建议。"
                + "回答要简洁、具体、可执行，语气温和鼓励。\n\n"
                + weeklyContextPrompt;
    }

    private TextView addUserMessage(String text) {
        return addMessageBubble(text, true);
    }

    private TextView addAssistantMessage(String text) {
        return addMessageBubble(text, false);
    }

    private TextView addMessageBubble(String text, boolean isUser) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setGravity(isUser ? android.view.Gravity.END : android.view.Gravity.START);
        row.setPadding(0, 0, 0, 24);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setTextColor(isUser ? getColor(android.R.color.white) : getColor(R.color.stellog_ink));
        bubble.setPadding(24, 16, 24, 16);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        bubble.setBackgroundResource(isUser ? R.drawable.bg_chat_user_bubble : R.drawable.bg_chat_ai_bubble);

        row.addView(bubble);
        chatMessages.addView(row);

        return bubble;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        aiExecutor.shutdown();
    }
}
