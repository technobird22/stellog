package com.example.stellog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.stellog.R;
import com.example.stellog.data.model.CalendarDaySpec;
import com.example.stellog.data.model.CheckInRecord;
import com.example.stellog.data.model.Habit;
import com.example.stellog.data.repository.HabitRepository;
import com.example.stellog.util.DateUtils;
import com.example.stellog.util.DimensionUtils;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 应用主页面。
 *
 * 当前页面负责展示活动卡片、处理卡片滑动、创建活动结果、今日打卡和取消打卡。
 */
public class MainActivity extends AppCompatActivity {

    // 当前版本中每次打卡默认增加 1，后续可以改成用户填写的数量。
    private static final long DEFAULT_RECORD_VALUE = 0L;

    private HabitRepository habitRepository;
    private List<Habit> habits;

    // 内存中的打卡记录列表；卡片上的本周状态和今日状态都由它推导。

    private ViewPager2 habitPager;
    private HabitPagerAdapter habitAdapter;
    private TextView pageIndicatorText;
    private View calendarContent;
    private TextView homeTab;
    private TextView calendarTab;
    private TextView calendarActivityFilterLabel;
    private GridLayout calendarGrid;
    private TextView calendarMonthTitle;
    private TextView calendarSelectedDateTitle;
    private TextView calendarSelectedDateHint;
    private LinearLayout calendarSelectedRecords;
    private TextView calendarCompletedCount;
    private TextView calendarPlanCount;
    private TextView calendarCompletionRate;
    private final Calendar visibleMonth = Calendar.getInstance();
    private Calendar selectedDate = Calendar.getInstance();
    private final HashSet<Long> selectedCalendarHabitIds = new HashSet<>();

    private int currentHabitPosition = 0;

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

        pageIndicatorText = findViewById(R.id.page_indicator_text);
        calendarContent = findViewById(R.id.calendar_content);
        homeTab = findViewById(R.id.home_tab);
        calendarTab = findViewById(R.id.calendar_tab);
        calendarActivityFilterLabel = findViewById(R.id.calendar_activity_filter_label);
        calendarGrid = findViewById(R.id.calendar_grid);
        calendarMonthTitle = findViewById(R.id.calendar_month_title);
        calendarSelectedDateTitle = findViewById(R.id.calendar_selected_date_title);
        calendarSelectedDateHint = findViewById(R.id.calendar_selected_date_hint);
        calendarSelectedRecords = findViewById(R.id.calendar_selected_records);
        calendarCompletedCount = findViewById(R.id.calendar_completed_count);
        calendarPlanCount = findViewById(R.id.calendar_plan_count);
        calendarCompletionRate = findViewById(R.id.calendar_completion_rate);
        habitRepository = new HabitRepository(getApplicationContext());
        habits = habitRepository.getHabits();
        selectAllCalendarHabits();
        setupHabitPager();
        updateHeader(0);
        setupCalendarNavigation();
        renderCalendarGrid();
        setupBottomTabs();

