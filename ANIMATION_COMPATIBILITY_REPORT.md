# ANIMATION_COMPATIBILITY_REPORT — 动画兼容性审计

- 审计对象：
  - ParCool `3.4.3.3`（`ParCool-1.20.1-3.4.3.3.jar`）
  - Better Combat Forge `1.9.0+1.20.1`（`bettercombat-forge-1.9.0+1.20.1.jar`）
  - Player Animator `1.0.2-rc1+1.20`（`player-animation-lib-forge-1.0.2-rc1+1.20.jar`）
  - ParCool Compatibility Addon `1.1`（`ParCool-CompatibilityAddon-1.20.1-3.4.1.5-1.1.jar`）
- 审计方式：`mods.toml`、Mixin 配置、反编译源码、`javap` 字节码、专用服务器启动日志
- 结论标记：**CONFIRMED** / **PARTIALLY_CONFIRMED** / **UNCONFIRMED**

---

## 1. 版本与依赖关系

| 项 | 实际值 | 状态 | 证据 |
|---|---|---|---|
| Player Animator | `1.0.2-rc1+1.20` | **CONFIRMED** | `mods.toml` `version="1.0.2-rc1+1.20"`；文件名一致 |
| ParCool 是否依赖 Player Animator | 否 | **CONFIRMED** | ParCool `mods.toml` 无 `playeranimator` 依赖；JAR 内反编译源码无任何 `dev.kosmx` 引用 |
| Better Combat 是否依赖 Player Animator | 是 | **CONFIRMED** | BC `mods.toml`：`modId="playeranimator" mandatory=true versionRange="[1,)"` |
| Compatibility Addon 的 Player Animator 依赖 | 可选，`[1.0.2-rc1+1.20,)` | **CONFIRMED** | Addon `mods.toml` |
| Compatibility Addon 的 Better Combat 依赖 | 可选，`[1.8.6+1.20.1,)` | **CONFIRMED** | Addon `mods.toml` |
| Compatibility Addon 的 ParCool 依赖 | 必需，`[3.4.1.5,)` | **CONFIRMED** | Addon `mods.toml` |

> **CONFIRMED**：ParCool 的动画系统与 Player Animator 完全独立；Better Combat 的动画完全建立在 Player Animator 之上。两者没有共享的动画栈。

---

## 2. Player Animator 1.0.2-rc1 的机制

### 2.1 API 面

- `dev.kosmx.playerAnim.api.IPlayer`：

  ```java
  public interface IPlayer {
      AnimationStack getAnimationStack();
  }
  ```

- `dev.kosmx.playerAnim.api.layered.AnimationStack`：
  - `addAnimLayer(int priority, IAnimation layer)`：按 priority **升序**插入（`while (layers.get(search).priority < priority) search++`）。
  - `get3DTransform(...)`：从低 priority 到高 priority 依次应用；**高 priority 后应用，覆盖低 priority**。
  - `isActive()`：任意层 active 即为 true。
- `AnimationStack` 是纯客户端的动画组合器；`IAnimatedPlayer` 由 Mixin 实现在 `AbstractClientPlayer` 上。
- **CONFIRMED**：Player Animator 的 Mixin 全部在 `playerAnimator-common.mixins.json` 的 `"client"` 列表；服务端不应用。

### 2.2 模型应用点

`dev.kosmx.playerAnim.mixin.PlayerModelMixin`（`@Mixin(priority = 2000)`，目标 `PlayerModel`）：

```java
@Inject(method = "setupAnim(...)", at = @At("HEAD"))
private void setDefaultBeforeRender(...) { this.setDefaultPivot(); }

@Inject(method = "setupAnim(...)",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V",
                 ordinal = 0))
private void setEmote(...) {
    if (!firstPersonNext && livingEntity instanceof AbstractClientPlayer
        && ((IAnimatedPlayer) livingEntity).playerAnimator_getAnimation().isActive()) {
        AnimationApplier emote = ...;
        emote.updatePart("head", ...); emote.updatePart("leftArm", ...);
        ...
    }
}
```

