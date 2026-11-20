package com.makentmgreatagain.blocks;

/**
 * 电池组「形状校验」的独立单元测试（不依赖 Minecraft）。
 *
 * <p>用户要求：立方体 / 长方体都行，但<b>内部必须实心</b>、<b>外壳必须完全封住</b>。
 * 所以校验分三条：尺寸成长方体（含立方体）、六面外壳封闭（端口算外壳）、内部无空洞。</p>
 *
 * <p>格子编码：0=空气(空洞)、1=外壳/端口、2=电池或电容。</p>
 */
public final class BatteryArrayShapeTest {

	private static int passed = 0;

	public static void main(String[] args) {
		testSolidCubeOk();
		testSolidCuboidOk();
		testHollowInsideRejected();
		testMissingCasingRejected();
		testPortCountsAsCasing();
		testPortOnEdgeRejected();
		testPortOnCornerRejected();
		testPortOnFaceCenterOk();
		testTooThinRejected();
		testNonRectRejected();
		testControllerFaceCenterOk();
		testControllerOnEdgeRejected();
		testControllerOnCornerRejected();
		System.out.println("ALL " + passed + " TESTS PASSED");
	}

	/** 3×3×3 实心：外圈外壳、中心电池 → 合法。 */
	private static void testSolidCubeOk() {
		int[][][] g = box(3, 3, 3);
		check(BatteryArrayShape.validate(g, 3, 3, 3) == null, "3x3x3 实心应合法");
		passed++;
	}

	/** 3×5×3 长方体实心 → 合法（用户的摆法）。 */
	private static void testSolidCuboidOk() {
		int[][][] g = box(3, 5, 3);
		check(BatteryArrayShape.validate(g, 3, 5, 3) == null, "3x5x3 实心应合法");
		passed++;
	}

	/** 内部挖空 → 必须拒绝（用户明确要求实心）。 */
	private static void testHollowInsideRejected() {
		int[][][] g = box(3, 3, 3);
		g[1][1][1] = 0;
		String err = BatteryArrayShape.validate(g, 3, 3, 3);
		check(err != null && err.contains("实心"), "内部空洞应被拒绝并提示实心");
		passed++;
	}

	/** 外壳缺一块（露出内部）→ 拒绝。 */
	private static void testMissingCasingRejected() {
		int[][][] g = box(3, 3, 3);
		g[0][1][1] = 0;   // 面上挖穿
		String err = BatteryArrayShape.validate(g, 3, 3, 3);
		check(err != null && err.contains("封"), "外壳未封闭应被拒绝");
		passed++;
	}

	/** 端口(编码3)在外壳面上算合法外壳。 */
	private static void testPortCountsAsCasing() {
		int[][][] g = box(3, 3, 3);
		g[0][1][1] = 3;   // 端口
		check(BatteryArrayShape.validate(g, 3, 3, 3) == null, "端口应算作外壳");
		passed++;
	}

	/**
	 * 端口放在<b>棱</b>上（同时属于两个面）→ 拒绝。
	 * 朝外方向不唯一，线缆会接错面，用户实测在边角会错位。
	 */
	private static void testPortOnEdgeRejected() {
		int[][][] g = box(3, 3, 3);
		g[0][0][1] = 3;   // x=0 与 y=0 两面交界的棱
		String err = BatteryArrayShape.validate(g, 3, 3, 3);
		check(err != null && err.contains("端口"), "棱上的端口应被拒绝，实际 " + err);
		passed++;
	}

	/** 端口放在<b>角</b>上（同时属于三个面）→ 拒绝。 */
	private static void testPortOnCornerRejected() {
		int[][][] g = box(3, 3, 3);
		g[0][0][0] = 3;   // 三面交角
		String err = BatteryArrayShape.validate(g, 3, 3, 3);
		check(err != null && err.contains("端口"), "角上的端口应被拒绝，实际 " + err);
		passed++;
	}

	/** 端口放在面中央（只属于一个面）→ 合法。 */
	private static void testPortOnFaceCenterOk() {
		int[][][] g = box(5, 5, 5);
		g[0][2][2] = 3;   // x=0 面正中
		check(BatteryArrayShape.validate(g, 5, 5, 5) == null, "面心端口应合法");
		passed++;
	}

	/** 任一维 < 3 无法包住内部 → 拒绝。 */
	private static void testTooThinRejected() {
		int[][][] g = box(3, 2, 3);
		String err = BatteryArrayShape.validate(g, 3, 2, 3);
		check(err != null, "厚度不足应被拒绝");
		passed++;
	}

	/** 尺寸非法（0）→ 拒绝，不抛异常。 */
	private static void testNonRectRejected() {
		check(BatteryArrayShape.validate(null, 3, 3, 3) != null, "null 应被拒绝");
		check(BatteryArrayShape.validate(new int[0][0][0], 0, 3, 3) != null, "0 尺寸应被拒绝");
		passed++;
	}

	/** 造一个 sx×sy×sz 的实心盒：表面=1(外壳)，内部=2(电池)。 */
	private static int[][][] box(int sx, int sy, int sz) {
		int[][][] g = new int[sx][sy][sz];
		for(int x = 0; x < sx; x++)
			for(int y = 0; y < sy; y++)
				for(int z = 0; z < sz; z++) {
					boolean surface = x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1;
					g[x][y][z] = surface ? 1 : 2;
				}
		return g;
	}

	/** 控制器在面心（三个局部坐标恰好一个贴边）→ 合法。 */
	private static void testControllerFaceCenterOk() {
		check(BatteryArrayShape.validateControllerPos(0, 2, 2, 5, 5, 5) == null, "5x5x5 面心控制器应合法");
		check(BatteryArrayShape.validateControllerPos(1, 1, 2, 3, 3, 3) == null, "3x3x3 面心控制器应合法");
		passed++;
	}

	/** 控制器在棱上（两个坐标贴边）→ 拒绝。 */
	private static void testControllerOnEdgeRejected() {
		String err = BatteryArrayShape.validateControllerPos(0, 0, 2, 5, 5, 5);
		check(err != null && err.contains("棱或角"), "棱上控制器应被拒绝");
		passed++;
	}

	/** 控制器在角上（三个坐标贴边）→ 拒绝。 */
	private static void testControllerOnCornerRejected() {
		String err = BatteryArrayShape.validateControllerPos(0, 4, 4, 5, 5, 5);
		check(err != null && err.contains("棱或角"), "角上控制器应被拒绝");
		passed++;
	}

	private static void check(boolean cond, String msg) {
		if(!cond) throw new AssertionError(msg);
	}

	private BatteryArrayShapeTest() {
	}
}