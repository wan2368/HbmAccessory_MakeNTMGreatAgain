package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.gui.GuiInfoContainerProcessor;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.module.machine.ModuleMachineBase;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerLargeCatalyticChemicalFactory;
import com.makentmgreatagain.tileentity.ModuleLargeChemicalFactory;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.nbt.NBTTagCompound;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;

/**
 * 大型催化化工厂界面。
 *
 * <p>贴图为 286×256，面板整体右移 29 像素，左侧 29 像素留给内部温度条、
 * 两个电极槽和加热指示。坐标均以 GUI 左上角为原点。</p>
 */
public class GUILargeCatalyticChemicalFactory extends GuiInfoContainerProcessor {

	/** 高并行模式贴图，按催化剂等级切换：0 无 / 1 原版催化重整 / 2 铂 / 3 CMB */
	private static final ResourceLocation[] TEXTURES = new ResourceLocation[] {
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory0.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory1.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory2.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory3.png")
	};

	/** 多配方模式贴图（_m 系列），同样按催化剂等级切换 */
	private static final ResourceLocation[] TEXTURES_MULTI = new ResourceLocation[] {
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory_m0.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory_m1.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory_m2.png"),
			new ResourceLocation(MakeNTMGreatAgain.MODID + ":textures/gui/gui_large_catalytic_chemical_factory_m3.png")
	};

	/** 4 个输入流体条的左上角（整体左移 1 像素对齐底图） */
	private static final int[][] IN_TANK = {{107, 15}, {116, 15}, {107, 59}, {116, 59}};
	/** 4 个输出流体条的左上角 */
	private static final int[][] OUT_TANK = {{213, 15}, {222, 15}, {213, 59}, {222, 59}};

	private static final int TANK_W = 7;
	private static final int TANK_H = 38;

	/**
	 * MF（多配方）模式：输入/输出各 8 路，条高 16（宽度不变）。
	 *
	 * <p>前 4 路沿用原位置，只是高度从 38 缩到 16；后 4 路是新增的。</p>
	 */
	private static final int[][] IN_TANK_MF = {
			{107, 15}, {116, 15}, {107, 59}, {116, 59},
			{107, 37}, {116, 37}, {107, 81}, {116, 81}};
	private static final int[][] OUT_TANK_MF = {
			{213, 15}, {222, 15}, {213, 59}, {222, 59},
			{213, 37}, {222, 37}, {213, 81}, {222, 81}};
	private static final int TANK_H_MF = 16;

	/** 按当前模式取输入流体条坐标 */
	private int[][] inTanks() {
		return this.chemfac.multiRecipeMode ? IN_TANK_MF : IN_TANK;
	}

	/** 按当前模式取输出流体条坐标 */
	private int[][] outTanks() {
		return this.chemfac.multiRecipeMode ? OUT_TANK_MF : OUT_TANK;
	}

	/** 按当前模式取流体条高度 */
	private int tankH() {
		return this.chemfac.multiRecipeMode ? TANK_H_MF : TANK_H;
	}

	/** 内部温度条 */
	private static final int HEAT_X = 6, HEAT_Y = 18, HEAT_W = 13, HEAT_H = 68;
	/** 加热指示（火焰） */
	private static final int FIRE_X = 4, FIRE_Y = 90;

	/** 模式旋钮：界面位置与尺寸。外观由服务端模式决定，不再本地覆盖贴图 */
	private static final int KNOB_X = 234, KNOB_Y = 194, KNOB_W = 36, KNOB_H = 15;

	private final TileEntityLargeCatalyticChemicalFactory chemfac;

	/**
	 * 贴图宽度不是 256（现在是 286），而 MC 的 drawTexturedModalRect 固定按 1/256 采样 U，
	 * 会把整张图横向压扁。这里按 GL 里实际纹理宽度换算，并覆盖绘制方法让基类也走同一套 UV。
	 */
	private float texU = 1F / 256F;

	public GUILargeCatalyticChemicalFactory(InventoryPlayer invPlayer, TileEntityLargeCatalyticChemicalFactory tile) {
		super(new ContainerLargeCatalyticChemicalFactory(invPlayer, tile));
		this.chemfac = tile;

		this.xSize = 277;
		this.ySize = 226;
		this.processorModule = new ModuleMachineBase[] { tile.module };
	}

	@Override
	public int[][] getSelectorPositions() {
		return new int[][] {{131, 80, TileEntityLargeCatalyticChemicalFactory.SLOT_BLUEPRINT}};
	}