- **CONFIRMED**：Player Animator 的可见模型修改发生在 `PlayerModel.setupAnim` **方法体内部**（第一个 `copyFrom` 调用处），而不是 TAIL。
- **CONFIRMED**：因此任何在 `setupAnim` HEAD 取消方法体的 Mixin，都会让 Player Animator 在该帧完全不生效（其 `setEmote` 注入不会执行）。

---

## 3. ParCool 3.4.3.3 的动画机制

### 3.1 客户端 Capability

`com.alrex.parcool.common.capability.Animation` 标注 `@OnlyIn(Dist.CLIENT)`，只在客户端附加：

```java
@OnlyIn(Dist.CLIENT)
public class Animation {
    private Animator animator;
    private AnimationOption option = new AnimationOption();
    public static Animation get(Player player); // 仅客户端
    public void setAnimator(Animator animator);
    public boolean animatePre(Player, PlayerModelTransformer);
    public void animatePost(Player, PlayerModelTransformer);
    public boolean rotatePre(AbstractClientPlayer, PlayerModelRotator);
    public void rotatePost(AbstractClientPlayer, PlayerModelRotator);
    public void updateAnimationInfo(AbstractClientPlayer player) {
        ParCoolAnimationInfoEvent event = new ParCoolAnimationInfoEvent(player, this.animator);
        MinecraftForge.EVENT_BUS.post(event);
        this.option = event.getOption();
    }
}
```

- **CONFIRMED**：`Animation` Capability 在 `AttachCapabilityHandler` 中仅在 `level.isClientSide()` 时附加；服务端没有该 Capability。
- **CONFIRMED**：ParCool 不使用 Player Animator 的任何类（JAR 内无 `dev.kosmx` 引用）。

### 3.2 模型/渲染注入点

| Mixin | 目标 | 注入 | 作用 | 状态 |
|---|---|---|---|---|
| `com.alrex.parcool.mixin.client.PlayerModelMixin` | `PlayerModel.setupAnim` | HEAD（cancellable） | 创建 `PlayerModelTransformer`，调用 `Animation.animatePre`；**返回 true 时 `ci.cancel()`，整个 `setupAnim` 被取消** | **CONFIRMED** |
| 同上 | `PlayerModel.setupAnim` | TAIL | 调用 `Animation.animatePost`，在原生姿势之上叠加 | **CONFIRMED** |
| `com.alrex.parcool.mixin.client.PlayerRendererMixin` | `PlayerRenderer.setupRotations` | HEAD（cancellable） | `Animation.rotatePre`；返回 true 时取消原版旋转 | **CONFIRMED** |
| 同上 | `PlayerRenderer.setupRotations` | RETURN | `Animation.rotatePost` 叠加 | **CONFIRMED** |
| `com.alrex.parcool.mixin.client.LivingRendererMixin` | `LivingEntityRenderer.shouldShowName` | HEAD | 根据 `BehaviorEnforcer` 隐藏名字 | **CONFIRMED** |

- **CONFIRMED**：ParCool 的“全身动画”（`animatePre` 返回 true 的部分动画师）通过取消 `setupAnim` 完全接管模型；其余动画师通过 `animatePost` 在原生姿势上叠加。
- **CONFIRMED**：`PlayerModelMixin` 使用 `@Inject(... at = HEAD, cancellable = true)`，没有声明 Mixin priority（默认 1000）。

### 3.3 动画信息事件

`com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent`（`@OnlyIn(CLIENT)`）：

```java
public class ParCoolAnimationInfoEvent extends Event {
    AbstractClientPlayer getPlayer();
    Animator getAnimator();
    AnimationOption getOption();
}
```

- **CONFIRMED**：该事件在 `Animation.onRenderTick`（`Phase.START`）对每个玩家**每渲染帧**发送一次；`Animation` 每帧构造新的 `AnimationOption`，外部设置不会跨帧保留。
- **CONFIRMED**：事件不通过 `Event.setCanceled` 取消；取消语义由 `AnimationOption.cancel(AnimationPart)` / `cancelAnimation()` 表达。
- **CONFIRMED**：`AnimationPart` 枚举：`HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, ROTATION, CAMERA`。

