package com.makentmgreatagain.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.MakeNTMGreatAgain;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.BobMathUtil;
import com.makentmgreatagain.container.ContainerNuclearExplosionGenerator;
import com.makentmgreatagain.packet.NukeRunPacket;
import com.makentmgreatagain.recipes.NukeGeneratorData;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;
import com.makentmgreatagain.init.ModNetwork;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * 可控核爆炸发生器界面。
 *
 * <p>底图 gui_nuclear_explosion.png 是 256×256，窗口本体占 (0,0) 191×219。
 * 运行时才画的东西（电力显示条、两条液态钠、进度光效、按下后的运行键）都不在底图里，
 * 而是同一张图上另外几个位置的贴片：</p>
 * <pre>
 *  电力   源 (192, 76) 16×74  → 画到 (7, 18)
 *  液态钠  源 (192, 12)  7×53  → 画到 (156, 23)
 *  高温钠  源 (208, 97)  7×53  → 画到 (175, 23)
 *  光效   源 (199, 30) 44×44  → 画到 (65, 38)，按半径 11→22 从内往外揭开
 *  运行键  源 (199, 12) 18×18  → 画到 (78, 90)
 * </pre>
 */
public class GUINuclearExplosion extends GuiContainer {

	public static final ResourceLocation TEX =
			new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/gui/gui_nuclear_explosion.png");

	private static final int W = 191;
	private static final int H = 219;
	private static final int FLUX_SEGMENTS = 35;
	private static final double FLUX_SEGMENT_WIDTH = 1.64D;
	private static final double FLUX_SEGMENT_GAP = 0.5D;
	private static final int FLUX_BAR_HEIGHT = 6;

	/** 进度光效区域 */
	private static final int ANIM_X = 65;
	private static final int ANIM_Y = 38;
	private static final int ANIM_SIZE = 44;
	/** 半径从 11 px 涨到 22 px */
	private static final float ANIM_R_MIN = 11F;
	private static final float ANIM_R_MAX = 22F;

	private final TileEntityNuclearExplosionGenerator te;

	public GUINuclearExplosion(InventoryPlayer inv, TileEntityNuclearExplosionGenerator te) {
		super(new ContainerNuclearExplosionGenerator(inv, te));
		this.te = te;
		this.xSize = W;
		this.ySize = H;
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mx, int my) {

		this.fontRendererObj.drawString(I18n.format("container.nuclear_explosion_generator"), 51, 3, 0xE0E0E0);
		this.fontRendererObj.drawString(I18n.format("container.inventory"), 17, H - 96 + 2, 4210752);

		// 综合信息显示 (151,96) 36×14：先报运行 / 冷却倒计时，再报缺件 / 能源 / 冷却剂 / 输出空间
		ItemStack core = this.te.getStackInSlot(TileEntityNuclearExplosionGenerator.SLOT_CORE);

		String status;

		if(this.te.running) {
			status = String.format("%.1fs", (TileEntityNuclearExplosionGenerator.CYCLE - this.te.progress) / 20D);
		} else if(this.te.cooling) {
			// 核心已经在爆炸那一 tick 烧掉了，冷却期间先报倒计时
			status = String.format("%.1fs", (TileEntityNuclearExplosionGenerator.CYCLE - this.te.cool) / 20D);
		} else {
			switch(this.te.getStartStatus()) {
			case TileEntityNuclearExplosionGenerator.STATUS_NO_CORE:
				status = I18n.format("gui.nuclear_explosion.no_core");
				break;
			case TileEntityNuclearExplosionGenerator.STATUS_NO_COATING:
				status = I18n.format("gui.nuclear_explosion.no_coating");
				break;
			case TileEntityNuclearExplosionGenerator.STATUS_NO_POWER:
				status = I18n.format("gui.nuclear_explosion.no_power");
				break;
			case TileEntityNuclearExplosionGenerator.STATUS_NO_COOLANT:
				status = I18n.format("gui.nuclear_explosion.no_coolant");
				break;
			case TileEntityNuclearExplosionGenerator.STATUS_NO_OUTPUT:
				status = I18n.format("gui.nuclear_explosion.no_output_space");
				break;
			case TileEntityNuclearExplosionGenerator.STATUS_NON_EXPLOSIVE:
				status = I18n.format("gui.nuclear_explosion.non_explosive");
				break;
			default:
				NukeGeneratorData.CoreStat stat = core == null ? null : NukeGeneratorData.getCore(core);
				status = stat == null ? "N/A" : String.valueOf(stat.flux);
			}
		}

		// 状态框只有 36×14，字宽了按比例缩小，保证不越界
		int sw = this.fontRendererObj.getStringWidth(status);
		float scale = sw > 36 ? 36F / sw : 1F;
		float sx = 169F - sw * scale / 2F;

		GL11.glPushMatrix();
		GL11.glTranslatef(sx, 99F, 0F);
		GL11.glScalef(scale, scale, 1F);
		this.fontRendererObj.drawString(status, 0, 0, 0xE8E8E8);
		GL11.glPopMatrix();
	}