        findViewById(R.id.add_activity_button).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateHabitActivity.class);
            createHabitLauncher.launch(intent);
        });
        findViewById(R.id.calendar_activity_filter_button).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HabitFilterActivity.class);
            intent.putExtra(
                    HabitFilterActivity.EXTRA_SELECTED_HABIT_IDS,
                    new HashSet<>(selectedCalendarHabitIds)
            );
            habitFilterLauncher.launch(intent);
        });
    }

    private void selectAllCalendarHabits() {
        selectedCalendarHabitIds.clear();
        for (Habit habit : habits) {
            selectedCalendarHabitIds.add(habit.id);
        }
        updateCalendarFilterLabel();
    }

    @SuppressWarnings("unchecked")
    private void applyHabitFilterResult(Intent data) {
        Object extra = data.getSerializableExtra(HabitFilterActivity.EXTRA_SELECTED_HABIT_IDS);
        if (!(extra instanceof HashSet<?>)) {
            return;
        }

        selectedCalendarHabitIds.clear();
        selectedCalendarHabitIds.addAll((HashSet<Long>) extra);
        updateCalendarFilterLabel();
        renderCalendarGrid();
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
        showHomePage();
    }

    private void showHomePage() {
        findViewById(R.id.view_mode_switch).setVisibility(View.VISIBLE);
        findViewById(R.id.page_indicator).setVisibility(View.VISIBLE);
        findViewById(R.id.side_rail).setVisibility(View.VISIBLE);
        habitPager.setVisibility(View.VISIBLE);
        findViewById(R.id.add_activity_button).setVisibility(View.VISIBLE);
        calendarContent.setVisibility(View.GONE);

        homeTab.setTextColor(getColor(R.color.stellog_primary));
        homeTab.setTypeface(null, android.graphics.Typeface.BOLD);
        calendarTab.setTextColor(getColor(R.color.stellog_ink));
        calendarTab.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    private void showCalendarPage() {
        findViewById(R.id.view_mode_switch).setVisibility(View.GONE);
        findViewById(R.id.page_indicator).setVisibility(View.GONE);
        findViewById(R.id.side_rail).setVisibility(View.GONE);
        habitPager.setVisibility(View.GONE);
        findViewById(R.id.add_activity_button).setVisibility(View.GONE);
        calendarContent.setVisibility(View.VISIBLE);

        homeTab.setTextColor(getColor(R.color.stellog_ink));
        homeTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        calendarTab.setTextColor(getColor(R.color.stellog_primary));
        calendarTab.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void setupCalendarNavigation() {
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        DateUtils.clearTime(selectedDate);
        findViewById(R.id.calendar_prev_month).setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, -1);
            renderCalendarGrid();
        });
        findViewById(R.id.calendar_next_month).setOnClickListener(v -> {
            visibleMonth.add(Calendar.MONTH, 1);
            renderCalendarGrid();
        });
    }

    private void renderCalendarGrid() {
        calendarMonthTitle.setText(String.format(
                Locale.CHINA,
                "%d \u5E74 %d \u6708",
                visibleMonth.get(Calendar.YEAR),
                visibleMonth.get(Calendar.MONTH) + 1
        ));

        LayoutInflater inflater = LayoutInflater.from(this);
        calendarGrid.removeAllViews();
        CalendarDaySpec[] days = buildVisibleMonthDays();
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
        renderSelectedDateRecords();
    }

    private CalendarDaySpec[] buildVisibleMonthDays() {
        Calendar firstDay = (Calendar) visibleMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);

        int leadingDays = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        Calendar cellDate = (Calendar) firstDay.clone();
        cellDate.add(Calendar.DAY_OF_MONTH, -leadingDays);
        Calendar rangeStartDate = (Calendar) cellDate.clone();
        Calendar rangeEndDate = (Calendar) rangeStartDate.clone();
        rangeEndDate.add(Calendar.DAY_OF_MONTH, 41);
        Map<Integer, Integer> recordCountByDateKey =
                habitRepository.getCheckInCountByDateRange(
                        rangeStartDate,
                        rangeEndDate,
                        selectedCalendarHabitIds
                );

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
            renderCalendarGrid();
        });
    }

    private void renderSelectedDateRecords() {
        CheckInRecord.RecordDate recordDate = CheckInRecord.RecordDate.fromCalendar(selectedDate);
        Map<Long, CheckInRecord> recordByHabitId = habitRepository.getRecordsByDate(
                recordDate,
                selectedCalendarHabitIds
        );

        calendarSelectedDateTitle.setText(String.format(
                Locale.CHINA,
                "%d 月 %d 日",
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

        TextView recordText = new TextView(this);
        recordText.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        ));
        recordText.setGravity(android.view.Gravity.CENTER_VERTICAL);
        recordText.setTextColor(getColor(completed ? R.color.stellog_ink : R.color.stellog_muted));
        recordText.setTextSize(16);
        recordText.setTypeface(null, android.graphics.Typeface.BOLD);
        if (completed) {
            recordText.setText(String.format(
                    Locale.CHINA,
                    "%s  ·  已完成 %d %s",
                    habit.name,
                    record.value,
                    habit.unit
            ));
        } else {
            recordText.setText(String.format(Locale.CHINA, "%s  ·  待打卡", habit.name));
        }

        TextView actionButton = createCalendarRecordActionButton(habit, record, recordDate);
        row.addView(recordText);
        row.addView(actionButton);
        return row;
    }

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
        button.setOnClickListener(v -> checkInOnSelectedDate(habit, recordDate));
        return button;
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
        Habit habit = habitRepository.addHabit(name, unit);
        selectedCalendarHabitIds.add(habit.id);
        updateCalendarFilterLabel();
        habitAdapter.notifyItemInserted(habits.size() - 1);
        renderCalendarGrid();

        // 创建完成后自动滑到新活动卡片。
        habitPager.setCurrentItem(habits.size() - 1, true);
        updateHeader(habits.size() - 1);
    }

    /**
     * 更新右上角页码。
     */
    private void updateHeader(int position) {
        if (habits.isEmpty()) {
            pageIndicatorText.setText(getString(R.string.page_indicator_format, 0, 0));
            return;
        }
        pageIndicatorText.setText(getString(R.string.page_indicator_format, position + 1, habits.size()));
    }

    /**
     * 查找指定活动今天的打卡记录。
     */
    private CheckInRecord getTodayRecord(long habitId) {
        return habitRepository.getTodayRecord(habitId);
    }

    /**
     * 今日打卡：新增 record，并同步更新 Habit 上的统计字段。
     */
    private void checkInToday(Habit habit) {
        if (habitRepository.checkInToday(habit)) {
            habitAdapter.notifyItemChanged(currentHabitPosition);
            renderCalendarGrid();
        }
    }

    private void checkInOnSelectedDate(Habit habit, CheckInRecord.RecordDate recordDate) {
        Calendar selected = (Calendar) selectedDate.clone();
        DateUtils.clearTime(selected);
        Calendar today = Calendar.getInstance();
        DateUtils.clearTime(today);
        if (selected.after(today)) {
            Toast.makeText(this, "不能为未来日期打卡", Toast.LENGTH_SHORT).show();
            return;
        }

        String source = DateUtils.isSameDate(selected, today)
                ? CheckInRecord.SOURCE_NORMAL
                : CheckInRecord.SOURCE_PATCH;
        if (habitRepository.checkInOnDate(habit, recordDate, source)) {
            habitAdapter.notifyDataSetChanged();
            renderCalendarGrid();
        }
    }

    /**
     * 取消今日打卡：删除今天的 record，并回退 Habit 上的统计字段。
     */
    private void cancelTodayCheckIn(Habit habit) {
        if (habitRepository.cancelTodayCheckIn(habit)) {
            habitAdapter.notifyItemChanged(currentHabitPosition);
            renderCalendarGrid();
        }
    }

    /**
     * 判断指定活动在某一天是否已经打卡。
     */
    private boolean hasRecordOnDate(long habitId, CheckInRecord.RecordDate date) {
        return habitRepository.hasRecordOnDate(habitId, date);
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
        int habitPosition = habitRepository.findHabitPosition(habitId);
        if (habitRepository.applyRecordDetailValue(habitId, recordDate, newValue)) {
            if (habitPosition >= 0) {
                habitAdapter.notifyItemChanged(habitPosition);
            }
            renderCalendarGrid();
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
            private final TextView habitName;
            private final TextView streakValue;
            private final TextView targetSummary;
            private final TextView todayDate;
            private final TextView checkInButton;
            private final View checkedActions;
            private final TextView cancelCheckInButton;
            private final TextView[] weekDots;

            HabitViewHolder(@NonNull View itemView) {
                super(itemView);
                habitName = itemView.findViewById(R.id.habit_name);
                streakValue = itemView.findViewById(R.id.streak_value);
                targetSummary = itemView.findViewById(R.id.target_summary);
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

                // 此日计显示今天这条 record 的 value，而不是默认值
                long todayValue = todayRecord == null ? 0 : todayRecord.value;

                habitName.setText(habit.name);
                streakValue.setText(String.valueOf(habit.recordNum));
                streakValue.setTextColor(primaryColor);
                targetSummary.setText(getString(
                        R.string.habit_target_summary,
                        todayValue,
                        habit.unit,
                        habit.totalValue,
                        habit.unit
                ));
                todayDate.setText(getString(R.string.today_date_format, getTodayDateString()));
                todayDate.setTextColor(primaryColor);

                bindWeekDots(habit.id);

                // 未打卡时显示“打卡”按钮；已打卡后显示“记录详细 / 取消”操作区。
                checkInButton.setVisibility(checkedInToday ? View.GONE : View.VISIBLE);
                checkedActions.setVisibility(checkedInToday ? View.VISIBLE : View.GONE);
                checkInButton.setOnClickListener(v -> checkInToday(habit));
                cancelCheckInButton.setOnClickListener(v -> cancelTodayCheckIn(habit));
                // 为“记录详细”按钮设置点击监听器
                itemView.findViewById(R.id.record_detail_button).setOnClickListener(v -> showRecordDetailPage(habit));
            }

            private void bindWeekDots(long habitId) {
                List<CheckInRecord.RecordDate> weekDates = getCurrentWeekDates();
                for (int i = 0; i < weekDots.length; i++) {
                    boolean checked = hasRecordOnDate(habitId, weekDates.get(i));
                    weekDots[i].setBackgroundResource(checked ? R.drawable.bg_circle_green : R.drawable.bg_circle_outline);
                    weekDots[i].setText(checked ? "\u2713" : "");
                    weekDots[i].setTextColor(getColor(R.color.white));
                }
            }
        }
    }
}
