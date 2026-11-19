# PHASE_03_TEST_PLAN — 运动数据系统验证计划

- 阶段：03
- 文档状态：**DESIGNED（仅规划）**
- 目标：验证 `MotionSnapshot` 的数值正确性、采样边界、生命周期、性能和双端可信度。
- 前置：阶段 02 `CONDITIONALLY_ACCEPTED`；不重新测试/修改阶段 02 已通过语义。

## 1. 测试分层

| 层 | 内容 | 运行环境 |
|---|---|---|
| 单元 | `MotionAccumulator`、`MotionHistory`、`ParkourActionSelection`、`MotionSnapshot` | JUnit 5，无 Minecraft |
| 集成/启动 | `MotionTracker` 接入 adapter、客户端/服务端启动 | 开发客户端 + 专用服务器 |
| 人工 | 真实移动、跳跃、下落、落地、传送、维度、死亡、退出 | 客户端 + 日志 |
| 多人（可选） | 双客户端同步/隔离 | 专用服务器 |

## 2. 单元测试

| ID | 用例 | 预期 |
|---|---|---|
| U-01 | 静止：velocity=(0,0,0) | hSpeed=0，方向=0,0，加速度=0，movingTicks=0 |
| U-02 | 水平移动：vx=0.3，vz=0 | hSpeed=0.3，方向=(1,0) |
| U-03 | 斜向移动：vx=0.3，vz=0.4 | hSpeed=0.5，方向=(0.6,0.8) |
| U-04 | 跳跃：vy=+0.42 | verticalVelocity>0，垂直加速度正确 |
| U-05 | 下落：vy=-0.5 | verticalVelocity<0 |
| U-06 | 落地：vy 负→0，onGround=true | onGround 更新，hSpeed/方向保持规则正确 |
| U-07 | 首帧 | 加速度=0，位移=0，movingTicks=0 |
| U-08 | 正常加速 | 水平加速度等于速度差模长，不出现异常尖峰 |
| U-09 | 短暂静止 | 方向保持最多 5 tick，不立即跳变 |
| U-10 | 长时间静止 | 5 tick 后方向归零，movingTicks=0 |
| U-11 | 连续运动 | 水平移动/跳跃/下落按规则累加，静止归零 |
| U-12 | 单 tick 传送（>8 格） | 返回 null/不写历史，清空 previous；下一帧零加速度 |
| U-13 | 维度切换/死亡 | 清空历史，不产生跨维度快照 |
| U-14 | gameTime 跳变（非 +1） | 重置，不计算跨间隔加速度 |
| U-15 | 历史容量 20 | 插入 25 个后 size=20，age 顺序正确，最旧被淘汰 |
| U-16 | 历史清空 | clear 后 size=0、latest=null |
| U-17 | 主动作优先级 | WALL_RUN > WALL_JUMP > VAULT > SLIDE > FAST_RUN；无活动为 null |
| U-18 | 终态不进入快照 | 阶段 02 的 FINISHED/CANCELLED 终态不当作当前动作 |
| U-19 | 不修改阶段 02 状态 | 采样前后 `ParkourActionState` 字段不变 |
| U-20 | 有界内存 | 历史最多 20 个引用；重置后不保留旧快照 |

## 3. 集成与启动测试

| ID | 项目 | 预期 |
|---|---|---|
| I-01 | `compileJava` | PASS |
| I-02 | `build`（含全部单元测试） | PASS |
| I-03 | 客户端启动 | 双端 adapter 初始化；无 FATAL/Mixin 失败 |
| I-04 | 专用服务器启动 | `Done`；`side=SERVER`；无 ClassNotFound/NoSuchMethod |
| I-05 | 每 tick 单次采样 | 代码审查确认 `motion.tick` 只在 `onPlayerTick` 调用一次 |
| I-06 | 无世界扫描 | 代码审查/静态检查：无 `level.getEntities`/玩家列表循环 |
| I-07 | 无新网络包 | 代码审查：无 `SimpleChannel`/`NetworkRegistry` 变更 |
| I-08 | 既有 21 个阶段 02 测试 | 全部仍 PASS |