	/** 提示统一放到 drawScreen 里画，和 HBM 其它机器一样用绝对坐标 */
	@Override
	public void drawScreen(int mx, int my, float partial) {
		super.drawScreen(mx, my, partial);
		this.drawTooltips(mx, my);
	}

	/** 鼠标悬停提示：电力条、两条液态钠、被增殖物品槽、右下角综合显示 */
	private void drawTooltips(int mx, int my) {

		int rx = mx - this.guiLeft;
		int ry = my - this.guiTop;

		// 运行键 (78,90) 18×18：操作说明
		if(rx >= 78 && rx < 96 && ry >= 90 && ry < 108) {
			List<String> lines = new ArrayList<String>();
			lines.add(I18n.format("gui.nuclear_explosion.run_tip_cancel"));
			lines.add(I18n.format("gui.nuclear_explosion.run_tip_start"));
			lines.add(I18n.format("gui.nuclear_explosion.run_tip_auto"));
			lines.add(I18n.format("gui.nuclear_explosion.run_tip_auto_ready"));
			lines.add(I18n.format("gui.nuclear_explosion.run_tip_auto_loop"));
			GUIElements.drawHoveringText(lines, mx, my, this.fontRendererObj,
					this.itemRender, this.width, this.height);
			return;
		}

		// 电力条 (7,18) 16×74：HBM 统一格式 12.3k/100MHE
		if(rx >= 7 && rx < 23 && ry >= 18 && ry < 92) {
			List<String> lines = new ArrayList<String>();
			lines.add(BobMathUtil.getShortNumber(this.te.getPower()) + "/"
					+ BobMathUtil.getShortNumber(this.te.getMaxPower()) + "HE");
			GUIElements.drawHoveringText(lines, mx, my, this.fontRendererObj,
					this.itemRender, this.width, this.height);
			return;
		}

		// 液态钠 (156,23) / 高温液态钠 (175,23)，都是 7×53
		if(rx >= 156 && rx < 163 && ry >= 23 && ry < 76) {
			this.drawTankInfo(mx, my, this.te.tanks[0]);
			return;
		}

		if(rx >= 175 && rx < 182 && ry >= 23 && ry < 76) {
			this.drawTankInfo(mx, my, this.te.tanks[1]);
			return;
		}

		// 被增殖物品槽
		if(rx >= 39 && rx < 57 && ry >= 52 && ry < 70) {

			ItemStack input = this.te.getStackInSlot(TileEntityNuclearExplosionGenerator.SLOT_INPUT);
			List<String> lines = new ArrayList<String>();

			// GuiContainer 的默认提示会先绘制，但本类随后还要显示增殖参数；
			// 把原物品简介合并进同一个提示框，避免自定义信息把原简介盖住。
			if(input != null) {
				@SuppressWarnings("unchecked")
				List<String> itemTooltip = input.getTooltip(this.mc.thePlayer,
						this.mc.gameSettings.advancedItemTooltips);
				lines.addAll(itemTooltip);
				lines.add("");
			}

			if(input == null) {
				lines.add(I18n.format("gui.nuclear_explosion.no_item"));
				lines.add(I18n.format("gui.nuclear_explosion.breedable"));

				// 条目太多，分两列；左列按最宽的名字补空格，右列对齐
				List<NukeGeneratorData.Breeding> recipes = NukeGeneratorData.getRecipes();
				int rows = (recipes.size() + 1) / 2;
				int colWidth = 0;
				for(int i = 0; i < rows && i < recipes.size(); i++) {
					colWidth = Math.max(colWidth,
							this.fontRendererObj.getStringWidth(" " + recipes.get(i).input.getDisplayName()));
				}
				int spaceWidth = Math.max(1, this.fontRendererObj.getStringWidth(" "));
				for(int i = 0; i < rows && i < recipes.size(); i++) {
					StringBuilder line = new StringBuilder(" " + recipes.get(i).input.getDisplayName());
					int j = i + rows;
					if(j < recipes.size()) {
						int pad = colWidth - this.fontRendererObj.getStringWidth(line.toString());
						int spaces = (pad + spaceWidth - 1) / spaceWidth + 3;
						for(int s = 0; s < spaces; s++) line.append(' ');
						line.append(recipes.get(j).input.getDisplayName());
					}
					lines.add(line.toString());
				}
			} else {
				NukeGeneratorData.Breeding recipe = NukeGeneratorData.findRecipe(input);

				if(recipe == null) {
					lines.add(I18n.format("gui.nuclear_explosion.cannot_breed"));
				} else {
					lines.add(I18n.format("gui.nuclear_explosion.need_flux", recipe.reqFlux));
					lines.add(I18n.format("gui.nuclear_explosion.min_flux", recipe.minFlux));
					lines.add(I18n.format("gui.nuclear_explosion.output", recipe.output.getDisplayName()));
				}
			}

			GUIElements.drawHoveringText(lines, mx, my, this.fontRendererObj,
					this.itemRender, this.width, this.height);
			return;
		}

		// 输出槽只有一套 tooltip 绘制路径：原简介、通量数据和刻度一起排版。
		Slot output = this.inventorySlots.getSlot(TileEntityNuclearExplosionGenerator.SLOT_OUTPUT);
		if(this.mc.thePlayer.inventory.getItemStack() == null && this.isOverOutputSlot(mx, my)) {
			NukeGeneratorData.Breeding recipe = this.getActiveBreedingRecipe();
			if(recipe != null) this.drawOutputTooltip(output.getStack(), recipe, mx, my);
			return;
		}

		// 综合信息显示：核心 + 覆层都在时，报这个燃料的钠消耗 / 产量
		ItemStack core = this.te.getStackInSlot(TileEntityNuclearExplosionGenerator.SLOT_CORE);
		ItemStack coating = this.te.getStackInSlot(TileEntityNuclearExplosionGenerator.SLOT_COATING);

		if(rx >= 151 && rx < 187 && ry >= 96 && ry < 110 && core != null && coating != null) {

			NukeGeneratorData.CoreStat stat = NukeGeneratorData.getCore(core);

			if(stat != null) {
				List<String> lines = new ArrayList<String>();
				lines.add(I18n.format("gui.nuclear_explosion.sodium_in", stat.sodium));
				lines.add(I18n.format("gui.nuclear_explosion.sodium_out", stat.sodium));
				GUIElements.drawHoveringText(lines, mx, my, this.fontRendererObj,
						this.itemRender, this.width, this.height);
			}
		}
	}

