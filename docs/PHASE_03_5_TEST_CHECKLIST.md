# PHASE_03_5_TEST_CHECKLIST — Motion Debug HUD 人工验证清单

- 阶段：03.5
- 代码提交：`11a8b00`
- 状态：**PARTIALLY CLIENT-TESTED / INTEGRATED-SERVER-TESTED；动态与多人未测**
- 关联：[PHASE_03_5_TEST_REPORT.md](PHASE_03_5_TEST_REPORT.md)

## 1. 启动

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon runClient
```

进入单人世界后按 **F10**。

## 2. 验证项

| ID | 场景 | 预期 | 结果 |
|---|---|---|---|
| H-01 | 默认启动 | HUD 不显示 | PASS（代码/启动） |
| H-02 | 第一次 F10 | HUD 显示；C2S subscription true | **PASS（日志+截图）** |
| H-03 | 第二次 F10 | HUD 隐藏；C2S false | **PASS（日志+截图）** |
| H-04 | 左栏来源 | `CLIENT LOCAL`、`CLIENT_LOCAL/MEDIUM` | **PASS** |
| H-05 | 右栏来源 | `SERVER (DEBUG PACKET)`、`SERVER_NATIVE/HIGH`、`serverAuthoritative=true` | **PASS** |
| H-06 | 历史容量 | `historyCount / 20`，实际容量来自 `capacity()` | **PASS（20/20）** |
| H-07 | 服务端 age | 新包归零，约每 4 tick 更新 | **PASS（ageTicks=1）** |
| H-08 | 无服务端快照 | 右栏 NO SNAPSHOT，不显示左栏数据 | NOT TESTED（仅单元） |
| H-09 | STALE | age >10 显示 STALE；>20 清空 | NOT TESTED（仅单元） |
| H-10 | 动态移动 | 左右 hSpeed/vel/accel/dir/disp 变化 | NOT TESTED（本次输入未产生位移） |
| H-11 | 跳跃/下落/落地 | 左右 FALL/vy 更新 | NOT TESTED（动态） |
| H-12 | FALL 垂直速度 | 来自 FallSnapshot.verticalVelocity | PASS（静态显示） |
| H-13 | 动作源/trust | 左右 actionSource/actionTrust 独立 | NOT TESTED（本次 action=NONE） |
| H-14 | 死亡 | NO SNAPSHOT，无旧数据 | NOT TESTED |
| H-15 | 维度切换 | 清空旧服务端数据 | NOT TESTED |
| H-16 | 退出/断线 | HUD 清空；服务端 subscription false | **PARTIAL**（F10 false 有日志；断线未测） |
| H-17 | 专用服务器启动 | Done；side=SERVER | **PASS（启动级）** |
| H-18 | 专用服务器真实玩家 | 收到服务端数据 | NOT TESTED |
| H-19 | 双客户端 | 只有开启者收到包 | NOT TESTED |
| H-20 | HUD 关闭停发 | 服务端不再发包 | **PARTIAL（subscription false；未逐包统计）** |

## 3. 日志检查

```text
[DebugHUD] toggled enabled=true
[DebugHUD] subscription player=Dev enabled=true
[DebugHUD] toggled enabled=false
[DebugHUD] subscription player=Dev enabled=false
```

文件：

- [runClient-phase03_5-dynamic.debug.log](logs/runClient-phase03_5-dynamic.debug.log)
- [runServer-phase03_5.latest.log](logs/runServer-phase03_5.latest.log)

## 4. 截图

- HUD 开启：[phase03_5-hud-dynamic.png](logs/phase03_5-hud-dynamic.png)
- HUD 关闭：[phase03_5-hud-off.png](logs/phase03_5-hud-off.png)

## 5. 判定规则

- 没有实际游戏内证据的项不能标 PASS。
- HUD 动态运动、NO SNAPSHOT/STALE、死亡/维度、专用服务器玩家、多人必须人工复测。
- 编译/单测通过不能替代 CLIENT-TESTED / MULTIPLAYER-TESTED。
