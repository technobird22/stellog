package com.example.stellog.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.stellog.R;
import com.example.stellog.data.model.Achievement;
import com.example.stellog.data.model.CalendarDaySpec;
import com.example.stellog.data.model.CheckInRecord;
import com.example.stellog.data.model.Habit;
import com.example.stellog.data.repository.HabitRepository;
import com.example.stellog.util.DateUtils;
import com.example.stellog.util.DimensionUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用主页面。
 *
 * 当前页面负责展示活动卡片、处理卡片滑动、创建活动结果、今日打卡和取消打卡。
 */
public class MainActivity extends AppCompatActivity {

    // 当前版本中每次打卡默认增加 1，后续可以改成用户填写的数量。
    private static final long DEFAULT_RECORD_VALUE = 0L;
    private static final String PREF_NAME = "main_preferences";
    private static final String KEY_SMART_RECOMMENDATION_ENABLED = "smart_recommendation_enabled";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private HabitRepository habitRepository;
    private List<Habit> habits;

    // 内存中的打卡记录列表；卡片上的本周状态和今日状态都由它推导。

    private ViewPager2 habitPager;
    private HabitPagerAdapter habitAdapter;
    private RecyclerView habitList;
    private HabitListAdapter habitListAdapter;
    private View calendarContent;
    private View achievementContent;
    private View profileContent;
    private TextView homeTab;
    private TextView calendarTab;
    private TextView achievementTab;
    private TextView profileTab;
    private TextView calendarActivityFilterLabel;
    private GridLayout calendarGrid;
    private TextView calendarMonthTitle;
    private TextView calendarSelectedDateTitle;
    private TextView calendarSelectedDateHint;
    private LinearLayout calendarSelectedRecords;
    private TextView calendarCompletedCount;
    private TextView calendarPlanCount;
    private TextView calendarCompletionRate;
    private View mainLoadingOverlay;
    private View mainLoading;
    private final Calendar visibleMonth = Calendar.getInstance();
    private Calendar selectedDate = Calendar.getInstance();
    private final HashSet<Long> selectedCalendarHabitIds = new HashSet<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Map<Long, CheckInRecord> todayRecordByHabitId = new HashMap<>();
    private final HashSet<String> checkedRecordDateKeys = new HashSet<>();
    private final Map<Long, String> habitPriorityHintById = new HashMap<>();

    private boolean listMode = false;
    private int currentHabitPosition = 0;
    private boolean smartRecommendationEnabled = true;

