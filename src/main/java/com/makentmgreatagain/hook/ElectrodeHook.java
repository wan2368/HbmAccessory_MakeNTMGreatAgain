package com.makentmgreatagain.hook;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 被 ASM 注入的代码在运行时调用。
 *
 * 本类不属于 coremod（不在 TransformerExclusions 范围内），
 * 由 LaunchClassLoader 加载，因此可以正常引用 Minecraft 与 HBM 的类。
 *
 * 【关键设计：参数一律用 Object】
 * 打包发布时 ForgeGradle 的 reobf 会把本类方法签名里的 Minecraft 类型
 * 换成 SRG 名：isArcElectrode(Item) 会变成 isArcElectrode(adb)，
 * 描述符从 (Lnet/minecraft/item/Item;)Z 变成 (Ladb;)Z。
 * 而 ArcFurnaceTransformer 注入指令时写的描述符是硬编码字符串，reobf 不会去改它，
 * 两边一旦对不上，进游戏就是 NoSuchMethodError。
 * 用 Object 作参数后，描述符恒为 (Ljava/lang/Object;)Z —— java.lang 不参与混淆，
 * 开发环境与发布环境完全一致，这才是能同时兼容两者的唯一稳妥写法。
 * 调用点压入的是 Item / ItemStack，作为 Object 的子类型传入，字节码校验同样通过。
 */
public class ElectrodeHook {

	/**
	 * 替换 HBM 的 `getItem() == ModItems.arc_electrode` 判定。
	 * 传入 Object 以便描述符在混淆前后保持一致。
	 */
	public static boolean isArcElectrode(Object obj) {
		if (!(obj instanceof Item)) return false;
		Item item = (Item) obj;

		if (item == com.hbm.items.ModItems.arc_electrode) return true;

		Item cmb = com.makentmgreatagain.init.ModItems.cmb_electrode;
		return cmb != null && item == cmb;
	}

	/**
	 * 判断是否为 CMB 电极，用于短路耐久计算（CMB 电极永不磨损）。
	 */
	public static boolean isCmbElectrode(Object obj) {
		if (!(obj instanceof ItemStack)) return false;
		ItemStack stack = (ItemStack) obj;

		Item cmb = com.makentmgreatagain.init.ModItems.cmb_electrode;
		return cmb != null && stack.getItem() == cmb;
	}
}
