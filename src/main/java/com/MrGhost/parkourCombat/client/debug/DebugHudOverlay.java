package com.MrGhost.parkourCombat.client.debug;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

@OnlyIn(Dist.CLIENT)
public final class DebugHudOverlay implements IGuiOverlay {
    private static final int CLIENT_HEADER = 0xFF55FFFF;
    private static final int SERVER_HEADER = 0xFFFFD060;
    private static final int BACKGROUND = 0xAA101010;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientDebugTick.STATE.isEnabled() || minecraft.player == null) {
            return;
        }

        Font font = minecraft.font;
        boolean sideBySide = width >= 360;
        int gap = 4;
        int panelWidth = sideBySide
                ? Math.max(150, Math.min(420, (width - gap * 3) / 2))
                : Math.max(120, width - 8);
        int leftX = sideBySide ? gap : 4;
        int rightX = sideBySide ? width - panelWidth - gap : 4;
        int maxLines = sideBySide
                ? Math.max(1, (height - 20) / 9 - 2)
                : Math.max(1, (height - 28) / 2 / 9 - 2);
        int page = ClientDebugTick.STATE.detailPage();
        int offset = ClientDebugTick.STATE.isCompact() ? 0 : page * maxLines;

        drawPanel(graphics, font, leftX, 4, panelWidth, maxLines, offset,
                "客户端本地数据", CLIENT_HEADER,
                ClientDebugTick.STATE.getClientLines());
        int serverY = sideBySide ? 4 : 4 + 18 + maxLines * 9 + 8;
        drawPanel(graphics, font, rightX, serverY, panelWidth, maxLines, offset,
                "服务端观察数据" + (ClientDebugTick.STATE.isCompact() ? "（紧凑）" : "（详细）"),
                SERVER_HEADER, ClientDebugTick.STATE.getServerLines());
    }

    private static void drawPanel(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int maxLines, int offset,
                                  String title, int titleColor,
                                  List<HudLine> lines) {
        int lineHeight = 9;
        int visible = Math.min(maxLines, Math.max(1, lines.size() - offset));
        int panelHeight = 14 + Math.max(1, visible) * lineHeight;
        graphics.fill(x - 1, y - 1, x + width + 1, y + panelHeight, BACKGROUND);
        graphics.drawString(font, trim(font, title, width - 4), x + 2, y, titleColor, false);
        if (lines.isEmpty()) {
            graphics.drawString(font, "无数据", x + 2, y + 12, 0xFFFF5555, false);
            return;
        }
        int lineY = y + 12;
        int end = Math.min(lines.size(), offset + maxLines);
        for (int i = offset; i < end; i++) {
            HudLine line = lines.get(i);
            graphics.drawString(font, trim(font, line.text(), width - 4),
                    x + 2, lineY, line.color(), false);
            lineY += lineHeight;
        }
        if (lines.size() > maxLines) {
            String hint = "第 " + (offset / Math.max(1, maxLines) + 1) + " 页 · F7/F8 翻页";
            graphics.drawString(font, trim(font, hint, width - 4), x + 2,
                    y + panelHeight - 8, 0xFFFFAA00, false);
        }
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
