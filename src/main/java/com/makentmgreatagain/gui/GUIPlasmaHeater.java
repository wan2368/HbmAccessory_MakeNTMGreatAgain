package com.makentmgreatagain.gui;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.GUIScreenRecipeSelector;
import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.inventory.recipes.FusionRecipe;
import com.hbm.inventory.recipes.FusionRecipes;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.container.ContainerPlasmaHeater;
import com.makentmgreatagain.tileentity.TileEntityPlasmaHeater;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * 等离子加热器 GUI（底图 {@code gui/plasma_heater.png}，256×256，实画区域 176×208）。
 *
 * <p>布局（用户重写的底图）：电力条 (10,17) 16×58、电池槽 (10,77)；左输入罐 (57,17) 16×52
 * 配桶入 (39,17)/桶出 (39,53)；等离子缓冲 (88,17) 26 宽；右输入罐 (129,17) 16×52 配
 * 桶入 (147,17)/桶出 (147,53)；配方选择区 (42,76) 18×18（点击打开聚变配方选择器，
 * 与聚变堆一致）、蓝图槽 (62,77)、升级插件槽 (84,77)；玩家背包从 (8,126)。</p>
 *
 * <p>电力条：底图框内是空底，填充素材取自底图 (176,0) 的 16×58 竖条（上绿下蓝），
 * 按电量自底向上截取。</p>
 */
public class GUIPlasmaHeater extends GuiInfoContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation(
			MakeNTMGreatAgain.MODID, "textures/gui/plasma_heater.png");

	/**
	 * 实画区域只有 (0,0)-(176,208)：贴图右缘 x>176 的杂散像素和底部 y>208 的参考色条
	 * （上红渐变 + 绿色）属于素材区，不是面板，不渲染。
	 */
	private static final int W = 176, H = 208;

	/** 电力条位置与高度；填充素材是底图 (176,0) 的 16×58 竖条（上绿下蓝） */
	private static final int POWER_X = 10, POWER_Y = 17, POWER_W = 16, POWER_H = 58;
	private static final int POWER_U = 176, POWER_V = 0;

	/** 三根液柱：左输入罐、等离子缓冲（中间最宽）、右输入罐 */
	private static final int[] TANK_X = {57, 88, 129};
	private static final int[] TANK_W = {16, 26, 16};
	/** 每根液柱对应的 TE 罐下标（TE 里 0=左燃料、1=右燃料、2=等离子）：中间画等离子，两侧画燃料 */
	private static final int[] TANK_AT = {
			TileEntityPlasmaHeater.TANK_LEFT, TileEntityPlasmaHeater.TANK_PLASMA, TileEntityPlasmaHeater.TANK_RIGHT};
	private static final int TANK_Y = 17, TANK_H = 52;

	/** 配方选择点击区与图标位置 */
	private static final int SELECT_X = 56, SELECT_Y = 76, SELECT_SIZE = 18;

	private final TileEntityPlasmaHeater te;

	public GUIPlasmaHeater(InventoryPlayer invPlayer, TileEntityPlasmaHeater te) {
		super(new ContainerPlasmaHeater(invPlayer, te));
		this.te = te;
		this.xSize = W;
		this.ySize = H;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		for(int i = 0; i < TANK_X.length; i++) {
			this.te.tanks[TANK_AT[i]].renderTankInfo(this, mouseX, mouseY, guiLeft + TANK_X[i], guiTop + TANK_Y, TANK_W[i], TANK_H);
		}

		this.drawElectricityInfo(this, mouseX, mouseY, guiLeft + POWER_X, guiTop + POWER_Y, POWER_W, POWER_H,
				this.te.getPower(), this.te.getMaxPower());

		FusionRecipe recipe = this.te.getRecipe();
		if(this.checkClick(mouseX, mouseY, SELECT_X, SELECT_Y, SELECT_SIZE, SELECT_SIZE)) {
			if(recipe != null) {
				GUIElements.drawHoveringTextRecipe(recipe.print(), mouseX, mouseY, this.fontRendererObj, itemRender, this.width, this.height);
			} else {
				this.drawCreativeTabHoveringText(EnumChatFormatting.YELLOW + I18nUtil.resolveKey("gui.recipe.setRecipe"), mouseX, mouseY);
			}
		}
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);

		// 配方选择区：与聚变堆同款，打开 FusionRecipes 选择器，蓝图池取自蓝图槽
		if(this.checkClick(x, y, SELECT_X, SELECT_Y, SELECT_SIZE, SELECT_SIZE)) {
			GUIScreenRecipeSelector.openSelector(FusionRecipes.INSTANCE, te, te.getRecipeName(), 0,
					ItemBlueprints.grabPool(te.slots[TileEntityPlasmaHeater.SLOT_BLUEPRINT]), this);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int i, int j) {

		String name = this.te.hasCustomInventoryName() ? this.te.getInventoryName()
				: I18n.format(this.te.getInventoryName());
		this.fontRendererObj.drawString(name, this.xSize / 2 - this.fontRendererObj.getStringWidth(name) / 2, 6, 0xE8E8F0);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), ContainerPlasmaHeater.INV_X,
				ContainerPlasmaHeater.INV_Y - 12, 0x404040);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {

		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, W, H);

		// 三根液柱：renderTank 的 y 是液面底边，向上画
		for(int i = 0; i < TANK_X.length; i++) {
			this.te.tanks[TANK_AT[i]].renderTank(guiLeft + TANK_X[i], guiTop + TANK_Y + TANK_H, this.zLevel, TANK_W[i], TANK_H);
		}

		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);

		// 电力条：素材是底图 (176,0) 的 16×58 竖条，自底向上截取对应高度
		int p = this.te.getPowerScaled(POWER_H);
		if(p > 0) {
			this.drawTexturedModalRect(guiLeft + POWER_X, guiTop + POWER_Y + POWER_H - p,
					POWER_U, POWER_V + POWER_H - p, POWER_W, p);
		}

		// 配方图标：无配方画模板文件夹
		FusionRecipe recipe = this.te.getRecipe();
		ItemStack icon = recipe != null ? recipe.getIcon() : TEMPLATE_FOLDER;
		if(icon != null) this.renderItem(icon, SELECT_X + 1, SELECT_Y + 1);
	}
}
