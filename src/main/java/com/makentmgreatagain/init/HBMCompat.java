package com.makentmgreatagain.init;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.inventory.recipes.RefineryRecipes;
import com.hbm.render.util.EnumSymbol;
import com.hbm.util.CompatFluidRegistry;
import com.makentmgreatagain.MakeNTMGreatAgain;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * 跨 HBM 版本兼容层（dev X5860 ⇄ 航天版 X5778）。
 *
 * 职责一：补注册"航天版有、dev 版没有"的流体（目前：HCL 盐酸）。
 * 职责二：注入依赖这类流体的机器配方（目前：炼油厂热盐水分解）。
 *
 * 铁律：本类以及所有后续配方代码引用盐酸时，一律用 HBMCompat.hcl()（内部是
 * Fluids.fromName("HCL")），永远不要写 Fluids.HCL——dev 版源码没有这个字段，
 * 直接引用会编译失败/运行崩溃。
 */
public class HBMCompat {

	/** 盐酸流体的注册名（= 航天版原版字段名的小写来源），两版通用查找键 */
	public static final String HCL_NAME = "HCL";

	/** 仅在 dev 版补注册时使用的专属 ID。注意 2001~2003 已被
	 *  SALT_WATER/HOT_SALT_WATER/SEA_WATER 占用，不要用 2003！ */
	private static final int HCL_ID = 2004;

	private HBMCompat() { }

	/** 取盐酸流体。两版都没有时返回 Fluids.NONE（调用方需判空）。 */
	public static FluidType hcl() {
		return Fluids.fromName(HCL_NAME);
	}

	/** 盐酸 tooltip 描述的语言键，两版共用（文案在附属 lang 里） */
	private static final String HCL_LORE_KEY = "hbmfluid.hcl.desc";

	/** 盐酸腐蚀等级：照抄航天版 HCL 的 30，保证两版同一流体同一数值 */
	private static final int HCL_CORROSIVE = 30;

	private static boolean loreTraitRegistered = false;

	/**
	 * 给盐酸加属性：腐蚀 50 + 一段描述文字。
	 *
	 * 腐蚀等级取航天版原值 30，所以对宿主流体只是重复写入同值、不改行为；
	 * 描述文字只有在这儿挂了才会显示（FT_FluidLore 不在 HBM 内置 trait 表里）。
	 * addTraits 按类名覆盖，重复调用安全。
	 */
	private static void applyHCLFlavor(FluidType fluid) {
		if (fluid == null || fluid == Fluids.NONE) return;

		// FT_FluidLore 不在 HBM 内置的 traitList/traitNameMap 里，手动登记一次：
		// traitList 决定 FluidType.addInfo 会不会遍历到它（不登记就不显示）；
		// traitNameMap 决定 hbmFluidTraits.json 读写时叫什么名字（不登记写模板会拿到 null 键名）。
		if (!loreTraitRegistered) {
			if (!FluidTrait.traitList.contains(FT_FluidLore.class)) {
				FluidTrait.traitList.add(FT_FluidLore.class);
			}
			if (!FluidTrait.traitNameMap.containsValue(FT_FluidLore.class)) {
				FluidTrait.traitNameMap.put(FT_FluidLore.TRAIT_NAME, FT_FluidLore.class);
			}
			loreTraitRegistered = true;
		}

		fluid.addTraits(new FT_Corrosive(HCL_CORROSIVE));
		fluid.addTraits(new FT_FluidLore(HCL_LORE_KEY));
	}

	/**
	 * 盐酸补注册（幂等，可反复调用）：
	 * - 航天版：HCL 是 HBM 原生流体，不重复注册流体对象、不占 2004，但腐蚀与描述照样补齐；
	 * - dev 版：查不到 → 用同名 "HCL" 补注册一个（id 2004），
	 *   之后 fromName("HCL") 两版都能拿到有效流体。
	 */
	public static void ensureHCL() {
		if (hcl() != Fluids.NONE) {   // 已有（航天版路径）→ 用它的流体对象，只补属性
			applyHCLFlavor(hcl());
			return;
		}

		FluidType fluid = CompatFluidRegistry.registerFluid(
				HCL_NAME, HCL_ID,
				0x00D452,                     // 盐酸绿：照抄航天版 HCL 的颜色
				3, 0, 3,                      // 毒3/可燃0/反应3：同上，与航天版逐项一致
				EnumSymbol.ACID,
				new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/gui/fluids/hcl.png")); // 附属自己的液面图
		fluid.setTemp(20);
		fluid.addTraits(Fluids.LIQUID);
		fluid.addContainers(new Fluids.CD_Canister(0x00D452));     // 允许铁罐装运（罐内液体色同盐酸绿）

		// 储罐模型贴图：assets/makentmgreatagain/textures/tank/tank_HCL.png
		ModFluids.addTankTexture(fluid, "tank_HCL");

		// 进流体识别码/列表。reloadFluids 的回调期间不要加 —— 它收尾时会自己把
		// foreignFluids 里的流体（含这个盐酸）加进 metaOrder，这里再加就重复了。
		if (!ModFluids.isDuringReload() && !Fluids.metaOrder.contains(fluid)) {
			Fluids.metaOrder.add(fluid);    // 进流体识别码/列表
		}

		applyHCLFlavor(fluid);

		MakeNTMGreatAgain.LOG.info("此 HBM 版本没有盐酸流体，附属已补注册 " + HCL_NAME + " (id " + HCL_ID + ")");
	}

	public static void addHotBrineRefineryRecipe() {
		FluidType in = ModFluids.HOT_SALT_WATER.type;
		if (in == null) return;                                  // 流体尚未注册（时机异常）
		if (RefineryRecipes.getRefinery(in) != null) return;     // 已有配方 → 不管

		FluidType hcl = hcl();
		if (hcl == Fluids.NONE) {
			MakeNTMGreatAgain.LOG.warn("盐酸(HCL)尚未就绪，炼油厂热盐水配方本次跳过");
			return;
		}

		try {
			Class<?> owner = Class.forName("com.hbm.inventory.recipes.RefineryRecipes");
			Class<?> entry = Class.forName("com.hbm.inventory.recipes.RefineryRecipes$RefineryRecipe");

			// 真实构造（dev源码核实）：RefineryRecipe(FluidStack, FluidStack, FluidStack, FluidStack, ItemStack)
			// 固体槽必须给非 null：HBM 的 NEI 处理器比较固体时不判空，null 会让查看配方直接崩溃
			Constructor<?> ctor = entry.getConstructors()[0];
			Object recipe = ctor.newInstance(
					new com.hbm.inventory.FluidStack(Fluids.HYDROGEN,  40),
					new com.hbm.inventory.FluidStack(Fluids.CHLORINE,  35),
					new com.hbm.inventory.FluidStack(hcl,              15),   
					new com.hbm.inventory.FluidStack(Fluids.SPENTSTEAM,10),
					new ItemStack(com.hbm.items.ModItems.powder_sodium, 1));     // 副产物：钠粉

			Field f = owner.getDeclaredField("recipes");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<FluidType, Object> map = (Map<FluidType, Object>) f.get(null);
			map.put(in, recipe);

			MakeNTMGreatAgain.LOG.info("已注入炼油厂配方：热盐水 → 氢40/氯35/盐酸15/低压蒸汽10");
		} catch (Throwable ex) {
			MakeNTMGreatAgain.LOG.error("炼油厂配方注入失败（HBM 内部结构可能随版本变化），请反馈日志", ex);
		}
	}
}