    // 接收“创建活动页面”返回的数据，并将其转成 Habit 加入列表。
    private final ActivityResultLauncher<Intent> createHabitLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }

                        String name = result.getData().getStringExtra("habit_name");
                        String unit = result.getData().getStringExtra("habit_unit");
                        if (name == null || name.trim().isEmpty()) {
                            return;
                        }
                        if (unit == null) {
                            unit = "";
                        }

                        addHabit(name.trim(), unit.trim());
                    }
            );

    // 接收活动筛选页面返回的 habitId 集合，并刷新日历计数与选中日期明细。
    private final ActivityResultLauncher<Intent> habitFilterLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }
                        applyHabitFilterResult(result.getData());
                    }
            );

    // 接收记录详细页面返回的新数值，并同步更新对应日期的 record 与活动累计值。
    private final ActivityResultLauncher<Intent> recordDetailLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }

                        long habitId = result.getData().getLongExtra(RecordDetailActivity.EXTRA_HABIT_ID, -1L);
                        long newValue = result.getData().getLongExtra(
                                RecordDetailActivity.EXTRA_RECORD_VALUE,
                                DEFAULT_RECORD_VALUE
                        );
                        CheckInRecord.RecordDate recordDate = getRecordDateFromResult(result.getData());
                        applyRecordDetailValue(habitId, recordDate, newValue);
                    }
            );

    // 提醒编辑页面返回后刷新闹铃按钮的提示状态。
    private final ActivityResultLauncher<Intent> reminderLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (habitAdapter != null) {
                            habitAdapter.notifyDataSetChanged();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 开启 EdgeToEdge 后，手动给根布局添加系统栏 padding，避免内容被遮挡。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        habitList = findViewById(R.id.habit_list);
        calendarContent = findViewById(R.id.calendar_content);
        achievementContent = findViewById(R.id.achievement_content);
        profileContent = findViewById(R.id.profile_content);
        homeTab = findViewById(R.id.home_tab);
        calendarTab = findViewById(R.id.calendar_tab);
        achievementTab = findViewById(R.id.achievement_tab);
        profileTab = findViewById(R.id.profile_tab);
        calendarActivityFilterLabel = findViewById(R.id.calendar_activity_filter_label);
        calendarGrid = findViewById(R.id.calendar_grid);
        calendarMonthTitle = findViewById(R.id.calendar_month_title);
        calendarSelectedDateTitle = findViewById(R.id.calendar_selected_date_title);
        calendarSelectedDateHint = findViewById(R.id.calendar_selected_date_hint);
        calendarSelectedRecords = findViewById(R.id.calendar_selected_records);
        calendarCompletedCount = findViewById(R.id.calendar_completed_count);
        calendarPlanCount = findViewById(R.id.calendar_plan_count);
        calendarCompletionRate = findViewById(R.id.calendar_completion_rate);
        mainLoadingOverlay = findViewById(R.id.main_loading_overlay);
        mainLoading = findViewById(R.id.main_loading);
        smartRecommendationEnabled = getMainPreferences().getBoolean(KEY_SMART_RECOMMENDATION_ENABLED, true);
        setupSmartRecommendationSwitch();
        setupDarkModeEntry();
        setMainLoading(true);

        // 数据库操作放在单线程池中执行，避免阻塞 UI 线程。
        executeDatabaseTask(() -> {
            try {
                habitRepository = new HabitRepository(getApplicationContext());
                habits = habitRepository.getHabits();
                reloadHomeRecordStateFromDatabase();
                sortHabitsByPriority();

                runOnUiThread(() -> {
                    selectAllCalendarHabits();
                    setupHabitPager();
                    setupHabitList();
                    setupViewModeSwitch();
                    updateHeader(0);
                    setupCalendarNavigation();
                    setupBottomTabs();
                    setMainLoading(false);
                    loadCalendarDataAndRender(false);
                    findViewById(R.id.add_activity_button).setOnClickListener(v -> {
                        Intent intent = new Intent(MainActivity.this, CreateHabitActivity.class);
                        createHabitLauncher.launch(intent);
                    });
                    findViewById(R.id.ai_assistant_entry).setOnClickListener(v -> openAiAssistantOrSettings());
                    findViewById(R.id.ai_settings_entry).setOnClickListener(v -> {
                        Intent intent = new Intent(MainActivity.this, AiSettingsActivity.class);
                        startActivity(intent);
                    });
                    findViewById(R.id.calendar_activity_filter_button).setOnClickListener(v -> {
                        Intent intent = new Intent(MainActivity.this, HabitFilterActivity.class);
                        intent.putExtra(
                                HabitFilterActivity.EXTRA_SELECTED_HABIT_IDS,
                                new HashSet<>(selectedCalendarHabitIds)
                        );
                        habitFilterLauncher.launch(intent);
                    });
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setMainLoading(false);
                });
                runOnUiThread(() ->
                        Toast.makeText(this, "数据加载失败，请重试", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
    }

    private void executeDatabaseTask(Runnable task) {
        if (databaseExecutor.isShutdown()) {
            return;
        }
        try {
            databaseExecutor.execute(task);
        } catch (RuntimeException ignored) {
            // Activity may be finishing while a delayed UI callback tries to refresh data.
        }
    }

    private void setMainLoading(boolean loading) {
        if (mainLoadingOverlay == null || mainLoading == null) {
            return;
        }
        mainLoadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        mainLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setActionButtonLoading(TextView button, boolean loading, String text) {
        if (button == null) {
            return;
        }
        button.setEnabled(!loading);
        button.setAlpha(loading ? 0.65f : 1f);
        button.setText(text);
    }

    private SharedPreferences getMainPreferences() {
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE);
    }

    private void setupDarkModeEntry() {
        View entry = findViewById(R.id.dark_mode_entry);
        if (entry == null) {
            return;
        }
        updateDarkModeSubtitle();
        entry.setOnClickListener(v -> showDarkModeDialog());
    }

    private void updateDarkModeSubtitle() {
        TextView subtitle = findViewById(R.id.dark_mode_subtitle);
        if (subtitle == null) {
            return;
        }
        int mode = getMainPreferences().getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO);
        subtitle.setText(nightModeLabel(mode));
    }

    private String nightModeLabel(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return "深色";
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            return "跟随系统";
        }
        return "浅色";
    }

    private void showDarkModeDialog() {
        final int[] modes = {
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        };
        String[] labels = {"浅色", "深色", "跟随系统"};
        int current = getMainPreferences().getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO);
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("深色模式")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    getMainPreferences().edit().putInt(KEY_NIGHT_MODE, modes[which]).apply();
                    dialog.dismiss();
                    // 切换主题会重建界面以应用新配色。
                    AppCompatDelegate.setDefaultNightMode(modes[which]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupSmartRecommendationSwitch() {
        SwitchCompat smartRecommendationSwitch = findViewById(R.id.smart_recommendation_switch);
        if (smartRecommendationSwitch == null) {
            return;
        }

        smartRecommendationSwitch.setChecked(smartRecommendationEnabled);
        smartRecommendationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (smartRecommendationEnabled == isChecked) {
                return;
            }

            smartRecommendationEnabled = isChecked;
            getMainPreferences()
                    .edit()
                    .putBoolean(KEY_SMART_RECOMMENDATION_ENABLED, isChecked)
                    .apply();

            executeDatabaseTask(() -> {
                if (habitRepository == null || habits == null) {
                    return;
                }

                sortHabitsByPriority();
                runOnUiThread(() -> {
                    habitAdapter.notifyDataSetChanged();
                    habitListAdapter.notifyDataSetChanged();
                    if (!habits.isEmpty()) {
                        int targetPosition = Math.max(0, Math.min(currentHabitPosition, habits.size() - 1));
                        habitPager.setCurrentItem(targetPosition, false);
                        updateHeader(targetPosition);
                    } else {
                        updateHeader(0);
                    }
                    Toast.makeText(
                            this,
                            isChecked ? "已开启智能推荐排序" : "已关闭智能推荐排序",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            });
        });
    }

    private void selectAllCalendarHabits() {
        selectedCalendarHabitIds.clear();
        for (Habit habit : habits) {
            selectedCalendarHabitIds.add(habit.id);
        }
        updateCalendarFilterLabel();
    }

    private void applyHabitFilterResult(Intent data) {
        HashSet<Long> selectedHabitIds = getSelectedHabitIdsExtra(data);
        if (selectedHabitIds == null) {
            return;
        }

        selectedCalendarHabitIds.clear();
        selectedCalendarHabitIds.addAll(selectedHabitIds);
        updateCalendarFilterLabel();
        loadCalendarDataAndRender();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private HashSet<Long> getSelectedHabitIdsExtra(Intent data) {
        Object extra;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extra = data.getSerializableExtra(
                    HabitFilterActivity.EXTRA_SELECTED_HABIT_IDS,
                    HashSet.class
            );
        } else {
            extra = data.getSerializableExtra(HabitFilterActivity.EXTRA_SELECTED_HABIT_IDS);
        }

        if (!(extra instanceof HashSet<?>)) {
            return null;
        }
        return (HashSet<Long>) extra;
    }

    private void updateCalendarFilterLabel() {
        if (calendarActivityFilterLabel == null) {
            return;
        }

        int selectedCount = countSelectedExistingHabits();
        if (selectedCount == habits.size() && !habits.isEmpty()) {
            calendarActivityFilterLabel.setText("全部活动");
        } else if (selectedCount == 0) {
            calendarActivityFilterLabel.setText("未选择");
        } else {
            calendarActivityFilterLabel.setText(String.format(Locale.CHINA, "%d 个活动", selectedCount));
        }
    }

    private int countSelectedExistingHabits() {
        int count = 0;
        for (Habit habit : habits) {
            if (selectedCalendarHabitIds.contains(habit.id)) {
                count++;
            }
        }
        return count;
    }

    private void setupBottomTabs() {
        homeTab.setOnClickListener(v -> showHomePage());
        calendarTab.setOnClickListener(v -> showCalendarPage());
        achievementTab.setOnClickListener(v -> showAchievementPage());
        profileTab.setOnClickListener(v -> showProfilePage());
        showHomePage();
    }

    private void setupAchievementPage() {
        if (!(achievementContent instanceof ScrollView) || habitRepository == null) {
            return;
        }

        executeDatabaseTask(() -> {
            try {
                List<Achievement> achievements = habitRepository.getAchievements();
                runOnUiThread(() -> renderAchievementPage(achievements));
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "\u6210\u5c31\u52a0\u8f7d\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void renderAchievementPage(List<Achievement> achievements) {
        ScrollView scrollView = (ScrollView) achievementContent;
        scrollView.removeAllViews();

        LinearLayout root = new LinearLayout(this);
        root.setLayoutParams(new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                DimensionUtils.dpToPx(getResources(), 42)
        ));
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.setText("\u6210\u5c31");
        title.setTextColor(getColor(R.color.stellog_ink));
        title.setTextSize(30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        LinearLayout grid = new LinearLayout(this);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        gridParams.topMargin = DimensionUtils.dpToPx(getResources(), 22);
        grid.setLayoutParams(gridParams);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid);

        for (int i = 0; i < achievements.size(); i += 2) {
            Achievement right = i + 1 < achievements.size() ? achievements.get(i + 1) : null;
            addAchievementRow(grid, achievements.get(i), right);
        }

        scrollView.addView(root);
    }

    private void addAchievementRow(LinearLayout grid, Achievement left, Achievement right) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (grid.getChildCount() > 0) {
            rowParams.topMargin = DimensionUtils.dpToPx(getResources(), 16);
        }
        row.setLayoutParams(rowParams);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(createAchievementCard(left, true));
        if (right == null) {
            View placeholder = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 1, 1f);
            params.leftMargin = DimensionUtils.dpToPx(getResources(), 8);
            placeholder.setLayoutParams(params);
            row.addView(placeholder);
        } else {
            row.addView(createAchievementCard(right, false));
        }
        grid.addView(row);
    }

    private View createAchievementCard(Achievement achievement, boolean leftColumn) {
        int spacing = DimensionUtils.dpToPx(getResources(), 8);

        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                0,
                DimensionUtils.dpToPx(getResources(), 184),
                1f
        );
        if (leftColumn) {
            cardParams.rightMargin = spacing;
        } else {
            cardParams.leftMargin = spacing;
        }
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(achievement.unlocked
                ? R.drawable.bg_achievement_unlocked
                : R.drawable.bg_achievement_locked);
        card.setGravity(android.view.Gravity.CENTER);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                DimensionUtils.dpToPx(getResources(), 12),
                DimensionUtils.dpToPx(getResources(), 12),
                DimensionUtils.dpToPx(getResources(), 12),
                DimensionUtils.dpToPx(getResources(), 12)
        );

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(
                DimensionUtils.dpToPx(getResources(), 78),
                DimensionUtils.dpToPx(getResources(), 72)
        ));

        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                DimensionUtils.dpToPx(getResources(), 64),
                DimensionUtils.dpToPx(getResources(), 64),
                android.view.Gravity.CENTER
        );
        icon.setLayoutParams(iconParams);
        icon.setImageResource(getAchievementIconResId(achievement.iconKey));
        if (!achievement.unlocked) {
            icon.setColorFilter(android.graphics.Color.rgb(32, 36, 34));
            icon.setAlpha(0.88f);
        }
        iconFrame.addView(icon);

        if (achievement.unlocked) {
            TextView countBadge = new TextView(this);
            FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(
                    DimensionUtils.dpToPx(getResources(), 36),
                    DimensionUtils.dpToPx(getResources(), 22),
                    android.view.Gravity.BOTTOM | android.view.Gravity.END
            );
            countBadge.setLayoutParams(countParams);
            countBadge.setGravity(android.view.Gravity.CENTER);
            countBadge.setBackgroundResource(R.drawable.bg_calendar_count_badge);
            countBadge.setText(achievement.countText);
            countBadge.setTextColor(getColor(R.color.white));
            countBadge.setTextSize(12);
            countBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            iconFrame.addView(countBadge);
        }

        card.addView(iconFrame);

        TextView name = new TextView(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = DimensionUtils.dpToPx(getResources(), 10);
        name.setLayoutParams(nameParams);
        name.setGravity(android.view.Gravity.CENTER);
        name.setText(achievement.unlocked ? achievement.name : "\u9690\u85cf\u6210\u5c31");
        name.setTextColor(getColor(R.color.stellog_ink));
        name.setTextSize(15);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(name);

        TextView completedAt = new TextView(this);
        LinearLayout.LayoutParams completedAtParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        completedAtParams.topMargin = DimensionUtils.dpToPx(getResources(), 4);
        completedAt.setLayoutParams(completedAtParams);
        completedAt.setGravity(android.view.Gravity.CENTER);
        completedAt.setText(achievement.unlocked
                ? formatAchievementCompletedAt(achievement.completedAt)
                : "\u5b8c\u6210\u65f6\u95f4\u9690\u85cf");
        completedAt.setTextColor(getColor(R.color.stellog_muted));
        completedAt.setTextSize(12);
        card.addView(completedAt);

        TextView condition = new TextView(this);
        LinearLayout.LayoutParams conditionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        conditionParams.topMargin = DimensionUtils.dpToPx(getResources(), 5);
        condition.setLayoutParams(conditionParams);
        condition.setGravity(android.view.Gravity.CENTER);
        condition.setText(achievement.unlocked ? achievement.condition : "\u8fbe\u6210\u6761\u4ef6\u9690\u85cf");
        condition.setTextColor(getColor(R.color.stellog_muted));
        condition.setTextSize(12);
        card.addView(condition);

        return card;
    }

    private int getAchievementIconResId(String iconKey) {
        if ("attendance_7".equals(iconKey)) {
            return R.drawable.achievement_attendance_7;
        }
        if ("attendance_30".equals(iconKey)) {
            return R.drawable.achievement_attendance_30;
        }
        if ("attendance_180".equals(iconKey)) {
            return R.drawable.achievement_attendance_180;
        }
        if ("persistence_25".equals(iconKey)) {
            return R.drawable.achievement_persistence_25;
        }
        if ("persistence_100".equals(iconKey)) {
            return R.drawable.achievement_persistence_100;
        }
        if ("persistence_500".equals(iconKey)) {
            return R.drawable.achievement_persistence_500;
        }
        if ("diverse_5".equals(iconKey)) {
            return R.drawable.achievement_diverse_5;
        }
        return R.drawable.achievement_beginner;
    }

    private String formatAchievementCompletedAt(long completedAt) {
        if (completedAt <= 0) {
            return "\u5c1a\u672a\u5b8c\u6210";
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(completedAt));
    }

    private void openAiAssistantOrSettings() {
        SharedPreferences preferences = getSharedPreferences(AiSettingsActivity.PREF_NAME, MODE_PRIVATE);
        String apiKey = preferences.getString(AiSettingsActivity.KEY_API_KEY, "");
        String apiUrl = preferences.getString(AiSettingsActivity.KEY_API_URL, AiSettingsActivity.DEFAULT_API_URL);

        // 默认 DeepSeek 地址需要 API Key；自定义（如本地）地址允许留空。
        if (apiUrl.equals(AiSettingsActivity.DEFAULT_API_URL) && TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "请先在 AI 设置中填写 API Key", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, AiSettingsActivity.class);
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(MainActivity.this, AiAssistantActivity.class);
        startActivity(intent);
    }

    // 打开提醒编辑页面，标题默认带入活动名称。
    private void openReminderEditor(Habit habit) {
        Intent intent = new Intent(MainActivity.this, ReminderEditActivity.class);
        intent.putExtra(ReminderEditActivity.EXTRA_HABIT_ID, habit.id);
        intent.putExtra(ReminderEditActivity.EXTRA_HABIT_NAME, habit.name);
        reminderLauncher.launch(intent);
    }

    private boolean hasReminder(long habitId) {
        return getSharedPreferences(ReminderEditActivity.PREF_NAME, MODE_PRIVATE)
                .getBoolean("reminder_" + habitId + "_enabled", false);
    }

    // 没有活动时在主页展示创建提示。
    private void updateEmptyState() {
        View hint = findViewById(R.id.empty_home_hint);
        if (hint == null) {
            return;
        }
        boolean empty = habits == null || habits.isEmpty();
        hint.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showHomePage() {
        findViewById(R.id.view_mode_switch).setVisibility(View.VISIBLE);
        applyViewMode();
        findViewById(R.id.add_activity_button).setVisibility(View.VISIBLE);
        calendarContent.setVisibility(View.GONE);
        achievementContent.setVisibility(View.GONE);
        profileContent.setVisibility(View.GONE);
        updateEmptyState();

        selectBottomTab(homeTab);
    }

    private void showCalendarPage() {
        hideHomeViews();
        calendarContent.setVisibility(View.VISIBLE);
        achievementContent.setVisibility(View.GONE);
        profileContent.setVisibility(View.GONE);

        selectBottomTab(calendarTab);
    }

    private void showAchievementPage() {
        hideHomeViews();
        calendarContent.setVisibility(View.GONE);
        achievementContent.setVisibility(View.VISIBLE);
        profileContent.setVisibility(View.GONE);
        setupAchievementPage();

        selectBottomTab(achievementTab);
    }

    private void showProfilePage() {
        hideHomeViews();
        calendarContent.setVisibility(View.GONE);
        achievementContent.setVisibility(View.GONE);
        profileContent.setVisibility(View.VISIBLE);

        selectBottomTab(profileTab);
    }

    private void hideHomeViews() {
        findViewById(R.id.view_mode_switch).setVisibility(View.GONE);
        findViewById(R.id.page_dots).setVisibility(View.GONE);
        habitPager.setVisibility(View.GONE);
        habitList.setVisibility(View.GONE);
        findViewById(R.id.add_activity_button).setVisibility(View.GONE);
        findViewById(R.id.empty_home_hint).setVisibility(View.GONE);
    }

    private void selectBottomTab(TextView selectedTab) {
        TextView[] tabs = new TextView[]{homeTab, calendarTab, achievementTab, profileTab};
        for (TextView tab : tabs) {
            boolean selected = tab == selectedTab;
            tab.setTextColor(getColor(selected ? R.color.stellog_primary : R.color.stellog_ink));
            tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void setupCalendarNavigation() {
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        DateUtils.clearTime(selectedDate);
        findViewById(R.id.calendar_prev_month).setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, -1);
            loadCalendarDataAndRender();
        });
        findViewById(R.id.calendar_next_month).setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, 1);
            loadCalendarDataAndRender();
        });
    }

    // 加载当前 visibleMonth 范围内的打卡记录数量和 selectedDate 的打卡详情，并刷新日历界面。
    private void loadCalendarDataAndRender() {
        loadCalendarDataAndRender(true);
    }

    private void loadCalendarDataAndRender(boolean showLoading) {
        if (habitRepository == null) {
            return;
        }

        Calendar rangeStartDate = getCalendarGridStartDate();
        Calendar rangeEndDate = (Calendar) rangeStartDate.clone();
        rangeEndDate.add(Calendar.DAY_OF_MONTH, 41);
        CheckInRecord.RecordDate recordDate = CheckInRecord.RecordDate.fromCalendar(selectedDate);
        HashSet<Long> selectedHabitIds = new HashSet<>(selectedCalendarHabitIds);
        if (showLoading) {
            setMainLoading(true);
        }

        executeDatabaseTask(() -> {
            try {
                Map<Integer, Integer> recordCountByDateKey =
                        habitRepository.getCheckInCountByDateRange(
                                rangeStartDate,
                                rangeEndDate,
                                selectedHabitIds
                        );
                Map<Long, CheckInRecord> recordByHabitId =
                        habitRepository.getRecordsByDate(recordDate, selectedHabitIds);

                runOnUiThread(() -> renderCalendarGrid(recordCountByDateKey, recordByHabitId));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setMainLoading(false);
                });
                runOnUiThread(() ->
                        Toast.makeText(this, "日历加载失败，请重试", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    // 获取当前日历网格的起始日期（即第一个单元格的日期），用于查询打卡记录。
    private Calendar getCalendarGridStartDate() {
        Calendar firstDay = (Calendar) visibleMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);

        int leadingDays = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        Calendar cellDate = (Calendar) firstDay.clone();
        cellDate.add(Calendar.DAY_OF_MONTH, -leadingDays);
        return cellDate;
    }

    private void reloadHomeRecordStateFromDatabase() {
        todayRecordByHabitId.clear();
        checkedRecordDateKeys.clear();

        List<CheckInRecord.RecordDate> weekDates = getCurrentWeekDates();
        for (Habit habit : habits) {
            CheckInRecord todayRecord = habitRepository.getTodayRecord(habit.id);
            if (todayRecord != null) {
                todayRecordByHabitId.put(habit.id, todayRecord);
            }

            for (CheckInRecord.RecordDate date : weekDates) {
                if (habitRepository.hasRecordOnDate(habit.id, date)) {
                    checkedRecordDateKeys.add(getRecordCacheKey(habit.id, date));
                }
            }
        }
    }

    private void sortHabitsByPriority() {
        if (habitRepository == null || habits == null || habits.size() <= 1) {
            return;
        }

        if (!smartRecommendationEnabled) {
            habitPriorityHintById.clear();
            habits.sort((left, right) -> Long.compare(left.id, right.id));
            return;
        }

        List<HabitRepository.HabitPrioritySnapshot> snapshots = habitRepository.buildHabitPrioritySnapshots();
        if (snapshots.isEmpty()) {
            return;
        }

        Map<Long, HabitRepository.HabitPrioritySnapshot> snapshotByHabitId = new HashMap<>();
        habitPriorityHintById.clear();
        for (HabitRepository.HabitPrioritySnapshot snapshot : snapshots) {
            snapshotByHabitId.put(snapshot.habitId, snapshot);
            habitPriorityHintById.put(snapshot.habitId, snapshot.hint);
        }

        habits.sort((left, right) -> {
            HabitRepository.HabitPrioritySnapshot leftSnapshot = snapshotByHabitId.get(left.id);
            HabitRepository.HabitPrioritySnapshot rightSnapshot = snapshotByHabitId.get(right.id);

            double leftScore = leftSnapshot == null ? 0.0 : leftSnapshot.score;
            double rightScore = rightSnapshot == null ? 0.0 : rightSnapshot.score;
            int scoreCompare = Double.compare(rightScore, leftScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }

            return Long.compare(left.id, right.id);
        });
    }

    private String getPriorityHint(long habitId) {
        String hint = habitPriorityHintById.get(habitId);
        return hint == null ? "" : hint;
    }

    // 卡片统计行：今日数量与累计值，单位为空时省略。
    private String buildCardStatsLine(long todayValue, long totalValue, String unit) {
        String trimmedUnit = unit == null ? "" : unit.trim();
        String suffix = trimmedUnit.isEmpty() ? "" : " " + trimmedUnit;
        return String.format(Locale.CHINA, "今日 %d%s · 累计 %d%s", todayValue, suffix, totalValue, suffix);
    }

    private String getRecordCacheKey(long habitId, CheckInRecord.RecordDate date) {
        return habitId + ":" + DateUtils.toDateKey(date);
    }

    // 渲染日历表格，每个格子显示对应日期和打卡记录数量，选中日期高亮，并在下方显示选中日期的打卡详情。
    private void renderCalendarGrid(
            Map<Integer, Integer> recordCountByDateKey,
            Map<Long, CheckInRecord> recordByHabitId
    ) {
        calendarMonthTitle.setText(String.format(
                Locale.CHINA,
                "%d \u5E74 %d \u6708",
                visibleMonth.get(Calendar.YEAR),
                visibleMonth.get(Calendar.MONTH) + 1
        ));

        LayoutInflater inflater = LayoutInflater.from(this);
        calendarGrid.removeAllViews();
        CalendarDaySpec[] days = buildVisibleMonthDays(recordCountByDateKey);
        for (int i = 0; i < days.length; i++) {
            CalendarDaySpec day = days[i];
            View dayView = inflater.inflate(R.layout.item_calendar_day, calendarGrid, false);
            bindCalendarDay(dayView, day);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 7),
                    GridLayout.spec(i % 7, 1f)
            );
            params.width = 0;
            params.height = DimensionUtils.dpToPx(getResources(), 44);
            dayView.setLayoutParams(params);
            calendarGrid.addView(dayView);
        }
        renderSelectedDateRecords(recordByHabitId);
        setMainLoading(false);
    }

    // 计算每一个日期格上展示的日期，并判断是否属于本月
    private CalendarDaySpec[] buildVisibleMonthDays(Map<Integer, Integer> recordCountByDateKey) {
        Calendar cellDate = getCalendarGridStartDate();
        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);
        CalendarDaySpec[] days = new CalendarDaySpec[42];
        int visibleYear = visibleMonth.get(Calendar.YEAR);
        int visibleMonthValue = visibleMonth.get(Calendar.MONTH);

        for (int i = 0; i < days.length; i++) {
            boolean outsideMonth = cellDate.get(Calendar.YEAR) != visibleYear
                    || cellDate.get(Calendar.MONTH) != visibleMonthValue;
            boolean todayCell = !outsideMonth && DateUtils.isSameDate(cellDate, today);
            boolean selected = !outsideMonth && DateUtils.isSameDate(cellDate, selectedDate);
            int recordCount = recordCountByDateKey.getOrDefault(DateUtils.toDateKey(cellDate), 0);
            days[i] = new CalendarDaySpec(
                    (Calendar) cellDate.clone(),
                    String.valueOf(cellDate.get(Calendar.DAY_OF_MONTH)),
                    todayCell,
                    selected,
                    outsideMonth,
                    recordCount
            );
            cellDate.add(Calendar.DAY_OF_MONTH, 1);
        }
        return days;
    }

    // 将数据绑定到组件，并显示出相应状态
    private void bindCalendarDay(View dayView, CalendarDaySpec day) {
        TextView dayNumber = dayView.findViewById(R.id.calendar_day_number);
        TextView badge = dayView.findViewById(R.id.calendar_day_badge);
        View todayDot = dayView.findViewById(R.id.calendar_today_dot);

        dayNumber.setText(day.label);
        dayNumber.setTextColor(getColor(day.outsideMonth ? R.color.stellog_line : R.color.stellog_ink));
        dayNumber.setBackgroundResource(0);

        if (day.selected) {
            dayNumber.setBackgroundResource(R.drawable.bg_calendar_day_selected);
            dayNumber.setTextColor(getColor(R.color.white));
        } else if (day.recordCount > 0) {
            dayNumber.setBackgroundResource(R.drawable.bg_calendar_day_recorded);
            dayNumber.setTextColor(getColor(R.color.stellog_primary));
        }

        todayDot.setVisibility(day.today && !day.selected ? View.VISIBLE : View.GONE);
        if (day.recordCount > 1) {
            badge.setText(String.valueOf(day.recordCount));
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
        dayView.setOnClickListener(v -> {
            if (day.outsideMonth) {
                return;
            }
            selectedDate = (Calendar) day.date.clone();
            loadCalendarDataAndRender();
        });
    }

    // 渲染选中日期的打卡详情，包括每个活动的打卡状态和操作按钮，以及当日总体完成率。
    private void renderSelectedDateRecords(Map<Long, CheckInRecord> recordByHabitId) {
        CheckInRecord.RecordDate recordDate = CheckInRecord.RecordDate.fromCalendar(selectedDate);
        calendarSelectedDateTitle.setText(String.format(
                Locale.CHINA,
                "%d-%d-%d",
                recordDate.year,
                recordDate.month,
                recordDate.day   
        ));
        updateSelectedDateHint();

        calendarSelectedRecords.removeAllViews();
        int completedCount = 0;
        int planCount = 0;
        for (Habit habit : habits) {
            if (!selectedCalendarHabitIds.contains(habit.id)) {
                continue;
            }
            planCount++;
            CheckInRecord record = recordByHabitId.get(habit.id);
            boolean completed = record != null;
            if (completed) {
                completedCount++;
            }
            calendarSelectedRecords.addView(createSelectedDateRecordRow(habit, record, recordDate));
        }

        int completionRate = planCount == 0 ? 0 : Math.round(completedCount * 100f / planCount);
        calendarCompletedCount.setText(String.valueOf(completedCount));
        calendarPlanCount.setText(String.valueOf(planCount));
        calendarCompletionRate.setText(String.format(Locale.CHINA, "%d%%", completionRate));
    }

    // 根据选中日期与今天的关系，更新提示语。
    private void updateSelectedDateHint() {
        Calendar selected = (Calendar) selectedDate.clone();
        DateUtils.clearTime(selected);
        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);

        if (selected.before(today)) {
            calendarSelectedDateHint.setText("总有可以弥补的遗憾。");
        } else if (DateUtils.isSameDate(selected, today)) {
            calendarSelectedDateHint.setText("就是现在！");
        } else {
            calendarSelectedDateHint.setText("前方也值得期待。");
        }
    }

    // 渲染选中日期的每个活动打卡记录行，包括活动名称、完成状态和操作按钮（打卡/补打卡/查看详情）。
    private View createSelectedDateRecordRow(Habit habit, CheckInRecord record, CheckInRecord.RecordDate recordDate) {
        boolean completed = record != null;
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DimensionUtils.dpToPx(getResources(), 60)
        );
        if (calendarSelectedRecords.getChildCount() > 0) {
            params.topMargin = DimensionUtils.dpToPx(getResources(), 10);
        }
        row.setLayoutParams(params);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_calendar_summary);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(
                DimensionUtils.dpToPx(getResources(), 16),
                0,
                DimensionUtils.dpToPx(getResources(), 16),
                0
        );

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        ));
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView nameText = new TextView(this);
        nameText.setText(habit.name);
        nameText.setSingleLine(true);
        nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameText.setTextColor(getColor(completed ? R.color.stellog_ink : R.color.stellog_muted));
        nameText.setTextSize(16);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView statusText = new TextView(this);
        statusText.setText(buildSelectedDateStatus(completed, record, habit.unit));
        statusText.setSingleLine(true);
        statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusText.setTextColor(getColor(completed ? R.color.stellog_primary : R.color.stellog_muted));
        statusText.setTextSize(13);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = DimensionUtils.dpToPx(getResources(), 2);
        statusText.setLayoutParams(statusParams);

        textColumn.addView(nameText);
        textColumn.addView(statusText);

        TextView actionButton = createCalendarRecordActionButton(habit, record, recordDate);
        LinearLayout.LayoutParams actionParams =
                (LinearLayout.LayoutParams) actionButton.getLayoutParams();
        actionParams.leftMargin = DimensionUtils.dpToPx(getResources(), 12);
        actionButton.setLayoutParams(actionParams);

        row.addView(textColumn);
        row.addView(actionButton);
        return row;
    }

    // 选中日期记录行的状态文案，数量为 0 或单位为空时省略。
    private String buildSelectedDateStatus(boolean completed, CheckInRecord record, String unit) {
        if (!completed) {
            return "待打卡";
        }
        if (record.value <= 0) {
            return "已完成";
        }
        String trimmedUnit = unit == null ? "" : unit.trim();
        return trimmedUnit.isEmpty()
                ? String.format(Locale.CHINA, "已完成 %d", record.value)
                : String.format(Locale.CHINA, "已完成 %d %s", record.value, trimmedUnit);
    }

    // 根据打卡记录状态和选中日期与今天的关系，创建相应的操作按钮（打卡/补打卡/查看详情/不可打卡）。
    private TextView createCalendarRecordActionButton(
            Habit habit,
            CheckInRecord record,
            CheckInRecord.RecordDate recordDate
    ) {
        TextView button = new TextView(this);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                DimensionUtils.dpToPx(getResources(), 86),
                DimensionUtils.dpToPx(getResources(), 36)
        ));
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);

        if (record != null) {
            button.setText("记录详细");
            button.setTextColor(getColor(R.color.white));
            button.setBackgroundResource(R.drawable.bg_create_action_primary);
            button.setOnClickListener(v -> showRecordDetailPage(habit, record));
            return button;
        }

        Calendar selected = (Calendar) selectedDate.clone();
        DateUtils.clearTime(selected);
        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);
        if (selected.after(today)) {
            button.setText("不可打卡");
            button.setTextColor(getColor(R.color.stellog_muted));
            button.setBackgroundResource(R.drawable.bg_create_action_secondary);
            button.setEnabled(false);
            return button;
        }

        boolean todaySelected = DateUtils.isSameDate(selected, today);
        button.setText(todaySelected ? "打卡" : "补打卡");
        button.setTextColor(getColor(R.color.white));
        button.setBackgroundResource(R.drawable.bg_create_action_primary);
        button.setOnClickListener(v -> checkInOnSelectedDate(habit, recordDate, button));
        return button;
    }

    private void setupHabitList() {
        habitList.setLayoutManager(new LinearLayoutManager(this));
        habitListAdapter = new HabitListAdapter(habits);
        habitList.setAdapter(habitListAdapter);
    }

    private void setupViewModeSwitch() {
        findViewById(R.id.card_mode).setOnClickListener(v -> {
            listMode = false;
            applyViewMode();
        });
        findViewById(R.id.list_mode).setOnClickListener(v -> {
            listMode = true;
            applyViewMode();
        });
        applyViewMode();
    }

    private void applyViewMode() {
        View pageDots = findViewById(R.id.page_dots);
        TextView cardMode = findViewById(R.id.card_mode);
        TextView listModeButton = findViewById(R.id.list_mode);

        habitPager.setVisibility(listMode ? View.GONE : View.VISIBLE);
        habitList.setVisibility(listMode ? View.VISIBLE : View.GONE);
        pageDots.setVisibility(listMode ? View.GONE : View.VISIBLE);

        cardMode.setBackgroundResource(listMode ? 0 : R.drawable.bg_mode_selected);
        cardMode.setTextColor(getColor(listMode ? R.color.stellog_muted : R.color.white));
        listModeButton.setBackgroundResource(listMode ? R.drawable.bg_mode_selected : 0);
        listModeButton.setTextColor(getColor(listMode ? R.color.white : R.color.stellog_muted));
    }

    /**
     * 初始化活动卡片 ViewPager。
     *
     * ViewPager2 负责连续滑动效果，RecyclerView.Adapter 负责把 Habit 渲染成单张卡片。
     */
    private void setupHabitPager() {
        habitPager = findViewById(R.id.habit_pager);
        habitAdapter = new HabitPagerAdapter(habits);
        habitPager.setAdapter(habitAdapter);

        // 预渲染相邻卡片，让左右滑动时能看到连续过渡。
        habitPager.setOffscreenPageLimit(1);
        habitPager.setClipToPadding(false);
        habitPager.setClipChildren(false);

        // 左右 padding 控制卡片视觉宽度和两侧露出范围。
        habitPager.setPadding(128, 0, 128, 0);

        // 非当前卡片略微缩小、变淡，增强层次感。
        habitPager.setPageTransformer((page, position) -> {
            float scale = 0.94f + (1 - Math.min(Math.abs(position), 1f)) * 0.06f;
            page.setScaleY(scale);
            page.setAlpha(0.28f + (1 - Math.min(Math.abs(position), 1f)) * 0.72f);
        });
        habitPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentHabitPosition = position;
                updateHeader(position);
            }
        });
    }

    /**
     * 创建一个新活动并刷新卡片列表。
     */
    private void addHabit(String name, String unit) {
        executeDatabaseTask(() -> {
        try {
            Habit habit = habitRepository.addHabit(name, unit);
            List<Achievement> unlockedAchievements =
                habitRepository.checkAchievementsAfterHabitCreated();
            reloadHomeRecordStateFromDatabase();
            sortHabitsByPriority();

            runOnUiThread(() -> {
                selectedCalendarHabitIds.add(habit.id);
                updateCalendarFilterLabel();
                habitAdapter.notifyDataSetChanged();
                habitListAdapter.notifyDataSetChanged();
                updateEmptyState();

                int newPosition = habitRepository.findHabitPosition(habit.id);
                if (newPosition < 0) {
                    newPosition = 0;
                }

                //创建完成后自动定位到新活动卡片
                habitPager.setCurrentItem(newPosition, true);
                loadCalendarDataAndRender(false);
                updateHeader(newPosition);
                Toast.makeText(this, "活动已创建", Toast.LENGTH_SHORT).show();
                handleUnlockedAchievements(unlockedAchievements);
            });
        } catch (Exception e) {
            runOnUiThread(() ->
                    Toast.makeText(this, "创建失败，请重试", Toast.LENGTH_SHORT).show()
            );
        }
    });
    }

    private void updateHeader(int position) {
        renderPageDots();
    }

    // 卡片模式下在卡片下方用圆点显示当前位置；活动较多时退回文字。
    private void renderPageDots() {
        LinearLayout dots = findViewById(R.id.page_dots);
        if (dots == null) {
            return;
        }
        dots.removeAllViews();
        int count = habits == null ? 0 : habits.size();
        if (count <= 1) {
            return;
        }
        int active = Math.max(0, Math.min(currentHabitPosition, count - 1));
        if (count > 10) {
            TextView label = new TextView(this);
            label.setText(String.format(Locale.CHINA, "%d / %d", active + 1, count));
            label.setTextColor(getColor(R.color.stellog_muted));
            label.setTextSize(14);
            dots.addView(label);
            return;
        }
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int size = DimensionUtils.dpToPx(getResources(), i == active ? 9 : 7);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.leftMargin = DimensionUtils.dpToPx(getResources(), 4);
            params.rightMargin = DimensionUtils.dpToPx(getResources(), 4);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == active ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            dots.addView(dot);
        }
    }

    /**
     * 查找指定活动今天的打卡记录。
     */
    private CheckInRecord getTodayRecord(long habitId) {
        return todayRecordByHabitId.get(habitId);
    }

    /**
     * 今日打卡：新增 record，并同步更新 Habit 上的统计字段。
     */
    private void checkInToday(Habit habit) {
        checkInToday(habit, null);
    }

    private void checkInToday(Habit habit, TextView actionButton) {
        setActionButtonLoading(actionButton, true, "\u6253\u5361\u4e2d...");
        executeDatabaseTask(() -> {
            try {
                boolean success = habitRepository.checkInToday(habit);
                final List<Achievement> unlockedAchievements;
                if (success) {
                    reloadHomeRecordStateFromDatabase();
                    sortHabitsByPriority();
                    unlockedAchievements = habitRepository.checkAchievementsAfterCheckIn(
                        habit.id,
                        CheckInRecord.RecordDate.today(),
                        CheckInRecord.SOURCE_NORMAL
                    );
                }else{
                    unlockedAchievements = new ArrayList<>();
                }

                runOnUiThread(() -> {
                    setActionButtonLoading(actionButton, false, "\u6253\u5361");
                    if (success) {
                        habitAdapter.notifyDataSetChanged();
                        habitListAdapter.notifyDataSetChanged();
                        int position = habitRepository.findHabitPosition(habit.id);
                        if (position >= 0) {
                            currentHabitPosition = position;
                            habitPager.setCurrentItem(position, false);
                            updateHeader(position);
                        }
                        loadCalendarDataAndRender(false);
                        Toast.makeText(this, "打卡成功", Toast.LENGTH_SHORT).show();
                        handleUnlockedAchievements(unlockedAchievements);
                    } else {
                        Toast.makeText(this, "今天已经打过卡", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setActionButtonLoading(actionButton, false, "\u6253\u5361");
                });
                runOnUiThread(() ->
                        Toast.makeText(this, "打卡失败，请重试", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    // 取消打卡与补打卡时弹出二次确认窗口
    private void checkInOnSelectedDate(Habit habit, CheckInRecord.RecordDate recordDate, TextView actionButton) {
        Calendar selected = (Calendar) selectedDate.clone();
        DateUtils.clearTime(selected);
        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);
        if (selected.after(today)) {
            Toast.makeText(this, "不能为未来日期打卡", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selected.before(today)) {
            showPatchCheckInConfirmDialog(habit, recordDate, actionButton);
            return;
        }

        performCheckInOnSelectedDate(habit, recordDate, actionButton);
    }

    private void showPatchCheckInConfirmDialog(
            Habit habit,
            CheckInRecord.RecordDate recordDate,
            TextView actionButton
    ) {
        String dateText = String.format(
                Locale.CHINA,
                "%d-%02d-%02d",
                recordDate.year,
                recordDate.month,
                recordDate.day
        );

        new AlertDialog.Builder(this)
                .setTitle("\u786e\u8ba4\u8865\u6253\u5361")
                .setMessage(String.format(Locale.CHINA, "\u786e\u5b9a\u4e3a %s \u8865\u6253\u5361\u5417\uff1f", dateText))
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialog, which) ->
                        performCheckInOnSelectedDate(habit, recordDate, actionButton)
                )
                .show();
    }

    private void performCheckInOnSelectedDate(Habit habit, CheckInRecord.RecordDate recordDate, TextView actionButton) {
        Calendar selected = (Calendar) selectedDate.clone();
        DateUtils.clearTime(selected);
        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);

        String source = DateUtils.isSameDate(selected, today)
                ? CheckInRecord.SOURCE_NORMAL
                : CheckInRecord.SOURCE_PATCH;
        String idleText = DateUtils.isSameDate(selected, today)
                ? "\u6253\u5361"
                : "\u8865\u6253\u5361";
        setActionButtonLoading(actionButton, true, "\u6253\u5361\u4e2d...");
        executeDatabaseTask(() -> {
            try {
                boolean success = habitRepository.checkInOnDate(habit, recordDate, source);
                final List<Achievement> unlockedAchievements;

                if (success) {
                    unlockedAchievements = habitRepository.checkAchievementsAfterCheckIn(
                            habit.id,
                            recordDate,
                            source
                    );
                    reloadHomeRecordStateFromDatabase();
                    sortHabitsByPriority();
                } else {
                    unlockedAchievements = new ArrayList<>();
                }

                runOnUiThread(() -> {
                    setActionButtonLoading(actionButton, false, idleText);
                    if (success) {
                        habitAdapter.notifyDataSetChanged();
                        habitListAdapter.notifyDataSetChanged();
                        int position = habitRepository.findHabitPosition(habit.id);
                        if (position >= 0) {
                            currentHabitPosition = position;
                            habitPager.setCurrentItem(position, false);
                            updateHeader(position);
                        }
                        loadCalendarDataAndRender(false);
                        handleUnlockedAchievements(unlockedAchievements);
                        Toast.makeText(this, "\u6253\u5361\u6210\u529f", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "\u5df2\u7ecf\u6253\u8fc7\u5361", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setActionButtonLoading(actionButton, false, idleText);
                    Toast.makeText(this, "\u6253\u5361\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 取消今日打卡：删除今天的 record，并回退 Habit 上的统计字段。
     */
    private void cancelTodayCheckIn(Habit habit, TextView actionButton) {
        new AlertDialog.Builder(this)
                .setTitle("\u786e\u8ba4\u53d6\u6d88\u6253\u5361")
                .setMessage("\u786e\u5b9a\u53d6\u6d88\u4eca\u65e5\u6253\u5361\u5417\uff1f")
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", (dialog, which) ->
                        performCancelTodayCheckIn(habit, actionButton)
                )
                .show();
    }

    private void performCancelTodayCheckIn(Habit habit, TextView actionButton) {
        setActionButtonLoading(actionButton, true, "\u53d6\u6d88\u4e2d...");
        executeDatabaseTask(() -> {
            try {
                boolean success = habitRepository.cancelTodayCheckIn(habit);
                if (success) {
                    reloadHomeRecordStateFromDatabase();
                    sortHabitsByPriority();
                }

                runOnUiThread(() -> {
                    setActionButtonLoading(actionButton, false, "\u53d6\u6d88");
                    if (success) {
                        habitAdapter.notifyDataSetChanged();
                        habitListAdapter.notifyDataSetChanged();
                        int position = habitRepository.findHabitPosition(habit.id);
                        if (position >= 0) {
                            currentHabitPosition = position;
                            habitPager.setCurrentItem(position, false);
                            updateHeader(position);
                        }
                        loadCalendarDataAndRender(false);
                        Toast.makeText(this, "\u5df2\u53d6\u6d88\u6253\u5361", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "\u6ca1\u6709\u53ef\u53d6\u6d88\u7684\u6253\u5361", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setActionButtonLoading(actionButton, false, "\u53d6\u6d88");
                    Toast.makeText(this, "\u53d6\u6d88\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleUnlockedAchievements(List<Achievement> unlockedAchievements) {
        if (unlockedAchievements == null || unlockedAchievements.isEmpty()) {
            return;
        }

        setupAchievementPage();

        for(Achievement unlockedAchivement: unlockedAchievements){
            Toast.makeText(
                this,
                String.format("解锁成就：%s", unlockedAchivement.name),
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    /**
     * 判断指定活动在某一天是否已经打卡。
     */
    private boolean hasRecordOnDate(long habitId, CheckInRecord.RecordDate date) {
        return checkedRecordDateKeys.contains(getRecordCacheKey(habitId, date));
    }

    /**
     * 生成本周周一到周日的日期列表，用于绑定 7 个打卡圆点。
     */
    private List<CheckInRecord.RecordDate> getCurrentWeekDates() {
        return DateUtils.getCurrentWeekDates();
    }

    /**
     * 生成卡片上显示的今日日期文本，例如 2026-05-11。
     */
    private String getTodayDateString() {
        return DateUtils.getTodayDateString();
    }

    /**
     * 打开记录详细页面，让用户填写今天完成的数量。
     */
    private void showRecordDetailPage(Habit habit) {
        CheckInRecord todayRecord = getTodayRecord(habit.id);

        if (todayRecord == null) {
            Toast.makeText(this, "请先完成今日打卡", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(MainActivity.this, RecordDetailActivity.class);
        intent.putExtra(RecordDetailActivity.EXTRA_HABIT_ID, habit.id);
        intent.putExtra(RecordDetailActivity.EXTRA_HABIT_NAME, habit.name);
        intent.putExtra(RecordDetailActivity.EXTRA_HABIT_UNIT, habit.unit);
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_VALUE, todayRecord.value);
        recordDetailLauncher.launch(intent);
    }

    private void showRecordDetailPage(Habit habit, CheckInRecord record) {
        Intent intent = new Intent(MainActivity.this, RecordDetailActivity.class);
        intent.putExtra(RecordDetailActivity.EXTRA_HABIT_ID, habit.id);
        intent.putExtra(RecordDetailActivity.EXTRA_HABIT_NAME, habit.name);
        intent.putExtra(RecordDetailActivity.EXTRA_HABIT_UNIT, habit.unit);
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_VALUE, record.value);
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_YEAR, record.date.year);
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_MONTH, record.date.month);
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_DAY, record.date.day);
        recordDetailLauncher.launch(intent);
    }

    private CheckInRecord.RecordDate getRecordDateFromResult(Intent data) {
        int year = data.getIntExtra(RecordDetailActivity.EXTRA_RECORD_YEAR, -1);
        int month = data.getIntExtra(RecordDetailActivity.EXTRA_RECORD_MONTH, -1);
        int day = data.getIntExtra(RecordDetailActivity.EXTRA_RECORD_DAY, -1);
        if (year > 0 && month > 0 && day > 0) {
            return new CheckInRecord.RecordDate(year, month, day);
        }
        return CheckInRecord.RecordDate.today();
    }

    private void applyRecordDetailValue(long habitId, CheckInRecord.RecordDate recordDate, long newValue) {
        executeDatabaseTask(() -> {
            try {
                int habitPosition = habitRepository.findHabitPosition(habitId);
                boolean success = habitRepository.applyRecordDetailValue(habitId, recordDate, newValue);
                if (success) {
                    reloadHomeRecordStateFromDatabase();
                    sortHabitsByPriority();
                }

                runOnUiThread(() -> {
                    if (success) {
                        habitAdapter.notifyDataSetChanged();
                        habitListAdapter.notifyDataSetChanged();
                        int position = habitRepository.findHabitPosition(habitId);
                        if (position >= 0) {
                            currentHabitPosition = position;
                            habitPager.setCurrentItem(position, false);
                            updateHeader(position);
                        }
                        loadCalendarDataAndRender(false);
                        Toast.makeText(this, "\u8bb0\u5f55\u5df2\u4fdd\u5b58", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "\u8bb0\u5f55\u6ca1\u6709\u53d8\u5316", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "\u4fdd\u5b58\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void refreshHabitUi(Habit habit) {
        int habitPosition = habitRepository.findHabitPosition(habit.id);
        if (habitPosition < 0) {
            return;
        }
        habitAdapter.notifyItemChanged(habitPosition);
        habitListAdapter.notifyItemChanged(habitPosition);
    }

    /**
     * RecyclerView adapter for the compact list mode.
     */
    private class HabitListAdapter extends RecyclerView.Adapter<HabitListAdapter.HabitListViewHolder> {
        private final List<Habit> items;

        HabitListAdapter(List<Habit> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public HabitListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_habit_list, parent, false);
            return new HabitListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HabitListViewHolder holder, int position) {
            holder.bind(items.get(position), position);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HabitListViewHolder extends RecyclerView.ViewHolder {
            private final View statusDot;
            private final TextView habitName;
            private final TextView recordCount;
            private final TextView checkStatus;

            HabitListViewHolder(@NonNull View itemView) {
                super(itemView);
                statusDot = itemView.findViewById(R.id.list_status_dot);
                habitName = itemView.findViewById(R.id.list_habit_name);
                recordCount = itemView.findViewById(R.id.list_habit_record_count);
                checkStatus = itemView.findViewById(R.id.list_check_status);
            }

            void bind(Habit habit, int position) {
                boolean checkedInToday = getTodayRecord(habit.id) != null;
                String priorityHint = getPriorityHint(habit.id);

                // 今日已打卡绿点，未打卡红点。
                statusDot.setBackgroundResource(checkedInToday
                        ? R.drawable.bg_dot_active
                        : R.drawable.bg_dot_red);

                habitName.setText(habit.name);
                if (priorityHint.isEmpty()) {
                    recordCount.setText(String.format(Locale.CHINA, "\u7d2f\u8ba1\u6253\u5361 %d \u5929", habit.recordNum));
                } else {
                    recordCount.setText(String.format(Locale.CHINA, "\u7d2f\u8ba1\u6253\u5361 %d \u5929 \u00b7 %s", habit.recordNum, priorityHint));
                }

                if (checkedInToday) {
                    checkStatus.setText("\u5df2\u6253\u5361");
                    checkStatus.setTextColor(getColor(R.color.stellog_muted));
                    checkStatus.setBackgroundResource(R.drawable.bg_create_action_secondary);
                    checkStatus.setOnClickListener(null);
                } else {
                    checkStatus.setText("\u6253\u5361");
                    checkStatus.setTextColor(getColor(R.color.white));
                    checkStatus.setBackgroundResource(R.drawable.bg_check_in_button);
                    checkStatus.setOnClickListener(v -> {
                        currentHabitPosition = position;
                        checkInToday(habit, checkStatus);
                    });
                }

            }
        }
    }



    /**
     * ViewPager2 使用的适配器，将 Habit 列表转换为卡片页。
     */
    private class HabitPagerAdapter extends RecyclerView.Adapter<HabitPagerAdapter.HabitViewHolder> {
        private final List<Habit> items;

        HabitPagerAdapter(List<Habit> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_habit_card, parent, false);
            return new HabitViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * 单张卡片的 ViewHolder。
         *
         * 缓存控件引用，并负责把 Habit 和 CheckInRecord 状态渲染到 UI 上。
         */
        class HabitViewHolder extends RecyclerView.ViewHolder {
            private final View cardContent;
            private final ImageView reminderButton;
            private final TextView habitName;
            private final TextView streakValue;
            private final TextView targetSummary;
            private final TextView priorityHintChip;
            private final TextView todayDate;
            private final TextView checkInButton;
            private final View checkedActions;
            private final TextView cancelCheckInButton;
            private final TextView[] weekDots;

            HabitViewHolder(@NonNull View itemView) {
                super(itemView);
                cardContent = itemView.findViewById(R.id.habit_card_content);
                reminderButton = itemView.findViewById(R.id.card_reminder_button);
                habitName = itemView.findViewById(R.id.habit_name);
                streakValue = itemView.findViewById(R.id.streak_value);
                targetSummary = itemView.findViewById(R.id.target_summary);
                priorityHintChip = itemView.findViewById(R.id.priority_hint_chip);
                todayDate = itemView.findViewById(R.id.today_date);
                checkInButton = itemView.findViewById(R.id.check_in_button);
                checkedActions = itemView.findViewById(R.id.checked_actions);
                cancelCheckInButton = itemView.findViewById(R.id.cancel_check_in_button);
                weekDots = new TextView[]{
                        itemView.findViewById(R.id.week_dot_monday),
                        itemView.findViewById(R.id.week_dot_tuesday),
                        itemView.findViewById(R.id.week_dot_wednesday),
                        itemView.findViewById(R.id.week_dot_thursday),
                        itemView.findViewById(R.id.week_dot_friday),
                        itemView.findViewById(R.id.week_dot_saturday),
                        itemView.findViewById(R.id.week_dot_sunday)
                };
            }

            void bind(Habit habit) {
                int primaryColor = getColor(R.color.stellog_primary);
                // 找到当前 habit 今天的打卡记录
                CheckInRecord todayRecord = getTodayRecord(habit.id);
                boolean checkedInToday = todayRecord != null;
                String priorityHint = getPriorityHint(habit.id);

                // 此日计显示今天这条 record 的 value，而不是默认值
                long todayValue = todayRecord == null ? 0 : todayRecord.value;

                habitName.setText(habit.name);
                streakValue.setText(String.valueOf(habit.recordNum));
                streakValue.setTextColor(primaryColor);
                targetSummary.setText(buildCardStatsLine(todayValue, habit.totalValue, habit.unit));

                if (priorityHint.isEmpty()) {
                    priorityHintChip.setVisibility(View.GONE);
                } else {
                    priorityHintChip.setText(priorityHint);
                    priorityHintChip.setVisibility(View.VISIBLE);
                }

                todayDate.setText(getString(R.string.today_date_format, getTodayDateString()));
                todayDate.setTextColor(primaryColor);

                // 卡片边框：今日已打卡淡绿，未打卡淡红。
                cardContent.setBackgroundResource(checkedInToday
                        ? R.drawable.bg_main_habit_card_done
                        : R.drawable.bg_main_habit_card_undone);

                // 每张卡片自带提醒入口，已设提醒时高亮。
                if (hasReminder(habit.id)) {
                    reminderButton.setColorFilter(primaryColor);
                    reminderButton.setAlpha(1f);
                } else {
                    reminderButton.clearColorFilter();
                    reminderButton.setAlpha(0.5f);
                }
                reminderButton.setOnClickListener(v -> openReminderEditor(habit));

                bindWeekDots(habit.id);

                // 未打卡时显示“打卡”按钮；已打卡后显示“记录详细 / 取消”操作区。
                checkInButton.setVisibility(checkedInToday ? View.GONE : View.VISIBLE);
                checkedActions.setVisibility(checkedInToday ? View.VISIBLE : View.GONE);
                checkInButton.setOnClickListener(v -> checkInToday(habit, checkInButton));
                cancelCheckInButton.setOnClickListener(v -> cancelTodayCheckIn(habit, cancelCheckInButton));
                // 为“记录详细”按钮设置点击监听器
                itemView.findViewById(R.id.record_detail_button).setOnClickListener(v -> showRecordDetailPage(habit));
            }

            private void bindWeekDots(long habitId) {
                List<CheckInRecord.RecordDate> weekDates = getCurrentWeekDates();
                CheckInRecord.RecordDate today = CheckInRecord.RecordDate.today();
                for (int i = 0; i < weekDots.length; i++) {
                    CheckInRecord.RecordDate date = weekDates.get(i);
                    boolean checked = hasRecordOnDate(habitId, date);
                    if (checked) {
                        weekDots[i].setBackgroundResource(R.drawable.bg_circle_green);
                        weekDots[i].setText("\u2713");
                        weekDots[i].setTextColor(getColor(R.color.white));
                    } else if (date.isSameDay(today)) {
                        // \u4eca\u5929\u8fd8\u6ca1\u6253\u5361\u65f6\u7528\u7ea2\u5708\u7a81\u51fa\u63d0\u9192\u3002
                        weekDots[i].setBackgroundResource(R.drawable.bg_circle_red);
                        weekDots[i].setText("");
                    } else {
                        weekDots[i].setBackgroundResource(R.drawable.bg_circle_outline);
                        weekDots[i].setText("");
                    }
                }
            }
        }
    }
}