---

## 4. Better Combat 的动画机制

### 4.1 动画层

`net.bettercombat.mixin.client.AbstractClientPlayerEntityMixin`（`@Mixin(AbstractClientPlayer)`）在构造尾部：

```java
AnimationStack stack = ((IAnimatedPlayer) this).getAnimationStack();
stack.addAnimLayer(1, this.offHandItemPose.base);
stack.addAnimLayer(2, this.offHandBodyPose.base);
stack.addAnimLayer(3, this.mainHandItemPose.base);
stack.addAnimLayer(4, this.mainHandBodyPose.base);
stack.addAnimLayer(2000, this.attackAnimation.base);
```

- **CONFIRMED**：BC 在 Player Animator 栈中占用 5 层：1/2/3/4 为副手/主手物品与身体姿态，2000 为攻击动画。
- **CONFIRMED**：由于 `AnimationStack` 按 priority 升序应用，攻击层 2000 是 BC 层中优先级最高的（最后应用、覆盖 1-4 层）。
- **CONFIRMED**：跨玩家播放攻击动画通过 `Packets.AttackAnimation`（服务端转发 `playerId/animatedHand/animationName/length/upswing`），客户端 `ClientNetwork` 收到后对对应玩家调用 `playAttackAnimation`；`!STOP!` 表示停止。
- **CONFIRMED**：动画资源从 `assets/bettercombat/attack_animations/*.json` 加载（`AnimationRegistry.load`，资源管理器 `listResources("attack_animations", ...)`），使用 Player Animator 的 `KeyframeAnimation` 反序列化。
- **CONFIRMED**：`CustomAnimationPlayer extends KeyframeAnimationPlayer`，在收尾阶段把 `FirstPersonMode` 切为 `NONE`；动画带 fade-in（`AbstractFadeModifier.standardFadeIn`）。

### 4.2 与 ParCool 的根本差异

| 维度 | ParCool | Better Combat |
|---|---|---|
| 动画基础设施 | 自有 `Animator` + `PlayerModelTransformer` + Mixin | Player Animator 层栈 |
| 应用位置 | `PlayerModel.setupAnim` HEAD/TAIL、`PlayerRenderer.setupRotations` HEAD/RETURN | `PlayerModel.setupAnim` 方法体内部（copyFrom 处） |
| 动画优先级 | Mixin 执行顺序 + 是否取消整个 `setupAnim` | `AnimationStack` priority 升序，2000 最高 |
| 服务端参与 | 无（纯客户端） | 服务端只转发动画包，不参与渲染 |
| 可被外部取消 | `ParCoolAnimationInfoEvent`（按部位/整体） | 无公开取消事件；可替换层内容（内部 API） |

---

## 5. ParCool Compatibility Addon 1.1 的实际职责

反编译 Addon 的全部源码后确认：

| 集成 | 行为 | 代码位置 | 状态 |
|---|---|---|---|
| Better Combat | 当 `PlayerAttackHelper.isTwoHandedWielding(player)` 时，取消 ParCool 动画的 `LEFT_ARM` + `RIGHT_ARM` | `EventHandlerForBetterCombat.onUpdateParCoolAnimInfo` | **CONFIRMED** |
| Player Animator | 当 `player instanceof IPlayer` 且 `getAnimationStack().isActive()` 且当前 ParCool 动画师是 `FastRunningAnimator` / `FastSwimAnimator` / `CrawlAnimator` 时，`cancelAnimation()` 整体取消 | `EventHandlerForPlayerAnimator` | **CONFIRMED** |
| TaCZ | 持枪时按动画师类型取消腿/臂或整体动画；部分动画直接 return 不取消 | `EventHandlerForTaCZ` | **CONFIRMED** |
| CarryOn | 兼容搬运方块时的动画（`EventHandlerForCarryOn`） | 同包 | **CONFIRMED** |
| RealCamera | 第一人称 + RealCamera 激活 + `Dive.isDoing()` 时取消双臂 | `EventHandlerForRealCamera` | **CONFIRMED** |
| FirstPersonModel | 仅暴露 `isEnabled()`，用于 Mixin 判断 | `FirstPersonModelManager` | **CONFIRMED** |
| VicsPointBlank | 枪械动画兼容 | `EventHandlerForPointBlank` | **CONFIRMED** |
| RealCamera / FirstPersonModel Mixin | 在 `Animation.shouldCancelAnimation` HEAD 处，当第一人称且对应模组激活时返回 `false`，阻止 ParCool 因第一人称而取消动画 | `com.alrexu.parcool.compat.mixin.parcool.AnimationMixin`（`remap = false`） | **CONFIRMED** |
| Better Combat 管理器注册 | 仅当 `bettercombat` mod 已加载时，在客户端注册 `EventHandlerForBetterCombat` | `BetterCombatManager.initInClient`（`ModList` 判断） | **CONFIRMED** |

