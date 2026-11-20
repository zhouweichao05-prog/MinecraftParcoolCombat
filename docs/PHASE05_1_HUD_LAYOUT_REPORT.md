# PHASE05_1_HUD_LAYOUT_REPORT.md

> Phase 05.1 — F10 HUD 紧凑化与自适应布局  
> 分支：gate-2.14-test-fixes  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 06。

---

## 1. 修改文件

```text
src/main/java/com/MrGhost/parkourCombat/client/debug/DebugHudState.java
src/main/java/com/MrGhost/parkourCombat/client/debug/DebugHudOverlay.java
src/main/java/com/MrGhost/parkourCombat/client/debug/ClientDebugTick.java
src/main/java/com/MrGhost/parkourCombat/client/debug/ClientDebugHud.java
src/main/resources/assets/parkourcombat/lang/en_us.json
src/main/resources/assets/parkourcombat/lang/zh_cn.json
src/test/java/com/MrGhost/parkourCombat/client/debug/DebugHudStateTest.java
```

未修改网络协议、数据包 ID、服务端广播频率、CombatAttackTracker 观察行为或 ParCool 适配器。

---

## 2. 三种显示状态

### 关闭

F10 保持原有开关。

### 紧凑模式（默认）

F10 开启后默认紧凑：

客户端：

- 当前动作；
- 动作阶段；
- 水平速度；
- 垂直速度；
- 数据来源。

服务端：

- 当前动作/阶段；
- 水平/垂直速度；
- 攻击编号；
- 攻击阶段/结果；
- 连击；
- 武器（长名称截断）；
- 请求/观察目标；
- 攻击归属：无法确认；
- 处理器状态。

### 详细模式

F9 在紧凑/详细间切换；详细模式保留原所有字段：

- 攻击/伤害/取消/迟到计数；
- 目标覆盖；
- 处理顺序；
- `healthReductionVerified`；
- 运动采样时刻与请求阶段快照说明；
- 其他原 HUD 字段。

F7/F8 为详细模式翻页。

按键中文名称已加入 `zh_cn.json`。

---

## 3. 布局算法

`DebugHudOverlay`：

- 使用当前缩放后的 `width/height` 计算；
- 宽度允许时左右双面板；
- 宽度不足时上下堆叠；
- 面板宽 `max(150, min(420, 可用宽度/2))`，窄屏使用整行；
- 行高 9；
- 面板最大行数由屏幕高度计算；
- 文本按字体实际宽度截断并加 `...`；
- 详细模式分页，F7/F8 翻页；
- 不再使用固定 190/360 像素假设；
- 不在屏幕边界外绘制。

---

## 4. 测试

```text
compileJava：BUILD SUCCESSFUL
test：146/146 passed
build：BUILD SUCCESSFUL
jar：170,101 bytes
```

新增测试：

```text
compactModeShowsOnlyCoreFieldsAndDetailedRestoresThem
```

原有 HUD 测试改为显式选择详细模式；紧凑模式默认行为有独立断言。

---

## 5. 客户端启动

本轮执行 `runClient` 启动检查。

结果：

```text
CLIENT-STARTED：见运行日志
CLIENT-TESTED：NO
```

未在真实图形界面手动切换 F10/F9/F7/F8，未测试不同 GUI 缩放/分辨率，未截图。

严格标记：

```text
实际视觉布局测试：NOT_TESTED
修改前后截图：未提供（不能伪造）
```

---

## 6. 数据语义保护

保留：

- 请求阶段运动快照不是命中瞬间；
- `TARGET_OBSERVED != HIT`；
- `DAMAGE_EVENT != HEALTH_REDUCTION`；
- `healthReductionVerified=false` 显示“未验证”；
- `orderingKnown=false` 显示“未确认”；
- `UNKNOWN_PREEXISTING` 保留未知；
- 客户端数据不显示为服务端权威。

---

## 7. Git

```text
分支：gate-2.14-test-fixes
```

Phase05 功能提交与 Phase05.1 提交需在完成后统一报告实际 SHA；未合并 main，未 force push。

---

## 8. 最终状态

```text
IMPLEMENTED
COMPILED
UNIT-TESTED（146/146）
CLIENT-STARTED
CLIENT-TESTED：NO
VERIFIED：NO
CONDITIONALLY_IMPLEMENTED
```

仍需真实客户端人工检查：
- F10/F9/F7/F8；
- 低/高分辨率；
- GUI 缩放 1x/2x/3x/4x；
- 长武器名称；
- 中文可读性；
- 详细模式翻页到最后一页；
- 关闭 HUD 后重新打开无残留。
