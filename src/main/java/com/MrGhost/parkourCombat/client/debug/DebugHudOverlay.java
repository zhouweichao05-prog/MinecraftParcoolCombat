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
        int panelWidth = Math.max(190, Math.min(360, (width - 24) / 2));
        int leftX = 8;
        int rightX = width - panelWidth - 8;
        int y = 8;

        drawPanel(graphics, font, leftX, y, panelWidth,
                "客户端本地数据", CLIENT_HEADER,
                ClientDebugTick.STATE.getClientLines());
        drawPanel(graphics, font, rightX, y, panelWidth,
                "服务端观察数据", SERVER_HEADER,
                ClientDebugTick.STATE.getServerLines());
    }

    private static void drawPanel(GuiGraphics graphics, Font font,
                                  int x, int y, int width,
                                  String title, int titleColor,
                                  List<HudLine> lines) {
        int lineHeight = 10;
        int panelHeight = 16 + Math.max(1, lines.size()) * lineHeight;
        graphics.fill(x - 2, y - 2, x + width + 2, y + panelHeight, BACKGROUND);
        graphics.drawString(font, title, x + 2, y, titleColor, false);
        int lineY = y + 14;
        if (lines.isEmpty()) {
            graphics.drawString(font, "无数据", x + 2, lineY, 0xFFFF5555, false);
            return;
        }
        for (HudLine line : lines) {
            graphics.drawString(font, line.text(), x + 2, lineY, line.color(), false);
            lineY += lineHeight;
        }
    }
}
