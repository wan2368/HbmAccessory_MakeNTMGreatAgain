package com.makentmgreatagain.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.makentmgreatagain.util.PinyinSearch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.oredict.OreDictionary;

/** The selector owns only local UI state; selecting hands off one immutable target. */
@SideOnly(Side.CLIENT)
public class GuiNeutrinoLocator extends GuiScreen {

    public static final class Target {
        public final int blockId, meta;
        public final String oreKey, label;

        public Target(int blockId, int meta, String oreKey, String label) {
            this.blockId = blockId;
            this.meta = meta;
            this.oreKey = oreKey;
            this.label = label;
        }
    }

    private static final String LANG = "gui.makentmgreatagain.neutrino_locator.";
    private static final Pattern ORE_KEY = Pattern.compile("ore[A-Z][A-Za-z0-9_]*");
    private static final ResourceLocation PANEL = new ResourceLocation("makentmgreatagain", "textures/gui/neutrino_locator.png");
    private static final int WIDTH = 244, HEIGHT = 236;
    private static final int LIST_Y = 44, LIST_W = WIDTH - 16, ROW_H = 11, ROWS = 5;
    private static final int SLOT = 18, SAMPLE_X = 15, SAMPLE_Y = 131;
    private static final int GRID_X = (WIDTH - 9 * SLOT) / 2, GRID_Y = 131, HOTBAR_GAP = 4;
    /** The selected block target is retained when reopening the locator. */
    private static Target sample;

    /** Keep the scanner API list-shaped while exposing the single selected sample. */
    public static List<Target> currentSamples() {
        return sample == null ? Collections.emptyList() : Collections.singletonList(sample);
    }

    /** 样本槽图标：按目标方块+meta 造一个仅用于显示的 ItemStack。 */
    private ItemStack inventoryStack(int col, int row) {
        int slot = row == 3 ? col : (row + 1) * 9 + col;
        return mc.thePlayer == null ? null : mc.thePlayer.inventory.getStackInSlot(slot);
    }

    private static ItemStack sampleStack(Target t) {
        if (t == null) return null;
        Block block = Block.getBlockById(t.blockId);
        if (block == null) return null;
        Item item = Item.getItemFromBlock(block);
        if (item == null) return null;
        int meta = t.meta < 0 ? 0 : t.meta;
        return new ItemStack(item, 1, meta);
    }

    private static final class Entry {
        final Target target;
        final String searchText;
        final String details;

        Entry(Target target, String details, String extraNames) {
            this.target = target;
            this.details = details;
            this.searchText = (target.label + " " + details + " " + extraNames).toLowerCase(Locale.ROOT);
        }
    }

    private final Predicate<Target> onSelect;
    private final List<Entry> matches = new ArrayList<>();
    private List<Entry> catalog;
    private GuiTextField search;
    private GuiButton confirmButton, previousButton, nextButton;
    private int left, top, first, selected, heldOpeningKey;
    private boolean submitted;

    public GuiNeutrinoLocator(Predicate<Target> onSelect) {
        this(onSelect, 0);
    }

    /** Pass the held locator binding for a long-press open, or 0 for a tap/released key. */
    public GuiNeutrinoLocator(Predicate<Target> onSelect, int openingKey) {
        this.onSelect = Objects.requireNonNull(onSelect);
        this.heldOpeningKey = openingKey;
    }

    @Override
    @SuppressWarnings("unchecked") // Forge 1.7.10 exposes buttonList as a raw List.
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String query = search == null ? "" : search.getText();
        boolean focused = search != null && search.isFocused();
        search = new GuiTextField(fontRendererObj, left + 8, top + 22, WIDTH - 16, 16);
        search.setMaxStringLength(80);
        search.setText(query);
        search.setFocused(focused);
        if (catalog == null) {
            catalog = buildCatalog();
        }
        confirmButton = new GuiButton(0, left + (WIDTH - 104) / 2, top + 207, 104, 20, I18n.format(LANG + "confirm"));
        previousButton = new GuiButton(2, left + 8, top + 103, 22, 20, "<");
        nextButton = new GuiButton(3, left + WIDTH - 30, top + 103, 22, 20, ">");
        buttonList.add(confirmButton);
        buttonList.add(previousButton);
        buttonList.add(nextButton);
        updateConfirmButton();
        filter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    private static boolean validBlock(Block block) {
        return block != null && Block.getIdFromBlock(block) > 0 && block.getMaterial() != Material.air;
    }

    private static String registryName(Block block) {
        return String.valueOf(Block.blockRegistry.getNameForObject(block));
    }

