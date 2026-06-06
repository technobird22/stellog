# Stellog 项目说明

Stellog 是一个习惯打卡 Android 应用。当前版本已实现：活动创建 / 编辑 / 删除、主页面卡片与列表两种展示、今日打卡、取消打卡、记录详情数量、周视图打卡圆点、日历页面、日历记录展示、日历活动筛选、历史补打卡、提醒编辑、成就系统、智能推荐排序、AI 智能助手、深色模式。

项目使用 Java 编写，数据持久化使用 Room。`Habit`、`CheckInRecord`、`Achievement` 会保存到本地 SQLite 数据库 `stellog.db` 中；提醒、AI 设置、深色模式等偏好保存在 SharedPreferences。

> 说明：本文档下半部分（“DAO 接口”及之前）描述的是项目早期结构，仍然适用；末尾的 [“新增功能说明”](#新增功能说明) 一节补充了之后加入的成就、AI、提醒、智能推荐、深色模式与界面交互改动。

## 项目结构

```text
app/src/main/java/com/example/stellog/
├── StellogApp.java                  # Application，启动时应用深色模式设置
├── ui/
│   ├── MainActivity.java            # 主页（首页/日历/成就/我的）+ 卡片/列表
│   ├── CreateHabitActivity.java     # 创建 / 编辑活动
│   ├── RecordDetailActivity.java    # 记录详情数量
│   ├── HabitFilterActivity.java     # 日历活动筛选
│   ├── ReminderEditActivity.java    # 提醒编辑
│   ├── AiAssistantActivity.java     # AI 助手对话（OpenAI 兼容接口）
│   └── AiSettingsActivity.java      # AI 地址 / 模型 / API Key 设置
├── data/
│   ├── model/
│   │   ├── Habit.java
│   │   ├── CheckInRecord.java
│   │   ├── CalendarDaySpec.java
│   │   └── Achievement.java
│   ├── database/
│   │   ├── StellogDatabase.java     # version 3，含 2→3 迁移
│   │   ├── HabitEntity.java
│   │   ├── CheckInRecordEntity.java
│   │   ├── CheckInDateCount.java
│   │   ├── AchievementEntity.java
│   │   ├── HabitDao.java
│   │   ├── CheckInRecordDao.java
│   │   └── AchievementDao.java
│   └── repository/
│       └── HabitRepository.java
└── util/
    ├── DateUtils.java
    └── DimensionUtils.java
```

## 分层职责

### ui

`ui` 层负责页面展示、点击事件、页面跳转、ActivityResult 接收和界面刷新。

| 文件 | 作用 |
| --- | --- |
| `MainActivity.java` | 主页面。负责首页卡片、底部导航、日历表、选中日期记录区、活动筛选结果接收、今日打卡和取消打卡。 |
| `CreateHabitActivity.java` | 创建活动页面。收集活动名称和单位，通过 Intent 返回给主页面。 |
| `RecordDetailActivity.java` | 记录详情页面。收集今日完成数量，通过 Intent 返回给主页面。 |
| `HabitFilterActivity.java` | 日历活动筛选页面。展示全部活动，维护勾选状态，点击确定后返回 `HashSet<Long>`。 |

### data.model

`data.model` 层是业务模型，供 UI 和 Repository 使用。它不直接依赖 Room。

| 文件 | 作用 |
| --- | --- |
| `Habit.java` | 活动模型，对应用户创建的一个习惯或目标。 |
| `CheckInRecord.java` | 单次打卡记录模型，对应某个活动在某一天的一次打卡。 |
| `CalendarDaySpec.java` | 日历单个日期格子的展示模型，包含是否今天、是否选中、是否本月外日期和打卡数量。 |

### data.database

`data.database` 层负责 Room 数据库表结构和 SQL 接口。

| 文件 | 作用 |
| --- | --- |
| `StellogDatabase.java` | Room 数据库入口，声明 Entity 和 DAO，创建 `stellog.db`。 |
| `HabitEntity.java` | `habits` 表结构，并提供 `Habit` 和 `HabitEntity` 的互转。 |
| `CheckInRecordEntity.java` | `check_in_records` 表结构，并提供 `CheckInRecord` 和 `CheckInRecordEntity` 的互转。 |
| `CheckInDateCount.java` | 日历范围计数查询的结果对象，不是数据库表。 |
| `HabitDao.java` | `habits` 表的查询、插入和更新接口。 |
| `CheckInRecordDao.java` | `check_in_records` 表的查询、聚合、插入、更新和删除接口。 |

### data.repository

`data.repository` 层封装业务逻辑，对 UI 提供稳定方法，对下调用 DAO。

| 文件 | 作用 |
| --- | --- |
| `HabitRepository.java` | 统一管理活动和打卡记录，包括创建活动、今日打卡、取消打卡、记录详情更新、日历范围查询和选中日期记录查询。 |

### util

`util` 层放通用工具。

| 文件 | 作用 |
| --- | --- |
| `DateUtils.java` | 日期工具，包括本周日期、今日日期字符串、日期比较、清除时间、生成 `dateKey`。 |
| `DimensionUtils.java` | 尺寸工具，目前提供 `dpToPx(Resources, int)`。 |

## 数据格式

### Habit

`Habit` 表示一个活动，也是首页 ViewPager 中的一张活动卡片。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 活动 id。当前由数据库最大 id + 1 生成。 |
| `userId` | `long` | 用户 id。当前默认 `0`。 |
| `name` | `String` | 活动名称。 |
| `unit` | `String` | 计量单位，例如 `分钟`、`km`、`个`，允许为空。 |
| `recordNum` | `int` | 已打卡次数。 |
| `reminderEnabled` | `boolean` | 是否开启提醒。当前创建时默认 `false`。 |
| `sortWeight` | `int` | 排序权重。当前创建时默认 `1`。 |
| `totalValue` | `long` | 累计完成数量。 |
| `createdAt` | `long` | 创建时间戳，毫秒。 |
| `updatedAt` | `long` | 更新时间戳，毫秒。 |

### CheckInRecord

`CheckInRecord` 表示某个活动在某一天的一条打卡记录。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 记录 id。当前由数据库最大 id + 1 生成。 |
| `habitId` | `long` | 所属活动 id。 |
| `userId` | `long` | 用户 id。 |
| `date` | `RecordDate` | 打卡日期，只包含年月日。 |
| `value` | `long` | 完成数量。当前只支持非负整数。 |
| `source` | `String` | 来源，当前有 `正常打卡` 和 `补打卡` 常量。 |
| `createdAt` | `long` | 创建时间戳，毫秒。 |
| `updatedAt` | `long` | 更新时间戳，毫秒。 |

`RecordDate` 的字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `year` | `int` | 年，例如 `2026`。 |
| `month` | `int` | 月，范围 `1-12`。 |
| `day` | `int` | 日，范围 `1-31`。 |

`RecordDate` 提供的方法：

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `isSameDay(RecordDate other)` | `boolean` | 判断两个业务日期是否为同一天。 |
| `today()` | `RecordDate` | 生成今天的业务日期。 |
| `fromCalendar(Calendar calendar)` | `RecordDate` | 从 `Calendar` 转成业务日期。 |

### CalendarDaySpec

`CalendarDaySpec` 是日历格子的 UI 展示数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `date` | `Calendar` | 该格子的真实日期。 |
| `label` | `String` | 显示在格子中的日期数字。 |
| `today` | `boolean` | 是否今天。今天在日期下方显示绿色小点。 |
| `selected` | `boolean` | 是否当前选中日期。选中日期使用深绿色背景。 |
| `outsideMonth` | `boolean` | 是否为上月或下月补位日期。 |
| `recordCount` | `int` | 当前筛选活动中，该日期已有多少条打卡记录。 |

### dateKey

为了让日历范围查询更高效，打卡记录表额外保存 `dateKey`：

```text
dateKey = year * 10000 + month * 100 + day
```

示例：

```text
2026-05-12 -> 20260512
```

`dateKey` 的生成入口在 `DateUtils`：

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `toDateKey(CheckInRecord.RecordDate date)` | `int` | 从业务日期生成日期键。 |
| `toDateKey(Calendar calendar)` | `int` | 从 `Calendar` 生成日期键，会把 `Calendar.MONTH` 从 `0-11` 转成 `1-12`。 |
| `toDateKey(int year, int month, int day)` | `int` | 直接从年月日生成日期键。 |

## Room 数据库

数据库入口是 `StellogDatabase`：

```java
@Database(
        entities = {HabitEntity.class, CheckInRecordEntity.class, AchievementEntity.class},
        version = 3,
        exportSchema = false
)
public abstract class StellogDatabase extends RoomDatabase
```

当前创建方式：

```java
Room.databaseBuilder(
        context.getApplicationContext(),
        StellogDatabase.class,
        "stellog.db"
)
        .addMigrations(MIGRATION_2_3)
        .fallbackToDestructiveMigration(true)
        .build();
```

说明：

- 数据库文件名是 `stellog.db`。
- 文件位于应用私有目录，通常是 `/data/data/com.example.stellog/databases/stellog.db`。
- 数据库操作不在主线程执行：`MainActivity` 用一个单线程 `ExecutorService` 跑数据库任务，完成后通过 `runOnUiThread` 回到界面线程刷新；`AiAssistantActivity` 的网络请求也走独立线程。
- `MIGRATION_2_3` 为 2→3 新增 `achievements` 表；`fallbackToDestructiveMigration(true)` 作为兜底，版本变化且缺迁移脚本时会重建数据库（旧数据会被清空），正式版本应补齐迁移。

### habits 表

Entity：`HabitEntity`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 主键。 |
| `userId` | `long` | 用户 id。 |
| `name` | `String` | 活动名称。 |
| `unit` | `String` | 单位。 |
| `recordNum` | `int` | 打卡次数。 |
| `reminderEnabled` | `boolean` | 是否提醒。 |
| `sortWeight` | `int` | 排序权重。 |
| `totalValue` | `long` | 累计完成数量。 |
| `createdAt` | `long` | 创建时间。 |
| `updatedAt` | `long` | 更新时间。 |

转换方法：

| 方法 | 说明 |
| --- | --- |
| `HabitEntity.fromModel(Habit habit)` | 将业务模型转换成 Room Entity。 |
| `toModel()` | 将 Room Entity 转换成业务模型。 |

### check_in_records 表

Entity：`CheckInRecordEntity`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 主键。 |
| `habitId` | `long` | 活动 id。 |
| `userId` | `long` | 用户 id。 |
| `year` | `int` | 打卡年份。 |
| `month` | `int` | 打卡月份，范围 `1-12`。 |
| `day` | `int` | 打卡日期。 |
| `dateKey` | `int` | 日期键，用于范围查询和索引。 |
| `value` | `long` | 完成数量。 |
| `source` | `String` | 打卡来源。 |
| `createdAt` | `long` | 创建时间。 |
| `updatedAt` | `long` | 更新时间。 |

索引：

| 索引 | 说明 |
| --- | --- |
| `@Index("habitId")` | 按活动查记录。 |
| `@Index("dateKey")` | 按日期范围查记录。 |
| `@Index(value = {"habitId", "dateKey"}, unique = true)` | 保证同一活动同一天最多一条记录，并支持活动 + 日期查询。 |

转换方法：

| 方法 | 说明 |
| --- | --- |
| `CheckInRecordEntity.fromModel(CheckInRecord record)` | 将业务模型转换成 Room Entity，并生成 `dateKey`。 |
| `toModel()` | 将 Room Entity 转换成业务模型。 |

### CheckInDateCount

`CheckInDateCount` 是 Room 聚合查询结果：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dateKey` | `int` | 日期键。 |
| `count` | `int` | 该日期命中的打卡记录数量。 |

它不对应数据库表，只用于接收：

```sql
SELECT dateKey, COUNT(*) AS count
```

## DAO 接口

### HabitDao

| 方法 | SQL / 行为 | 说明 |
| --- | --- | --- |
| `getAll()` | `SELECT * FROM habits ORDER BY id ASC` | 获取全部活动，按 id 升序排列。 |
| `findById(long habitId)` | `SELECT * FROM habits WHERE id = :habitId LIMIT 1` | 根据 id 查找活动。 |
| `nextId()` | `SELECT COALESCE(MAX(id), 0) + 1 FROM habits` | 生成下一个活动 id。 |
| `insert(HabitEntity habit)` | `@Insert` | 插入活动。 |
| `update(HabitEntity habit)` | `@Update` | 更新活动。 |

### CheckInRecordDao

| 方法 | SQL / 行为 | 说明 |
| --- | --- | --- |
| `findOnDate(long habitId, int dateKey)` | 按 `habitId + dateKey` 查单条记录 | 判断某活动某天是否已打卡。 |
| `countByDateRange(int startDateKey, int endDateKey, List<Long> habitIds)` | `dateKey BETWEEN ... AND habitId IN (...) GROUP BY dateKey` | 查询日历 42 格范围内，每一天命中的记录数。 |
| `findByDate(int dateKey, List<Long> habitIds)` | 按 `dateKey + habitIds` 查询并 `ORDER BY habitId ASC` | 查询选中日期内，当前筛选活动的打卡记录。 |
| `nextId()` | `SELECT COALESCE(MAX(id), 0) + 1 FROM check_in_records` | 生成下一条记录 id。 |
| `insert(CheckInRecordEntity record)` | `@Insert` | 插入打卡记录。 |
| `update(CheckInRecordEntity record)` | `@Update` | 更新打卡记录。 |
| `delete(CheckInRecordEntity record)` | `@Delete` | 删除打卡记录。 |

## Repository 接口

`HabitRepository` 是 UI 层访问数据的主要入口。

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `getHabits()` | `List<Habit>` | 返回内存中的活动列表。Repository 初始化时会从 Room 加载。 |
| `addHabit(String name, String unit)` | `Habit` | 创建活动，写入 `habits` 表，并同步加入内存列表。 |
| `getTodayRecord(long habitId)` | `CheckInRecord` | 查询某活动今天的打卡记录，没有则返回 `null`。 |
| `checkInToday(Habit habit)` | `boolean` | 今日打卡。成功后插入记录并更新活动统计。重复打卡返回 `false`。 |
| `cancelTodayCheckIn(Habit habit)` | `boolean` | 取消今日打卡。成功后删除记录并回退活动统计。 |
| `hasRecordOnDate(long habitId, RecordDate date)` | `boolean` | 判断某活动某天是否已有记录。 |
| `getCheckInCountByDateRange(Calendar startDate, Calendar endDate, Set<Long> selectedHabitIds)` | `Map<Integer, Integer>` | 查询日历范围内每一天的记录数量，结果是 `dateKey -> count`。 |
| `getRecordsByDate(RecordDate date, Set<Long> selectedHabitIds)` | `Map<Long, CheckInRecord>` | 查询某天当前筛选活动的记录，结果是 `habitId -> record`。 |
| `applyRecordDetailValue(long habitId, long newValue)` | `boolean` | 修改今日记录数量，并同步更新活动累计值。 |
| `findHabitPosition(long habitId)` | `int` | 查找活动在内存列表中的位置，用于刷新 ViewPager 对应卡片。 |

内部方法：

| 方法 | 说明 |
| --- | --- |
| `getRecordOnDate(long habitId, RecordDate date)` | 私有方法。通过 `dateKey` 查询某活动某天的记录。 |
| `reloadHabits()` | 私有方法。从 Room 读取全部活动并转换成 `Habit`。 |

## UI 页面逻辑

### MainActivity

主要状态字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `habitRepository` | `HabitRepository` | 数据仓库。 |
| `habits` | `List<Habit>` | 当前活动列表，来自 Repository。 |
| `visibleMonth` | `Calendar` | 日历当前显示的月份，日期固定为该月 1 号。 |
| `selectedDate` | `Calendar` | 当前选中的日期。 |
| `selectedCalendarHabitIds` | `HashSet<Long>` | 日历当前筛选的活动 id 集合。 |
| `currentHabitPosition` | `int` | ViewPager 当前卡片位置。 |

主要方法：

| 方法 | 说明 |
| --- | --- |
| `setupBottomTabs()` | 绑定底部“首页 / 日历”切换。 |
| `showHomePage()` | 显示首页卡片，隐藏日历页。 |
| `showCalendarPage()` | 显示日历页，隐藏首页卡片。 |
| `setupCalendarNavigation()` | 初始化日历月份和左右切换按钮。 |
| `renderCalendarGrid()` | 生成并绑定 42 个日期格子，同时刷新选中日期记录区。 |
| `buildVisibleMonthDays()` | 根据 `visibleMonth` 生成 6 行 x 7 列日期数据，并查询范围内记录数量。 |
| `bindCalendarDay(View, CalendarDaySpec)` | 绑定单个日期格子的数字、选中态、今天小点、记录数量角标和点击事件。 |
| `renderSelectedDateRecords()` | 渲染选中日期下方的活动记录列表和完成统计。 |
| `createSelectedDateRecordRow(Habit, CheckInRecord)` | 创建选中日期记录区的一行文本。 |
| `setupHabitPager()` | 初始化首页 ViewPager2。 |
| `addHabit(String, String)` | 创建活动并刷新首页和日历。 |
| `checkInToday(Habit)` | 今日打卡并刷新当前卡片和日历。 |
| `cancelTodayCheckIn(Habit)` | 取消今日打卡并刷新当前卡片和日历。 |
| `showRecordDetailPage(Habit)` | 打开记录详情页面。 |
| `applyRecordDetailValue(long, long)` | 应用记录详情页面返回的新数量。 |
| `selectAllCalendarHabits()` | 初始化日历筛选集合，默认选择全部活动。 |
| `applyHabitFilterResult(Intent)` | 接收活动筛选页面返回的 `HashSet<Long>` 并刷新日历。 |
| `updateCalendarFilterLabel()` | 根据筛选数量更新顶部按钮文字。 |
| `countSelectedExistingHabits()` | 统计当前仍存在的已选活动数量。 |

ActivityResult：

| Launcher | 来源页面 | 返回数据 | 说明 |
| --- | --- | --- | --- |
| `createHabitLauncher` | `CreateHabitActivity` | `habit_name`, `habit_unit` | 创建活动。 |
| `recordDetailLauncher` | `RecordDetailActivity` | `habit_id`, `record_value` | 更新今日记录数量。 |
| `habitFilterLauncher` | `HabitFilterActivity` | `selected_habit_ids` | 更新日历活动筛选集合。 |

### CreateHabitActivity

输入字段：

| 控件 id | 数据 |
| --- | --- |
| `habit_name_input` | 活动名称，不能为空。 |
| `habit_unit_input` | 活动单位，允许为空。 |

返回 Intent：

| key | 类型 | 说明 |
| --- | --- | --- |
| `habit_name` | `String` | 活动名称。 |
| `habit_unit` | `String` | 活动单位。 |

主要方法：

| 方法 | 说明 |
| --- | --- |
| `saveHabitInput()` | 校验活动名称，生成返回 Intent。 |

### RecordDetailActivity

输入 Intent：

| key | 类型 | 说明 |
| --- | --- | --- |
| `habit_id` | `long` | 活动 id。 |
| `habit_name` | `String` | 活动名称。 |
| `habit_unit` | `String` | 单位。 |
| `record_value` | `long` | 当前记录数量。 |

返回 Intent：

| key | 类型 | 说明 |
| --- | --- | --- |
| `habit_id` | `long` | 活动 id。 |
| `record_value` | `long` | 新完成数量。 |

输入限制：

- 布局中 `record_value_input` 使用 `android:inputType="number"`，因此默认只允许数字输入。
- `saveRecordValue()` 中使用 `Long.parseLong(...)` 二次校验。
- 当前只支持非负整数，不支持小数或分数。

主要方法：

| 方法 | 说明 |
| --- | --- |
| `saveRecordValue()` | 校验数量，返回新记录值。 |

### HabitFilterActivity

用途：选择日历中要参与统计和展示的活动。

输入 Intent：

| key | 类型 | 说明 |
| --- | --- | --- |
| `selected_habit_ids` | `HashSet<Long>` | 主界面当前选中的活动 id 集合。 |

返回 Intent：

| key | 类型 | 说明 |
| --- | --- | --- |
| `selected_habit_ids` | `HashSet<Long>` | 用户点击确定时选中的活动 id 集合。 |

主要状态：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `habits` | `List<Habit>` | 从 Repository 读取的全部活动。 |
| `selectedHabitIds` | `HashSet<Long>` | 页面内当前选中的活动 id。 |
| `optionContainer` | `LinearLayout` | 选项列表容器。 |

主要方法：

| 方法 | 说明 |
| --- | --- |
| `loadInitialSelection()` | 从 Intent 读取初始选择；没有传入时默认全选。 |
| `renderOptions()` | 渲染“全部”选项和每个活动选项。 |
| `toggleAllHabits()` | 如果当前全选，则全部取消；否则全部选中。 |
| `toggleHabit(long habitId)` | 切换单个活动的选中状态。 |
| `confirmSelection()` | 把 `selectedHabitIds` 放入 Intent 并返回主页面。 |
| `areAllHabitsSelected()` | 判断是否所有活动都被选中。 |
| `getAllSubtitle()` | 生成“已选择 x / y”的副标题。 |
| `getHabitSubtitle(Habit habit)` | 生成活动副标题，当前显示单位。 |
| `createOptionRow(...)` | 创建一行可点击选项。 |

“全部”选项规则：

```text
如果所有活动都已选中：
    点击“全部” -> 全部取消选中
否则：
    点击“全部” -> 全部设为选中
```

## 日历逻辑

### 日期生成

日历始终生成 42 个日期格子：

```text
6 行 x 7 列
周一到周日排列
包含上月补位日期和下月补位日期
```

生成流程：

1. 取 `visibleMonth` 的月初。
2. 根据月初是星期几计算前置补位天数。
3. 从第一个格子的日期开始连续生成 42 天。
4. 标记每个格子是否属于当前月、是否今天、是否选中。
5. 用第 1 格和第 42 格作为范围边界，一次查询该范围内的打卡数量。

### 日期状态显示

| 状态 | 显示 |
| --- | --- |
| 今天 | 日期下方绿色小点。 |
| 选中日期 | 深绿色背景，白色数字。 |
| 有记录但未选中 | 浅绿色背景，深绿色数字。 |
| 记录数量大于 1 | 右上角白色圆形角标显示数量。 |
| 上月 / 下月补位日期 | 浅色数字，不可点击。 |

### 日历范围查询

日历页不会对 42 个日期逐个查数据库，而是一次范围查询：

```text
startDate = 第一个日期格子
endDate = 第 42 个日期格子
selectedHabitIds = 当前筛选活动集合
```

Repository 方法：

```java
getCheckInCountByDateRange(startDate, endDate, selectedHabitIds)
```

DAO 查询：

```sql
SELECT dateKey, COUNT(*) AS count
FROM check_in_records
WHERE dateKey BETWEEN :startDateKey AND :endDateKey
AND habitId IN (:habitIds)
GROUP BY dateKey
```

返回后转换成：

```text
Map<Integer, Integer>
dateKey -> count
```

每个日期格子只从这个 Map 中取数量，不再单独查数据库。

### 选中日期记录区

选中某一天后，下方区域展示当前筛选活动在这一天的状态。

查询流程：

```text
selectedDate
    -> RecordDate
    -> dateKey
    -> findByDate(dateKey, selectedHabitIds)
    -> Map<habitId, CheckInRecord>
```

展示规则：

| 状态 | 文案 | 颜色 |
| --- | --- | --- |
| 已打卡 | `活动名  ·  已完成 xx unit` | 深色 |
| 未打卡 | `活动名  ·  待打卡` | 浅色 |

统计区域：

| 指标 | 来源 |
| --- | --- |
| 已完成 | 当前筛选活动中，当天有记录的数量。 |
| 计划数 | 当前筛选活动数量。 |
| 完成率 | `已完成 / 计划数`。 |

## 主要业务流程

### 创建活动

```text
CreateHabitActivity
    -> 输入 name/unit
    -> setResult(habit_name, habit_unit)
    -> MainActivity.createHabitLauncher
    -> MainActivity.addHabit(...)
    -> HabitRepository.addHabit(...)
    -> HabitDao.insert(...)
    -> habits 内存列表新增
    -> 首页卡片刷新
    -> 日历筛选集合加入新 habitId
    -> 日历刷新
```

### 今日打卡

```text
MainActivity.checkInToday(habit)
    -> HabitRepository.checkInToday(habit)
    -> getTodayRecord(habit.id) 检查是否重复
    -> CheckInRecordDao.insert(...)
    -> Habit.recordNum + 1
    -> Habit.totalValue 更新
    -> HabitDao.update(...)
    -> 当前卡片刷新
    -> 日历刷新
```

### 取消今日打卡

```text
MainActivity.cancelTodayCheckIn(habit)
    -> HabitRepository.cancelTodayCheckIn(habit)
    -> getTodayRecord(habit.id)
    -> CheckInRecordDao.delete(...)
    -> Habit.recordNum - 1
    -> Habit.totalValue 回退
    -> HabitDao.update(...)
    -> 当前卡片刷新
    -> 日历刷新
```

### 修改记录详情数量

```text
MainActivity.showRecordDetailPage(habit)
    -> RecordDetailActivity
    -> 输入 record_value
    -> setResult(habit_id, record_value)
    -> MainActivity.recordDetailLauncher
    -> HabitRepository.applyRecordDetailValue(...)
    -> CheckInRecordDao.update(...)
    -> HabitDao.update(...)
    -> 当前卡片刷新
    -> 选中日期记录区刷新
```

当前限制：记录详情只修改今天的记录，不支持修改历史日期记录。

### 日历活动筛选

```text
点击日历顶部中间按钮
    -> MainActivity 把 selectedCalendarHabitIds 放入 Intent
    -> HabitFilterActivity 显示所有活动
    -> 用户切换“全部”或单个活动
    -> 点击确定
    -> 返回 HashSet<Long>
    -> MainActivity.applyHabitFilterResult(...)
    -> 更新 selectedCalendarHabitIds
    -> 更新顶部按钮文字
    -> 日历范围计数刷新
    -> 选中日期记录区刷新
```

## 资源和布局

| 文件 | 作用 |
| --- | --- |
| `activity_main.xml` | 主页面布局，包含首页卡片区域、日历区域、底部导航。 |
| `item_habit_card.xml` | 首页单张活动卡片。 |
| `item_calendar_day.xml` | 日历单个日期格子。 |
| `activity_create_habit.xml` | 创建活动页面。 |
| `activity_record_detail.xml` | 记录详情页面。 |
| `activity_habit_filter.xml` | 活动筛选页面。 |
| `bg_calendar_day_selected.xml` | 日历选中日期深绿色背景。 |
| `bg_calendar_day_recorded.xml` | 日历有记录但未选中日期的浅绿色背景。 |
| `bg_calendar_count_badge.xml` | 日期右上角数量角标背景。 |
| `bg_filter_check_selected.xml` | 筛选页面选中圆点。 |
| `bg_filter_check_unselected.xml` | 筛选页面未选中圆点。 |

## 当前限制和后续方向

- 数据库操作已移出主线程（单线程 Executor + `runOnUiThread`），但仍未引入 LiveData/Flow/ViewModel，后续可继续架构化。
- 数据库迁移目前只有 2→3，并保留 `fallbackToDestructiveMigration(true)` 兜底，正式版本应为每次版本变更补充迁移脚本。
- 完成数量当前是 `long`，只支持非负整数；如果要支持小数，应改为 `double` 或其他统一存储策略。
- 提醒只保存标题与起止时间（SharedPreferences），尚未接入系统通知/AlarmManager 真正触发提醒。
- 日历活动筛选状态目前保存在 `MainActivity` 内存中，重启应用后会恢复为默认全选。
- 深色模式对自定义 drawable（如成就徽章）为尽力适配，个别装饰元素在深色下仍保持浅色。
- 当前没有登录系统，`userId` 默认使用 `0`。

## 新增功能说明

早期 Readme 之后加入的功能与交互，简述如下。

### 活动编辑与删除

- 长按主页卡片或列表项弹出「编辑活动 / 删除活动」菜单。
- 编辑复用创建页（预填名称、单位）；`HabitRepository.updateHabit` 用拷贝其余字段的新 `Habit` 替换原对象后写库（`name`/`unit` 为 `final`）。
- 删除有二次确认，`HabitRepository.deleteHabit` 同时删除该活动及其全部打卡记录。

### 提醒

- 每张卡片顶部有闹铃入口，进入 `ReminderEditActivity` 为该活动设置提醒标题（默认带入活动名）、开始/结束时间，可取消或保存。
- 保存到 SharedPreferences（`reminders`），已设提醒的卡片闹铃会高亮。

### 成就系统

- `Achievement` / `AchievementEntity` / `AchievementDao`，首次运行写入默认成就。
- 创建活动、打卡后在 `HabitRepository` 中检查解锁条件（累计次数、连续天数、活动数量等），成就页（成就 Tab）展示已解锁/未解锁。

### 智能推荐排序

- 「我的」页开关，开启后 `HabitRepository.buildHabitPrioritySnapshots` 按近 7 天行为打分（中断风险 / 完成频率 / 连续天数加权）对首页活动排序，并在卡片给出轻量提示文案。详见 `SmartRecommendation.md`。

### AI 智能助手

- `AiAssistantActivity` 通过 OpenAI 兼容的 `/chat/completions` 流式接口对话，并带入本周习惯数据作为上下文。
- `AiSettingsActivity` 可配置「API 地址 / 模型 / API Key」，默认 DeepSeek（`deepseek-chat`）；地址改为本地等自定义服务时 API Key 可留空（请求不带 Authorization）。底部「AI助手」Tab 进入。

### 界面与交互

- 主页卡片：单行统计（今日 / 累计）+ 推荐提示 chip；内容可在卡片内滚动避免按钮被裁剪；今日未打卡时当天圆点变红、卡片描边按今日是否完成显示淡绿/淡红。
- 卡片下方圆点指示当前位置；新增活动按钮为右下角悬浮按钮；底部为「首页 / 日历 / AI助手 / 成就 / 我的」五个入口。
- 列表项左侧有今日完成状态圆点。
- 反馈与异步：创建/打卡/补打卡/记录/删除/保存提醒均有 Toast 或二次确认，加载有遮罩与按钮禁用，异常有「请重试」提示。
- 深色模式：`StellogApp` 启动时应用偏好，「我的」页可切换 浅色 / 深色 / 跟随系统（默认浅色），配色由 `values-night` 提供。
