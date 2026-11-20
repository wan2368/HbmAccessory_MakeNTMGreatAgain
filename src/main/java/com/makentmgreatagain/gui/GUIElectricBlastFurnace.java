package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerElectricBlastFurnace;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 电力高炉界面。
 *
 * <p>底图 {@code gui_electric_blast_furnace.png}，面板 294×240。
 * 电力/热量/熔融物三条的<b>效果素材</b>藏在底图左下（UI 不直接画那块区域）：
 * 电力 (22,121) 16×70、热量 (9,121) 13×70、熔融物 (38,121) 34×70，
 * 均从下往上按存量裁剪。开始加热时把 (54,200) 的 18×18 熔炼指示盖到 (184,69)。</p>
 */
public class GUIElectricBlastFurnace extends GuiInfoContainer {

	private static final ResourceLocation texture = new ResourceLocation(MakeNTMGreatAgain.MODID,
			"textures/gui/gui_arc_furnace.png");

	/** 坩埚贴图：熔融物分层堆叠用它的白色素材着金属色 */
	private static final ResourceLocation crucibleTexture = new ResourceLocation("hbm",
			"textures/gui/processing/gui_crucible.png");

	private final TileEntityElectricBlastFurnace furnace;

	/** 面板尺寸（贴图 320×256，面板实测 x9..298 / y0..220） */
	private static final int X_SIZE = 299;
	private static final int Y_SIZE = 221;

	/** 电力条 (44,21) 16×70，效果素材 (22,121) */
	private static final int POWER_X = 44, POWER_Y = 21, POWER_W = 16, POWER_H = 70;
	private static final int POWER_U = 22, POWER_V = 121;
	/** 热量条 (169,39) 13×70，效果素材 (9,121) */
	private static final int HEAT_X = 169, HEAT_Y = 39, HEAT_W = 13, HEAT_H = 70;
	private static final int HEAT_U = 9, HEAT_V = 121;
	/** 熔融物条 (204,39) 34×70，效果素材 (38,121) */
	private static final int MOLTEN_X = 204, MOLTEN_Y = 39, MOLTEN_W = 34, MOLTEN_H = 70;
	private static final int MOLTEN_U = 38, MOLTEN_V = 121;
	/** 熔炼指示：加热时把 (54,200) 18×18 盖到 (184,69) */
	private static final int FIRE_X = 184, FIRE_Y = 69;
	private static final int FIRE_U = 54, FIRE_V = 200;

	/** 底图实际宽度对应的 U 换算（贴图可能不是 256 宽） */
	private float texU = 1F / 256F;

	public GUIElectricBlastFurnace(InventoryPlayer invPlayer, TileEntityElectricBlastFurnace te) {
		super(new ContainerElectricBlastFurnace(invPlayer, te));
		this.furnace = te;
		this.xSize = X_SIZE;
		this.ySize = Y_SIZE;
	}

	@Override
	public void initGui() {
		super.initGui();
		// 整个界面相对屏幕中心右偏，整体左移 10 像素（背景/槽位/物品栏/悬浮共用 guiLeft）
		this.guiLeft -= 10;
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);

		// 配方选择槽 (185,93)：打开坩埚配方选择器
		if(x >= guiLeft + 185 && x < guiLeft + 203 && y >= guiTop + 93 && y < guiTop + 111) {
			this.click();
			com.hbm.inventory.gui.GUIScreenRecipeSelector.openSelector(
					com.hbm.inventory.recipes.CrucibleRecipes.INSTANCE, this.furnace,
					this.furnace.recipe, 0, null, this);
		}

