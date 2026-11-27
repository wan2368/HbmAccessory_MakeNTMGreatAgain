package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerStellarator;
import com.makentmgreatagain.tileentity.TileEntityStellarator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * 仿星器 GUI（底图 {@code gui/gui_stellarator.png}，256×256）。
 *
 * <p>底图是三块并排的面板：左侧电力/电池（x 0~31）、中间主面板（x 39~174）、
 * 右侧冷却（x 183~228），下方背包面板（y 98~226）。三块之间是透明缝，
 * 整块 230×227 一次画出来即可，缝隙自然透出世界。</p>
 *
 * <p>液柱全部高 52、底边在 y=70：等离子体 44（<b>26 宽</b>）、增值 72、输出流体 134、
 * 增值后输出 152、水 188、超浓密蒸汽 206（其余 16 宽）。水与超浓密蒸汽都在右侧面板，
 * 一进一出相邻。电力条 8,18 16×52，素材取底图 (230,0) 16×52（上绿下蓝，自底向上截取）。</p>
 *
 * <p>底图 y=197 以下是废弃的草稿（一条绿色渐变残片与 L 形描边），不参与渲染，
 * 所以整图只画 230×197，背包面板到 y=196 为止。</p>
 */
public class GUIStellarator extends com.hbm.inventory.gui.GuiInfoContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/gui_stellarator.png");

	private static final int W = 230, H = 197;

	/** 液柱顶部 y、高度、底边 */
	private static final int TANK_Y = 18, TANK_H = 52;
	/** 电力条位置与素材：(230,0) 起 16×52 垂直渐变，上绿下蓝，自底向上填充 */
	private static final int POWER_X = 8, POWER_Y = 18, POWER_W = 16, POWER_H = 52;
	private static final int POWER_U = 230, POWER_V = 0;

	/** 启动读条：(116,82) 52×6，素材取底图 (20,217) 52×6，从左往右填充 */
	private static final int IGN_X = 116, IGN_Y = 82, IGN_W = 52, IGN_H = 6;
	private static final int IGN_U = 20, IGN_V = 217;

	/** 三格指示灯：(187/201/215,72)，亮起时贴 (246,0) 的 8×8 绿灯（内芯 6×6 正好盖住底图的红灯） */
	private static final int[] LIGHT_X = { 187, 201, 215 };
	private static final int LIGHT_Y = 72;
	private static final int LIGHT_U = 246, LIGHT_V = 0, LIGHT_W = 8, LIGHT_H = 8;

	/** 仿星器开关按钮：交互区 (206,104) 16×16；按下（开启）时在 (206,102) 贴 (230,52) 的 16×17 像素 */
	private static final int BTN_X = 206, BTN_Y = 104, BTN_W = 16, BTN_H = 16;
	private static final int BTN_ON_X = 206, BTN_ON_Y = 102, BTN_ON_U = 230, BTN_ON_V = 52, BTN_ON_W = 16, BTN_ON_H = 17;

	/** 中子进度条：(89,59) 72×20，填充素材取自底图 (20,197) 72×20，按通量比例从左往右填充 */
	private static final int NEUTRON_X = 89, NEUTRON_Y = 59, NEUTRON_W = 72, NEUTRON_H = 20;
	private static final int NEUTRON_U = 20, NEUTRON_V = 197;

	/** 六根液柱的左上角 x 与宽度，顺序与 {@link TileEntityStellarator} 的罐索引一致 */
	private static final int[] TANK_X = { 44, 72, 206, 134, 152, 188 };
	/** 等离子体那一格比其余宽 10 像素（底图 44~69 是 26 宽），渲染时按 UV 横向平铺 */
	private static final int[] TANK_W = { 26, 16, 16, 16, 16, 16 };

	private final TileEntityStellarator te;

	public GUIStellarator(InventoryPlayer invPlayer, TileEntityStellarator te) {
		super(new ContainerStellarator(invPlayer, te));
		this.te = te;
		this.xSize = W;
		this.ySize = H;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {

		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, W, H);

		// 六根液柱：renderTank 的 y 是液面底边，向上画
		for(int i = 0; i < TANK_X.length; i++) {
			this.te.tanks[i].renderTank(guiLeft + TANK_X[i], guiTop + TANK_Y + TANK_H, this.zLevel, TANK_W[i], TANK_H);
		}

		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 电力条：素材 (230,0) 16×52 是上绿下蓝的渐变，取底部 h 高一段贴到底部
		int h = this.te.getPowerScaled(POWER_H);
		if(h > 0) {
			this.drawTexturedModalRect(guiLeft + POWER_X, guiTop + POWER_Y + POWER_H - h,
					POWER_U, POWER_V + POWER_H - h, POWER_W, h);
		}

		// 启动读条：从左往右填充，读满后保持满格
		int ign = this.te.getIgnitionScaled(IGN_W);
		if(ign > 0) {
			this.drawTexturedModalRect(guiLeft + IGN_X, guiTop + IGN_Y, IGN_U, IGN_V, ign, IGN_H);
		}

		// 中子进度条：槽 2 有增殖物品时，按「已攒通量 / 该物品要求的通量」从左往右填充
		int neutron = this.te.getNeutronScaled(NEUTRON_W);
		if(neutron > 0) {
			this.drawTexturedModalRect(guiLeft + NEUTRON_X, guiTop + NEUTRON_Y,
					NEUTRON_U, NEUTRON_V, neutron, NEUTRON_H);
		}

		// 三格指示灯：条件满足才贴绿灯，否则保持底图原样
		GL11.glColor4f(1F, 1F, 1F, 1F);
		boolean[] lights = {
				this.te.hasPowerForRun(), this.te.hasEnoughWater(), this.te.hasEnoughPlasma() };
		for(int i = 0; i < LIGHT_X.length; i++) {
			if(!lights[i]) continue;
			this.drawTexturedModalRect(guiLeft + LIGHT_X[i], guiTop + LIGHT_Y,
					LIGHT_U, LIGHT_V, LIGHT_W, LIGHT_H);
		}

		// 开关按钮：按下（开启）时在 (206,102) 绘制 (230,52) 的 16×17 像素
		if(this.te.isOn) {
			this.drawTexturedModalRect(guiLeft + BTN_ON_X, guiTop + BTN_ON_Y,
					BTN_ON_U, BTN_ON_V, BTN_ON_W, BTN_ON_H);
		}
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);

		if(button == 0 && this.checkClick(x, y, BTN_X, BTN_Y, BTN_W, BTN_H)) {
			if(this.te.isOn || this.te.canTurnOn()) {
				mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
				NBTTagCompound data = new NBTTagCompound();
				data.setBoolean("toggle", true);
				PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, this.te.xCoord, this.te.yCoord, this.te.zCoord));
			}
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		for(int i = 0; i < TANK_X.length; i++) {
			this.te.tanks[i].renderTankInfo(this, mouseX, mouseY, guiLeft + TANK_X[i], guiTop + TANK_Y, TANK_W[i], TANK_H);
		}

		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + POWER_X, guiTop + POWER_Y, POWER_W, POWER_H,
				this.te.getPower(), this.te.getMaxPower());

		if(this.checkClick(mouseX, mouseY, BTN_X, BTN_Y, BTN_W, BTN_H)) {
			this.drawCreativeTabHoveringText(this.te.isOn ? "关闭机器" : "开启机器", mouseX, mouseY);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {

		String name = this.te.hasCustomInventoryName() ? this.te.getInventoryName()
				: I18n.format(this.te.getInventoryName());
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2 - 10, 6, 0xE8E8F0);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), ContainerStellarator.INV_X,
				ContainerStellarator.INV_Y - 12, 0x404040);
	}
}