	@Override
	public IControlReceiver getControlReceiver() {
		return this.chemfac;
	}

	@Override
	public ResourceLocation getTexture() {
		ResourceLocation[] set = this.chemfac.multiRecipeMode ? TEXTURES_MULTI : TEXTURES;
		return set[MathHelper.clamp_int(this.chemfac.catalystType, 0, set.length - 1)];
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		super.drawScreen(mouseX, mouseY, f);

		int[][] in = inTanks();
		int[][] out = outTanks();
		int th = tankH();
		for(int i = 0; i < in.length; i++) {
			chemfac.inputTanks[i].renderTankInfo(this, mouseX, mouseY, guiLeft + in[i][0], guiTop + in[i][1], TANK_W, th);
			chemfac.outputTanks[i].renderTankInfo(this, mouseX, mouseY, guiLeft + out[i][0], guiTop + out[i][1], TANK_W, th);
		}

		chemfac.coolant.renderTankInfo(this, mouseX, mouseY, guiLeft + 253, guiTop + 125, 7, 52);
		chemfac.coolantOut.renderTankInfo(this, mouseX, mouseY, guiLeft + 262, guiTop + 125, 7, 52);

		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + 253, guiTop + 18, 16, 68, chemfac.power, chemfac.maxPower);

		if(this.chemfac.targetHeat > 0 && mouseX >= guiLeft + HEAT_X && mouseX < guiLeft + HEAT_X + HEAT_W
				&& mouseY >= guiTop + HEAT_Y && mouseY < guiTop + HEAT_Y + HEAT_H) {
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft + HEAT_X, guiTop + HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY,
					new String[] { "Heat: " + (int) this.chemfac.heat + " / " + this.chemfac.targetHeat + " TU" });
		}

		// 模式旋钮说明：第一行是将要切到的模式，第二行是当前模式，空行后是该模式的能力
		if(this.chemfac.multiRecipeMode) {
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft + KNOB_X, guiTop + KNOB_Y, KNOB_W, KNOB_H, mouseX, mouseY,
					new String[] {
						EnumChatFormatting.YELLOW + "切换为：高并行模式",
						EnumChatFormatting.GRAY + "当前为“多配方模式”",
						"",
						EnumChatFormatting.WHITE + "最多处理5个配方，最大16x并行"
					});
		} else {
			this.drawCustomInfoStat(mouseX, mouseY, guiLeft + KNOB_X, guiTop + KNOB_Y, KNOB_W, KNOB_H, mouseX, mouseY,
					new String[] {
						EnumChatFormatting.YELLOW + "切换为：多配方模式",
						EnumChatFormatting.GRAY + "当前为“高并行模式”",
						"",
						EnumChatFormatting.WHITE + "只有一个处理配方，但最高64x并行"
					});
		}
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {

		// MF 模式：配方选择走附属专属界面，必须在 super 之前拦下，
		// 否则基类会先打开原版选择器
		if(this.chemfac.multiRecipeMode) {
			int[][] selectors = this.getSelectorPositions();
			for(int[] sel : selectors) {
				if(this.checkClick(x, y, sel[0], sel[1], 18, 18)) {
					this.click();
					GUIScreenMultiRecipeSelector.openSelector(
							this.chemfac.module.getRecipeSet(), this.chemfac,
							this.chemfac.multiSelections,
							com.hbm.items.machine.ItemBlueprints.grabPool(
									this.inventorySlots.getSlot(sel[2]).getStack()),
							this);
					return;
				}
			}
		}

		super.mouseClicked(x, y, button);

		// 模式旋钮：发包给服务端切换，服务端会清空全部输入输出流体
		if(this.checkClick(x, y, KNOB_X, KNOB_Y, KNOB_W, KNOB_H)) {
			this.click();
			NBTTagCompound data = new NBTTagCompound();
			data.setBoolean("mode", true);
			PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data,
					chemfac.xCoord, chemfac.yCoord, chemfac.zCoord));
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {

		String name = this.chemfac.hasCustomInventoryName() ? this.chemfac.getInventoryName() : I18nUtil.resolveKey(this.chemfac.getInventoryName());
		this.fontRendererObj.drawString(name, 153 - this.fontRendererObj.getStringWidth(name) / 2, 2, 4210752);
		this.fontRendererObj.drawString(I18nUtil.resolveKey("container.inventory"), 55, this.ySize - 104, 4210752);
	}

	@Override
	public void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {

		float fv = 1F / 256F;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertexWithUV(x, y + h, this.zLevel, u * texU, (v + h) * fv);
		tess.addVertexWithUV(x + w, y + h, this.zLevel, (u + w) * texU, (v + h) * fv);
		tess.addVertexWithUV(x + w, y, this.zLevel, (u + w) * texU, v * fv);
		tess.addVertexWithUV(x, y, this.zLevel, u * texU, v * fv);
		tess.draw();
	}

	/**
	 * 输入被均分到多组时，每一组的第一格也画出配方幽灵物品，和流体条一样能看出“分了几份”。
	 */
	@Override
	protected void renderRecipeIcons() {

		GenericRecipe current = this.chemfac.module.getRecipe();

		// MF：16 格全部独立，按种类均分片区，片区内每格都画该原料
		if(this.chemfac.multiRecipeMode) {
			renderRecipeIconsMF(current);
			return;
		}

		int kinds = current != null && current.inputItem != null ? current.inputItem.length : 0;
		int span = ModuleLargeChemicalFactory.spanFor(kinds);

		// 客户端不会跑 module.update，这里按当前配方把每种原料的起始槽刷一遍，
		// 否则基类会把幽灵物品画到错误的槽位上
		for(int kind = 0; kind < kinds && kind < ModuleLargeChemicalFactory.GROUPS; kind++) {
			this.chemfac.module.inputSlots[kind] = TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT
					+ kind * span * TileEntityLargeCatalyticChemicalFactory.INPUT_SLOTS;
		}

		super.renderRecipeIcons();

		if(kinds == 0 || span <= 1) return;

		// 第 0 组由基类画过了，这里先补其余组的物品图标
		for(int kind = 0; kind < kinds; kind++) {
			AStack ingredient = current.inputItem[kind];
			if(ingredient == null) continue;

			for(int group = 1; group < span; group++) {
				Slot slot = this.extraGroupSlot(kind, group, span);
				if(slot == null || slot.getHasStack()) continue;

				this.renderItem(ingredient.extractForCyclingDisplay(20), slot.xDisplayPosition, slot.yDisplayPosition, 10F);
			}
		}

		// 再统一画半透明槽底，避免物品图标切换贴图后把槽底画成物品图集
		Minecraft.getMinecraft().getTextureManager().bindTexture(this.getTexture());
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		GL11.glColor4f(1F, 1F, 1F, 0.5F);
		GL11.glEnable(GL11.GL_BLEND);
		this.zLevel = 300F;

		for(int kind = 0; kind < kinds; kind++) {
			AStack ingredient = current.inputItem[kind];
			if(ingredient == null) continue;

			for(int group = 1; group < span; group++) {
				Slot slot = this.extraGroupSlot(kind, group, span);
				if(slot == null || slot.getHasStack()) continue;

				drawTexturedModalRect(guiLeft + slot.xDisplayPosition, guiTop + slot.yDisplayPosition,
						slot.xDisplayPosition, slot.yDisplayPosition, 16, 16);
			}
		}

		this.zLevel = 0F;
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_BLEND);
	}

	/**
	 * MF 幽灵物品：16 个输入格按种类均分成连续片区，<b>每格都画该原料</b>，
	 * 所以整片 16 格都会闪烁。已有物品的格子不画。
	 */
	private void renderRecipeIconsMF(GenericRecipe current) {

		// 先画配方选择格的图标（基类本来在 renderRecipeIcons 里画，
		// MF 分支绕过了基类，必须自己补，否则那个格子是空白）
		int[][] selectors = this.getSelectorPositions();
		for(int[] sel : selectors) {
			this.renderItem(current != null ? current.getIcon() : TEMPLATE_FOLDER, sel[0] + 1, sel[1] + 1);
		}

		int kinds = current != null && current.inputItem != null ? current.inputItem.length : 0;
		if(kinds == 0) return;

		int inCount = TileEntityLargeCatalyticChemicalFactory.INPUT_TYPES
				* TileEntityLargeCatalyticChemicalFactory.INPUT_SLOTS;

		for(int i = 0; i < inCount; i++) {
			int index = TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT + i;
			if(index >= this.inventorySlots.inventorySlots.size()) break;

			Slot slot = (Slot) this.inventorySlots.inventorySlots.get(index);
			if(slot == null || slot.getHasStack()) continue;

			int kind = TileEntityLargeCatalyticChemicalFactory.mfKindOfSlot(i, kinds);
			if(kind < 0) continue;
			AStack ingredient = current.inputItem[kind];
			if(ingredient == null) continue;

			this.renderItem(ingredient.extractForCyclingDisplay(20), slot.xDisplayPosition, slot.yDisplayPosition, 10F);
		}

		// 统一补半透明槽底，避免图标切换贴图后槽底变成物品图集
		Minecraft.getMinecraft().getTextureManager().bindTexture(this.getTexture());
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		GL11.glColor4f(1F, 1F, 1F, 0.5F);
		GL11.glEnable(GL11.GL_BLEND);
		this.zLevel = 300F;

		for(int i = 0; i < inCount; i++) {
			int index = TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT + i;
			if(index >= this.inventorySlots.inventorySlots.size()) break;
			Slot slot = (Slot) this.inventorySlots.inventorySlots.get(index);
			if(slot == null || slot.getHasStack()) continue;

			drawTexturedModalRect(guiLeft + slot.xDisplayPosition, guiTop + slot.yDisplayPosition,
					slot.xDisplayPosition, slot.yDisplayPosition, 16, 16);
		}

		this.zLevel = 0F;
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_BLEND);
	}

	/** 某种原料第 {@code group} 组第一格的槽位 */
	private Slot extraGroupSlot(int kind, int group, int span) {
		int index = TileEntityLargeCatalyticChemicalFactory.SLOT_INPUT
				+ (kind * span + group) * TileEntityLargeCatalyticChemicalFactory.INPUT_SLOTS;
		if(index < 0 || index >= this.inventorySlots.inventorySlots.size()) return null;
		return (Slot) this.inventorySlots.inventorySlots.get(index);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {

		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(this.getTexture());

		int texWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		this.texU = 1F / (texWidth > 0 ? texWidth : 256F);

		// 贴图左侧与底部画着状态条素材，分三块绘制避开它们
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, 116);
		drawTexturedModalRect(guiLeft, guiTop + 116, 0, 116, 47, 26);
		drawTexturedModalRect(guiLeft + 47, guiTop + 116, 47, 116, xSize - 47, 99);

		// 电力条：贴图 (0,171) 16×68，从下往上裁
		int p = (int) (this.chemfac.power * 68 / Math.max(1L, this.chemfac.maxPower));
		if(p > 0) drawTexturedModalRect(guiLeft + 253, guiTop + 18 + 68 - p, 0, 171 + 68 - p, 16, p);

		// 内部温度条：贴图 (16,171) 13×68
		if(this.chemfac.targetHeat > 0) {
			int h = (int) Math.min(HEAT_H, this.chemfac.heat * HEAT_H / this.chemfac.targetHeat);
			if(h > 0) drawTexturedModalRect(guiLeft + HEAT_X, guiTop + HEAT_Y + HEAT_H - h, 16, 171 + HEAT_H - h, HEAT_W, h);
		}

		// 进度条：贴图 (0,161) 32×6
		int w = (int) Math.ceil(this.chemfac.module.progress * 32);
		if(w > 0) drawTexturedModalRect(guiLeft + 134, guiTop + 72, 0, 161, w, 6);

		// 指示灯：绿灯=正在制作 (4,167)，黄灯=有配方但输出满 (0,167)
		GenericRecipe recipe = this.chemfac.module.getRecipe();
		if(recipe != null) {
			if(this.chemfac.didProcess) {
				drawTexturedModalRect(guiLeft + 142, guiTop + 64, 4, 167, 4, 4);
			} else if(this.chemfac.outputFull) {
				drawTexturedModalRect(guiLeft + 134, guiTop + 64, 0, 167, 4, 4);
			}
		}

		// 加热指示：贴图 (0,143) 18×18
		if(this.chemfac.heatingUp) {
			drawTexturedModalRect(guiLeft + FIRE_X, guiTop + FIRE_Y, 0, 143, 18, 18);
		}

		// 流体条：FF 4 路高 38，MF 8 路高 16
		int[][] inBg = inTanks();
		int[][] outBg = outTanks();
		int thBg = tankH();
		for(int i = 0; i < inBg.length; i++) {
			chemfac.inputTanks[i].renderTank(guiLeft + inBg[i][0], guiTop + inBg[i][1] + thBg, this.zLevel, TANK_W, thBg);
			chemfac.outputTanks[i].renderTank(guiLeft + outBg[i][0], guiTop + outBg[i][1] + thBg, this.zLevel, TANK_W, thBg);
		}

		chemfac.coolant.renderTank(guiLeft + 253, guiTop + 177, this.zLevel, 7, 52);
		chemfac.coolantOut.renderTank(guiLeft + 262, guiTop + 177, this.zLevel, 7, 52);

		// 配方图标 + 空槽幽灵物品
		this.renderRecipeIcons();
	}
}
