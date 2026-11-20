package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.items.ModItems;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * “燃油 → 固体燃料”的合并配方。
 *
 * <p>热解炉里所有“单流体进、一块普通固体燃料出”的配方（野火燃料除外，它产的是
 * solid_fuel_bf）在大型催化化工厂的配方界面里合并成这一条；NEI 仍然逐条展示原始配方。</p>
 *
 * <p>选中这条配方后（处理逻辑在 {@code ModuleLargeChemicalFactory}）：</p>
 * <ul>
 *   <li>4 个流体输入罐不再锁定罐型，可以分别装 4 种不同的可制燃料流体；</li>
 *   <li>每个罐按自己的配方比例独立折算固体燃料，互不影响；</li>
 *   <li>某个罐里的流体不够再产一块固体燃料时，余料自动烧成灰烬
 *       （燃烧时间 100 的 SOOT 灰），随固体燃料一起从输出槽排出。</li>
 * </ul>
 *
 * <p>本类同时是流体白名单与折算比例的持有者：每次配方表重建时由
 * {@link LargeChemicalFactoryRecipes#rebuild()} 调用 {@link #setEntries(List)} 刷新，
 * 所以其他附属注册的配方、玩家改 JSON 魔改的比例都会自动跟上。</p>
 */
public class SolidFuelRecipe extends HeatChemicalRecipe {

	public static final String NAME = "makentmgreatagain.pyro.solidfuel";

	/** 一种可制燃料流体的折算规则 */
	public static class Entry {
		/** 流体种类 */
		public final FluidType type;
		/** 产一块固体燃料需要的流体量（mB），沿用热解炉原配方的比例 */
		public final int perFuel;

		public Entry(FluidType type, int perFuel) {
			this.type = type;
			this.perFuel = Math.max(1, perFuel);
		}
	}

	/** 流体 → 折算规则。重建时整体替换，运行期只读 */
	private final Map<FluidType, Entry> entries = new LinkedHashMap<FluidType, Entry>();

	public SolidFuelRecipe(int requiredHeat) {
		super(NAME, requiredHeat);
	}

	public void setEntries(List<Entry> list) {
		this.entries.clear();
		for(Entry entry : list) this.entries.put(entry.type, entry);
	}

	/** 这种流体能不能在这条配方里制固体燃料 */
	public boolean accepts(FluidType type) {
		return type != null && type != Fluids.NONE && this.entries.containsKey(type);
	}

	/** 产一块固体燃料需要多少 mB；不在白名单里返回 0 */
	public int perFuel(FluidType type) {
		Entry entry = this.entries.get(type);
		return entry == null ? 0 : entry.perFuel;
	}

	/** 白名单快照，给流体输入接口遍历用 */
	public List<Entry> getEntries() {
		return new ArrayList<Entry>(this.entries.values());
	}

	/** 余料烧成的灰：SOOT 灰在本体燃料表里燃烧时间正好是 100 */
	public static ItemStack ash(int count) {
		return DictFrame.fromOne(ModItems.powder_ash, EnumAshType.SOOT, count);
	}

	/** 悬停提示：说明多流体并行与余料转灰的规则。
	 *  覆写 print 而不是 header：旧版 HBM 的 GenericRecipe 没有 header 方法。 */
	@Override
	public List<String> print() {
		List<String> list = super.print();
		int insertAt = list.isEmpty() ? 0 : 1;
		list.add(insertAt, EnumChatFormatting.GRAY + com.hbm.util.i18n.I18nUtil.resolveKey(NAME + ".desc1"));
		list.add(insertAt + 1, EnumChatFormatting.GRAY + com.hbm.util.i18n.I18nUtil.resolveKey(NAME + ".desc2"));
		return list;
	}

	/** 搜索框里输入任意一种原料流体的名字也能找到这条配方 */
	@Override
	public boolean matchesSearch(String substring) {
		if(super.matchesSearch(substring)) return true;
		String needle = substring.toLowerCase(Locale.US);
		for(Entry entry : this.entries.values()) {
			if(entry.type.getLocalizedName().toLowerCase(Locale.US).contains(needle)) return true;
		}
		return false;
	}
}