## 4. 人工验证（客户端/服务端）

通过 `run/logs/debug.log` 过滤 `[MotionTracker]`（若采用 DEBUG 采样日志）或只读命令输出。

| ID | 场景 | 检查 |
|---|---|---|
| M-01 | 静止 2 秒 | hSpeed≈0，movingTicks=0，onGround=true |
| M-02 | 步行/冲刺 | hSpeed 随速度变化，方向与移动方向一致 |
| M-03 | 跳跃上升 | verticalVelocity>0，不误报 FALL |
| M-04 | 下落 | verticalVelocity<0，falling=true |
| M-05 | 落地 | onGround=true，垂直速度/加速度合理更新 |
| M-06 | 短暂停步再走 | 方向不瞬间跳到 0 或相反方向 |
| M-07 | 传送（同维度） | 无巨大速度/加速度尖峰；历史清空 |
| M-08 | 下界/末地切换 | 新维度无旧速度/旧历史 |
| M-09 | 死亡 | 状态清理，无残留采样 |
| M-10 | 退出重进 | 新基线，无旧历史 |
| M-11 | 历史窗口 | 最多 20 条，时间顺序正确 |
| M-12 | 集成服务端 | 服务端快照 `source=SERVER_NATIVE`、trust=HIGH；客户端 `CLIENT_LOCAL`/MEDIUM |
| M-13 | 专用服务器 | 服务端采样正常，客户端数据不参与服务端 |
| M-14 | 多人（可选） | 每个玩家独立历史，无串线 |

## 5. 数据正确性判定

| 检查 | 判定 |
|---|---|
| 静止速度 | `horizontalSpeed < 0.01`，`abs(vy) < 0.01`（除非重力/站地微调） |
| 水平速度 | 与位移/已知速度方向一致 |
| 跳跃 | vy>0；FALL 不应在上升阶段进入 |
| 下落 | vy<0；FALL 进入/结束符合阶段 02 |
| 着地 | onGround=true 后不保留旧 falling |
| 方向 | 短暂静止不突变；长时间静止归零 |
| 加速度 | 传送/维度/重建后无巨大尖峰 |
| 历史 | 不跨维度、不跨死亡、不无限增长 |

## 6. 性能验证

- 每个玩家每个逻辑端每 tick 最多一次 `MotionTracker.tick`。
- 不遍历世界实体；只按 UUID 查 map。
- `MotionHistory` 固定 20；无无限集合。
- 采样路径不创建 `List`/`Stream`/`Optional`（actionState 使用 nullable）。
- 日志：普通采样 DEBUG，每 20 tick 最多一次；重置 INFO/WARN；禁止每 tick INFO。
- 不新增网络包。

## 7. 退出标准

必须全部满足才能标记阶段 03 通过：

1. U-01～U-20 全部 PASS。
2. 阶段 02 的 21 个测试仍 PASS。
3. `compileJava`、`build` PASS。
4. 客户端/专用服务器启动 PASS。
5. 人工 M-01～M-12 至少完成，M-13/M-14 视范围标记 NOT TESTED。
6. 无战斗/伤害/词条/网络/Mixin 变更。
7. 未完成项明确记录，不能标 VERIFIED。

## 8. 状态记录模板

| 功能 | DESIGNED | IMPLEMENTED | COMPILED | TESTED | SERVER-TESTED | VERIFIED |
|---|---|---|---|---|---|---|
| MotionSnapshot |  |  |  |  |  |  |
| MotionAccumulator |  |  |  |  |  |  |
| MotionHistory |  |  |  |  |  |  |
| MotionTracker 接入 |  |  |  |  |  |  |
| 客户端采样 |  |  |  |  |  |  |
| 服务端采样 |  |  |  |  |  |  |
| 生命周期清理 |  |  |  |  |  |  |

> 当前全部为 DESIGNED；编码后按实际结果填写。