	@Override
	protected void renderToolTip(ItemStack stack, int x, int y) {
		Slot output = this.inventorySlots.getSlot(TileEntityNuclearExplosionGenerator.SLOT_OUTPUT);
		if(stack == output.getStack() && this.mc.thePlayer.inventory.getItemStack() == null
				&& this.isOverOutputSlot(x, y)) return;
		super.renderToolTip(stack, x, y);
	}

	private boolean isOverOutputSlot(int x, int y) {
		Slot output = this.inventorySlots.getSlot(TileEntityNuclearExplosionGenerator.SLOT_OUTPUT);
		return this.func_146978_c(output.xDisplayPosition, output.yDisplayPosition, 16, 16, x, y);
	}

	private NukeGeneratorData.Breeding getActiveBreedingRecipe() {
		ItemStack input = this.te.getStackInSlot(TileEntityNuclearExplosionGenerator.SLOT_INPUT);
		if(input != null) {
			NukeGeneratorData.Breeding recipe = NukeGeneratorData.findRecipe(input);
			return recipe != null && recipe.reqFlux > 0 ? recipe : null;
		}

		String breedItem = this.te.getBreedItem();
		if(breedItem == null) return null;
		for(NukeGeneratorData.Breeding candidate : NukeGeneratorData.getRecipes()) {
			if(candidate.reqFlux <= 0) continue;
			String name = net.minecraft.item.Item.itemRegistry.getNameForObject(candidate.input.getItem());
			if(name != null && name.equals(breedItem)) return candidate;
		}
		return null;
	}

