package com.MrGhost.parkourCombat.client.debug;

import com.MrGhost.parkourCombat.adapter.FallSnapshot;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionState;
import com.MrGhost.parkourCombat.adapter.ParkourTrust;
import com.MrGhost.parkourCombat.network.ClientboundCombatDebugSnapshot;
import com.MrGhost.parkourCombat.network.ClientboundMotionDebugSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Client-only HUD cache. Client-local and server-packet data are stored in
 * separate fields and are never merged.
 *
 * <p>No Minecraft classes are referenced, so the state and its formatting can
 * be unit tested.</p>
 */
public final class DebugHudState {
    public static final int STALE_AFTER_TICKS = 10;
    public static final int DROP_AFTER_TICKS = 20;

    private static final int WHITE = 0xFFE0E0E0;
    private static final int HEADER_CLIENT = 0xFF55FFFF;
    private static final int HEADER_SERVER = 0xFFFFD060;
    private static final int HIGH = 0xFF55FF55;
    private static final int MEDIUM = 0xFFFFFF55;
    private static final int LOW = 0xFFFFAA00;
    private static final int UNKNOWN = 0xFFFF5555;

    private boolean enabled;
    private MotionSnapshot clientSnapshot;
    private int clientHistoryCount;
    private int clientHistoryCapacity;

    private ClientboundMotionDebugSnapshot serverSnapshot;
    private ClientboundCombatDebugSnapshot combatSnapshot;
    private int serverAgeTicks;

