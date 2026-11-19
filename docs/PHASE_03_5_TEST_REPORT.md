# PHASE_03_5_TEST_REPORT — Motion Debug HUD

- 阶段：03.5
- 代码提交：`11a8b00`
- 测试日期：2026-11-19
- 状态：**部分 TESTED；未 VERIFIED**
- 关联：
  - [PHASE_03_5_IMPLEMENTATION_REPORT.md](PHASE_03_5_IMPLEMENTATION_REPORT.md)
  - [PHASE_03_5_TEST_CHECKLIST.md](PHASE_03_5_TEST_CHECKLIST.md)

## 1. 结果摘要

| 项 | 结果 |
|---|---|
| 代码实现 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（67/67 PASS）** |
| F10 开启/关闭 | **CLIENT-TESTED** |
| 双栏 HUD / 来源隔离 | **CLIENT-TESTED** |
| 集成服务端真实快照包 | **INTEGRATED-SERVER-TESTED** |
| 专用服务器启动 | **DEDICATED-SERVER-TESTED（启动级）** |
| HUD 动态运动 | **NOT TESTED** |
| NO SNAPSHOT / STALE 实机 | **NOT TESTED（仅单元）** |
| 专用服务器真实玩家 | **NOT TESTED** |
| 多人 | **NOT TESTED** |
| 整体 VERIFIED | **NO** |

## 2. 单元测试

`gradlew test`：**67/67 PASS**

| 测试类 | 数量 | 失败 |
|---|---:|---:|
| `DebugNetworkCodecTest` | 8 | 0 |
| `DebugHudStateTest` | 5 | 0 |
| `DebugHudSubscriptionsTest` | 2 | 0 |
| `DebugHudBroadcasterTest` | 1 | 0 |
| `MotionHistoryTest`（新增 capacity） | 4 | 0 |
| 阶段 02/03 既有测试 | 47 | 0 |
| **合计** | **67** | **0** |

覆盖：

- C2S 订阅 roundtrip。
- S2C 有/无快照 roundtrip。
- 有动作/无动作、有 FALL/无 FALL。
- 非法枚举、NaN、Infinity。
- 负数/越界历史数量。
- 客户端/服务端 view 隔离。
- 服务端无快照不补客户端数据。
- STALE 与清理。
- 订阅/取消/退出清理逻辑。
- 4 tick 限流。
- `MotionHistory.capacity()` 实际实例值。

## 3. 编译与构建

- `compileJava`：PASS
- `build`：PASS（12s）
- 产物：`build/libs/parkourcombat-2.0.0-alpha.1.jar`（80,867 B）

日志：[compileJava-phase03_5.log](logs/compileJava-phase03_5.log)、[build-phase03_5.log](logs/build-phase03_5.log)

## 4. 客户端 HUD 实测

### 4.1 F10 开启/关闭

日志：

```text
[DebugHUD] toggled enabled=true
[DebugHUD] subscription player=Dev enabled=true

[DebugHUD] toggled enabled=false
[DebugHUD] subscription player=Dev enabled=false
```

证据：[runClient-phase03_5-dynamic.debug.log](logs/runClient-phase03_5-dynamic.debug.log)

### 4.2 HUD 双栏

开启截图：[phase03_5-hud-dynamic.png](logs/phase03_5-hud-dynamic.png)

观察到：

- 左栏 `CLIENT LOCAL`：
  - `motionSource=CLIENT_LOCAL`
  - `motionTrust=MEDIUM`
  - `serverAuthoritative=false`
  - `history=20 / 20`
  - `action=NONE`
  - `fallPhase=FALL_END`
  - `fallSource=VANILLA_DERIVED`
  - `fallTrust=MEDIUM`
- 右栏 `SERVER (DEBUG PACKET)`：
  - `motionSource=SERVER_NATIVE`
  - `motionTrust=HIGH`
  - `serverAuthoritative=true`
  - `history=20 / 20`
  - `ageTicks=1`
  - `fallSource=VANILLA_DERIVED`
  - `fallTrust=HIGH`

关闭截图：[phase03_5-hud-off.png](logs/phase03_5-hud-off.png)  
关闭后 HUD 不再绘制；服务端订阅日志为 `enabled=false`。

### 4.3 动态运动 HUD

尝试按 W/跳跃注入输入，HUD 显示 `hSpeed=0`，未获得有效位移。  
因此 **HUD 动态运动显示 NOT TESTED**；阶段 03 的运动数据已通过此前的动态日志验证，但本次 HUD 未观察到动态值。

### 4.4 NO SNAPSHOT / STALE

未在实机中构造无快照/停发场景；仅由 `DebugHudStateTest` 覆盖：

- 无服务端快照 → `NO SNAPSHOT`，不显示客户端替代数据。
- age >10 → `STALE`。
- age >20 → 清空服务端 view。

## 5. 专用服务器

- `Done (7.276s)!`
- `[ParkourAdapter] initialized side=SERVER parCoolVersion=3.4.3.3`
- 无 FATAL / ClassNotFound / NoSuchMethod。
- 无玩家连接，因此 **没有专用服务器真实玩家动态测试**。
- 日志：[runServer-phase03_5.latest.log](logs/runServer-phase03_5.latest.log)

## 6. 多人

**NOT TESTED。**

未完成：

- 客户端 A 开启、B 关闭。
- 仅 A 收到服务端包。
- A 关闭/退出后停止发送。
- 数据不串线。

代码按订阅 UUID + `PacketDistributor.PLAYER` 实现，但没有实际双客户端证据。

## 7. 未验证项

- HUD 动态运动值（移动/疾跑/跳跃/下落/落地/转向）。
- NO SNAPSHOT / STALE 实机。
- 死亡/维度切换后的 HUD 清理实机。
- 专用服务器真实玩家。
- 多人隔离/丢包。
- 第一阶段遗留的 FALL 环境/垂直墙跑/墙跳/CANCELLED 仍不因 HUD 而变成 PASS。

## 8. 结论

- F10、双栏布局、来源/可信度隔离、历史容量 20/20、服务端真实快照包均有实际证据。
- HUD 动态运动、专用服务器真实玩家和多人尚未验证。
- 整体不能标记 VERIFIED；未进入 Phase 04。
