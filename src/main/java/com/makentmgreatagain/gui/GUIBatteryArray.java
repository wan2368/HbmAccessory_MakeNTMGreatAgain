package com.makentmgreatagain.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerBatteryArray;
import com.makentmgreatagain.tileentity.TileEntityBatteryArray;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * 电池组控制器 GUI。
 *
 * <h2>布局（按贴图实测像素）</h2>
 * <ul>
 *   <li><b>控件整体右移 27</b>：电池槽 62,35；红石低/高按钮 133,16 与 133,52；优先级 152,35。</li>
 *   <li><b>电量条保持原位</b>：内部区域 89..122 × 17..68（34×52），精灵仍取 (176,0)。</li>
 *   <li><b>左侧信息区</b>：绿屏 10..48 × 18..66，四行文字左上顶点 (11,19)/(11,28)/(11,37)/(11,46)，
 *       每行高 7 像素、可用宽度 37 像素（超宽自动横向压缩，不溢出屏幕）。</li>
 * </ul>
 *
 * <p>数值单位走 HBM 的 {@link BobMathUtil#getShortNumber(long)}（k/M/G/T/P/E），与本体一致。</p>
 */
public class GUIBatteryArray extends GuiInfoContainer {

	private static final ResourceLocation texture =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/gui/gui_mnga_battery_.png");

	/** 控件右移量（用户指定 27 格）。 */
	private static final int SHIFT = 27;

	/** 电量条：保持原位置不动。 */
	private static final int BAR_X = 89, BAR_Y = 17, BAR_W = 34, BAR_H = 52;

	/** 左侧信息区：左上顶点与可用宽度。绿屏实测内宽约 58（x10..67），
	 *  留 2 像素边距取 56；字号按固定基准宽度算，不随数值跳动。 */
	private static final int INFO_X = 11, INFO_W = 56;
	/** 行距 11 像素：放大字号后末行 54+字高 仍在绿屏 18..67 内。 */
	private static final int[] INFO_Y = { 21, 32, 43, 54 };
	/** 亮绿色（与 HBM 屏幕风格一致）。 */
	private static final int INFO_COLOR = 0x33FF33;

	private final TileEntityBatteryArray array;

	public GUIBatteryArray(InventoryPlayer invPlayer, TileEntityBatteryArray te) {
		super(new ContainerBatteryArray(invPlayer, te));
		this.array = te;
		this.xSize = 176;
		this.ySize = 181;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		super.drawScreen(mouseX, mouseY, f);

		// 电量条悬浮：总量 + 每秒净变化
		long max = array.getMaxPower();
		if(max > 0) {
			String deltaText = BobMathUtil.getShortNumber(Math.abs(array.delta)) + "HE/s";
			if(array.delta > 0) deltaText = EnumChatFormatting.GREEN + "+" + deltaText;
			else if(array.delta < 0) deltaText = EnumChatFormatting.RED + "-" + deltaText;
			else deltaText = EnumChatFormatting.YELLOW + "+" + deltaText;

			String[] info = {
				BobMathUtil.getShortNumber(array.getPower()) + "/" + BobMathUtil.getShortNumber(max) + "HE",
				deltaText
			};
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft + BAR_X, guiTop + BAR_Y, BAR_W, BAR_H, mouseX, mouseY, info);
		}

		// 优先级悬浮（右移 27）
		String lang;
		switch(array.priority) {
			case LOW: lang = "low"; break;
			case HIGH: lang = "high"; break;
			default: lang = "normal"; break;
		}
		List<String> priority = new ArrayList<String>();
		priority.add(I18nUtil.resolveKey("battery.priority." + lang));
		priority.add(I18nUtil.resolveKey("battery.priority.recommended"));
		for(String s : I18nUtil.resolveKeyArray("battery.priority." + lang + ".desc")) priority.add(s);
		this.drawCustomInfoStat(mouseX, mouseY, guiLeft + 125 + SHIFT, guiTop + 35, 16, 16, mouseX, mouseY, priority);
	}

	@Override
	protected void mouseClicked(int x, int y, int i) {
		super.mouseClicked(x, y, i);

		NBTTagCompound data = new NBTTagCompound();
		if(this.checkClick(x, y, 106 + SHIFT, 16, 18, 18)) { this.click(); data.setBoolean("low", true); }
		if(this.checkClick(x, y, 106 + SHIFT, 52, 18, 18)) { this.click(); data.setBoolean("high", true); }
		if(this.checkClick(x, y, 125 + SHIFT, 35, 16, 16)) { this.click(); data.setBoolean("priority", true); }

		if(!data.hasNoTags()) PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, array.xCoord, array.yCoord, array.zCoord));
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {
		// 标题：电池矩阵，水平居中，行高 5
		String title = "电池矩阵";
		this.fontRendererObj.drawString(title,
				(this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 5, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);

		// ---- 左侧四行信息：统一字号、亮绿色 ----
		String state;
		if(array.delta > 0) state = "充电中...";
		else if(array.delta < 0) state = "放电中...";
		else state = "待机";

		String[] lines = {
			"总储电量:" + BobMathUtil.getShortNumber(array.getPower()) + "HE",
			"充电:" + BobMathUtil.getShortNumber(array.getStructCharge()) + "HE/t",
			"放电:" + BobMathUtil.getShortNumber(array.getStructDischarge()) + "HE/t",
			"当前状态:" + state
		};

		// 缩放比例必须恒定，不能随实时数值宽度变：
		// 早先按「当前最长行」算 scale，充电时总储电量数值位数一变（999HE→1.0kHE），
		// widest 跟着变 → scale 变 → 四行字号一起忽大忽小，看着就是来回跳动。
		// 现在按「标签+单位骨架 + 固定数值预留位宽」算，与实时数值无关，字号稳定。
		float scale = Math.min(1.0F, (float) INFO_W / (float) infoBaseWidth());

		for(int row = 0; row < lines.length; row++) {
			drawInfoLine(row, lines[row], scale);
		}
	}

	/**
	 * 左侧信息区的固定基准宽度：取四行「标签+单位」骨架的最宽者，
	 * 再预留一段固定数值位宽（按最长的常见数值串 "999.9k" 估）。
	 * 不读实时电量，保证 scale 恒定、字号不跳。
	 */
	private int infoBaseWidth() {
		String[] skeletons = { "总储电量:HE", "充电:HE/t", "放电:HE/t", "当前状态:待机" };
		int widest = 1;
		for(String s : skeletons) widest = Math.max(widest, this.fontRendererObj.getStringWidth(s));
		// 数值预留位按常见短串估，字号更大更 readable；极端长串（如 999.9k）
		// 由 drawInfoLine 的逐行宽度钳制兜底，不会溢出绿屏。
		int valueReserve = this.fontRendererObj.getStringWidth("0.0k");
		return widest + valueReserve;
	}

	/**
	 * 画一行信息：四行共用 {@code scale}，<b>横纵同比缩放</b>。
	 *
	 * <p>不再单独压纵向（早先乘 7/8 会把字压扁）；行距已放大到 11 像素，
	 * 原版 8 像素字高按 scale 缩放后不会与相邻行重叠。</p>
	 */
	private void drawInfoLine(int row, String text, float scale) {
		// 极端长串（数值位数暴涨）单独再压一点，保证不溢出绿屏；
		// 常态下 scale 由固定基准算出、四行一致，不随数值跳动。
		float s = scale;
		int w = this.fontRendererObj.getStringWidth(text);
		if(w * s > INFO_W) s = (float) INFO_W / (float) w;
		GL11.glPushMatrix();
		GL11.glTranslatef(INFO_X, INFO_Y[row], 0);
		GL11.glScalef(s, s, 1.0F);
		this.fontRendererObj.drawString(text, 0, 0, INFO_COLOR);
		GL11.glPopMatrix();
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float p1, int p2, int p3) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		// 电量条：位置不动
		long power = array.getPower();
		long maxPower = array.getMaxPower();
		if(maxPower > 0) {
			if(power > Long.MAX_VALUE / 100) { power /= 100; maxPower /= 100; }
			if(maxPower <= 1) maxPower = 1;
			int p = (int) (power * BAR_H / maxPower);
			if(p > 0) drawTexturedModalRect(guiLeft + BAR_X, guiTop + BAR_Y + BAR_H - p, 176, BAR_H - p, BAR_W, p);
		}

		// 三个控件：整体右移 27
		drawTexturedModalRect(guiLeft + 106 + SHIFT, guiTop + 16, 176, 52 + array.redLow * 18, 18, 18);
		drawTexturedModalRect(guiLeft + 106 + SHIFT, guiTop + 52, 176, 52 + array.redHigh * 18, 18, 18);
		drawTexturedModalRect(guiLeft + 125 + SHIFT, guiTop + 35, 194, 52 + array.priority.ordinal() * 16 - 16, 16, 16);
	}
}