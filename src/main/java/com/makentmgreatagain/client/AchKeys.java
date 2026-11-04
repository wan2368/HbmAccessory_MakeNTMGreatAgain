package com.makentmgreatagain.client;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * 成就系统的客户端壳：开界面的按键、提示队列推进、右上角提示渲染。
 *
 * 默认键位 K，可在「选项 → 控制」里改。不占用 HBM 的 C（背包）和本模组的左 Ctrl（悬浮背包）。
 */
@SideOnly(Side.CLIENT)
public class AchKeys {

    public static final KeyBinding openKey =
            new KeyBinding("makentmgreatagain.key.achievements", Keyboard.KEY_K, "makentmgreatagain.key.group");

    public static void register() {
        ClientRegistry.registerKeyBinding(openKey);
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {

        if (!openKey.isPressed()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        mc.displayGuiScreen(new GuiAchievements());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;

        AchToast.tick();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {

        if (event.type != RenderGameOverlayEvent.ElementType.EXPERIENCE) return;

        AchToast.render();
    }
}
