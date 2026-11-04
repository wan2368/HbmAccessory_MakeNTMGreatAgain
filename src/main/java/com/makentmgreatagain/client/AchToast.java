package com.makentmgreatagain.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.achievement.AchDef;
import com.makentmgreatagain.achievement.AchRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * 右上角滑入的解锁提示。
 *
 * 完全自绘，不复用 Minecraft 原生的 Achievement 提示——
 * 本模组的成就本来就不在原生成就页里，用原生的弹窗反而对不上。
 * 队列一次只显示一条，播完自动接下一 条。
 */
@SideOnly(Side.CLIENT)
public class AchToast {

    /** 每条停留时长（毫秒）；滑入滑出各占 ANIM 毫秒 */
    private static final long DURATION = 3600L;
    private static final long ANIM = 220L;

    private static final int W = 168;
    private static final int H = 34;
    private static final int MARGIN = 6;

    private static final List<String> QUEUE = new ArrayList<String>();
    private static String current = null;
    private static long startTime = 0L;

    private static final RenderItem RENDER_ITEM = new RenderItem();

    public static void push(String id) {
        if (id != null) QUEUE.add(id);
    }

    /** 由客户端 tick 调用，负责推进队列 */
    public static void tick() {

        long now = System.currentTimeMillis();

        if (current == null) {
            if (!QUEUE.isEmpty()) {
                current = QUEUE.remove(0);
                startTime = now;
            }
            return;
        }

        if (now - startTime > DURATION) {
            current = null;
        }
    }

    /** 由 RenderGameOverlayEvent.Post 调用 */
    public static void render() {

        if (current == null) return;

        AchDef def = AchRegistry.get(current);
        if (def == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRenderer == null) return;

        long elapsed = System.currentTimeMillis() - startTime;

        // 滑入：从右侧屏幕外进来；滑出：最后 ANIM 毫秒再退回去
        float slide = 0F;
        if (elapsed < ANIM) {
            slide = 1F - easeOut(elapsed / (float) ANIM);
        } else if (elapsed > DURATION - ANIM) {
            slide = easeOut((elapsed - (DURATION - ANIM)) / (float) ANIM);
        }

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        int x = res.getScaledWidth() - W - MARGIN + (int) (slide * (W + MARGIN));
        int y = MARGIN;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);

        // 底：深色半透明 + 分类色左边条
        Gui.drawRect(x, y, x + W, y + H, 0xE0101014);
        Gui.drawRect(x, y, x + 2, y + H, categoryColor(def));
        Gui.drawRect(x + 2, y, x + W, y + 1, 0x40FFFFFF);
        Gui.drawRect(x, y + H - 1, x + W, y + H, 0x30000000);

        // 图标
        ItemStack icon = def.getIcon();
        if (icon != null) {
            RenderHelper.enableGUIStandardItemLighting();
            RENDER_ITEM.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), icon, x + 7, y + 9);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        int textX = x + 30;

        String unlockedLabel = StatCollector.translateToLocal("mntga.ach.toast.label");
        mc.fontRenderer.drawStringWithShadow(unlockedLabel, textX, y + 6, 0xFFFFD24A);

        String title = mc.fontRenderer.trimStringToWidth(def.getTitle(), W - 36);
        mc.fontRenderer.drawStringWithShadow(title, textX, y + 18, 0xFFFFFFFF);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static int categoryColor(AchDef def) {

        switch (def.category) {
            case TUNGSTEN: return 0xFFC9A227;
            case INDUSTRY: return 0xFF3E8FB0;
            default:       return 0xFF8E7CC3;
        }
    }

    private static float easeOut(float t) {
        if (t < 0F) return 0F;
        if (t > 1F) return 1F;
        return 1F - (1F - t) * (1F - t);
    }
}
