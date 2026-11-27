package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerLargeThermalPowerStation;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.packet.LargeThermalPowerStationControlPacket;
import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public class GUILargeThermalPowerStation extends com.hbm.inventory.gui.GuiInfoContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MakeNTMGreatAgain.MODID,
            "textures/gui/gui_large_thermal_power_station.png");
    private static final int WIDTH = 176, HEIGHT = 202;
    private final TileEntityLargeThermalPowerStation te;

    public GUILargeThermalPowerStation(InventoryPlayer player, TileEntityLargeThermalPowerStation te) {
        super(new ContainerLargeThermalPowerStation(player, te));
        this.te = te; xSize = WIDTH; ySize = HEIGHT;
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, WIDTH, 94);
        drawTexturedModalRect(guiLeft, guiTop + 104, 0, 104, WIDTH, 98);
        int heat = (int)((long)te.heat * 70 / TileEntityLargeThermalPowerStation.MAX_HEAT);
        if(heat > 0) drawTexturedModalRect(guiLeft + 36, guiTop + 87 - heat, 176, 52 + 70 - heat, 4, heat);
        int power = (int)(te.power * 52 / TileEntityLargeThermalPowerStation.MAX_POWER);
        if(power > 0) drawTexturedModalRect(guiLeft + 152, guiTop + 69 - power, 176, 52 - power, 16, power);
        // 流体显示用 HBM 真实罐渲染（流体本色+贴图），不再硬编码色块
        te.tanks[0].renderTank(guiLeft + 68, guiTop + 69, this.zLevel, 16, 52);
        // renderTank 内部绑定了流体贴图，必须重绑界面贴图，否则后续开关图采样流体图集变黑
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        if(te.enabled) drawTexturedModalRect(guiLeft + 94, guiTop + 52, 192, 16, 35, 15);
        // 燃烧效果：燃烧中才把 (192,0) 的 16×16 火焰块覆盖到 (105,34)，停机不画
        if(te.enabled && te.heat >= te.heatPerTick())
            drawTexturedModalRect(guiLeft + 105, guiTop + 34, 192, 0, 16, 16);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partial) {
        super.drawScreen(mouseX, mouseY, partial);
        int x = mouseX - guiLeft, y = mouseY - guiTop;
        if(x >= 36 && x < 40 && y >= 17 && y < 87) {
            List<String> lines = new ArrayList<String>();
            lines.add(I18n.format("mnga.thermal_station.heat", te.heat, TileEntityLargeThermalPowerStation.MAX_HEAT));
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        }
        if(x >= 152 && x < 168 && y >= 17 && y < 69)
            drawElectricityInfo(this, mouseX, mouseY, guiLeft + 152, guiTop + 17, 16, 52, te.power, TileEntityLargeThermalPowerStation.MAX_POWER);
        te.tanks[0].renderTankInfo(this, mouseX, mouseY, guiLeft + 68, guiTop + 17, 16, 52);
        // 读数角标 (54,75) 9×9：当前发电量 / 热值消耗速度，类型同燃气轮机
        if(x >= 54 && x < 63 && y >= 75 && y < 84) {
            List<String> tooltip = new ArrayList<String>();
            tooltip.add(I18n.format("mnga.thermal_station.readout.power", te.lastProduction));
            tooltip.add(I18n.format("mnga.thermal_station.readout.heat", te.lastConsumption));
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
        if(x >= 94 && x < 129 && y >= 52 && y < 67)
            drawCreativeTabHoveringText(I18n.format(te.enabled ? "mnga.thermal_station.off" : "mnga.thermal_station.on"), mouseX, mouseY);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("mnga.thermal_station.gui_title"), 96, 7, 4210752);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, 108, 4210752);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) {
        int x = mouseX - guiLeft, y = mouseY - guiTop;
        if(button == 0 && x >= 94 && x < 129 && y >= 52 && y < 67) {
            Minecraft.getMinecraft().getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
                    new ResourceLocation("hbm:block.leverLarge"), 1.0F));
            ModNetwork.NETWORK.sendToServer(new LargeThermalPowerStationControlPacket(
                    te.xCoord, te.yCoord, te.zCoord, !te.enabled));
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }
}