	/** 物品简介和通量刻度共用一次提示框布局，贴近屏幕边缘时一起移动。 */
	@SuppressWarnings("unchecked")
	private void drawOutputTooltip(ItemStack stack, NukeGeneratorData.Breeding recipe, int mx, int my) {
		List<String> lines = new ArrayList<String>();
		FontRenderer font = this.fontRendererObj;
		if(stack != null) {
			lines.addAll(stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips));
			for(int i = 0; i < lines.size(); i++) {
				lines.set(i, (i == 0 ? stack.getRarity().rarityColor : EnumChatFormatting.GRAY) + lines.get(i));
			}
			FontRenderer itemFont = stack.getItem().getFontRenderer(stack);
			if(itemFont != null) font = itemFont;
			if(!lines.isEmpty()) lines.add("");
		}

		int current = this.te.getBreedingProgress(recipe);
		int required = recipe.reqFlux;
		int percent = (int) Math.min(100L, current * 100L / required);
		lines.add(I18n.format("gui.nuclear_explosion.flux", String.format(Locale.ROOT, "%,d", current),
				String.format(Locale.ROOT, "%,d", required), percent));
		lines.add(I18n.format("gui.nuclear_explosion.output", recipe.output.getDisplayName()));
		if(current >= required && this.te.isBreedingOutputBlocked(recipe)) {
			lines.add(EnumChatFormatting.RED + I18n.format("gui.nuclear_explosion.flux_blocked"));
		}

