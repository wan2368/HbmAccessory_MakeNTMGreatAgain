package com.makentmgreatagain.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.container.ContainerRiserRig;
import com.makentmgreatagain.tileentity.TileEntityRiserRig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * 海面立管钻机的 GUI，贴图由大型采矿钻机（挖掘机）的界面改绘而成。
 *
 * <p>左侧拉杆＝采掘：输入罐里必须是高性能溶剂且至少 8000 mB、并且底部有
 * 5×5×5 实心地基才能开启；不满足的那一条会在拉杆描述里以红字显示，
 * 点击不会切换也不会弹额外错误提示。右侧拉杆＝粉碎。</p>
 */
public class GUIRiserRig extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE =
		new ResourceLocation("makentmgreatagain:textures/gui/gui_riser_rig.png");

	private static final int CAN_X = 135;
	private static final int CAN_TANK_Y = 70;

	private final TileEntityRiserRig rig;

	public GUIRiserRig(InventoryPlayer invPlayer, TileEntityRiserRig te) {
		super(new ContainerRiserRig(invPlayer, te));
		this.rig = te;
		this.xSize = 242;
		this.ySize = 204;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		// 采掘拉杆：条件不满足的那一行用红字显示在同一条描述里
		if(this.isHoveringMining(mouseX, mouseY)) {
			this.drawMiningInfo(mouseX, mouseY);
		}
		// 粉碎拉杆：说明当前状态，切换后玩家才知道产物会变成混粉
		List<String> crusher = new ArrayList<String>();
		crusher.add(I18n.format("container.riser_rig.crusher"));
		crusher.add(I18n.format(this.rig.enableCrusher
				? "container.riser_rig.crusher.on" : "container.riser_rig.crusher.off"));
		this.drawCustomInfoStat(mouseX, mouseY, guiLeft + 30, guiTop + 42, 20, 40, mouseX, mouseY, crusher);

		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + 220, guiTop + 18, 16, 52,
				this.rig.getPower(), this.rig.getMaxPower());
		this.rig.inputTanks[0].renderTankInfo(this, mouseX, mouseY, guiLeft + 202, guiTop + 18, 16, 52);

		for(int i = 0; i < this.rig.outputTanks.length; i++) {
			this.rig.outputTanks[i].renderTankInfo(this, mouseX, mouseY, guiLeft + CAN_X + i * 18, guiTop + 18, 16, 52);
		}
	}

	private boolean isHoveringMining(int mouseX, int mouseY) {
		return mouseX >= guiLeft + 6 && mouseX < guiLeft + 6 + 20
				&& mouseY >= guiTop + 42 && mouseY < guiTop + 42 + 40;
	}

	private void drawMiningInfo(int mouseX, int mouseY) {

		List<String> info = new ArrayList<String>();
		info.add(I18n.format("container.riser_rig.mining"));

		String red = EnumChatFormatting.RED.toString();
		info.add((this.rig.hasEnoughSolvent() ? "" : red)
				+ I18n.format("container.riser_rig.mining.tip", this.rig.getMiningCost()));
		info.add((this.rig.hasSolidBase() ? "" : red)
				+ I18n.format("container.riser_rig.mining.base"));
		info.add((this.rig.outputFull ? red : "")
				+ I18n.format("container.riser_rig.mining.output"));
		info.add(I18n.format("container.riser_rig.mining.power", this.rig.getPowerConsumption()));
		// 每颗结核的溶剂消耗单独一行：开矿门槛只要 8000mB，采一颗却要 16000mB，不够时会停在 100% 等补液
		info.add((this.rig.hasOreSolvent() ? "" : red)
				+ I18n.format("container.riser_rig.mining.ore_solvent", TileEntityRiserRig.ORE_SOLVENT_COST));

		// 开采进度：计时器有没有在走，一眼能看出「停在矿上却不产出」这类问题。
		// 分母用 getOreMiningInterval()（已含速度/节能升级倍率），不能用 ORE_MINING_INTERVAL 常量，
		// 否则插了速度芯片还是一轮 400 tick 地爬，和实际产出对不上。
		if(this.rig.enableMining) {
			if(this.rig.hasOreTarget) {
				int interval = Math.max(1, this.rig.getOreMiningInterval());
				int pct = (int) (this.rig.oreTimer * 100L / interval);
				info.add(I18n.format("container.riser_rig.mining.progress",
						Math.min(100, pct), String.format(Locale.US, "%.1f", interval / 20.0D)));
			} else {
				info.add(I18n.format("container.riser_rig.mining.nodule"));
			}
		}

		this.drawCustomInfoStat(mouseX, mouseY, guiLeft + 6, guiTop + 42, 20, 40, mouseX, mouseY, info);
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);

		String toggle = null;

		if(guiLeft + 6 <= x && guiLeft + 6 + 20 > x && guiTop + 42 <= y && guiTop + 42 + 40 >= y) toggle = "mining";
		if(guiLeft + 30 <= x && guiLeft + 30 + 20 > x && guiTop + 42 <= y && guiTop + 42 + 40 >= y) toggle = "crusher";

		if(toggle != null) {

			// 采掘开启前先本地检查门槛；不满足就什么也不做（提示在拉杆描述里）
			// 物品输出格没有同步到客户端，所以用一个服务端算好的 outputFull 标志补上
			if("mining".equals(toggle) && !this.rig.enableMining
					&& (!this.rig.canStartMining() || this.rig.outputFull)) {
				return;
			}

			mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("hbm:block.leverLarge"), 1.0F));
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean(toggle, true);
			PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, rig.xCoord, rig.yCoord, rig.zCoord));
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8 + 33, this.ySize - 96 + 2, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		drawTexturedModalRect(guiLeft, guiTop, 0, 0, 242, 96);
		drawTexturedModalRect(guiLeft + 33, guiTop + 104, 33, 104, 176, 100);

		// 电力条（右上角的闪电图标是贴图自带的，不再叠加挖掘机的电池图标）
		int i = (int) (this.rig.getPower() * 52L / this.rig.getMaxPower());
		drawTexturedModalRect(guiLeft + 220, guiTop + 70 - i, 229, 156 - i, 16, i);

		// 两个拉杆的激活叠加（贴图中位置与挖掘机一致）；拉杆上方的指示灯激活时变绿
		if(this.rig.enableMining) {
			drawTexturedModalRect(guiLeft + 6, guiTop + 42, 209, 114, 20, 40);
			drawTexturedModalRect(guiLeft + 11, guiTop + 5, 209, 104, 10, 10);
		}
		if(this.rig.enableCrusher) {
			drawTexturedModalRect(guiLeft + 30, guiTop + 42, 209, 114, 20, 40);
			drawTexturedModalRect(guiLeft + 35, guiTop + 5, 209, 104, 10, 10);
		}

		// 流体槽（最后画，renderTank 会切换贴图并设置颜色）
		this.rig.inputTanks[0].renderTank(guiLeft + 202, guiTop + CAN_TANK_Y, this.zLevel, 16, 52);
		for(int t = 0; t < this.rig.outputTanks.length; t++) {
			this.rig.outputTanks[t].renderTank(guiLeft + CAN_X + t * 18, guiTop + CAN_TANK_Y, this.zLevel, 16, 52);
		}
	}
}
