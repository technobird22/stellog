# Stellog 项目说明

Stellog 是一个习惯打卡 Android 应用。当前版本主要实现了活动创建、卡片展示、今日打卡、取消打卡、记录详细数量、本周打卡圆点展示等功能。

当前项目还没有接入数据库，数据仍然保存在内存中。为了后续更容易接入 Room / SQLite，本项目已经按简单分层整理为 UI 层、数据模型层、Repository 数据操作层和工具层。

## 项目结构

```text
app/src/main/java/com/example/stellog/
├─ ui/
│  ├─ MainActivity.java
│  ├─ CreateHabitActivity.java
│  └─ RecordDetailActivity.java
│
├─ data/
│  ├─ model/
│  │  ├─ Habit.java
│  │  └─ CheckInRecord.java
│  │
│  └─ repository/
│     └─ HabitRepository.java
│
└─ util/
   └─ DateUtils.java
```

## 分层说明

### ui

`ui` 包负责页面展示、按钮点击、页面跳转和刷新界面。

当前包含：

| 文件 | 作用 |
| --- | --- |
| `MainActivity.java` | 主页面，展示活动卡片，处理打卡、取消打卡、记录详细入口。 |
| `CreateHabitActivity.java` | 创建活动页面，输入活动名称和单位。 |
| `RecordDetailActivity.java` | 记录详细页面，输入今天完成的数量。 |

### data.model

`data.model` 包负责保存核心数据结构。

当前包含：

| 文件 | 作用 |
| --- | --- |
| `Habit.java` | 活动数据模型。 |
| `CheckInRecord.java` | 单次打卡记录数据模型。 |

### data.repository

`data.repository` 包负责数据操作和业务逻辑。

当前包含：

| 文件 | 作用 |
| --- | --- |
| `HabitRepository.java` | 管理内存中的活动列表和打卡记录列表，提供创建活动、打卡、取消打卡、更新记录详细等方法。 |

目前 Repository 内部仍然使用：

```java
private final List<Habit> habits = new ArrayList<>();
private final List<CheckInRecord> records = new ArrayList<>();
```

以后如果接入 Room / SQLite，可以优先替换 Repository 内部实现，UI 层可以尽量少改。

### util

`util` 包负责通用工具逻辑。

当前包含：

| 文件 | 作用 |
| --- | --- |
| `DateUtils.java` | 生成本周日期列表、生成今日日期字符串。 |

## 核心数据结构

### Habit

文件：`app/src/main/java/com/example/stellog/data/model/Habit.java`

`Habit` 表示一个习惯活动，也对应主页面中的一张活动卡片。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `id` | `long` | 活动自身 id，用于和打卡记录关联。 |
| `userId` | `long` | 创建者 id。当前默认是 `0`。 |
| `name` | `String` | 活动名称，例如 `run`、`看书`。 |
| `unit` | `String` | 活动计量单位，例如 `km`、`分钟`，允许为空字符串。 |
| `recordNum` | `int` | 已打卡次数。今日打卡加 1，取消今日打卡减 1。 |
| `reminderEnabled` | `boolean` | 是否开启提醒。当前创建时默认为 `false`。 |
| `sortWeight` | `int` | 排序权重。当前创建时默认为 `1`。 |
| `totalValue` | `long` | 累计完成数量，由记录详细中的 `record.value` 汇总得到。 |
| `createdAt` | `long` | 创建时间，格式为 `System.currentTimeMillis()` 的毫秒时间戳。 |
| `updatedAt` | `long` | 更新时间，打卡、取消打卡或修改记录详细时更新。 |

构造函数格式：

```java
new Habit(
        long id,
        long userId,
        String name,
        String unit,
        int recordNum,
        boolean reminderEnabled,
        int sortWeight,
        long totalValue,
        long createdAt,
        long updatedAt
)
```

### CheckInRecord

