package com.example.stellog.ui;

import com.example.stellog.data.model.Habit;
import com.example.stellog.data.repository.HabitRepository;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAssistantActivity extends AppCompatActivity {

    private EditText messageInput;
    private TextView sendButton;
    private LinearLayout chatMessages;
    private ScrollView chatScroll;
    private TextView introSubtitle;

    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    // 对话内容在应用进程内保留，离开再回来不会丢失；点“新对话”可清空。
    private static JSONArray conversationInput = new JSONArray();

    private HabitRepository habitRepository;
    private String contextPrompt = "习惯数据正在读取中。";

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
        chatScroll = findViewById(R.id.ai_assistant_content);
        introSubtitle = findViewById(R.id.ai_intro_subtitle);

        findViewById(R.id.ai_assistant_close_button).setOnClickListener(v -> finish());
        findViewById(R.id.ai_new_chat_button).setOnClickListener(v -> startNewChat());
        sendButton.setOnClickListener(v -> sendMessage());
        setupQuickActions();

        renderSavedConversation();
        loadContextPrompt();
    }

    private void setupQuickActions() {
        wireQuickAction(R.id.ai_chip_review, "请帮我做本周打卡复盘，总结完成情况并给出可执行的改进建议。");
        wireQuickAction(R.id.ai_chip_encourage, "请根据我的近期表现，给我一句简短有力的鼓励。");
        wireQuickAction(R.id.ai_chip_today, "我今天还有哪些活动没完成？请按优先级简要建议我先做什么。");
        wireQuickAction(R.id.ai_chip_risk, "结合我的数据，指出哪些习惯有中断风险，并给出补救建议。");
        wireQuickAction(R.id.ai_chip_time, "根据我的习惯和已设提醒，推荐更合理的每日打卡时间安排。");
    }

    private void wireQuickAction(int viewId, String prompt) {
        TextView chip = findViewById(viewId);
        if (chip != null) {
            chip.setOnClickListener(v -> sendUserText(prompt));
        }
    }

    // 进入页面时把进程内保留的对话重新渲染出来。
    private void renderSavedConversation() {
        chatMessages.removeAllViews();
        for (int i = 0; i < conversationInput.length(); i++) {
            JSONObject message = conversationInput.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String role = message.optString("role", "");
            String content = message.optString("content", "");
            if ("user".equals(role)) {
                addUserMessage(content);
            } else if ("assistant".equals(role)) {
                addAssistantMessage(content);
            }
        }
        updateIntroVisibility();
        scrollToBottom();
    }

    private void startNewChat() {
        conversationInput = new JSONArray();
        chatMessages.removeAllViews();
        messageInput.setText("");
        updateIntroVisibility();
        Toast.makeText(this, "已开始新对话", Toast.LENGTH_SHORT).show();
    }

    private void updateIntroVisibility() {
        if (introSubtitle != null) {
            introSubtitle.setVisibility(conversationInput.length() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void loadContextPrompt() {
        aiExecutor.execute(() -> {
            try {
                habitRepository = new HabitRepository(getApplicationContext());
                contextPrompt = habitRepository.buildAiContextPrompt() + buildReminderContext();
                runOnUiThread(() ->
                        Toast.makeText(this, "习惯数据已就绪", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                contextPrompt = "习惯数据读取失败。回答时不要假设用户的打卡数据。";
                runOnUiThread(() ->
                        Toast.makeText(this, "正在读取习惯数据，请稍后再试", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    // 把已设置的提醒时间作为“日程”补充进上下文，便于推荐打卡时间。
    private String buildReminderContext() {
        if (habitRepository == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Habit habit : habitRepository.getHabits()) {
            if (!habit.reminderEnabled || habit.reminderTimeMinutes < 0) {
                continue;
            }
            sb.append("- ").append(habit.name).append("：")
                    .append(formatMinutes(habit.reminderTimeMinutes)).append("\n");
        }
        if (sb.length() == 0) {
            return "\n\n当前没有设置任何提醒时间。";
        }
        return "\n\n已设置的提醒时间：\n" + sb;
    }

    private String formatMinutes(int minutesOfDay) {
        return String.format(Locale.CHINA, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    private void sendMessage() {
        sendUserText(messageInput.getText().toString().trim());
    }

    private void sendUserText(String userText) {
        if (TextUtils.isEmpty(userText)) {
            Toast.makeText(this, "请输入问题", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!sendButton.isEnabled()) {
            // 正在请求中，避免重复发送。
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
        if (introSubtitle != null) {
            introSubtitle.setVisibility(View.GONE);
        }
        TextView aiBubble = addAssistantMessage("");
        sendButton.setEnabled(false);
        sendButton.setText("发送中");
        scrollToBottom();

        aiExecutor.execute(() -> {
            try {
                requestAiReply(apiUrl, model, apiKey, userText, aiBubble);
                runOnUiThread(() -> {
                    sendButton.setEnabled(true);
                    sendButton.setText("发送");
                    scrollToBottom();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    aiBubble.setText("请求失败了，请检查网络、API 地址、模型名称、API Key 或账户余额后重试。");
                    sendButton.setEnabled(true);
                    sendButton.setText("发送");
                });
            }
        });
    }

    private void scrollToBottom() {
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
        }
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

            runOnUiThread(() -> {
                aiBubble.setText(fullReply.toString());
                scrollToBottom();
            });
        }

        String reply = fullReply.toString();

        JSONObject assistantMessage = new JSONObject();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", reply);
        conversationInput.put(assistantMessage);

        return reply;
    }

    private String buildSystemPrompt() {
        return "你是 Stellog 应用内的任务助手，围绕习惯养成、活动安排、打卡复盘提供帮助。"
                + "语气积极、鼓励、给人动力；回答要非常简洁、务实、可立即执行，多给具体的小步骤。"
                + "请用纯文本中文回答，不要使用 Markdown 语法（不要用 # 标题、* 或 - 列表、** 加粗、表格、代码块）；"
                + "如需分点，用「1. 2. 3.」或短句。\n\n"
                + contextPrompt;
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
