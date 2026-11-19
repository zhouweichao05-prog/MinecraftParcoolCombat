# PHASE_03_TEST_REPORT — 运动数据系统测试记录

- 阶段：03
- 代码提交：`f3dc5f1`（初始 `156b114` + 修复）
- 测试日期：2026-11-19
- 状态：**部分 TESTED；未 VERIFIED**
- 关联文档：
  - [PHASE_03_IMPLEMENTATION_REPORT.md](PHASE_03_IMPLEMENTATION_REPORT.md)
  - [PHASE_03_TEST_PLAN.md](PHASE_03_TEST_PLAN.md)
  - [PHASE_03_TEST_CHECKLIST.md](PHASE_03_TEST_CHECKLIST.md)

## 1. 结果摘要

| 项 | 结果 |
|---|---|
| 代码实现 | **IMPLEMENTED** |
| 编译 | **COMPILED** |
| 单元测试 | **TESTED（50/50 PASS）** |
| 客户端实际动态采样 | **TESTED（复测）** |
| 集成服务端实际动态采样 | **TESTED（复测）** |
| 专用服务器启动 | **SERVER-TESTED（启动级）** |
| 真实移动/转向 | **TESTED（基础观察）** |
| 真实跳跃/下落/落地 | **TESTED（基础观察）** |
| 真实死亡清理 | **TESTED（复测）** |
| 真实传送/维度清理 | **NOT TESTED** |
| 历史运行时覆盖 | **NOT TESTED（仅单元）** |
| 多人 | **NOT TESTED** |
| 整体 VERIFIED | **NO** |

## 2. 单元测试

命令：`gradlew test`

| 测试类 | 数量 | 失败 |
|---|---:|---:|
| `MotionAccumulatorTest` | 20 | 0 |
| `MotionHistoryTest` | 3 | 0 |
| `ParkourActionSelectionTest` | 6 | 0 |
| 阶段 02 既有测试 | 21 | 0 |
| **合计** | **50** | **0** |

覆盖：

- 静止、水平移动、方向改变。
- 跳跃上升、自由下落、落地。
- 短暂静止方向保持 5 tick、之后归零。
- 连续运动累计/归零。
- 历史 20 条覆盖和 newest-first。
- 传送 >8 格重置、不生成虚假加速度。
- 短采样间隔按 deltaTicks 平均；>5 tick 重置。
- 同 gameTick 重复帧忽略且不清历史。
- 死亡/维度/非有限数据重置。
- 动作优先级（WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN）。
- 空动作、终态不选择。
- 运动/ FALL / ParCool 动作的 source/trust 隔离。
- 服务端位置差推导速度（`ServerPlayer#getDeltaMovement` 为 0 的场景）。
- 服务端空闲不把原始重力误当运动。
- 阶段 02 的 21 个测试仍通过。

日志：[test-phase03.log](logs/test-phase03.log)

## 3. 编译与构建

- `compileJava`：PASS
- `build`：PASS（13s）
- 产物：`build/libs/parkourcombat-2.0.0-alpha.1.jar`（52,070 B）
- 未修改 `build.gradle` 依赖；JUnit 仍是测试专用。

日志：[compileJava-phase03.log](logs/compileJava-phase03.log)、[build-phase03.log](logs/build-phase03.log)

## 4. 客户端实际采样

使用开发客户端 `runClient` + 测试世界 `Phase03Test`（复制自现有测试世界，避免修改原世界），进入世界后空闲采样。

- `[MotionTracker]` 共 18 条：客户端 9、集成服务端 9。
- 客户端：
  ```text
  side=CLIENT motionSource=CLIENT_LOCAL motionTrust=MEDIUM
  fallSource=VANILLA_DERIVED fallTrust=MEDIUM
  ```
- 集成服务端：
  ```text
  side=SERVER motionSource=SERVER_NATIVE motionTrust=HIGH
  fallSource=VANILLA_DERIVED fallTrust=HIGH
  ```
- 空闲值：`hSpeed=0.0`、`dir=(0,0)`、`hAccel=0.0`、`vAccel=0.0`、`movingTicks=0`、`onGround=true`、`actionType=NONE`。
- **无 reset**；无 FATAL；仅有 Patchouli 指南书缺失 ERROR。
- 日志：[runClient-phase03-final.debug.log](logs/runClient-phase03-final.debug.log)

> 该证据证明修复后空闲采样管线、双端 source/trust 和“无 reset”。

### 4.2 修复后动态复测（2026-11-19 17:16–17:18）

- `[MotionTracker]` 共 142 条：客户端 71、集成服务端 71。
- **0 次 reset**（无 TICK_INVALID/TICK_GAP/DEAD）。
- 客户端 38 条、服务端 38 条 `hSpeed>0`；服务端 36 条 `movingTicks>0`。
- 服务端示例：
  ```text
  side=SERVER ... hSpeed=0.3163 ... movingTicks=10
  motionSource=SERVER_NATIVE motionTrust=HIGH
  actionType=FAST_RUN actionSource=PARCOOL_SYNCED actionTrust=LOW
  ```