文件：`app/src/main/java/com/example/stellog/data/model/CheckInRecord.java`

`CheckInRecord` 表示某个活动在某一天的一条打卡记录。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `id` | `long` | 记录自身 id。 |
| `habitId` | `long` | 绑定的活动 id，对应 `Habit.id`。 |
| `userId` | `long` | 创建者 id，来自对应 habit。 |
| `date` | `RecordDate` | 打卡日期，只保存年月日。 |
| `value` | `long` | 用户在“记录详细”中填写的完成数量。普通打卡时默认是 `0`。 |
| `source` | `String` | 打卡来源，例如 `正常打卡`、`补打卡`。 |
| `createdAt` | `long` | 创建时间，毫秒时间戳。 |
| `updatedAt` | `long` | 更新时间，修改记录详细时更新。 |

构造函数格式：

```java
new CheckInRecord(
        long id,
        long habitId,
        long userId,
        CheckInRecord.RecordDate date,
        long value,
        String source,
        long createdAt,
        long updatedAt
)
```

### RecordDate

`RecordDate` 是 `CheckInRecord` 的内部类，只记录年月日，避免直接比较毫秒时间戳时受到时分秒影响。

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `year` | `int` | 年，例如 `2026`。 |
| `month` | `int` | 月，范围是 `1-12`。 |
| `day` | `int` | 日，范围通常是 `1-31`。 |

重要函数：

```java
boolean isSameDay(RecordDate other)
```

输入：另一个 `RecordDate`。  
输出：年月日相同返回 `true`，否则返回 `false`。

```java
static RecordDate today()
```

输入：无。  
输出：本地系统今天对应的 `RecordDate`。

```java
static RecordDate fromCalendar(Calendar calendar)
```

输入：Java 的 `Calendar` 对象。  
输出：转换后的 `RecordDate`，其中月份会从 `Calendar` 的 `0-11` 转为正常的 `1-12`。

## Repository 说明

文件：`app/src/main/java/com/example/stellog/data/repository/HabitRepository.java`

`HabitRepository` 负责管理活动和打卡记录。`MainActivity` 不再直接维护 `records`，而是通过 Repository 查询和更新数据。

### 核心数据

| 变量 | 类型 | 作用 |
| --- | --- | --- |
| `DEFAULT_CHECK_IN_VALUE` | `long` | 普通打卡时 `CheckInRecord.value` 的默认值，目前是 `0L`。 |
| `habits` | `List<Habit>` | 内存中的活动列表。 |
| `records` | `List<CheckInRecord>` | 内存中的打卡记录列表。 |

### 核心函数

```java
public List<Habit> getHabits()
```

输出：当前内存中的活动列表。  
作用：供 UI 层绑定卡片列表。

```java
public Habit addHabit(String name, String unit)
```

输入：活动名称和单位。  
输出：新创建的 `Habit`。  
作用：创建活动并加入 `habits`。

```java
public CheckInRecord getTodayRecord(long habitId)
```

输入：活动 id。  
输出：如果该活动今天已有打卡记录，返回对应 `CheckInRecord`；否则返回 `null`。  
作用：判断今日是否打卡。

```java
public boolean checkInToday(Habit habit)
```

输入：要打卡的活动。  
输出：成功新增今日打卡返回 `true`；如果今天已打卡返回 `false`。  
作用：创建今日 `CheckInRecord`，并更新 `habit.recordNum`、`habit.totalValue`、`habit.updatedAt`。

```java
public boolean cancelTodayCheckIn(Habit habit)
```

输入：要取消今日打卡的活动。  
输出：成功取消返回 `true`；如果今天没有打卡记录返回 `false`。  
作用：删除今日 `CheckInRecord`，并回退 `habit.recordNum` 和 `habit.totalValue`。

```java
public boolean hasRecordOnDate(long habitId, CheckInRecord.RecordDate date)
```

