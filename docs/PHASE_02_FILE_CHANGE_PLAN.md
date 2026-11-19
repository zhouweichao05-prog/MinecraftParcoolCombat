# PHASE_02_FILE_CHANGE_PLAN — 阶段 02 文件变更计划

- 阶段：02
- 状态：**PLANNED（未修改任何阶段 02 代码）**
- 范围：只新增 ParCoolAdapter 相关源码、测试与文档；不触碰锁定依赖。

---

## 1. 新增源码文件

根路径：`src/main/java/com/MrGhost/parkourCombat/`

| 文件 | 职责 |
|---|---|
| `adapter/ParkourActionType.java` | 7 个类型：FAST_RUN、SLIDE、VAULT、WALL_RUN（聚合）、WALL_RUN_HORIZONTAL、WALL_RUN_VERTICAL、WALL_JUMP |
| `adapter/ParkourActionPhase.java` | STARTED / ACTIVE / FINISHED / CANCELLED / INTERRUPTED / UNKNOWN |
| `adapter/ParkourStateSource.java` | SERVER_NATIVE / PARCOOL_SYNCED / CLIENT_LOCAL / VANILLA_DERIVED |
| `adapter/ParkourTrust.java` | HIGH / MEDIUM / LOW / UNKNOWN |
| `adapter/ParkourActionState.java` | 动作状态接口 |
| `adapter/ImmutableParkourActionState.java` | 不可变状态实现（record/不可变类） |
| `adapter/FallPhase.java` | NOT_FALLING / FALLING / FALL_END |
| `adapter/FallEndReason.java` | LANDED / WATER / LAVA / FLYING / CLIMBING / VEHICLE / TELEPORT / DEATH / LOGOUT / DIMENSION_CHANGE / UNLOAD / UNKNOWN |
| `adapter/FallSnapshot.java` | FALL 数据：起始位置、距离、垂直速度、时长、接地、结束原因、来源 |
| `adapter/WallRunSnapshot.java` | 墙跑共享数据；`directionAvailable=false` |
| `adapter/ParkourActionAdapter.java` | 查询接口：getState/getActiveActions/isActive/getFallState |
| `adapter/ParCoolActionMapping.java` | ParCool 类 → 类型映射；版本守卫；不可用类处理 |
| `adapter/ParkourActionTracker.java` | 单玩家动作状态机（纯 Java，可单测） |
| `adapter/FallStateMachine.java` | 单玩家 FALL 状态机（纯 Java，可单测） |
| `adapter/MotionTracker.java` | 从玩家物理快照驱动 FALL 状态机；记录起始/结束数据 |
| `adapter/ParCoolActionAdapter.java` | 统一门面：玩家状态映射、事件分发、查询、清理 |
| `adapter/ParkourAdapterEvents.java` | Forge 事件订阅与路由（ParCool 事件、PlayerTick、生命周期） |
| `adapter/server/ServerParCoolAdapter.java` | 服务端来源/可信度、同步事件、启发式中断、服务端 FALL |
| `adapter/client/ClientParCoolAdapter.java` | 客户端本地状态、TryToStart/TryToContinue 取消、LoggingOut/Clone 清理 |

### 1.1 不新增的内容

- 不新增 Mixin 类/config。
- 不新增网络包/通道。
- 不新增资源文件（无贴图、模型、音效）。
- 不新增配置系统（本阶段日志固定，不引入配置）。
- 不新增命令（如后续需要调试命令，单独评审）。

---

## 2. 修改文件

| 文件 | 修改内容 | 风险 |
|---|---|---|
| `src/main/java/com/MrGhost/parkourCombat/ParkourCombat.java` | 构造器中注册 `ParkourAdapterEvents`；客户端 adapter 用 `DistExecutor.unsafeRunWhenOn(CLIENT, ...)` 创建 | 低；不写玩法逻辑 |
| `docs/PHASE02_CLIENT_CHECKLIST.md` | 状态来源命名统一为 `VANILLA_DERIVED`（旧 `ADAPTER_DERIVED` 同义） | 仅文档 |
| `README.md`（可选） | 阶段 02 完成后的状态/文档链接 | 仅文档 |

以下文件**不修改**：

- `build.gradle`：Forge/ParCool/BC 版本锁定不变；依赖坐标不变。
- `gradle.properties`：版本参数不变。
- `src/main/resources/META-INF/mods.toml`：依赖声明不变（ParCool 必需、BC 可选）。
- `libs/*.jar`：4 个锁定 JAR 不修改、不重打包。
- `docs/PARCOOL_API_AUDIT.md`、`BETTERCOMBAT_API_AUDIT.md`、`ANIMATION_COMPATIBILITY_REPORT.md`、`RUNTIME_VERSION_MATRIX.md`：只读参考，不改结论。
- `gradle/wrapper/*`、`gradlew*`：构建工具不变。

