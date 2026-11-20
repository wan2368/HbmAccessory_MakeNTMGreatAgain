package com.makentmgreatagain.gui;

import org.lwjgl.opengl.GL11;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.makentmgreatagain.container.ContainerHybridCatalyticFractionTower;
import com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 混合催化分馏塔 GUI。
 *
 * <p>流体条坐标取自界面底图（以图片左上角为 0,0）：左侧两条进料、右侧四条出料，
 * 条顶在 y=15，高度 52，底在 y=67。</p>
 */
public class GUIHybridCatalyticFractionTower extends GuiInfoContainer {

	/** 四张底图：0 无催化剂 / 1 原版催化转换器 / 2 铂 / 3 CMB */
	private static final ResourceLocation[] TEXTURES = {
		new ResourceLocation("makentmgreatagain:textures/gui/gui_hybrid_catalytic_fraction_tower.png"),
		new ResourceLocation("makentmgreatagain:textures/gui/gui_hybrid_catalytic_fraction_tower_catalyze.png"),
		new ResourceLocation("makentmgreatagain:textures/gui/gui_hybrid_catalytic_fraction_tower_platinum.png"),
		new ResourceLocation("makentmgreatagain:textures/gui/gui_hybrid_catalytic_fraction_tower_cmb.png")
	};

	/** 流体条左上角 X（比底图凹槽右移 1 px，实测液面偏左） */
	private static final int[] BAR_X = {5, 23, 95, 113, 131, 149};
	/** 流体条顶端 Y（比底图凹槽下移 1 px） */
	private static final int BAR_TOP = 16;
	/** 流体条高度 */
	private static final int BAR_H = 52;

	private TileEntityHybridCatalyticFractionTower tower;

	public GUIHybridCatalyticFractionTower(InventoryPlayer invPlayer, TileEntityHybridCatalyticFractionTower te) {
		super(new ContainerHybridCatalyticFractionTower(invPlayer, te));
		this.tower = te;
		this.xSize = 176;
		this.ySize = 238;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float interp) {
		super.drawScreen(mouseX, mouseY, interp);

		for(int i = 0; i < BAR_X.length; i++) {
			tower.tanks[i].renderTankInfo(this, mouseX, mouseY,
					guiLeft + BAR_X[i], guiTop + BAR_TOP, 16, BAR_H);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		// 界面上只显示短名「分馏塔」，方块/容器本身的名字仍是完整名称
		this.fontRendererObj.drawString(I18n.format("gui.hybrid_catalytic_fraction_tower.title"), 53, 6, 4210752);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(currentTexture());
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		for(int i = 0; i < BAR_X.length; i++) {
			// renderTank 的 y 是条底，所以底 = 顶 + 高度
			tower.tanks[i].renderTank(guiLeft + BAR_X[i], guiTop + BAR_TOP + BAR_H, this.zLevel, 16, BAR_H);
		}
	}

	/** 按 6 号槽里的催化剂选底图，未知值退回无催化版 */
	private ResourceLocation currentTexture() {

		int type = tower.catalystType;

		if(type < 0 || type >= TEXTURES.length) return TEXTURES[0];

		return TEXTURES[type];
	}
}