- 实际动作：FAST_RUN、VAULT、WALL_RUN、SLIDE；FALLING/FALL_END 均出现。
- 死亡清理：`cleanup side=SERVER reason=DEATH`、`cleanup side=CLIENT reason=DEATH_TICK` 各一次，无 DEAD reset。
- 退出清理双端成功。
- 无 FATAL；仅 Patchouli/认证等既有错误。
- 证据：[runClient-phase03-retest-motion.log](logs/runClient-phase03-retest-motion.log)

> 该证据覆盖基础移动/动作/死亡清理；传送、维度、历史容量、多人仍未验证。

### 4.1 修复前动态日志

用户提供的 16:02 日志（[runClient-phase03-prefix-movement.log](logs/runClient-phase03-prefix-movement.log)）：

- `[MotionTracker]` 98 条客户端 + 98 条服务端。
- 客户端 `hSpeed` 非零、方向变化、`movingTicks` 增长；说明客户端动态采样有效。
- **服务端位置在变化，但 `vel=(0,0,0)`、`hSpeed=0`**；`ServerPlayer#getDeltaMovement` 不可作为唯一速度来源。
- 死亡后 `DEAD` reset 在服务端 39 次、客户端 20 次，每 tick 重复。
- 这些是真实缺陷，已在 `f3dc5f1` 修复；修复后动态复测尚未完成。

## 5. 专用服务器

命令：`gradlew runServer`

- `Done (7.812s)!`
- `[ParkourAdapter] initialized side=SERVER parCoolVersion=3.4.3.3`
- 无 ClassNotFound/NoSuchMethod/FATAL。
- 无玩家连接，因此没有专用服务器玩家采样。
- 日志：[runServer-phase03-final.latest.log](logs/runServer-phase03-final.latest.log)

## 6. 阶段 03 要求的 15 项覆盖

| 要求 | 覆盖 |
|---|---|
| 1 静止 | `stationaryFirstSampleHasZeroDerivedValues`；实际 M-01 |
| 2 水平移动 | `horizontalMovementComputesSpeedDirectionAccelerationAndDisplacement` |
| 3 方向改变 | `directionChangeUsesCurrentVelocityAndVectorDelta` |
| 4 跳跃上升 | `jumpHasPositiveVerticalVelocityAndCountsAsMotion` |
| 5 自由下落 | `fallingHasNegativeVerticalVelocity` |
| 6 着地 | `landingUpdatesOnGroundAndVerticalAcceleration` |
| 7 短暂静止方向保持 | `briefStopKeepsDirectionForFiveTicksThenClears` |
| 8 连续运动计时 | `continuousMotionCountsAndResets` |
| 9 历史 20 覆盖 | `historyIsBoundedAndNewestFirst` + `MotionHistoryTest` |
| 10 传送异常位移 | `teleportResetsHistoryAndDoesNotEmitSpike` |
| 11 采样间隔异常 | `shortSampleGapUsesDeltaTicksAndResetsContinuity`、`largeSampleGapResetsBaseline` |
| 12 玩家数据重置 | `deadFrameResetsPlayerData`、`duplicateTickIsIgnoredWithoutClearingHistory` |
| 13 主动作优先级 | `ParkourActionSelectionTest` 6 项 |
| 14 空动作状态 | `nullActionIsAllowed` |
| 15 可信度隔离 | `trustIsolationKeepsFallAndActionSources` |

> 以上为单元/建模覆盖；“实际游戏内”仅 M-01 空闲场景已观察，其余仍待人工验证。

## 7. 未验证项

- 真实短暂停步方向保持。
- 真实传送、维度切换清理。
- 真实历史 20 条覆盖行为。
- 专用服务器真实玩家移动。
- 多人隔离/丢包。
- FALL 水/岩浆/鞘翅/攀爬/载具等阶段 02 遗留项仍 NOT TESTED。
- 服务端 FALL 仍使用阶段 02 raw `getDeltaMovement.y`，未改其语义。

## 8. 已知实现注记

1. 同一 gameTick 的重复 `PlayerTickEvent` 会被忽略，避免清空历史；不同 tick 的异常间隔按规则处理。
2. `ParkourActionSelection` 每 tick 从阶段 02 `getState` 取一次状态，产生一个 `Optional`，这是禁止修改 `ParkourActionTracker` 条件下的 API 边界；运动快照/历史/累加器本身不使用 Optional/List/Stream。
3. 客户端世界加载阶段曾观察到 `TICK_INVALID`/`TICK_GAP`；改用 `Player#tickCount` 后复测为 0 reset。

## 9. 结论

- 单元测试和编译通过；修复后复测确认双端动态速度非零、0 reset、死亡清理不刷屏。
- 传送/维度、历史运行时覆盖、专用服务器真实玩家和多人仍未验证。
- 整体不能标记 VERIFIED；未进入阶段 04。