- **CONFIRMED**：Addon 的所有事件处理器都标注 `@OnlyIn(Dist.CLIENT)`，并通过 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` 注册；服务端只构造 manager 列表，不注册这些处理器。
- **CONFIRMED**：Addon 的 `parcool_compat_addon.mixins.json` 只有 `"client": ["parcool.AnimationMixin"]`，`"mixins": []`。
- **CONFIRMED（专用服务器）**：在专用服务器启动测试中，Addon 1.1 与 ParCool 3.4.3.3 / BC 1.9.0 一起加载成功，未出现类加载崩溃；仅出现 `refmap` 缺失警告（该 Mixin 仅客户端应用，不影响服务端）。

---

## 6. 动画优先级与动作中断分析

### 6.1 两套动画管线如何叠加

1. **ParCool 全身动画**（`animatePre` 返回 true，如 `ChargeJumpAnimator`、`JumpChargingAnimator` 的某些分支、`SlidingAnimator.rotatePre` 等）：
   - 在 `PlayerModel.setupAnim` HEAD 取消方法；
   - Player Animator 的 `setEmote` 注入位于方法体内部 → **该帧完全不会执行**；
   - 结果：BC 攻击动画层仍在 tick（`AnimationStack.tick`），但模型上看不到 BC 动画。
2. **ParCool 局部动画**（只实现 `animatePost`，如 `FastRunningAnimator`、`SlidingAnimator.animatePost`）：
   - 原生 `setupAnim` 正常执行 → Player Animator 的 `setEmote` 执行并应用 BC 动画；
   - ParCool `animatePost` 在 TAIL 再对指定部位做旋转/位移 → **ParCool 后应用，覆盖对应部位**。
3. **BC 攻击层 2000**：
   - 只在 Player Animator 内部最高优先；对 ParCool 的模型变换没有优先级可言（两者是不同管线）。

### 6.2 典型组合结果

| 场景 | 预期表现 | 依据 | 状态 |
|---|---|---|---|
| FastRun + BC 双手武器攻击 | ParCool 取消双臂（Addon）；BC 攻击层渲染手臂；ParCool 身体/腿仍生效 | `EventHandlerForBetterCombat` + `FastRunningAnimator.animatePost` | **PARTIALLY_CONFIRMED**（静态代码推导；未实机观察） |
| FastRun + BC 单手武器攻击 | ParCool 不取消手臂；BC 攻击动画与 ParCool 摆臂叠加，ParCool post 覆盖手臂 | 同上，无 Addon 手臂取消 | **PARTIALLY_CONFIRMED** |
| Slide/Vault/WallRun + Player Animator 外部动画 | Addon 的 PlayerAnimator 兼容只覆盖 FastRun/FastSwim/Crawl；Slide/Vault/WallRun 不取消 → 可能与外部动画冲突 | `EventHandlerForPlayerAnimator` 仅判断三个动画师 | **CONFIRMED（覆盖范围有限）** |
| Slide（`rotatePre` 返回 true） | ParCool 取消 `setupRotations`，整体旋转由 ParCool 接管；Player Animator 的动画仍可能作用于模型（其应用在 `setupAnim` 内部，若未被取消） | `SlidingAnimator.rotatePre`、`PlayerRendererMixin` | **PARTIALLY_CONFIRMED** |
| BC 双手武器 + ParCool Vault | Addon 只取消双臂；Vault 动画师可能仍修改身体/腿 | `EventHandlerForBetterCombat` | **PARTIALLY_CONFIRMED** |

### 6.3 中断/优先级结论

- **CONFIRMED**：ParCool 的 `Animation.setAnimator` 会直接替换当前动画师；动作开始时（如 Slide、Vault、WallJump）通过各自 `onStartInLocalClient` 设置专属动画师，从而中断上一个动画。
- **CONFIRMED**：Player Animator 的层不会被 ParCool 移除或暂停；ParCool 只是通过取消 `setupAnim` 让其在某些帧“不可见”。
- **CONFIRMED**：Compatibility Addon 是**单向**兼容（ParCool 侧感知 BC/PlayerAnimator），Better Combat 不知道 ParCool 的存在（BC JAR 内无 `parcool` 引用）。
- **CONFIRMED**：Addon 的 PlayerAnimator 兼容只覆盖 `FastRunningAnimator` / `FastSwimAnimator` / `CrawlAnimator`；对 `SlidingAnimator`、`KongVaultAnimator`、`SpeedVaultAnimator`、`WallRunAnimator`、`WallJumpAnimator` 等没有整体取消逻辑。
- **PARTIALLY_CONFIRMED**：上述覆盖缺口在实际渲染中的视觉严重程度（穿模/抖动/动作被覆盖）未做客户端实机对比；需要后续客户端测试。

---

## 7. 客户端专属类加载边界

| 组件 | 边界 | 证据 | 状态 |
|---|---|---|---|
| ParCool `Animation` Capability | 仅客户端注册与附加；`Animation` 类标注 `@OnlyIn(CLIENT)` | `Capabilities.registerClient`、`AttachCapabilityHandler` | **CONFIRMED** |
| ParCool 动作的客户端回调 | `onStartInLocalClient` / `onStartInOtherClient` / `onWorkingTickInClient` 等标注 `@OnlyIn(CLIENT)`；专用服务器由 Forge RuntimeDistCleaner 处理 | `Action` 源码 | **CONFIRMED** |
| ParCool 动画事件 | `ParCoolAnimationInfoEvent` / `AnimationOption` / `AnimationPart` 均 `@OnlyIn(CLIENT)` | 源码 | **CONFIRMED** |
| Better Combat 客户端 | `ClientNetwork`、`MinecraftClientInject`、`AbstractClientPlayerEntityMixin` 等全部在 `bettercombat.mixins.json` 的 `client` 列表 | Mixin 配置 | **CONFIRMED** |
| Better Combat 服务端 | `ServerNetwork.initializeHandlers` 在 `BetterCombat.onInitialize()` 中无条件注册；专用服务器测试启动成功 | 启动日志 `Done (2.413s)` | **CONFIRMED** |
| Better Combat 配置界面 | `BetterCombatForge` 构造器中的 `ConfigScreenHandler.ConfigScreenFactory` lambda 引用客户端 `AutoConfig`；专用服务器启动成功，未触发类加载错误 | 服务器启动测试 | **CONFIRMED（在当前组合下）** |
| Player Animator | 所有 Mixin 在 `client` 列表；`IPlayer`/`AnimationStack` 由客户端实现 | `playerAnimator-common.mixins.json` | **CONFIRMED** |
| Compatibility Addon | 事件处理器 `@OnlyIn(CLIENT)`；通过 `DistExecutor.unsafeRunWhenOn(CLIENT, ...)` 注册；Mixin 配置仅 client | `ExternalModManager`、`IModManager.initInClient` | **CONFIRMED** |
| Compatibility Addon 服务端加载 | 服务器启动测试中 Addon 加载成功，未因引用 `Animation` 等客户端类崩溃 | 启动日志 | **CONFIRMED** |

> **CONFIRMED**：在本组合（ParCool 3.4.3.3 + BC 1.9.0 + PlayerAnimator 1.0.2-rc1 + Addon 1.1）下，专用服务器可以启动；客户端动画类没有在服务端被强制加载。

---

## 8. 对 ParkourCombat 的动画集成建议（仅结论，不实现）

1. **CONFIRMED**：不要试图用 Player Animator 层去“覆盖” ParCool 全身动作；ParCool 取消 `setupAnim` 后，Player Animator 的模型应用在该帧不执行。
2. **CONFIRMED**：需要让 BC 动画在 ParCool 动作中可见时，应通过 `ParCoolAnimationInfoEvent` 取消对应 `AnimationPart`（Addon 已示范），而不是加 Player Animator 层。
3. **CONFIRMED**：如果 ParkourCombat 要新增攻击动画，应注册到 Better Combat 的 `attack_animations` 数据体系 / 武器属性 `animation` 字段，而不是另起一套 Player Animator 层。
4. **CONFIRMED**：任何动画相关类必须只在客户端加载（`@OnlyIn(CLIENT)`、`DistExecutor`、客户端事件），公共代码不得直接引用 `Animation`、`AnimationStack`、`PlayerModelTransformer` 等。
5. **PARTIALLY_CONFIRMED**：Addon 对 Slide/Vault/WallRun/WallJump 的外部动画冲突没有处理；若后续阶段需要在这些动作中播放 BC 攻击动画，需要自行在 `ParCoolAnimationInfoEvent` 中取消相应部位，或接受 ParCool 覆盖。

---

## 9. 可复现验证

```powershell
# 反编译
$ff='D:\IntelliJ IDEA 2024.1.7\plugins\java-decompiler\lib\java-decompiler.jar'
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp $ff org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler `
  -dgs=1 -asc=1 "$env:TEMP\pc_audit\x_playeranim" "$env:TEMP\pc_audit\src_playeranim"
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp $ff org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler `
  -dgs=1 -asc=1 "$env:TEMP\pc_audit\x_compataddon" "$env:TEMP\pc_audit\src_compat"

