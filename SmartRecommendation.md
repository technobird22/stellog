# 智能推荐说明

## 1. 当前展示顺序

当前首页活动的原始顺序是按数据库读取顺序展示，`HabitDao` 里使用的是 `ORDER BY id ASC`，因此等价于按创建时间先后展示。

当“智能推荐排序”开启时，首页会基于习惯数据重新排序；当开关关闭时，会回到原始顺序。

## 2. 当前智能推荐公式

当前版本暂不纳入“提醒时间”因素，只根据近期行为数据计算优先级。

### 2.1 评分公式

$$
score = 0.45 \cdot riskAdjustedByActivity + 0.35 \cdot completionRate + 0.20 \cdot streakScore
$$

其中：

$$
completionRate = \frac{recentCheckInDays}{7}
$$

$$
interruptionRisk = \frac{recentGapDays}{7}
$$

$$
streakScore = \frac{\min(streakDays, 14)}{14}
$$

$$
riskAdjustedByActivity = interruptionRisk \cdot (0.4 + 0.6 \cdot completionRate)
$$

### 2.2 各项含义

- `recentCheckInDays`：最近 7 天内有记录的天数。
- `recentGapDays`：最近 7 天内缺失打卡的天数。
- `streakDays`：从今天往前连续打卡的天数。
- `completionRate`：近期完成频率，反映活跃程度。
- `interruptionRisk`：近期中断风险，漏打越多风险越高。
- `streakScore`：连续坚持表现，连续越久分数越高，但做了上限截断，避免长期连续把其他因素完全压住。
- `riskAdjustedByActivity`：把“风险”和“活跃度”合并，优先把更需要关注且仍有行动基础的习惯排在前面。

### 2.3 公式理由

- `riskAdjustedByActivity` 权重最高，因为它最能体现“需要优先关注”的习惯。
- `completionRate` 次高，因为近期活跃的习惯更可能被顺手完成。
- `streakScore` 保留激励作用，但不让连续天数单独主导排序。
- 整体目标是：优先展示更需要关注、也更有可能完成的活动。

## 3. 加入提醒时间后的扩展公式

提醒时间逻辑由队友后续实现，因此这里先给出可扩展版本。

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
