package com.makentmgreatagain.recipes;

/** 核爆发生器禁爆与核心配比 TDD 测试。
 *  用户定稿：
 *  ① 铯137/碘131/铀燃料/钍燃料核心不能参与爆轰（氙无对应核心物品，见报告）；
 *  ② 删除钍232→钍燃料棒的增殖配方（NukeGeneratorData）与除气机配方（OutgasserRecipes）；
 *  ③ 其余标准核心配方 7:3 改 1:1（主料1+辅料1），气凝氦自定义配方同步 1:1。 */
public class NukeCoreBalanceSpecTest {

	private static int passed = 0;

	private static void check(boolean cond, String name) {
		if(!cond) throw new AssertionError("FAIL: " + name);
		passed++;
	}

	public static void main(String[] args) {

		// ① 禁爆名单：四种核心
		check(NukeCoreBalanceSpec.isNonExplosive("cs137"), "cs137 non-explosive");
		check(NukeCoreBalanceSpec.isNonExplosive("i131"), "i131 non-explosive");
		check(NukeCoreBalanceSpec.isNonExplosive("uranium_fuel"), "uranium fuel non-explosive");
		check(NukeCoreBalanceSpec.isNonExplosive("thorium_fuel"), "thorium fuel non-explosive");

		// 其余核心照常可爆
		check(!NukeCoreBalanceSpec.isNonExplosive("u235"), "u235 still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("pu239"), "pu239 still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("plutonium_fuel"), "plutonium fuel still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("sr90"), "sr90 still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("co60"), "co60 still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("helium"), "helium still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("balefire"), "balefire still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive("mamba"), "mamba still explosive");
		check(!NukeCoreBalanceSpec.isNonExplosive(null), "null id safe");

		// ③ 标准核心配比 1:1
		check(NukeCoreBalanceSpec.MAIN_COUNT == 1, "main count 1");
		check(NukeCoreBalanceSpec.AUX_COUNT == 1, "aux count 1");

		// ② 钍燃料棒增殖配方必须从表里消失。
		// 裸 JVM 里 HBM 物品字段未注册（全 null，breed 会跳过），
		// 所以用替身物品填字段后再 ensureInit，让真实配方表逻辑跑起来。
		com.hbm.items.ModItems.ingot_th232 = new net.minecraft.item.Item();
		com.hbm.items.ModItems.ingot_thorium_fuel = new net.minecraft.item.Item();
		com.hbm.items.ModItems.ingot_u233 = new net.minecraft.item.Item();
		NukeGeneratorData.ensureInit();
		boolean thFuelGone = true;
		for(NukeGeneratorData.Breeding r : NukeGeneratorData.getRecipes()) {
			if(r.output.getItem() == com.hbm.items.ModItems.ingot_thorium_fuel) thFuelGone = false;
		}
		check(thFuelGone, "th232->thorium fuel breeding removed");

		// 钍232→铀233 增殖保留（只删钍燃料棒那条）
		boolean th233Kept = false;
		for(NukeGeneratorData.Breeding r : NukeGeneratorData.getRecipes()) {
			if(r.input.getItem() == com.hbm.items.ModItems.ingot_th232
				&& r.output.getItem() == com.hbm.items.ModItems.ingot_u233) th233Kept = true;
		}
		check(th233Kept, "th232->u233 breeding kept");

		System.out.println("ALL " + passed + " TESTS PASSED");
	}
}