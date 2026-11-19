# PHASE_02_ACCEPTANCE — 阶段 02 有条件验收记录

- 验收日期：2026-11-19
- 验收状态：**CONDITIONALLY_ACCEPTED**
- 验收时 Git HEAD：`3389c13`
- 分支：`master`
- 工作区：clean
- 验收依据：[PHASE_02_TEST_REPORT.md](PHASE_02_TEST_REPORT.md)、[PHASE02_CLIENT_CHECKLIST.md](PHASE02_CLIENT_CHECKLIST.md)

## 1. 已通过项目

- FAST_RUN 双端识别。
- SLIDE 双端识别。
- VAULT 双端识别。
- WALL_RUN_HORIZONTAL。
- 中断状态与 `inferred` 语义。
- FALL 基础落地。
- `VANILLA_DERIVED` source/trust 修复。
- 客户端退出清理。
- 服务端退出清理。
- 21/21 单元测试。
- build。
- 专用服务器启动。

## 2. 尚未 VERIFIED（遗留任务，不得删除或标记 PASS）

- WALL_RUN_VERTICAL。
- WALL_JUMP。
- CANCELLED。
- FALL 水、岩浆、鞘翅、攀爬、载具等环境。
- FALL 传送和维度切换。
- 玩家死亡清理。
- 多人同步和丢包。
- FALL 双端计数差异（第三轮 20 vs 23；待观察）。

## 3. 验收时的提交历史

| 提交 | 内容 |
|---|---|
| `18ea034` | `feat: implement phase 02 ParCoolAdapter` |
| `2540298` | `docs: record phase 02 client test evidence` |
| `c1c206b` | `fix: track integrated server adapter alongside client` |
| `aa96ec9` | `fix: correct adapter side and FALL metadata` |
| `71da605` | `docs: record dual-side phase 02 evidence and fixes` |
| `3389c13` | `docs: record fixed-metadata phase 02 retest` |

## 4. 验收条件

1. 上述未验证项目保留在测试报告中，不得删除、合并或标记为 PASS。
2. 阶段 03 不得重新实现 ParCool 动作识别，不得改变阶段 02 的动作分类、生命周期和可信度语义；发现真实缺陷时先报告。
3. 阶段 03 的 MotionSnapshot 必须继续区分 `CLIENT_LOCAL`、`PARCOOL_SYNCED`、`SERVER_NATIVE`、`VANILLA_DERIVED`，服务端不得把客户端数据作为战斗权威。
4. 后续完成遗留测试时，更新本文件与测试报告，不覆盖历史记录。
5. 阶段 03 完成后停止等待验收，不自动进入阶段 04。

## 5. 结论

阶段 02 以 **CONDITIONALLY_ACCEPTED** 状态关闭；核心动作链路、双端同步、中断语义、FALL 基础落地、退出清理已通过。整体仍未 VERIFIED，遗留测试任务继续有效。