    private static String blockName(Block block, ItemStack stack) {
        try {
            String name = stack == null ? block.getLocalizedName() : stack.getDisplayName();
            if (name != null && !name.trim().isEmpty() && !name.endsWith(".name")) {
                return StringUtils.stripControlCodes(name);
            }
        } catch (RuntimeException ignored) {
            // Some mod blocks have no display name outside their normal world/item context.
        }
        return registryName(block);
    }

    private static Target blockTarget(Block block, int meta, ItemStack stack) {
        return new Target(Block.getIdFromBlock(block), meta, null, blockName(block, stack));
    }

    private static Target stackTarget(ItemStack stack, boolean allowWildcard) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return null;
        ItemBlock item = (ItemBlock) stack.getItem();
        Block block = Block.getBlockFromItem(item);
        if (!validBlock(block)) return null;
        int damage = stack.getItemDamage();
        if (damage == OreDictionary.WILDCARD_VALUE) {
            return allowWildcard ? blockTarget(block, 0, null) : null;
        }
        int meta = item.getMetadata(damage);
        if (meta < 0 || meta > 15) return null;
        // Non-subtyped items do not encode placed state (e.g. a machine's orientation).
        return blockTarget(block, !item.getHasSubtypes() && meta == 0 ? -1 : meta, stack);
    }

    private static List<Entry> buildCatalog() {
        Map<Integer, Entry> blocks = new HashMap<>();
        for (Object registered : Block.blockRegistry) {
            Block block = (Block) registered;
            if (!validBlock(block)) continue;
            Item item = Item.getItemFromBlock(block);
            String name = registryName(block);
            boolean foundVariant = false;
            if (item instanceof ItemBlock) {
                List<ItemStack> variants = new ArrayList<>();
                try {
                    CreativeTabs tab = block.getCreativeTabToDisplayOn();
                    block.getSubBlocks(item, tab == null ? CreativeTabs.tabAllSearch : tab, variants);
                } catch (RuntimeException ignored) {
                    // Fallback to the registry entry if a mod does not expose usable variants.
                }
                for (ItemStack variant : variants) {
                    Target target = stackTarget(variant, false);
                    if (target == null || target.blockId != Block.getIdFromBlock(block)) continue;
                    blocks.putIfAbsent(target.blockId * 17 + target.meta + 1,
                            new Entry(target, target.meta < 0 ? name : name + " #" + target.meta, ""));
                    foundVariant = true;
                }
            }
            if (!foundVariant) {
                Target target = blockTarget(block, -1, null);
                blocks.putIfAbsent(target.blockId * 17, new Entry(target, name, ""));
            }
        }

        List<Entry> entries = new ArrayList<>(blocks.values());
        for (String key : OreDictionary.getOreNames()) {
            if (key.length() > 64 || !ORE_KEY.matcher(key).matches()) continue;
            Target representative = null;
            StringBuilder names = new StringBuilder();
            for (ItemStack stack : OreDictionary.getOres(key, false)) {
                Target member = stackTarget(stack, true);
                if (member == null) continue;
                if (representative == null) representative = member;
                names.append(' ').append(member.label).append(' ').append(registryName(Block.getBlockById(member.blockId)));
            }
            if (representative != null) {
                Target target = new Target(representative.blockId, representative.meta, key,
                        I18n.format(LANG + "ore_family", representative.label));
                entries.add(new Entry(target, key, names.toString()));
            }
        }
        entries.sort(Comparator.comparing((Entry entry) -> entry.target.label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.details));
        return entries;
    }

    /** Search results populate the single sample slot. */
    private void putSample(Target target) {
        if (target == null) return;
        sample = target;
        updateConfirmButton();
    }

    private void updateConfirmButton() {
        confirmButton.enabled = sample != null;
    }

    private void filter() {
        String[] words = search.getText().toLowerCase(Locale.ROOT).trim().split("\\s+");
        matches.clear();
        for (Entry entry : catalog) {
            boolean found = true;
            for (String word : words) {
                if (!PinyinSearch.contains(entry.searchText, word)) {
                    found = false;
                    break;
                }
            }
            if (found) matches.add(entry);
        }
        selected = 0;
        scrollTo(0);
    }

    private void scrollTo(int row) {
        first = Math.max(0, Math.min(row, matches.size() - ROWS));
        selected = Math.max(first, Math.min(selected, first + ROWS - 1));
        previousButton.enabled = first > 0;
        nextButton.enabled = first + ROWS < matches.size();
    }

    /** Confirm the selected sample and hand it to the scanner. */
    private void confirm() {
        if (submitted || mc.thePlayer == null || mc.theWorld == null) return;
        if (sample == null) return;
        if (!onSelect.test(sample)) return;
        submitted = true;
        mc.displayGuiScreen(null);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0: confirm(); break;
            case 2: scrollTo(first - ROWS); break;
            case 3: scrollTo(first + ROWS); break;
            default: break;
        }
    }

    @Override
    public void handleKeyboardInput() {
        if (heldOpeningKey > 0 && Keyboard.getEventKey() == heldOpeningKey) {
            if (!Keyboard.getEventKeyState()) heldOpeningKey = 0;
            else if (Keyboard.isRepeatEvent()) return;
            else heldOpeningKey = 0; // A fresh press is valid even if the release happened before opening.
        }
        super.handleKeyboardInput();
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (key == Keyboard.KEY_ESCAPE || (!search.isFocused() && key != 0
                && key == mc.gameSettings.keyBindInventory.getKeyCode() && !Keyboard.isRepeatEvent())) {
            mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_TAB) {
            if (!Keyboard.isRepeatEvent()) search.setFocused(!search.isFocused());
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            // Enter adds the highlighted result while searching; otherwise it confirms.
            if (search.isFocused()) {
                if (!matches.isEmpty()) putSample(matches.get(selected).target);
            } else {
                confirm();
            }
            return;
        }
        if (!matches.isEmpty() && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
            selected = Math.max(0, Math.min(selected + (key == Keyboard.KEY_UP ? -1 : 1), matches.size() - 1));
            if (selected < first) scrollTo(selected);
            else if (selected >= first + ROWS) scrollTo(selected - ROWS + 1);
            return;
        }
        if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) {
            scrollTo(first + (key == Keyboard.KEY_PRIOR ? -ROWS : ROWS));
            return;
        }
        String old = search.getText();
        if (search.textboxKeyTyped(typed, key)) {
            if (!old.equals(search.getText())) filter();
        } else {
            super.keyTyped(typed, key);
        }
    }

    @Override
    public void handleMouseInput() {
        int button = Mouse.getEventButton();
        if (button >= 0) {
            int binding = button - 100;
            if (binding == heldOpeningKey) {
                heldOpeningKey = 0;
                if (!Mouse.getEventButtonState()) return;
            }
            if (Mouse.getEventButtonState() && binding == mc.gameSettings.keyBindInventory.getKeyCode()
                    && !search.isFocused() && (button > 1 || !inside(mouseX(), mouseY(), left, top, WIDTH, HEIGHT))) {
                mc.displayGuiScreen(null);
                return;
            }
        }
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (inside(mouseX(), mouseY(), left + 8, top + LIST_Y, LIST_W, ROWS * ROW_H)) {
            scrollTo(first + (wheel > 0 ? -3 : 3));
        }
    }

    private int mouseX() {
        return Mouse.getEventX() * width / mc.displayWidth;
    }

    private int mouseY() {
        return height - Mouse.getEventY() * height / mc.displayHeight - 1;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        search.mouseClicked(x, y, button);
        if ((button == 0 || button == 1) && inside(x, y, left + SAMPLE_X, top + SAMPLE_Y, SLOT, SLOT)) {
            if (button == 1) sample = null;
            updateConfirmButton();
            return;
        }
        if (button == 0) {
            if (inside(x, y, left + 8, top + LIST_Y, LIST_W, ROWS * ROW_H)) {
                int index = first + (y - top - LIST_Y) / ROW_H;
                // Selecting a result sets the sample without closing the screen.
                if (index < matches.size()) putSample(matches.get(index).target);
                return;
            }
            int ix = x - left - GRID_X;
            int iy = y - top - GRID_Y;
            if (mc.thePlayer != null && ix >= 0 && ix < 9 * SLOT && iy >= 0 && iy < 4 * SLOT + HOTBAR_GAP) {
                if (iy >= 3 * SLOT + HOTBAR_GAP) iy -= HOTBAR_GAP;
                else if (iy >= 3 * SLOT) return;
                ItemStack stack = inventoryStack(ix / SLOT, iy / SLOT);
                Target selectedTarget = stackTarget(stack, false);
                if (selectedTarget != null) putSample(selectedTarget);
                return;
            }
        }
        super.mouseClicked(x, y, button);
    }

    private static boolean inside(int x, int y, int bx, int by, int w, int h) {
        return x >= bx && y >= by && x < bx + w && y < by + h;
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(PANEL);
        drawTexturedModalRect(left, top, 0, 0, WIDTH, HEIGHT);
        drawCenteredString(fontRendererObj, I18n.format(LANG + "title"), left + WIDTH / 2, top + 8, 0xFFFFFF);
        search.drawTextBox();
        if (search.getText().isEmpty()) {
            if (!search.isFocused()) drawString(fontRendererObj, I18n.format(LANG + "focus_hint"), left + 13, top + 26, 0xA7B3BB);
        }

        int listX = left + 8;
        int listY = top + LIST_Y;
        List<String> hover = null;
        for (int row = 0; row < ROWS && first + row < matches.size(); row++) {
            int index = first + row;
            int rowY = listY + row * ROW_H;
            boolean over = inside(x, y, listX, rowY, LIST_W, ROW_H);
            if (over || index == selected) {
                drawRect(listX, rowY, listX + LIST_W, rowY + ROW_H, over ? 0xFF35544F : 0xFF263D3D);
            }
            Entry entry = matches.get(index);
            String suffix = entry.target.oreKey == null ? (entry.target.meta < 0 ? "" : "#" + entry.target.meta) : entry.target.oreKey;
            suffix = fontRendererObj.trimStringToWidth(suffix, LIST_W / 3);
            int suffixWidth = fontRendererObj.getStringWidth(suffix);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(entry.target.label, LIST_W - suffixWidth - 12),
                    listX + 4, rowY + 2, entry.target.oreKey == null ? 0xECEFF1 : 0x8BE3CF);
            drawString(fontRendererObj, suffix, listX + LIST_W - suffixWidth - 4, rowY + 2, 0xA7B3BB);
            if (over) hover = Collections.singletonList(entry.target.label + " (" + entry.details + ")");
        }
        if (matches.isEmpty()) {
            drawCenteredString(fontRendererObj, I18n.format(LANG + "empty"), left + WIDTH / 2, listY + 8, 0xA7B3BB);
        }
        String range = matches.isEmpty() ? "0 / 0" : (first + 1) + "-" + Math.min(first + ROWS, matches.size()) + " / " + matches.size();
        drawCenteredString(fontRendererObj, range, left + WIDTH / 2, top + 105, 0xFFFFFF);
        String inventory = fontRendererObj.trimStringToWidth(I18n.format(LANG + "inventory"), WIDTH - 16);
        drawCenteredString(fontRendererObj, inventory, left + WIDTH / 2, top + 116, 0xFFFFFF);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = inventoryStack(col, row);
                int sx = left + GRID_X + col * SLOT;
                int sy = top + GRID_Y + row * SLOT + (row == 3 ? HOTBAR_GAP : 0);
                boolean over = inside(x, y, sx, sy, SLOT, SLOT);
                drawSlot(stack, sx, sy, over, true);
                if (over && stack != null) {
                    hover = Collections.singletonList(StringUtils.stripControlCodes(stack.getDisplayName()));
                }
            }
        }

        int sampleY = top + SAMPLE_Y;
        boolean sampleHover = inside(x, y, left + SAMPLE_X, sampleY, SLOT, SLOT);
        drawHorizontalLine(left + SAMPLE_X, left + SAMPLE_X + SLOT - 1, sampleY, 0xFF80CDFF);
        drawHorizontalLine(left + SAMPLE_X, left + SAMPLE_X + SLOT - 1, sampleY + SLOT - 1, 0xFF80CDFF);
        drawVerticalLine(left + SAMPLE_X, sampleY, sampleY + SLOT - 1, 0xFF80CDFF);
        drawVerticalLine(left + SAMPLE_X + SLOT - 1, sampleY, sampleY + SLOT - 1, 0xFF80CDFF);
        ItemStack sampleIcon = sampleStack(sample);
        if (sampleIcon == null) drawCenteredString(fontRendererObj, "?", left + SAMPLE_X + SLOT / 2, sampleY + 5, 0xB5B5B5);
        drawSlot(sampleIcon, left + SAMPLE_X, sampleY, sampleHover, false);
        if (sampleHover) {
            hover = new ArrayList<>(2);
            hover.add(I18n.format(LANG + "sample_slot"));
            if (sample != null) hover.add(sample.label);
        }
        if (inside(x, y, left + 8, top + 22, WIDTH - 16, 16)) hover = Collections.singletonList(I18n.format(LANG + "search"));
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        super.drawScreen(x, y, partialTicks);
        if (hover != null) drawHoveringText(hover, x, y, fontRendererObj);
    }

    private void drawSlot(ItemStack stack, int x, int y, boolean over, boolean showCount) {
        if (over) drawRect(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x4099CFFF);
        if (stack == null) return;
        // 物品渲染会开深度测试/光照/混合并改矩阵与颜色；只 pop ENABLE|CURRENT 不够，
        // 残留的 depth/blend/颜色会污染随后 super.drawScreen 画的按钮（下半截不渲染）。
        // 这里全量 push/pop 属性+矩阵，并在 finally 里把 2D 绘制状态复位。
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x + 1, y + 1);
            if (showCount) itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x + 1, y + 1);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            RenderHelper.disableStandardItemLighting();
        }
    }
}
