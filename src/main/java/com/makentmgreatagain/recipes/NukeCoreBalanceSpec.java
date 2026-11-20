package com.makentmgreatagain.recipes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 核爆发生器平衡规格（纯逻辑，便于单元测试）。
 *  ① 禁爆核心：铯137/碘131/铀燃料/钍燃料——能进核心槽，但不能启动爆轰；
 *  ② 标准核心装配配比 1:1（主料1+辅料1，原 7:3）。 */
public final class NukeCoreBalanceSpec {

	private NukeCoreBalanceSpec() { }

	/** 不能参与爆轰的核心 id。 */
	private static final Set<String> NON_EXPLOSIVE = new HashSet<String>(Arrays.asList(
		"cs137", "i131", "uranium_fuel", "thorium_fuel"));

	public static boolean isNonExplosive(String id) {
		return id != null && NON_EXPLOSIVE.contains(id);
	}

	/** 配方里主原料的个数（原 7）。 */
	public static final int MAIN_COUNT = 1;
	/** 配方里辅料的个数（铍锭 / 铅板，原 3）。 */
	public static final int AUX_COUNT = 1;
}