    private List<HudLine> clientLines = List.of();
    private List<HudLine> serverLines = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clearServer();
            clientLines = List.of();
            serverLines = List.of();
        }
    }

    public void updateClient(MotionSnapshot snapshot, int historyCount, int historyCapacity) {
        this.clientSnapshot = snapshot;
        this.clientHistoryCount = Math.max(0, historyCount);
        this.clientHistoryCapacity = Math.max(0, historyCapacity);
    }

    public void acceptServer(ClientboundMotionDebugSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasSnapshot()) {
            clearServer();
            return;
        }
        this.serverSnapshot = snapshot;
        this.serverAgeTicks = 0;
    }

    public void tickServerAge() {
        if (serverSnapshot == null && combatSnapshot == null) {
            return;
        }
        serverAgeTicks++;
        if (serverAgeTicks > DROP_AFTER_TICKS) {
            clearServer();
        }
    }

    public void clearServer() {
        serverSnapshot = null;
        combatSnapshot = null;
        serverAgeTicks = 0;
        serverLines = List.of();
    }

    public void acceptCombat(ClientboundCombatDebugSnapshot snapshot) {
        this.combatSnapshot = snapshot == null || !snapshot.hasContext() ? null : snapshot;
        this.serverAgeTicks = 0;
    }

    public ClientboundCombatDebugSnapshot getCombatSnapshot() {
        return combatSnapshot;
    }

    public void clearAll() {
        enabled = false;
        clientSnapshot = null;
        clientHistoryCount = 0;
        clientHistoryCapacity = 0;
        serverSnapshot = null;
        combatSnapshot = null;
        serverAgeTicks = 0;
        clientLines = List.of();
        serverLines = List.of();
    }

    /**
     * Rebuilds cached strings. Call at most every 4 client ticks.
     */
    public void refresh() {
        if (!enabled) {
            clientLines = List.of();
            serverLines = List.of();
            return;
        }
        clientLines = buildClientLines();
        serverLines = buildServerLines();
    }

    public List<HudLine> getClientLines() {
        return clientLines;
    }

    public List<HudLine> getServerLines() {
        return serverLines;
    }

    public int getServerAgeTicks() {
        return serverAgeTicks;
    }

    public MotionSnapshot getClientSnapshot() {
        return clientSnapshot;
    }

    public ClientboundMotionDebugSnapshot getServerSnapshot() {
        return serverSnapshot;
    }

    public boolean isServerStale() {
        return serverSnapshot != null && serverAgeTicks > STALE_AFTER_TICKS;
    }

    private List<HudLine> buildClientLines() {
        List<HudLine> lines = new ArrayList<>();
        if (clientSnapshot == null) {
            lines.add(new HudLine("无数据", UNKNOWN));
            return Collections.unmodifiableList(lines);
        }

        MotionSnapshot snapshot = clientSnapshot;
        lines.add(new HudLine("有效", HIGH));
        lines.add(new HudLine("运动来源：" + sourceName(snapshot.source().name()),
                trustColor(snapshot.trust())));
        lines.add(new HudLine("运动可信度：" + trustName(snapshot.trust().name()), trustColor(snapshot.trust())));
        lines.add(new HudLine("采样时刻：" + snapshot.sampleTick(), WHITE));
        lines.add(new HudLine("服务端权威：false", WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "水平速度：%.4f", snapshot.horizontalSpeed()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "速度：(%.4f, %.4f, %.4f)",
                snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "水平加速度：%.4f 垂直加速度：%.4f",
                snapshot.horizontalAcceleration(), snapshot.verticalAcceleration()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "方向：(%.4f, %.4f)",
                snapshot.directionX(), snapshot.directionZ()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "位移：(%.4f, %.4f, %.4f)",
                snapshot.displacementX(), snapshot.displacementY(), snapshot.displacementZ()), WHITE));
        lines.add(new HudLine("连续运动刻：" + snapshot.continuousMotionTicks()
                + " 着地：" + snapshot.onGround(), WHITE));
        lines.add(new HudLine("历史：" + clientHistoryCount + " / "
                + (clientHistoryCapacity > 0 ? clientHistoryCapacity : "无"), WHITE));

        ParkourActionState action = snapshot.actionState();
        if (action == null) {
            lines.add(new HudLine("跑酷动作：NONE", UNKNOWN));
        } else {
            lines.add(new HudLine("跑酷动作：" + actionName(action.type().name()) + " 阶段：" + phaseName(action.phase().name()), WHITE));
            lines.add(new HudLine("动作来源：" + sourceName(action.source().name()),
                    trustColor(action.trust())));
            lines.add(new HudLine("动作可信度：" + trustName(action.trust().name()), trustColor(action.trust())));
        }

        FallSnapshot fall = snapshot.fallSnapshot();
        if (fall == null) {
            lines.add(new HudLine("下落：无", UNKNOWN));
        } else {
            lines.add(new HudLine("下落阶段：" + fall.phase(), WHITE));
            lines.add(new HudLine("下落距离：" + format(fall.fallDistance()), WHITE));
            lines.add(new HudLine("下落垂直速度：" + format(fall.verticalVelocity()), WHITE));
            lines.add(new HudLine("下落来源：" + sourceName(fall.source().name()), trustColor(fall.trust())));
            lines.add(new HudLine("下落可信度：" + trustName(fall.trust().name()), trustColor(fall.trust())));
        }
        return Collections.unmodifiableList(lines);
    }

    private List<HudLine> buildServerLines() {
        List<HudLine> lines = new ArrayList<>();
        ClientboundMotionDebugSnapshot snapshot = serverSnapshot;
        if (snapshot == null) {
            lines.add(new HudLine("无数据", UNKNOWN));
            return Collections.unmodifiableList(lines);
        }

        String status = isServerStale() ? "数据已过期" : "有效";
        lines.add(new HudLine(status + " ageTicks=" + serverAgeTicks,
                isServerStale() ? UNKNOWN : HIGH));
        lines.add(new HudLine("运动来源：" + sourceName(snapshot.motionSource()),
                trustColor(snapshot.motionTrust())));
        lines.add(new HudLine("运动可信度：" + trustName(snapshot.motionTrust()),
                trustColor(snapshot.motionTrust())));
        lines.add(new HudLine("采样时刻：" + snapshot.sampleTick(), WHITE));
        lines.add(new HudLine("服务端权威：" + snapshot.serverAuthoritative(), WHITE));
        lines.add(new HudLine("水平速度：" + format(snapshot.horizontalSpeed()), WHITE));
        lines.add(new HudLine("速度：(" + format(snapshot.velocityX()) + ", "
                + format(snapshot.velocityY()) + ", "
                + format(snapshot.velocityZ()) + ")", WHITE));
        lines.add(new HudLine("水平加速度：" + format(snapshot.horizontalAcceleration())
                + " 垂直加速度：" + format(snapshot.verticalAcceleration()), WHITE));
        lines.add(new HudLine("方向：(" + format(snapshot.directionX()) + ", "
                + format(snapshot.directionZ()) + ")", WHITE));
        lines.add(new HudLine("位移：(" + format(snapshot.displacementX()) + ", "
                + format(snapshot.displacementY()) + ", "
                + format(snapshot.displacementZ()) + ")", WHITE));
        lines.add(new HudLine("连续运动刻：" + snapshot.continuousMotionTicks()
                + " 着地：" + snapshot.onGround(), WHITE));
        lines.add(new HudLine("历史：" + snapshot.historyCount() + " / "
                + snapshot.historyCapacity(), WHITE));

        if (!snapshot.hasAction()) {
            lines.add(new HudLine("跑酷动作：NONE", UNKNOWN));
        } else {
            lines.add(new HudLine("跑酷动作：" + snapshot.actionType()
                    + " 阶段：" + snapshot.actionPhase(), WHITE));
            lines.add(new HudLine("动作来源：" + sourceName(snapshot.actionSource()),
                    trustColor(snapshot.actionTrust())));
            lines.add(new HudLine("动作可信度：" + trustName(snapshot.actionTrust()),
                    trustColor(snapshot.actionTrust())));
        }

        if (!snapshot.hasFall()) {
            lines.add(new HudLine("下落：无", UNKNOWN));
        } else {
            lines.add(new HudLine("下落阶段：" + snapshot.fallPhase(), WHITE));
            lines.add(new HudLine("下落距离：" + format(snapshot.fallDistance()), WHITE));
            lines.add(new HudLine("下落垂直速度：" + format(snapshot.fallVerticalSpeed()), WHITE));
            lines.add(new HudLine("下落来源：" + sourceName(snapshot.fallSource()),
                    trustColor(snapshot.fallTrust())));
            lines.add(new HudLine("下落可信度：" + trustName(snapshot.fallTrust()),
                    trustColor(snapshot.fallTrust())));
        }
        ClientboundCombatDebugSnapshot combat = combatSnapshot;
        if (combat == null) {
            lines.add(new HudLine("战斗攻击观察：尚未观察到", UNKNOWN));
        } else {
            lines.add(new HudLine("---------- 战斗攻击观察 ----------", HEADER_SERVER));
            lines.add(new HudLine("攻击请求：已观察到", HIGH));
            lines.add(new HudLine("攻击编号：" + combat.attackId(), WHITE));
            lines.add(new HudLine("攻击阶段：" + phaseName(combat.phase()), WHITE));
            lines.add(new HudLine("攻击结果：" + resultName(combat.result()), WHITE));
            lines.add(new HudLine("连击次数：" + combat.comboCurrent() + " / " + combat.comboTotal(), WHITE));
            lines.add(new HudLine("当前武器：" + empty(combat.weaponId()), WHITE));
            lines.add(new HudLine("攻击时动作：" + actionName(combat.actionType()), WHITE));
            lines.add(new HudLine("动作阶段：" + empty(combat.actionPhase()), WHITE));
            lines.add(new HudLine("运动采样时刻：" + combat.sampleTick(), WHITE));
            lines.add(new HudLine("采样说明：请求阶段运动快照", LOW));
            lines.add(new HudLine("关联可信度：有限", LOW));
            lines.add(new HudLine("水平速度：" + format(combat.horizontalSpeed()), WHITE));
            lines.add(new HudLine("垂直速度：" + format(combat.verticalSpeed()), WHITE));
            lines.add(new HudLine("请求目标数量：" + combat.requestedTargets(), WHITE));
            lines.add(new HudLine("已观察目标数量：" + combat.observedTargets(), WHITE));
            lines.add(new HudLine("目标覆盖：" + coverageName(combat.targetCoverage()), WHITE));
            lines.add(new HudLine("攻击事件：" + combat.attackEventCount(), WHITE));
            lines.add(new HudLine("伤害事件：" + (combat.damageEventCount() > 0
                    ? "已观察到 " + combat.damageEventCount() : "尚未观察到"), WHITE));
            lines.add(new HudLine("取消事件：" + combat.canceledEventCount(), WHITE));
            lines.add(new HudLine("迟到诊断：" + combat.lateEventCount(), WHITE));
            lines.add(new HudLine("事件明细截断：" + (combat.truncated() ? "是" : "否"), WHITE));
            lines.add(new HudLine("处理器状态：" + handlerName(combat.handlerState())
                    + " / " + empty(combat.handlerIdentity()), WHITE));
            lines.add(new HudLine("处理顺序：" + (combat.orderingKnown() ? "已确认" : "未确认"), WHITE));
            lines.add(new HudLine("攻击归属：无法确认", LOW));
            lines.add(new HudLine("实际生命值减少："
                    + (combat.healthReductionVerified() ? "已验证" : "未验证"), LOW));
            lines.add(new HudLine("数据状态：" + (isServerStale() ? "数据已过期" : "有效"),
                    isServerStale() ? UNKNOWN : HIGH));
        }
        return Collections.unmodifiableList(lines);
    }

    private static String sourceName(String value) {
        return switch (value == null ? "" : value) {
            case "CLIENT_LOCAL" -> "客户端本地数据";
            case "SERVER_NATIVE" -> "服务端原生数据";
            case "PARCOOL_SYNCED" -> "ParCool 同步数据";
            case "VANILLA_DERIVED" -> "原版派生数据";
            case "SERVER" -> "服务端观察数据";
            default -> "未知来源";
        };
    }

    private static String trustName(String value) {
        return switch (value == null ? "" : value) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            default -> "未知";
        };
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private static String actionName(String value) {
        return switch (value == null ? "" : value) {
            case "FAST_RUN" -> "快速奔跑";
            case "SLIDE" -> "滑铲";
            case "VAULT" -> "翻越";
            case "WALL_RUN", "WALL_RUN_HORIZONTAL", "WALL_RUN_VERTICAL" -> "墙面跑酷";
            case "WALL_JUMP" -> "蹬墙跳跃";
            case "NONE" -> "无特殊动作";
            default -> "未知动作";
        };
    }

    private static String phaseName(String value) {
        return switch (value == null ? "" : value) {
            case "REQUESTED" -> "已请求";
            case "PREPARED" -> "已准备";
            case "EXECUTING" -> "执行中";
            case "FINALIZED" -> "已结束";
            default -> "未知";
        };
    }

    private static String resultName(String value) {
        return switch (value == null ? "" : value) {
            case "REQUEST_RECEIVED" -> "请求已收到";
            case "NO_REQUESTED_TARGETS" -> "未请求目标";
            case "NO_ATTACK_EVENT_OBSERVED" -> "尚未观察到攻击事件";
            case "TARGET_OBSERVED" -> "已观察目标";
            case "DAMAGE_EVENT_OBSERVED" -> "已观察伤害事件";
            case "DAMAGE_AMOUNT_OBSERVED_POSITIVE" -> "已观察正伤害数值";
            default -> "未知";
        };
    }

    private static String coverageName(String value) {
        return switch (value == null ? "" : value) {
            case "COMPLETE" -> "完整";
            case "PARTIAL_TRUNCATED" -> "部分观察（目标截断）";
            case "PARTIAL_UNRESOLVED" -> "部分观察（目标未解析）";
            case "PARTIAL_PART" -> "部分观察（部件目标）";
            case "PARTIAL_TARGET_OVERFLOW" -> "部分观察（目标溢出）";
            default -> "未知";
        };
    }

    private static String handlerName(String value) {
        return switch (value == null ? "" : value) {
            case "INSTALLED" -> "已安装";
            case "DEGRADED_OR_UNKNOWN" -> "已安装但处理顺序未知";
            case "NOT_INSTALLED" -> "未安装";
            default -> "未知";
        };
    }

    private static int trustColor(String trust) {
        return switch (trust) {
            case "HIGH" -> HIGH;
            case "MEDIUM" -> MEDIUM;
            case "LOW" -> LOW;
            default -> UNKNOWN;
        };
    }

    private static int trustColor(ParkourTrust trust) {
        if (trust == null) {
            return UNKNOWN;
        }
        return switch (trust) {
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
            default -> UNKNOWN;
        };
    }
}