输入：活动 id 和指定日期。  
输出：该活动在指定日期存在打卡记录返回 `true`，否则返回 `false`。  
作用：用于渲染本周 7 个打卡圆点。

```java
public boolean applyRecordDetailValue(long habitId, long newValue)
```

输入：活动 id 和记录详细页面返回的新完成数量。  
输出：成功更新返回 `true`；找不到活动、找不到今日记录或值未变化时返回 `false`。  
作用：更新今日 `record.value`，并按差值更新 `habit.totalValue`。

累计值更新方式：

```java
habit.totalValue = habit.totalValue - oldValue + newValue;
```

这样可以避免用户第二次修改记录详细时重复累计。

```java
public int findHabitPosition(long habitId)
```

输入：活动 id。  
输出：活动在 `habits` 列表中的位置；找不到返回 `-1`。  
作用：供 UI 层刷新指定卡片。

## MainActivity 说明

文件：`app/src/main/java/com/example/stellog/ui/MainActivity.java`

`MainActivity` 现在主要负责 UI 逻辑：

- 初始化 `ViewPager2`；
- 打开创建活动页面；
- 打开记录详细页面；
- 调用 `HabitRepository` 完成数据操作；
- 根据 Repository 返回结果刷新卡片；
- 绑定卡片 UI。

### 核心变量

| 变量 | 类型 | 作用 |
| --- | --- | --- |
| `DEFAULT_RECORD_VALUE` | `long` | 接收记录详细返回值时使用的默认值，目前是 `0L`。 |
| `habitRepository` | `HabitRepository` | 活动数据仓库。 |
| `habits` | `List<Habit>` | 来自 `habitRepository.getHabits()` 的活动列表。 |
| `habitPager` | `ViewPager2` | 主页面卡片滑动组件。 |
| `habitAdapter` | `HabitPagerAdapter` | 将 `habits` 渲染为卡片页面的适配器。 |
| `pageIndicatorText` | `TextView` | 右上角页码文本。 |
| `currentHabitPosition` | `int` | 当前展示的卡片序号。 |

### 核心函数

```java
private void setupHabitPager()
```

输入：无。  
输出：无。  
作用：初始化 `ViewPager2`，绑定 `HabitPagerAdapter`，设置预渲染、左右 padding、卡片缩放/透明度变换，并监听当前页变化。

```java
private void addHabit(String name, String unit)
```

输入：活动名称和单位。  
输出：无。  
作用：调用 `habitRepository.addHabit(name, unit)` 创建活动，刷新卡片列表，并滑动到新活动。

```java
private void updateHeader(int position)
```

输入：当前卡片位置 `position`，从 `0` 开始。  
输出：无。  
作用：更新右上角页码，例如 `1 / 3`。

```java
private CheckInRecord getTodayRecord(long habitId)
```

输入：活动 id。  
输出：今日打卡记录或 `null`。  
作用：转调 `habitRepository.getTodayRecord(habitId)`。

```java
private void checkInToday(Habit habit)
```

输入：当前活动。  
输出：无。  
作用：调用 `habitRepository.checkInToday(habit)`；如果成功打卡，则刷新当前卡片。

```java
private void cancelTodayCheckIn(Habit habit)
```

输入：当前活动。  
输出：无。  
作用：调用 `habitRepository.cancelTodayCheckIn(habit)`；如果成功取消，则刷新当前卡片。

```java
private boolean hasRecordOnDate(long habitId, CheckInRecord.RecordDate date)
```

输入：活动 id 和日期。  
输出：该日期是否打卡。  
作用：转调 `habitRepository.hasRecordOnDate(habitId, date)`。

```java
private List<CheckInRecord.RecordDate> getCurrentWeekDates()
```

输入：无。  
输出：本周周一到周日的 7 个日期。  
作用：转调 `DateUtils.getCurrentWeekDates()`。

```java
private String getTodayDateString()
```

