# PHASE_03_FILE_CHANGE_PLAN — 文件变更清单

- 阶段：03
- 文档状态：**IMPLEMENTED（见 PHASE_03_IMPLEMENTATION_REPORT；实际文件与清单一致）**
- 前置 HEAD：`73ed388`（阶段 02 验收记录提交）
- 原则：最小改动、复用阶段 02、不改变动作/FALL 语义。

## 1. 新增源码

`src/main/java/com/MrGhost/parkourCombat/adapter/`

| 文件 | 职责 | 预计 |
|---|---|---|
| `MotionFrame.java` | 纯运动输入 record（gameTime/position/velocity/flags） | ~35 行 |
| `MotionSnapshot.java` | 不可变运动快照 record | ~55 行 |
| `MotionHistory.java` | 固定 20 的环形历史缓存 | ~70 行 |
| `MotionAccumulator.java` | 纯计算：速度/方向/加速度/连续运动/重置 | ~180 行 |
| `ParkourActionSelection.java` | 从活动动作中选择唯一主状态 | ~60 行 |

## 2. 新增测试

`src/test/java/com/MrGhost/parkourCombat/adapter/`

| 文件 | 覆盖 |
|---|---|
| `MotionAccumulatorTest.java` | U-01～U-14、U-19 |
| `MotionHistoryTest.java` | U-15～U-16、U-20 |
| `ParkourActionSelectionTest.java` | U-17～U-18 |

> JUnit 5 已在阶段 02 加入，本阶段不修改 `build.gradle`。

## 3. 修改源码

| 文件 | 修改内容 |
|---|---|
| `adapter/MotionTracker.java` | 保留 FALL；增加一次 `MotionFrame` 构建、`MotionAccumulator`、`MotionHistory`；`tick` 增加主状态/source/trust 参数；增加 `latestSnapshot()`/`history()`/`clear()`；维度/死亡/重置 |
| `adapter/ParkourActionAdapter.java` | 增加 `getMotionSnapshot(Player)` 与 `getMotionHistory(Player, int)` |
| `adapter/ParCoolActionAdapter.java` | `onPlayerTick` 中 `actions.onTick` 后选择主状态并调用扩展后的 `motion.tick`；查询方法转发；生命周期移除时清空历史；DEBUG 采样日志（可选） |

**不修改**：

- `ParkourActionTracker.java`（动作识别/生命周期/trust 不变）
- `ParkourActionState.java` / `ImmutableParkourActionState.java`
- `FallStateMachine.java` / `FallSnapshot.java` / `FallEndReason.java`
- `ParCoolActionMapping.java`
- `ParkourAdapterEvents.java`（仍只在 PlayerTick.END 分发一次）
- `ParkourCombat.java`（除非批准调试命令）
- `build.gradle`（JUnit 已存在）
- `libs/*.jar`、`mods.toml`、`gradle.properties`、锁定版本

## 4. 可选（待确认，不计入默认范围）

| 文件 | 条件 |
|---|---|
| `command/MotionDebugCommand.java` | 仅在你批准只读 `/parkourcombat motion` 时添加 |
| `ParkourCombat.java` | 注册上述命令；否则不改 |
| `resources/...` | 不需要；无新资源 |

默认**不添加命令**。

## 5. 文档

新增：

- `docs/PHASE_03_IMPLEMENTATION_PLAN.md`
- `docs/PHASE_03_API_EVIDENCE.md`
- `docs/PHASE_03_TEST_PLAN.md`
- `docs/PHASE_03_FILE_CHANGE_PLAN.md`（本文件）

实现后新增：

- `docs/PHASE_03_TEST_REPORT.md`
- `docs/PHASE_03_CLIENT_CHECKLIST.md`
- `docs/logs/` 对应日志

## 6. 预计总变更

- 新增主源码：5 个文件，约 400 行。
- 新增测试：3 个文件，约 350～450 行。
- 修改源码：3 个文件，约 100～160 行。
- 文档：4 个规划文件 + 实现后报告/清单。
- 无新依赖、无新网络包、无 Mixin、无资源文件。

## 7. 禁止变更

- 不实现 Better Combat 攻击桥接、特殊攻击、伤害/霸体/词条。
- 不修改阶段 02 动作分类/生命周期/trust。
- 不把客户端运动数据作为服务端权威。
- 不新增每 tick 世界扫描/无限历史/每 tick INFO。
- 不修改第三方 JAR/锁定版本。

## 8. 实施前检查

- [ ] 用户确认本文与 `PHASE_03_IMPLEMENTATION_PLAN.md`
- [ ] 确认历史窗口 20 tick
- [ ] 确认方向保持 5 tick
- [ ] 确认传送阈值 8 格
- [ ] 确认主活动动作优先级
- [ ] 确认是否加只读调试命令（默认不加）
- [ ] 确认“当前位移”= `position` + 每 tick `displacement`
