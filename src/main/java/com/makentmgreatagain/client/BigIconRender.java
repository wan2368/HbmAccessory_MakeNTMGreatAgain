package com.makentmgreatagain.client;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.init.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;

/**
 * 高强度碳化钨锭物品栏图标放大 20%。
 *
 * <p>1.7.10 的 Item 没有 GUI 渲染钩子，也不做 ASM 注入（项目约定注入易出 bug）。
 * 这里用 RenderItem 子类替换 {@link GuiContainer#itemRender} 与 {@link Gui#itemRender}
 * 两个实例字段（反射去 final，纯字段赋值不动字节码）：容器槽位与快捷栏都经这两个实例画图标。
 * 反射失败时静默回退原尺寸，不影响游戏。</p>
 */
@SideOnly(Side.CLIENT)
public final class BigIconRender {

    private BigIconRender() { }

    public static void install() {
        ScaledRenderItem scaled = new ScaledRenderItem();
        replace(GuiContainer.class, "itemRender", "field_146998_K", scaled);
        replace(Gui.class, "itemRender", "field_73733_i", scaled);
    }

    private static void replace(Class<?> cls, String mcp, String srg, RenderItem value) {
        for(String name : new String[] {mcp, srg}) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                java.lang.reflect.Field mods = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                mods.setAccessible(true);
                mods.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                f.set(null, value);
                return;
            } catch(Throwable ignored) {
                // 字段名不匹配或 final 去除失败：试下一个名字，最终回退原尺寸
            }
        }
    }

    private static class ScaledRenderItem extends RenderItem {

        /** 创造模式物品栏槽位密、页签边框紧贴，放大图标会溢出压边——该界面按原尺寸画 */
        private static boolean shouldScale(ItemStack stack) {
            if(stack == null || stack.getItem() != ModItems.powder_cobalt_tungsten_ingot) return false;
            return !(net.minecraft.client.Minecraft.getMinecraft().currentScreen
                    instanceof net.minecraft.client.gui.inventory.GuiContainerCreative);
        }

        @Override
        public void renderItemIntoGUI(FontRenderer fr, TextureManager tm, ItemStack stack, int x, int y, boolean renderEffect) {
            if(shouldScale(stack)) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x + 8, y + 8, 0);
                GL11.glScalef(1.2F, 1.2F, 1.2F);
                GL11.glTranslatef(-(x + 8), -(y + 8), 0);
                super.renderItemIntoGUI(fr, tm, stack, x, y, renderEffect);
                GL11.glPopMatrix();
            } else {
                super.renderItemIntoGUI(fr, tm, stack, x, y, renderEffect);
            }
        }

        @Override
        public void renderItemAndEffectIntoGUI(FontRenderer fr, TextureManager tm, ItemStack stack, int x, int y) {
            if(shouldScale(stack)) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x + 8, y + 8, 0);
                GL11.glScalef(1.2F, 1.2F, 1.2F);
                GL11.glTranslatef(-(x + 8), -(y + 8), 0);
                super.renderItemAndEffectIntoGUI(fr, tm, stack, x, y);
                GL11.glPopMatrix();
            } else {
                super.renderItemAndEffectIntoGUI(fr, tm, stack, x, y);
            }
        }
    }
}