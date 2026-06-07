# 智能推荐说明

## 1. 当前展示顺序

当前首页活动的原始顺序是按数据库读取顺序展示，`HabitDao` 里使用的是 `ORDER BY id ASC`，因此等价于按创建时间先后展示。

当“智能推荐排序”开启时，首页会基于习惯数据重新排序；当开关关闭时，会回到原始顺序。

## 2. 当前智能推荐公式

当前版本暂不纳入“提醒时间”因素，只根据近期行为数据计算优先级。

### 2.1 评分公式

$$
score = 0.5 \cdot notDoneToday + 0.3 \cdot interruptionRisk + 0.2 \cdot riskAdjustedByActivity
$$

其中：

$$
notDoneToday = \begin{cases} 1 & \text{今日尚未打卡} \\ 0 & \text{今日已打卡} \end{cases}
$$

$$
completionRate = \frac{recentCheckInDays}{7}
$$

$$
interruptionRisk = \frac{recentGapDays}{7}
$$

$$
riskAdjustedByActivity = interruptionRisk \cdot (0.4 + 0.6 \cdot completionRate)
$$

### 2.2 各项含义

- `notDoneToday`：今日是否还没打卡，是排序的主因素。
- `recentCheckInDays` / `recentGapDays`：最近 7 天内有记录 / 缺失打卡的天数。
- `completionRate`：近期完成频率（等于 `1 − interruptionRisk`）。
- `interruptionRisk`：近期中断风险，漏打越多越高。
- `riskAdjustedByActivity`：把“风险”和“活跃度”合并，对“仍在坚持但开始漏打”的习惯略作加权。

### 2.3 公式理由

- 目标是“需要行动的排在前面”：今日未打卡 > 中断风险高 > 仍有行动基础的高风险习惯。
- `notDoneToday` 权重最高（0.5）：由于该项为 0/1，且其余两项权重之和也是 0.5，所有今日未打卡的活动整体排在已打卡之前；打卡之后该活动会自然下沉到“已完成”一组。
- 组内顺序由其余两项决定：`interruptionRisk` 为主（漏打越多越靠前），`riskAdjustedByActivity` 为辅（更偏向“还在坚持却开始漏打”的习惯）。
- 不再把 `completionRate`、`streakScore` 作为正向加分项，避免出现“完成得越好反而排得越靠前”的反直觉现象。

## 3. 加入提醒时间后的扩展公式

提醒时间逻辑由队友后续实现，因此这里先给出可扩展版本。

> 注：下面这版扩展公式写于第 2 节改版之前，仅作思路参考；真正接入提醒时间时，应在第 2.1 节的新公式基础上再加入 `dueSoonScore` 因子。

### 3.1 扩展评分公式

$$
score' = 0.38 \cdot riskAdjustedByActivity + 0.30 \cdot completionRate + 0.17 \cdot streakScore + 0.15 \cdot dueSoonScore
$$

其中：

$$
dueSoonScore = \max\left(0, 1 - \frac{|\Delta t|}{W}\right)
$$

- `\Delta t`：当前时间与提醒时间的差值，按分钟或小时计算均可。
- `W`：提醒窗口宽度，例如 180 分钟。

### 3.2 加入提醒时间后的说明

- `dueSoonScore` 越高，说明当前越接近提醒时间，活动越适合被提前展示。
- 给它较低但稳定的权重，避免提醒时间压过“风险”和“活跃度”这两个核心行为信号。
- 这样可以兼顾“该提醒了”和“更值得关注”两种目标。

## 4. 当前提示文案

智能推荐开启后，卡片和列表会保留轻量提示，但不再在前面加“建议：”前缀。

当前提示语示例：

- 中断风险较高，建议优先关注
- 连续坚持表现较好
- 近期活跃度较高
- 建议从今天的小目标开始
- 保持当前节奏

## 5. 开关行为

- 开启：按智能评分排序。
- 关闭：恢复原始顺序。
- 开关位置：主页“我的”页。

## 6. 给队友的对接备注

如果后续要接入提醒时间，只需要再提供每个习惯的提醒时刻字段即可，然后把 `dueSoonScore` 纳入评分。

当前版本已经预留了扩展思路，后续可以只改评分函数，不影响首页结构和展示逻辑。
