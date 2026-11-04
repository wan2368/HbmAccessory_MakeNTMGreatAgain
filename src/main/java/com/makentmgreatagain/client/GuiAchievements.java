package com.makentmgreatagain.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.achievement.AchDef;
import com.makentmgreatagain.achievement.AchDef.EnumAchCategory;
import com.makentmgreatagain.achievement.AchManager;
import com.makentmgreatagain.achievement.AchRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * 独立的成就界面。
 *
 * 不走原生成就页，所以布局自己定：三个分类横向并排，每类一个网格。
 * 已解锁正常显示，未解锁压一层暗色遮罩——图标轮廓仍能看见，给玩家「还有什么没拿到」的暗示。
 */
@SideOnly(Side.CLIENT)
public class GuiAchievements extends GuiScreen {

    /* 尺寸与格距 */
    private static final int GUI_W = 320;
    private static final int GUI_H = 168;
    private static final int CELL = 24;
    private static final int GAP = 4;
    private static final int PANEL_W = CELL * 3 + GAP * 2;     // 80
    private static final int PANEL_GAP = 8;

    private static final int COLOR_PANEL = 0xC818181C;
    private static final int COLOR_PANEL_EDGE = 0x40FFFFFF;
    private static final int COLOR_TEXT = 0xFFD8D8D8;
    private static final int COLOR_DIM = 0xFF7A7A82;

    private final RenderItem renderItem = new RenderItem();

    private int guiLeft;
    private int guiTop;

    @Override
    public void initGui() {
        this.guiLeft = (this.width - GUI_W) / 2;
        this.guiTop = (this.height - GUI_H) / 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        this.drawDefaultBackground();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);

        // 底板
        Gui.drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, 0xE0101014);
        drawBorder(guiLeft, guiTop, GUI_W, GUI_H, COLOR_PANEL_EDGE);

        // 标题
        String title = StatCollector.translateToLocal("mntga.ach.gui.title");
        this.fontRendererObj.drawStringWithShadow(title, guiLeft + 12, guiTop + 10, 0xFFFFD24A);

        int total = AchRegistry.size();
        int got = AchManager.unlockedCount(this.mc.thePlayer);

        String progress = got + " / " + total;
        this.fontRendererObj.drawStringWithShadow(progress,
                guiLeft + GUI_W - 12 - this.fontRendererObj.getStringWidth(progress), guiTop + 10, COLOR_DIM);

        // 顶部分隔线
        Gui.drawRect(guiLeft + 8, guiTop + 24, guiLeft + GUI_W - 8, guiTop + 25, 0x30FFFFFF);

        List<Object[]> hotspots = new ArrayList<Object[]>();

        int px = guiLeft + (GUI_W - (PANEL_W * 3 + PANEL_GAP * 2)) / 2;
        int py = guiTop + 34;

        for (EnumAchCategory cat : EnumAchCategory.values()) {

            drawCategory(cat, px, py, hotspots);

            px += PANEL_W + PANEL_GAP;
        }

        // tooltip 最后画，盖在格子上
        for (Object[] hot : hotspots) {

            int hx = (Integer) hot[0];
            int hy = (Integer) hot[1];

            if (mouseX >= hx && mouseX < hx + CELL && mouseY >= hy && mouseY < hy + CELL) {
                // 1.7.10 的 drawHoveringText 只接受 List<String>（新版本才有多参重载）
                this.func_146283_a(buildTooltip((AchDef) hot[2]), mouseX, mouseY);
                break;
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawCategory(EnumAchCategory cat, int px, int py, List<Object[]> hotspots) {

        // 分类名
        this.fontRendererObj.drawStringWithShadow(cat.getTitle(), px, py - 12, categoryColor(cat));

        int rows = 0;

        for (AchDef def : AchRegistry.byCategory(cat)) {

            int cx = px + def.col * (CELL + GAP);
            int cy = py + def.row * (CELL + GAP);

            rows = Math.max(rows, def.row + 1);

            boolean unlocked = AchManager.isUnlocked(this.mc.thePlayer, def.id);

            // 格底
            Gui.drawRect(cx, cy, cx + CELL, cy + CELL, unlocked ? COLOR_PANEL : 0xFF141418);
            drawBorder(cx, cy, CELL, CELL, unlocked ? 0x60FFFFFF : 0x28FFFFFF);

            // 图标
            ItemStack icon = def.getIcon();
            if (icon != null) {
                RenderHelper.enableGUIStandardItemLighting();
                this.renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj,
                        this.mc.getTextureManager(), icon, cx + 4, cy + 4);
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_LIGHTING);
            }

            if (!unlocked) {
                Gui.drawRect(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0xB0101014);
            }

            hotspots.add(new Object[] { cx, cy, def });
        }

        // 面板外框
        int panelH = rows * (CELL + GAP) - GAP;
        drawBorder(px - 4, py - 4, PANEL_W + 8, panelH + 8, COLOR_PANEL_EDGE);
    }

    private List<String> buildTooltip(AchDef def) {

        List<String> lines = new ArrayList<String>();

        boolean unlocked = AchManager.isUnlocked(this.mc.thePlayer, def.id);

        lines.add((unlocked ? EnumChatFormatting.YELLOW : EnumChatFormatting.GRAY) + def.getTitle());

        String desc = def.getDesc();
        if (desc != null && !desc.isEmpty() && !desc.startsWith("mntga.")) {
            lines.add(EnumChatFormatting.GRAY + desc);
        }

        lines.add(unlocked
                ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("mntga.ach.gui.done")
                : EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("mntga.ach.gui.locked"));

        return lines;
    }

    private void drawBorder(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + 1, color);
        Gui.drawRect(x, y + h - 1, x + w, y + h, color);
        Gui.drawRect(x, y, x + 1, y + h, color);
        Gui.drawRect(x + w - 1, y, x + w, y + h, color);
    }

    private int categoryColor(EnumAchCategory cat) {

        switch (cat) {
            case TUNGSTEN: return 0xFFC9A227;
            case INDUSTRY: return 0xFF3E8FB0;
            default:       return 0xFF8E7CC3;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
