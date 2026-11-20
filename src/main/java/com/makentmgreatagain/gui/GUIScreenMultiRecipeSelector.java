package com.makentmgreatagain.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toserver.NBTControlPacket;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * MF（多配方）模式专用的配方选择器。
 *
 * <p><b>结构与原版 {@code GUIScreenRecipeSelector} 完全一致</b>，只做两处改动：</p>
 * <ol>
 *   <li>所有控件与网格<b>整体右移 40 像素</b>（{@link #SHIFT}）；</li>
 *   <li>删掉原版 (152,72) 的「当前配方 + 点击取消」单格，改成左侧
 *       <b>5 个已选配方格位</b>：点网格配方依次填入第一个空格，点已选格位删除并让后面前移补位，
 *       满 5 个后不再接受。</li>
 * </ol>
 *
 * <p>贴图里面板本身从 x=21 起、按钮素材在 x=216..249，所以底图必须整张按 (0,0) 画、
 * 尺寸取 {@link #xSize}/{@link #ySize}（含左侧留白），不能按非透明区裁剪，否则按钮会错位。</p>
 *
 * <p>配方列表每次打开实时读 {@code recipeSet.recipeOrderedList}，后续新增配方自动出现。</p>
 */
public class GUIScreenMultiRecipeSelector extends GuiScreen {

	private static final ResourceLocation texture = new ResourceLocation(MakeNTMGreatAgain.MODID,
			"textures/gui/gui_large_catalytic_recipe_selector.png");

	/** 相对原版整体右移量 */
	private static final int SHIFT = 40;

	/**
	 * 素材区在贴图里的横向偏移。
	 *
	 * <p>原版素材（按钮/选中框）在 x=176 起，<b>这张贴图整体右移了 40</b>，
	 * 实测按钮在 x=216..249、选中框在 x=232..249。所以取样 U 必须加这个偏移，
	 * 否则会取到面板像素，表现为悬浮时贴图错乱。</p>
	 */
	private static final int SPRITE_U = 40;

	/** 与原版一致的面板尺寸（原版 176×132，右移 40 后宽度加 40） */
	private final int xSize = 176 + SHIFT;
	private final int ySize = 132;

	private int guiLeft;
	private int guiTop;

	private static final int COLS = 8;
	private static final int PER_PAGE = 40;

	/** 左侧 5 个已选配方格位 */
	private static final int[][] SLOTS = {{28, 18}, {28, 36}, {28, 54}, {28, 72}, {28, 90}};

	private final GenericRecipes recipeSet;
	private final IControlReceiver tile;
	private final GuiScreen previousScreen;
	private final String installedPool;

	private final String[] selected = new String[TileEntityLargeCatalyticChemicalFactory.MULTI_RECIPE_COUNT];

	private final List<GenericRecipe> recipes = new ArrayList<GenericRecipe>();
	private int pageIndex = 0;
	private int size = 0;
	private GuiTextField search;

	private static final RenderItem itemRender = new RenderItem();

	public GUIScreenMultiRecipeSelector(GenericRecipes recipeSet, IControlReceiver tile,
			String[] current, String installedPool, GuiScreen previousScreen) {

		this.recipeSet = recipeSet;
		this.tile = tile;
		this.installedPool = installedPool;
		this.previousScreen = previousScreen;

		for(int i = 0; i < selected.length; i++) {
			selected[i] = current != null && i < current.length && current[i] != null ? current[i] : "";
		}

		regenerateRecipes();
	}

	public static void openSelector(GenericRecipes recipeSet, IControlReceiver tile,
			String[] current, String installedPool, GuiScreen previousScreen) {
		FMLCommonHandler.instance().showGuiScreen(
				new GUIScreenMultiRecipeSelector(recipeSet, tile, current, installedPool, previousScreen));
	}

	/** 与原版 regenerateRecipes 同逻辑：实时读表 + 蓝图池过滤 + 搜索 */
	private void regenerateRecipes() {
		recipes.clear();

		String term = this.search != null ? this.search.getText() : "";

		for(Object obj : recipeSet.recipeOrderedList) {
			if(!(obj instanceof GenericRecipe)) continue;
			GenericRecipe recipe = (GenericRecipe) obj;

			if(!recipe.isPooled() || (installedPool != null && recipe.isPartOfPool(installedPool))) {
				if(term == null || term.isEmpty() || recipe.matchesSearch(term)) {
					recipes.add(recipe);
				}
			}
		}

		this.size = Math.max(0, (int) Math.ceil((recipes.size() - PER_PAGE) / (double) COLS));
		if(this.pageIndex > this.size) this.pageIndex = this.size;
	}

	@Override
	public void initGui() {
		this.guiLeft = (this.width - this.xSize) / 2;
		this.guiTop = (this.height - this.ySize) / 2;

		Keyboard.enableRepeatEvents(true);
		this.search = new GuiTextField(this.fontRendererObj,
				guiLeft + 28 + SHIFT, guiTop + 111, 102, 12);
		this.search.setMaxStringLength(32);
		// 关掉 MC 自带的黑底，否则搜索框会出现黑边（原版同样这么做）
		this.search.setEnableBackgroundDrawing(false);
		this.search.setFocused(true);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f) {
		this.drawDefaultBackground();
		drawBackground(f, mouseX, mouseY);
		GL11.glDisable(GL11.GL_LIGHTING);
		this.search.drawTextBox();
		GL11.glEnable(GL11.GL_LIGHTING);
		handleScroll();

		// 网格悬浮说明（原版判定区 7,17 144×90，右移 40）
		if(guiLeft + 7 + SHIFT <= mouseX && guiLeft + 7 + SHIFT + 144 > mouseX
				&& guiTop + 17 < mouseY && guiTop + 17 + 90 >= mouseY) {
			for(int i = pageIndex * COLS; i < pageIndex * COLS + PER_PAGE; i++) {
				if(i >= recipes.size()) break;

				int ind = i - pageIndex * COLS;
				int ix = 7 + SHIFT + 18 * (ind % COLS);
				int iy = 17 + 18 * (ind / COLS);

				if(guiLeft + ix <= mouseX && guiLeft + ix + 18 > mouseX
						&& guiTop + iy < mouseY && guiTop + iy + 18 >= mouseY) {
					GUIElements.drawHoveringTextRecipe(recipes.get(i).print(), mouseX, mouseY,
							this.fontRendererObj, itemRender, this.width, this.height);
				}
			}
		}

		// 左侧已选格位悬浮说明
		for(int i = 0; i < SLOTS.length; i++) {
			int sx = SLOTS[i][0];
			int sy = SLOTS[i][1];
			if(guiLeft + sx <= mouseX && guiLeft + sx + 18 > mouseX
					&& guiTop + sy < mouseY && guiTop + sy + 18 >= mouseY) {
				String name = selected[i];
				if(name != null && !name.isEmpty() && recipeSet.recipeNameMap.containsKey(name)) {
					GenericRecipe recipe = (GenericRecipe) recipeSet.recipeNameMap.get(name);
					GUIElements.drawHoveringTextRecipe(recipe.print(), mouseX, mouseY,
							this.fontRendererObj, itemRender, this.width, this.height);
				}
			}
		}

		if(hover(mouseX, mouseY, 152 + SHIFT, 90, 16, 16)) {
			this.drawCreativeTabHoveringText(EnumChatFormatting.YELLOW + "Close", mouseX, mouseY);
		}
		if(hover(mouseX, mouseY, 134 + SHIFT, 108, 16, 16)) {
			this.drawCreativeTabHoveringText(EnumChatFormatting.YELLOW + "Clear search", mouseX, mouseY);
		}
		if(hover(mouseX, mouseY, 8 + SHIFT, 108, 16, 16)) {
			this.drawCreativeTabHoveringText(EnumChatFormatting.ITALIC + "Press ENTER to toggle focus", mouseX, mouseY);
		}

		// 超限提示：固定画在 (48,7)，2~2.5 秒后自动消失
		if(this.limitWarning != null && System.currentTimeMillis() < this.warnUntil) {
			this.drawString(this.fontRendererObj, EnumChatFormatting.RED + this.limitWarning,
					guiLeft + 48, guiTop + 7, 0xFFFFFF);
		}
	}

	private boolean hover(int mx, int my, int rx, int ry, int w, int h) {
		return guiLeft + rx <= mx && guiLeft + rx + w > mx && guiTop + ry < my && guiTop + ry + h >= my;
	}

	private void handleScroll() {
		if(!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1) && Mouse.next()) {
			int scroll = Mouse.getEventDWheel();
			if(scroll > 0 && this.pageIndex > 0) this.pageIndex--;
			if(scroll < 0 && this.pageIndex < this.size) this.pageIndex++;
		}
	}

	/** 与原版 drawGuiContainerBackgroundLayer 同结构：按钮只在悬浮时画高亮 */
	private void drawBackground(float f, int mouseX, int mouseY) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		if(this.search.isFocused()) {
			this.drawTexturedModalRect(guiLeft + 26 + SHIFT, guiTop + 108, 0 + SPRITE_U, 132, 106, 16);
		}

		if(hover(mouseX, mouseY, 152 + SHIFT, 18, 16, 16)) {
			this.drawTexturedModalRect(guiLeft + 152 + SHIFT, guiTop + 18, 176 + SPRITE_U, 0, 16, 16);
		}
		if(hover(mouseX, mouseY, 152 + SHIFT, 36, 16, 16)) {
			this.drawTexturedModalRect(guiLeft + 152 + SHIFT, guiTop + 36, 176 + SPRITE_U, 16, 16, 16);
		}
		if(hover(mouseX, mouseY, 152 + SHIFT, 90, 16, 16)) {
			this.drawTexturedModalRect(guiLeft + 152 + SHIFT, guiTop + 90, 176 + SPRITE_U, 32, 16, 16);
		}
		if(hover(mouseX, mouseY, 134 + SHIFT, 108, 16, 16)) {
			this.drawTexturedModalRect(guiLeft + 134 + SHIFT, guiTop + 108, 176 + SPRITE_U, 48, 16, 16);
		}
		if(hover(mouseX, mouseY, 8 + SHIFT, 108, 16, 16)) {
			this.drawTexturedModalRect(guiLeft + 8 + SHIFT, guiTop + 108, 176 + SPRITE_U, 64, 16, 16);
		}

		// 已选配方在网格里的高亮框（原版是单个 selection，这里 5 个都比）
		for(int i = pageIndex * COLS; i < pageIndex * COLS + PER_PAGE; i++) {
			if(i >= recipes.size()) break;
			int ind = i - pageIndex * COLS;
			String name = recipes.get(i).getInternalName();
			if(isSelected(name)) {
				this.drawTexturedModalRect(guiLeft + 7 + SHIFT + 18 * (ind % COLS),
						guiTop + 17 + 18 * (ind / COLS), 192 + SPRITE_U, 0, 18, 18);
			}
		}

		// 网格图标（原版坐标 8/18，右移 40）
		for(int i = pageIndex * COLS; i < pageIndex * COLS + PER_PAGE; i++) {
			if(i >= recipes.size()) break;
			int ind = i - pageIndex * COLS;
			renderItem(recipes.get(i).getIcon(), 8 + SHIFT + 18 * (ind % COLS), 18 + 18 * (ind / COLS));
			this.mc.getTextureManager().bindTexture(texture);
		}

		// 左侧 5 个已选格位的图标
		for(int i = 0; i < SLOTS.length; i++) {
			String name = selected[i];
			if(name == null || name.isEmpty()) continue;
			if(!recipeSet.recipeNameMap.containsKey(name)) continue;
			GenericRecipe recipe = (GenericRecipe) recipeSet.recipeNameMap.get(name);
			renderItem(recipe.getIcon(), SLOTS[i][0], SLOTS[i][1]);
			this.mc.getTextureManager().bindTexture(texture);
		}
	}

	private boolean isSelected(String name) {
		for(String s : selected) if(s != null && s.equals(name)) return true;
		return false;
	}

	/** 与原版 renderItem 完全一致：内部自己加 guiLeft/guiTop */
	public void renderItem(ItemStack stack, int x, int y) {
		if(stack == null) return;

		FontRenderer font = stack.getItem().getFontRenderer(stack);
		if(font == null) font = fontRendererObj;

		GL11.glColor4f(1F, 1F, 1F, 1F);
		RenderHelper.enableGUIStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);

		itemRender.zLevel = 100F;
		itemRender.renderItemAndEffectIntoGUI(font, this.mc.getTextureManager(), stack, guiLeft + x, guiTop + y);
		itemRender.zLevel = 0F;

		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_LIGHTING);
	}

	@Override
	protected void mouseClicked(int x, int y, int k) {
		super.mouseClicked(x, y, k);
		this.search.mouseClicked(x, y, k);

		// 翻页
		if(hover(x, y, 152 + SHIFT, 18, 16, 16)) {
			click();
			if(this.pageIndex > 0) this.pageIndex--;
			return;
		}
		if(hover(x, y, 152 + SHIFT, 36, 16, 16)) {
			click();
			if(this.pageIndex < this.size) this.pageIndex++;
			return;
		}
		// 关闭
		if(hover(x, y, 152 + SHIFT, 90, 16, 16)) {
			close();
			return;
		}
		// 清搜索
		if(hover(x, y, 134 + SHIFT, 108, 16, 16)) {
			click();
			this.search.setText("");
			regenerateRecipes();
			return;
		}
		// 搜索聚焦
		if(hover(x, y, 8 + SHIFT, 108, 16, 16)) {
			click();
			this.search.setFocused(!this.search.isFocused());
			return;
		}

		// 左侧已选格位：点一下删除，后面前移补位
		for(int i = 0; i < SLOTS.length; i++) {
			if(hover(x, y, SLOTS[i][0], SLOTS[i][1], 18, 18)) {
				if(selected[i] != null && !selected[i].isEmpty()) {
					click();
					removeAt(i);
				}
				return;
			}
		}

		// 网格：点选填入第一个空格，满 5 个不再接受
		for(int i = pageIndex * COLS; i < pageIndex * COLS + PER_PAGE; i++) {
			if(i >= recipes.size()) break;
			int ind = i - pageIndex * COLS;
			int ix = 7 + SHIFT + 18 * (ind % COLS);
			int iy = 17 + 18 * (ind / COLS);

			if(hover(x, y, ix, iy, 18, 18)) {
				click();
				addSelection(recipes.get(i).getInternalName());
				return;
			}
		}
	}

	private void addSelection(String name) {
		if(name == null || name.isEmpty()) return;

		// 已满 5 个
		boolean hasRoom = false;
		for(String s : selected) if(s == null || s.isEmpty()) { hasRoom = true; break; }
		if(!hasRoom) {
			this.limitWarning = "已选满 5 个配方";
			this.warnUntil = System.currentTimeMillis() + 2000L;
			return;
		}

		// 实时试算「已选 + 这个」合并后的种类数，超限就不让选
		String err = com.makentmgreatagain.recipes.MergedRecipe.checkLimits(recipeSet, selected, name);
		if(err != null) {
			this.limitWarning = err;
			this.warnUntil = System.currentTimeMillis() + 2500L;
			return;
		}

		for(int i = 0; i < selected.length; i++) {
			if(selected[i] == null || selected[i].isEmpty()) {
				selected[i] = name;
				return;
			}
		}
	}

	/** 超限提示文本与显示截止时间 */
	private String limitWarning = null;
	private long warnUntil = 0L;

	private void removeAt(int index) {
		for(int i = index; i < selected.length - 1; i++) {
			selected[i] = selected[i + 1];
		}
		selected[selected.length - 1] = "";
	}

	private void click() {
		this.mc.getSoundHandler().playSound(
				net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
						new ResourceLocation("gui.button.press"), 1.0F));
	}

	private void close() {
		FMLCommonHandler.instance().showGuiScreen(this.previousScreen);
	}

	@Override
	protected void keyTyped(char c, int key) {
		if(key == Keyboard.KEY_ESCAPE) {
			close();
			return;
		}
		if(key == Keyboard.KEY_RETURN) {
			this.search.setFocused(!this.search.isFocused());
			return;
		}
		if(this.search.textboxKeyTyped(c, key)) {
			regenerateRecipes();
			return;
		}
		if(key == Keyboard.KEY_UP && this.pageIndex > 0) this.pageIndex--;
		if(key == Keyboard.KEY_DOWN && this.pageIndex < this.size) this.pageIndex++;
	}

	/** 关屏时把 5 个格位整份发给服务端，保证删除补位后的顺序一致 */
	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);

		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < selected.length; i++) {
			data.setString("mf" + i, selected[i] == null ? "" : selected[i]);
		}
		data.setBoolean("mfSet", true);

		TileEntity te = (TileEntity) tile;
		PacketDispatcher.wrapper.sendToServer(new NBTControlPacket(data, te.xCoord, te.yCoord, te.zCoord));
	}
}