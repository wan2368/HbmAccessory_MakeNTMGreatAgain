package com.makentmgreatagain.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 替换 ItemBlueprints 的 tooltip：
 * - 本模组的等级模板：默认只提示按住 Shift，按住后每页最多 10 条，Shift+X / Shift+Z 翻页；
 * - 原版蓝图：保持原来的列表显示。
 */
@SideOnly(Side.CLIENT)
public final class AnvilTemplateTooltip {

    private static final int PER_PAGE = 9;
    private static final Map<String, Integer> PAGES = new HashMap<String, Integer>();
    private static boolean lastX = false;
    private static boolean lastZ = false;

    private AnvilTemplateTooltip() {
    }

    public static void addInformation(ItemStack stack, List list) {
        if(stack == null || !stack.hasTagCompound()) return;

        String poolName = stack.stackTagCompound.getString("pool");
        if(poolName == null || poolName.isEmpty()) return;

        List<String> pool = GenericRecipes.blueprintPools.get(poolName);
        if(pool == null || pool.isEmpty()) return;

        boolean ours = poolName.startsWith("mnga.anvil.");

        if(!ours) {
            if(poolName.startsWith(GenericRecipes.POOL_PREFIX_SECRET)) {
                list.add(EnumChatFormatting.RED + "Cannot be copied!");
            } else {
                list.add(EnumChatFormatting.YELLOW + "Right-click to copy (requires paper)");
            }
            for(String name : pool) {
                GenericRecipe recipe = GenericRecipes.nameToRecipeGlobal.get(name);
                if(recipe != null) list.add(recipe.getLocalizedName());
            }
            return;
        }

        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean x = Keyboard.isKeyDown(Keyboard.KEY_X);
        boolean z = Keyboard.isKeyDown(Keyboard.KEY_Z);

        if(shift && x && !lastX) cycle(poolName, pool.size(), 1);
        if(shift && z && !lastZ) cycle(poolName, pool.size(), -1);
        lastX = x;
        lastZ = z;

        list.add(EnumChatFormatting.GOLD + templateName(poolName));

        if(!shift) {
            list.add(EnumChatFormatting.YELLOW + "按住 Shift 查看配方");
            list.add(EnumChatFormatting.DARK_GRAY + "Shift+X 下一页");
            list.add(EnumChatFormatting.DARK_GRAY + "Shift+Z 上一页");
            return;
        }

        int pages = Math.max(1, (pool.size() + PER_PAGE - 1) / PER_PAGE);
        int page = page(poolName, pool.size());
        int from = page * PER_PAGE;
        int to = Math.min(pool.size(), from + PER_PAGE);

        list.add(EnumChatFormatting.AQUA + "第 " + (page + 1) + "/" + pages + " 页（" + (from + 1) + "-" + to + " / " + pool.size() + "）");

        for(int i = from; i < to; i++) {
            GenericRecipe recipe = GenericRecipes.nameToRecipeGlobal.get(pool.get(i));
            if(recipe != null) list.add(recipe.getLocalizedName());
        }

        list.add(EnumChatFormatting.DARK_GRAY + "Shift+X 下一页");
        list.add(EnumChatFormatting.DARK_GRAY + "Shift+Z 上一页");
    }

    private static int page(String pool, int size) {
        Integer stored = PAGES.get(pool);
        int page = stored == null ? 0 : stored;
        int pages = Math.max(1, (size + PER_PAGE - 1) / PER_PAGE);
        if(page >= pages) page = pages - 1;
        if(page < 0) page = 0;
        PAGES.put(pool, page);
        return page;
    }

    private static void cycle(String pool, int size, int delta) {
        int pages = Math.max(1, (size + PER_PAGE - 1) / PER_PAGE);
        int page = page(pool, size) + delta;
        if(page >= pages) page = 0;
        if(page < 0) page = pages - 1;
        PAGES.put(pool, page);
    }

    private static String templateName(String pool) {
        String tail = pool.substring("mnga.anvil.".length());
        if(tail.equals("murky")) return "暗黑砧装配模板";
        if(tail.startsWith("tier")) {
            String n = tail.substring("tier".length());
            return n + " 级砧装配模板";
        }
        return "装配模板";
    }
}