		// (189,40) 8×8：把内部熔融金属直接转成铸造废料进输出槽
		if(x >= guiLeft + 189 && x < guiLeft + 197 && y >= guiTop + 40 && y < guiTop + 48) {
			this.click();
			net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
			data.setBoolean("scrap", true);
			com.hbm.packet.PacketDispatcher.wrapper.sendToServer(new com.hbm.packet.toserver.NBTControlPacket(data,
					this.furnace.xCoord, this.furnace.yCoord, this.furnace.zCoord));
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		super.drawScreen(mouseX, mouseY, f);

		// 电力悬浮：HBM 标准显示
		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + POWER_X, guiTop + POWER_Y, POWER_W, POWER_H,
				furnace.power, furnace.getMaxPower());
		// 热量悬浮
		this.drawCustomInfoStat(mouseX, mouseY, guiLeft + HEAT_X, guiTop + HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY,
				new String[] { (int) furnace.heat + " / " + TileEntityElectricBlastFurnace.MAX_HEAT + " TU" });
		// 熔融物悬浮：坩埚同款，逐种 formatAmount
		java.util.List<String> moltenLines = new java.util.ArrayList<String>();
		if(furnace.molten.isEmpty()) {
			moltenLines.add(net.minecraft.util.EnumChatFormatting.RED + "Empty");
		} else {
			for(com.hbm.inventory.material.Mats.MaterialStack stack : furnace.molten) {
				moltenLines.add(net.minecraft.util.EnumChatFormatting.YELLOW
						+ com.hbm.util.i18n.I18nUtil.resolveKey(stack.material.getUnlocalizedName()) + ": "
						+ com.hbm.inventory.material.Mats.formatAmount(stack.amount, org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)));
			}
		}
		this.drawCustomInfoStat(mouseX, mouseY, guiLeft + MOLTEN_X, guiTop + MOLTEN_Y, MOLTEN_W, MOLTEN_H, mouseX, mouseY,
				moltenLines.toArray(new String[0]));
	}

	/** 标题行：(82,8) 处显示机器名，文案走已有 lang 键（中文即「电力高炉」） */
	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		super.drawGuiContainerForegroundLayer(mouseX, mouseY);

		this.fontRendererObj.drawString(
				net.minecraft.client.resources.I18n.format("container.electric_blast_furnace"),
				82, 8, 4210752);

		// 字体绘制会关掉贴图并改颜色，这里恢复，避免影响后续 tooltip / 悬浮条绘制
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {

		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

		// 贴图真实宽 320，固定 UV，避免运行时查询返回 256 造成横向拉伸右偏
		this.texU = 1F / 320F;

		// 底图分三块画，避开左下 (9..72, 121..191) 的效果素材区
		blit(guiLeft, guiTop, 0, 0, X_SIZE, 121);
		blit(guiLeft, guiTop + 121, 0, 121, 9, Y_SIZE - 121);
		blit(guiLeft + 72, guiTop + 121, 72, 121, X_SIZE - 72, Y_SIZE - 121);

		// 电力条：从下往上裁
		int p = (int) (this.furnace.power * POWER_H / Math.max(1L, this.furnace.getMaxPower()));
		if(p > 0) blit(guiLeft + POWER_X, guiTop + POWER_Y + POWER_H - p, POWER_U, POWER_V + POWER_H - p, POWER_W, p);

		// 热量条
		int h = (int) Math.min(HEAT_H, this.furnace.heat * HEAT_H / TileEntityElectricBlastFurnace.MAX_HEAT);
		if(h > 0) blit(guiLeft + HEAT_X, guiTop + HEAT_Y + HEAT_H - h, HEAT_U, HEAT_V + HEAT_H - h, HEAT_W, h);

		// 熔融物：坩埚同款按金属熔融色分层堆叠
		this.drawMoltenStack();

		// 熔炼指示：开始加热时盖上去
		if(this.furnace.heatingUp) {
			blit(guiLeft + FIRE_X, guiTop + FIRE_Y, FIRE_U, FIRE_V, 18, 18);
		}

		// 配方选择框：未选显示配方书，选中显示合金图标
		com.hbm.inventory.recipes.CrucibleRecipe sel = this.furnace.getLoadedRecipe();
		this.renderItem(sel != null ? sel.getIcon() : TEMPLATE_FOLDER, 185, 93);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

		this.renderGhosts();
	}

	/** 每种配方金属只在其第一格循环显示「能熔成它的所有物品」 */
	private void renderGhosts() {

		com.hbm.inventory.recipes.CrucibleRecipe rec = this.furnace.getLoadedRecipe();
		if(rec == null || rec.input == null) return;

		long cycle = System.currentTimeMillis() / 1000L;

		for(int type = 0; type < rec.input.length && type < TileEntityElectricBlastFurnace.INPUT_TYPES; type++) {

			java.util.List<net.minecraft.item.ItemStack> items = this.furnace.getGhostItems(type);
			if(items.isEmpty()) continue;

			int slotIndex = TileEntityElectricBlastFurnace.SLOT_INPUT + type * TileEntityElectricBlastFurnace.INPUT_PER_TYPE;
			net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) this.inventorySlots.inventorySlots.get(slotIndex);
			if(slot == null || slot.getHasStack()) continue;

			net.minecraft.item.ItemStack show = items.get((int) (cycle % items.size()));
			this.renderItem(show, slot.xDisplayPosition, slot.yDisplayPosition, 10F);
		}

		// 补半透明槽底，避免图标切换贴图后槽底变成物品图集
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glColor4f(1F, 1F, 1F, 0.5F);
		this.zLevel = 300F;

		for(int type = 0; type < rec.input.length && type < TileEntityElectricBlastFurnace.INPUT_TYPES; type++) {
			int slotIndex = TileEntityElectricBlastFurnace.SLOT_INPUT + type * TileEntityElectricBlastFurnace.INPUT_PER_TYPE;
			net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) this.inventorySlots.inventorySlots.get(slotIndex);
			if(slot == null || slot.getHasStack()) continue;
			blit(guiLeft + slot.xDisplayPosition, guiTop + slot.yDisplayPosition,
					slot.xDisplayPosition, slot.yDisplayPosition, 16, 16);
		}

		this.zLevel = 0F;
		GL11.glColor4f(1F, 1F, 1F, 1F);
		GL11.glDisable(GL11.GL_BLEND);
	}

	/** 坩埚同款：按金属熔融色自下而上分层堆叠，白色素材着色 */
	private void drawMoltenStack() {

		if(this.furnace.molten.isEmpty()) return;

		Minecraft.getMinecraft().getTextureManager().bindTexture(crucibleTexture);

		int capacity = TileEntityElectricBlastFurnace.MOLTEN_CAP;
		int x = guiLeft + MOLTEN_X;
		int bottom = guiTop + MOLTEN_Y + MOLTEN_H;

		int lastHeight = 0;
		int lastQuant = 0;

		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

		for(com.hbm.inventory.material.Mats.MaterialStack sta : this.furnace.molten) {

			int targetHeight = (lastQuant + sta.amount) * MOLTEN_H / capacity;
			if(lastHeight == targetHeight) continue;

			int offset = sta.material.smeltable == com.hbm.inventory.material.NTMMaterial.SmeltingBehavior.ADDITIVE ? 34 : 0;

			java.awt.Color color = new java.awt.Color(sta.material.moltenColor);
			GL11.glColor3f(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F);
			crucibleBlit(x, bottom - targetHeight, 176 + offset, 89 - targetHeight, MOLTEN_W, targetHeight - lastHeight);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glColor4f(1F, 1F, 1F, 0.3F);
			crucibleBlit(x, bottom - targetHeight, 176 + offset, 89 - targetHeight, MOLTEN_W, targetHeight - lastHeight);
			GL11.glDisable(GL11.GL_BLEND);

			lastQuant += sta.amount;
			lastHeight = targetHeight;
		}

		net.minecraft.client.renderer.OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		GL11.glColor3f(1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
	}

	/** 坩埚贴图 256 宽的标准 UV 绘制 */
	private void crucibleBlit(int x, int y, int u, int v, int w, int h) {
		float f = 1F / 256F;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertexWithUV(x, y + h, this.zLevel, u * f, (v + h) * f);
		tess.addVertexWithUV(x + w, y + h, this.zLevel, (u + w) * f, (v + h) * f);
		tess.addVertexWithUV(x + w, y, this.zLevel, (u + w) * f, v * f);
		tess.addVertexWithUV(x, y, this.zLevel, u * f, v * f);
		tess.draw();
	}

	/** 按底图实际宽度换算 U 的分块绘制 */
	private void blit(int x, int y, int u, int v, int w, int h) {
		float fv = 1F / 256F;
		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.addVertexWithUV(x, y + h, this.zLevel, u * texU, (v + h) * fv);
		tess.addVertexWithUV(x + w, y + h, this.zLevel, (u + w) * texU, (v + h) * fv);
		tess.addVertexWithUV(x + w, y, this.zLevel, (u + w) * texU, v * fv);
		tess.addVertexWithUV(x, y, this.zLevel, u * texU, v * fv);
		tess.draw();
	}
}