输入：无。  
输出：今日日期字符串，格式为 `yyyy-MM-dd`。  
作用：转调 `DateUtils.getTodayDateString()`。

```java
private void showRecordDetailPage(Habit habit)
```

输入：当前活动。  
输出：无。  
作用：如果今天已经打卡，则打开 `RecordDetailActivity`，并传入活动 id、名称、单位和今日 record 的 value。

```java
private void applyRecordDetailValue(long habitId, long newValue)
```

输入：活动 id 和新完成数量。  
输出：无。  
作用：调用 `habitRepository.applyRecordDetailValue(habitId, newValue)`；如果更新成功，则刷新对应卡片。

## DateUtils 说明

文件：`app/src/main/java/com/example/stellog/util/DateUtils.java`

```java
public static List<CheckInRecord.RecordDate> getCurrentWeekDates()
```

输入：无。  
输出：本周周一到周日的 7 个 `RecordDate`。  
作用：供卡片上的星期圆点绑定打卡状态。

```java
public static String getTodayDateString()
```

输入：无。  
输出：今日日期字符串，格式为 `yyyy-MM-dd`。  
作用：供卡片显示 `今天 | yyyy-MM-dd`。

## 页面间传参格式

### 创建活动页面返回 MainActivity

发送方：`CreateHabitActivity`  
接收方：`MainActivity.createHabitLauncher`

| key | 类型 | 作用 |
| --- | --- | --- |
| `habit_name` | `String` | 新活动名称，不能为空。 |
| `habit_unit` | `String` | 新活动单位，可以为空。 |

### 记录详细页面输入参数

发送方：`MainActivity.showRecordDetailPage()`  
接收方：`RecordDetailActivity`

| key | 类型 | 作用 |
| --- | --- | --- |
| `habit_id` | `long` | 当前活动 id。 |
| `habit_name` | `String` | 当前活动名称，用于页面展示。 |
| `habit_unit` | `String` | 当前活动单位，用于页面展示。 |
| `record_value` | `long` | 今天这条打卡记录已有的完成数量。 |

### 记录详细页面返回 MainActivity

发送方：`RecordDetailActivity.saveRecordValue()`  
接收方：`MainActivity.recordDetailLauncher`

| key | 类型 | 作用 |
| --- | --- | --- |
| `habit_id` | `long` | 要更新的活动 id。 |
| `record_value` | `long` | 用户填写的新完成数量。 |

## 当前数据流

### 创建活动

```text
CreateHabitActivity 输入 name/unit
        ↓
setResult 返回 habit_name/habit_unit
        ↓
MainActivity.createHabitLauncher 接收
        ↓
MainActivity.addHabit(name, unit)
        ↓
HabitRepository.addHabit(name, unit)
        ↓
habits 新增 Habit
        ↓
ViewPager2 刷新并展示新卡片
```

### 今日打卡

```text
点击打卡按钮
        ↓
MainActivity.checkInToday(habit)
        ↓
HabitRepository.checkInToday(habit)
        ↓
records 新增 CheckInRecord(value = 0)
        ↓
habit.recordNum + 1
        ↓
刷新当前卡片
```

### 记录详细

```text
点击记录详细
        ↓
MainActivity.showRecordDetailPage(habit)
        ↓
RecordDetailActivity 输入完成数量
        ↓
setResult 返回 habit_id/record_value
        ↓
MainActivity.recordDetailLauncher 接收
        ↓
HabitRepository.applyRecordDetailValue(habitId, newValue)
        ↓
更新 todayRecord.value 和 habit.totalValue
        ↓
刷新对应卡片
```

### 取消今日打卡

```text
点击取消
        ↓
MainActivity.cancelTodayCheckIn(habit)
        ↓
HabitRepository.cancelTodayCheckIn(habit)
        ↓
删除今天的 CheckInRecord
        ↓
habit.recordNum - 1
habit.totalValue - todayRecord.value
        ↓
刷新当前卡片
```
