package com.makentmgreatagain.blocks;

/**
 * 电池组「形状校验」—— 纯逻辑，不依赖 Minecraft，可独立单测。
 *
 * <h2>规则（用户给定）</h2>
 * <ol>
 *   <li><b>立方体 / 长方体</b>都合法，三边都必须 ≥ 3（要有外壳包住内部）；</li>
 *   <li><b>外壳必须完全封住</b>：六个面上每一格都是外壳或端口，不许有洞；</li>
 *   <li><b>内部必须实心</b>：内部每格都得是电池/电容，不许留空气。</li>
 * </ol>
 *
 * <p>比 PWR 严格：PWR 允许内部有空腔、外形任意；这里只收规整的实心长方体。</p>
 *
 * <h2>格子编码</h2>
 * <p>{@link #AIR}=0 空洞、{@link #CASING}=1 外壳、{@link #CELL}=2 电池/电容、
 * {@link #PORT}=3 端口（端口算外壳）。返回 {@code null} 表示合法，否则返回中文错误描述。</p>
 */
public final class BatteryArrayShape {

	public static final int AIR = 0;
	public static final int CASING = 1;
	public static final int CELL = 2;
	public static final int PORT = 3;

	private BatteryArrayShape() {
	}

	/**
	 * 校验一个 sx×sy×sz 的实心长方体。
	 *
	 * @param g  格子编码 {@code [x][y][z]}
	 * @return null=合法；否则为可直接发给玩家的中文错误描述
	 */
	public static String validate(int[][][] g, int sx, int sy, int sz) {
		return validate(g, sx, sy, sz, null);
	}

	/**
	 * 校验并把所有不合格格子的<b>局部坐标</b>收集到 {@code badOut}（可为 null）。
	 *
	 * <p>调用方据此在缺口方块上画红边，玩家一眼能看出该补哪里。</p>
	 */
	public static String validate(int[][][] g, int sx, int sy, int sz, java.util.List<int[]> badOut) {
		if(g == null || sx <= 0 || sy <= 0 || sz <= 0) return "结构尺寸无效。";
		if(sx < 3 || sy < 3 || sz < 3)
			return "结构太小：长宽高都必须至少 3 格（当前 " + sx + "×" + sy + "×" + sz + "）。";
		if(g.length < sx) return "结构数据不完整。";

		String firstErr = null;

		// 1) 六面必须被外壳/端口完全封住
		for(int x = 0; x < sx; x++) {
			for(int y = 0; y < sy; y++) {
				for(int z = 0; z < sz; z++) {
					boolean surface = x == 0 || y == 0 || z == 0
							|| x == sx - 1 || y == sy - 1 || z == sz - 1;
					if(!surface) continue;

					int v = at(g, x, y, z);
					if(v != CASING && v != PORT) {
						if(badOut != null) badOut.add(new int[] { x, y, z });
						if(firstErr == null) {
							firstErr = v == AIR
								? "外壳没有封住：有缺口，请补上外壳或端口。"
								: "外壳没有封住：表面只能是外壳或端口。";
						}
					} else if(v == PORT) {
						// 端口必须只属于一个面：棱/角上的端口朝外方向不唯一，
						// 线缆会接错面（用户实测边角错位），直接拒绝并提示。
						int faces = (x == 0 || x == sx - 1 ? 1 : 0)
								+ (y == 0 || y == sy - 1 ? 1 : 0)
								+ (z == 0 || z == sz - 1 ? 1 : 0);
						if(faces != 1) {
							if(badOut != null) badOut.add(new int[] { x, y, z });
							if(firstErr == null) {
								firstErr = "端口不能放在棱或角上：请把端口移到某个面的中央区域（局部坐标 "
										+ x + "," + y + "," + z + "）。";
							}
						}
					}
				}
			}
		}

		// 2) 内部必须实心（全是电池/电容）
		for(int x = 1; x < sx - 1; x++) {
			for(int y = 1; y < sy - 1; y++) {
				for(int z = 1; z < sz - 1; z++) {
					int v = at(g, x, y, z);
					if(v != CELL) {
						if(badOut != null) badOut.add(new int[] { x, y, z });
						if(firstErr == null) {
							firstErr = v == AIR
								? "内部必须实心：有空位，请填满电池或电容。"
								: "内部必须实心且只能放电池/电容。";
						}
					}
				}
			}
		}

		return firstErr;
	}

	/**
	 * 控制器必须嵌在某个外壳面的「面中心区域」：三个局部坐标里恰好一个贴边
	 * （=0 或 =尺寸-1，即所在的面），另外两个都不贴边。
	 * 贴两条边说明在棱上、贴三条边在角上，一律拒绝（与 PORT 的「只属于一个面」同一套规则）。
	 *
	 * @return null=合法；否则为可直接发给玩家的中文错误描述
	 */
	public static String validateControllerPos(int lx, int ly, int lz, int sx, int sy, int sz) {
		int faces = (lx == 0 || lx == sx - 1 ? 1 : 0)
				+ (ly == 0 || ly == sy - 1 ? 1 : 0)
				+ (lz == 0 || lz == sz - 1 ? 1 : 0);
		if(faces != 1)
			return "控制器不能放在结构的棱或角上：请把控制器移到某个外壳面的中心区域。";
		return null;
	}

	private static int at(int[][][] g, int x, int y, int z) {
		if(x >= g.length || y >= g[x].length || z >= g[x][y].length) return AIR;
		return g[x][y][z];
	}
}