		int tooltipWidth = (int) Math.ceil(FLUX_SEGMENTS * FLUX_SEGMENT_WIDTH
				+ (FLUX_SEGMENTS - 1) * FLUX_SEGMENT_GAP);
		for(String line : lines) tooltipWidth = Math.max(tooltipWidth, font.getStringWidth(line));
		int barOffset = 2 + 10 * lines.size();
		int tooltipHeight = barOffset + FLUX_BAR_HEIGHT;
		int x = mx + 12;
		if(x + tooltipWidth + 4 > this.width) x = mx - 16 - tooltipWidth;
		x = Math.max(4, Math.min(x, this.width - tooltipWidth - 4));
		int y = Math.max(4, Math.min(my - 12, this.height - tooltipHeight - 4));

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		float previousZ = this.zLevel;
		this.zLevel = 300F;
		try {
			int bg = GUIElements.STANDARD_COLOR_BACKGROUND;
			int top = GUIElements.STANDARD_COLOR_LINE0;
			int bottom = GUIElements.STANDARD_COLOR_LINE1;
			int right = x + tooltipWidth;
			int end = y + tooltipHeight;
			this.drawGradientRect(x - 3, y - 4, right + 3, end + 4, bg, bg);
			this.drawGradientRect(x - 4, y - 3, x - 3, end + 3, bg, bg);
			this.drawGradientRect(right + 3, y - 3, right + 4, end + 3, bg, bg);
			this.drawGradientRect(x - 3, y - 3, right + 3, y - 2, top, top);
			this.drawGradientRect(x - 3, end + 2, right + 3, end + 3, bottom, bottom);
			this.drawGradientRect(x - 3, y - 2, x - 2, end + 2, top, bottom);
			this.drawGradientRect(right + 2, y - 2, right + 3, end + 2, top, bottom);

			for(int i = 0; i < lines.size(); i++) {
				font.drawStringWithShadow(lines.get(i), x, y + 10 * i + (i == 0 ? 0 : 2), 0xFFFFFF);
			}
			this.drawOutputFluxBar(x, y + barOffset, current, required);
		} finally {
			this.zLevel = previousZ;
			GL11.glPopAttrib();
		}
	}

	/** 35 格，单格宽 1.64 个 GUI 像素、间隔 0.5 像素；总宽约增加 25%。 */
	private void drawOutputFluxBar(int x, int y, int current, int required) {
		float progress = (float) Math.min(FLUX_SEGMENTS, current * (double) FLUX_SEGMENTS / required);
		Tessellator tess = Tessellator.instance;
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		tess.startDrawingQuads();
		for(int i = 0; i < FLUX_SEGMENTS; i++) {
			double left = x + i * (FLUX_SEGMENT_WIDTH + FLUX_SEGMENT_GAP);
			double fill = clamp01(progress - i) * FLUX_SEGMENT_WIDTH;
			double right = left + FLUX_SEGMENT_WIDTH;
			if(fill < FLUX_SEGMENT_WIDTH) this.addFluxSegment(tess, left + fill, right, y, 0x8C3434);
			if(fill > 0D) this.addFluxSegment(tess, left, left + fill, y, 0x63D46B);
		}
		tess.draw();
	}

	private void addFluxSegment(Tessellator tess, double left, double right, int y, int color) {
		tess.setColorOpaque_I(color);
		tess.addVertex(left, y + FLUX_BAR_HEIGHT, this.zLevel);
		tess.addVertex(right, y + FLUX_BAR_HEIGHT, this.zLevel);
		tess.addVertex(right, y, this.zLevel);
		tess.addVertex(left, y, this.zLevel);
	}

	/** 流体罐提示：流体名 + 存量/容量，边框颜色取自流体，和 HBM 罐子一致 */
	private void drawTankInfo(int mx, int my, FluidTank tank) {
		List<String> lines = new ArrayList<String>();
		lines.add(tank.getTankType().getLocalizedName());
		lines.add(tank.getFill() + "/" + tank.getMaxFill() + "mB");
		GUIElements.drawHoveringTextFluid(lines, mx, my, this.fontRendererObj,
				this.itemRender, this.width, this.height, tank.getTankType());
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float interp, int mx, int my) {

		GL11.glColor4f(1F, 1F, 1F, 1F);
		this.mc.getTextureManager().bindTexture(TEX);

		int x = this.guiLeft;
		int y = this.guiTop;

		this.drawTexturedModalRect(x, y, 0, 0, W, H);

		this.drawGauge(x + 7, y + 18, 16, 74, 192, 76,
				this.te.getPower() / (double) this.te.getMaxPower());
		this.drawGauge(x + 156, y + 23, 7, 53, 192, 12,
				this.te.tanks[0].getFill() / (double) this.te.tanks[0].getMaxFill());
		this.drawGauge(x + 175, y + 23, 7, 53, 208, 97,
				this.te.tanks[1].getFill() / (double) this.te.tanks[1].getMaxFill());

		if(this.te.running) {
			float t = clamp01(this.te.progress / (float) TileEntityNuclearExplosionGenerator.CYCLE);
			this.drawProgressGlow(x, y, 0F, ANIM_R_MIN + (ANIM_R_MAX - ANIM_R_MIN) * t);
		} else if(this.te.cooling) {
			// 消散：外圈固定 r22，内圈从 r11 长到 r22，看起来就是从内到外消失
			float t = clamp01(this.te.cool / (float) TileEntityNuclearExplosionGenerator.CYCLE);
			this.drawProgressGlow(x, y, ANIM_R_MIN + (ANIM_R_MAX - ANIM_R_MIN) * t, ANIM_R_MAX);
		}

		// 自动模式下运行键一直保持按下的样子，再点一下就是退出自动
		if(this.te.running || this.te.auto) {
			this.drawTexturedModalRect(x + 78, y + 90, 199, 12, 18, 18);
		}
	}

	private static float clamp01(float v) {
		return v < 0F ? 0F : (v > 1F ? 1F : v);
	}

	/** 竖直条从下往上填：源图上对应的那一段直接裁着画 */
	private void drawGauge(int dx, int dy, int w, int h, int su, int sv, double fill) {

		int fh = (int) Math.round(h * Math.max(0D, Math.min(1D, fill)));
		if(fh <= 0) return;

		this.drawTexturedModalRect(dx, dy + h - fh, su, sv + h - fh, w, fh);
	}

	/**
	 * 进度光效：把 (199,30) 的 44×44 圆放射贴片画到 (65,38) 上，
	 * 只画半径落在 [rMin, rMax] 之间的像素。
	 * 展开时 rMin=0、rMax 从 11 涨到 22；消散时 rMax=22、rMin 从 11 涨到 22。
	 */
	private void drawProgressGlow(int x, int y, float rMin, float rMax) {

		int rMax2 = (int) (rMax * rMax);
		int rMin2 = (int) (rMin * rMin);
		int center = ANIM_SIZE / 2;
		if(rMax2 <= rMin2) return;

		for(int dy = 0; dy < ANIM_SIZE; dy++) {

			int d = dy - center;
			int edgeMax = rMax2 - d * d;
			if(edgeMax <= 0) continue;

			int halfMax = (int) Math.sqrt(edgeMax);
			int halfMin = rMin2 > d * d ? (int) Math.sqrt(rMin2 - d * d) : -1;

			if(halfMin < 0) {
				drawGlowRow(x, y, dy, center - halfMax, center + halfMax + 1);
			} else {
				drawGlowRow(x, y, dy, center - halfMax, center - halfMin);
				drawGlowRow(x, y, dy, center + halfMin + 1, center + halfMax + 1);
			}
		}
	}

	private void drawGlowRow(int x, int y, int dy, int from, int to) {

		from = Math.max(0, from);
		to = Math.min(ANIM_SIZE, to);
		if(to <= from) return;

		this.drawTexturedModalRect(
				x + ANIM_X + from, y + ANIM_Y + dy,
				199 + from, 30 + dy,
				to - from, 1);
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) {

		int rx = mx - this.guiLeft;
		int ry = my - this.guiTop;

		// 运行键 (78,90) 18×18：
		//   普通点击 = 启动；运行中再点一次 = 取消本轮充能
		//   自动模式下点一下 = 立刻退出自动（运行中也一并取消）
		//   Shift+点击 = 开关自动模式（任何状态都能切）
		if(button == 0 && rx >= 78 && rx < 96 && ry >= 90 && ry < 108) {
			boolean shift = isShiftKeyDown();
			if(shift || this.te.auto || this.te.running || this.te.canStart()) {
				this.playClick();
				ModNetwork.NETWORK.sendToServer(
						new NukeRunPacket.Request(this.te.xCoord, this.te.yCoord, this.te.zCoord, shift));
			}
			return;
		}

		super.mouseClicked(mx, my, button);
	}

	/** 和 HBM 其它机器界面同一个按键音 */
	private void playClick() {
		this.mc.getSoundHandler().playSound(
				PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1F));
	}
}