# 关键类
javap -p -classpath player-animation-lib-forge-1.0.2-rc1+1.20.jar dev.kosmx.playerAnim.api.IPlayer
javap -p -classpath player-animation-lib-forge-1.0.2-rc1+1.20.jar dev.kosmx.playerAnim.api.layered.AnimationStack
javap -p -classpath ParCool-1.20.1-3.4.3.3.jar com.alrex.parcool.common.capability.Animation
javap -p -classpath bettercombat-forge-1.9.0+1.20.1.jar net.bettercombat.client.animation.AttackAnimationSubStack
```

服务器启动复核：

```powershell
# 审计使用的临时服务器目录（不入库）
$d="$env:TEMP\pc_server"
Get-Content "$d\console.out" | Select-String 'Done \(|ERROR|Exception'
```

---

## 10. 结论汇总

- **CONFIRMED**：Player Animator 实际版本 `1.0.2-rc1+1.20`；BC 依赖它，ParCool 不依赖它。
- **CONFIRMED**：ParCool 使用自有 `Animation` Capability + `PlayerModel`/`PlayerRenderer` Mixin；BC 使用 Player Animator 层栈（优先级 1/2/3/4/2000）。
- **CONFIRMED**：ParCool 全身动画通过取消 `PlayerModel.setupAnim` 使 Player Animator 在该帧失效；局部动画在 TAIL 覆盖对应部位。
- **CONFIRMED**：Addon 1.1 的职责是“ParCool 侧兼容”：BC 双手武器取消双臂、PlayerAnimator 活动时取消 FastRun/FastSwim/Crawl、TaCZ/CarryOn/RealCamera/FirstPersonModel/VicsPointBlank 兼容，以及一个客户端 `Animation.shouldCancelAnimation` Mixin。
- **CONFIRMED**：当前组合可在专用服务器启动，客户端专属类未被服务端强制加载。
- **PARTIALLY_CONFIRMED**：实际视觉叠加效果、Addon 对 Slide/Vault/WallRun 未覆盖的冲突程度，需要客户端实机测试。
- **UNCONFIRMED**：客户端画面验证（未启动 Minecraft 客户端）。