---

## 3. 新增测试文件

`src/test/java/com/MrGhost/parkourCombat/adapter/`

| 文件 | 覆盖 |
|---|---|
| `ParkourActionTrackerTest.java` | U-01～U-15：开始/持续/结束/取消/中断/超时/聚合/来源 |
| `FallStateMachineTest.java` | F-01～F-15：进入/排除/结束原因/TTL |
| `AdapterLifecycleTest.java` | C-01～C-08：退出/死亡/维度/卸载/无泄漏 |
| `ParCoolVersionGuardTest.java` | V-01/V-02（用模拟版本字符串，不加载真实 mod） |

测试依赖：

- JUnit 5（由 ForgeGradle/Minecraft 依赖提供；若未提供，需在 `build.gradle` 添加 `testImplementation`，属于计划内变更，需评审）。
- 不启动 Minecraft 客户端/服务端。

---

## 4. 新增文档

| 文件 | 说明 |
|---|---|
| `docs/PHASE_02_IMPLEMENTATION_PLAN.md` | 本阶段实施计划（已创建） |
| `docs/PHASE_02_API_EVIDENCE.md` | API 证据（已创建） |
| `docs/PHASE_02_TEST_PLAN.md` | 测试计划（已创建） |
| `docs/PHASE_02_FILE_CHANGE_PLAN.md` | 本文件（已创建） |
| `docs/PHASE_02_TEST_REPORT.md` | 实现完成后生成的实际测试记录（未创建） |

---

## 5. 第三方文件红线

| 对象 | 规则 |
|---|---|
| `libs/ParCool-1.20.1-3.4.3.3.jar` | 只读；不修改/重打包/替换 |
| `libs/bettercombat-forge-1.9.0+1.20.1.jar` | 只读；本阶段不引用其 API |
| `libs/player-animation-lib-forge-1.0.2-rc1+1.20.jar` | 只读 |
| `libs/cloth-config-11.1.136-forge.jar` | 只读 |
| Forge / Minecraft | 只使用公开 API；不修改 |

---

## 6. 文件依赖关系

```text
ParkourCombat
  └─ ParkourAdapterEvents
       ├─ ParCoolActionAdapter
       │    ├─ ParkourActionTracker
       │    ├─ MotionTracker ── FallStateMachine
       │    ├─ ParCoolActionMapping
       │    └─ ImmutableParkourActionState / FallSnapshot
       ├─ ServerParCoolAdapter
       └─ ClientParCoolAdapter  (client-only)

数据对象：ParkourActionType / Phase / Source / Trust / State / Fall* / WallRunSnapshot
测试：Tracker / FallStateMachine / Lifecycle / VersionGuard
```

- 公共代码不引用 `ClientParCoolAdapter`、`Minecraft`、`Animation`、`Animator`。
- `ClientParCoolAdapter` 标注 `@OnlyIn(Dist.CLIENT)`，只在 `DistExecutor` 中实例化。

---

## 7. 变更规模估算

| 类别 | 文件数 | 估算 |
|---|---:|---|
| 类型/接口 | 10 | 300–450 行 |
| 状态机/映射/门面 | 7 | 500–700 行 |
| 事件与侧别 | 3 | 300–450 行 |
| 测试 | 4 | 500–800 行 |
| 文档 | 1–2 | 测试报告 |

不创建空文件；每个类都有实际职责。

---

## 8. 构建影响

- 不改变 `mods.toml` 依赖；构建 JAR 仍只包含本模组类。
- 不引入新第三方库（除非 JUnit 需要 `testImplementation`，需单独确认）。
- 不改变 `jarJar` / `reobf` 流程。
- 不新增 Mixin 配置，因此 `mixin.env` 仍只用于现有第三方模组。

---

## 9. 未决问题

1. JUnit 5 是否由当前 ForgeGradle 提供；若否，是否允许在 `build.gradle` 增加 `testImplementation`（仅测试，不影响运行时）。
2. 是否需要调试命令（`/parkourcombat state`）；本计划默认不加，依赖 INFO 相位日志。
3. `FALL_END` 保留时长 40 tick 是否合适；可调，但必须有界。
4. 服务端超时窗口 2 tick 是否足够；需实测。

以上问题在执行前需确认；不确认则采用默认值并在测试报告中记录。
