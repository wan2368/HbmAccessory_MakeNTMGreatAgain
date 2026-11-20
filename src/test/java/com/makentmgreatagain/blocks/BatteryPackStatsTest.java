package com.makentmgreatagain.blocks;

/**
 * 大型电池组「容量 / 充放电速率」聚合规则的独立单元测试（不依赖 Minecraft）。
 *
 * <p>规则（用户给定）：</p>
 * <ol>
 *   <li><b>容量</b> = 所有电池容量之和；</li>
 *   <li><b>基础充/放</b> = 组内最低那块电池的速率（堆叠电池不提速）；</li>
 *   <li><b>电容按等级给倍率，充电比放电慢</b>，可堆叠（同级相加）：
 *       金 1.1/1.3、铌 1.4/1.8、钽 1.8/2.5、Sa酸铁 2.5/5.0。</li>
 * </ol>
 */
public final class BatteryPackStatsTest {

	private static int passed = 0;

	public static void main(String[] args) {
		testSingleBattery();
		testStackAddsCapacityNotRate();
		testRateIsMinBattery();
		testGoldTier();
		testNiobiumTier();
		testTantaliumTier();
		testSchrabidateTier();
		testStackSameTier();
		testMixedTiers();
		testChargeSlowerThanDischarge();
		testNoBattery();
		testEmpty();
		System.out.println("ALL " + passed + " TESTS PASSED");
	}

	private static void testSingleBattery() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{ BatteryCellSpec.SODIUM_BATTERY });
		check(s.capacity == 500_000_000L, "单钠容量");
		check(s.chargeSpeed == 2_500_000L, "无电容充电=基础");
		check(s.dischargeSpeed == 833_333L, "无电容放电=基础");
		passed++;
	}

	private static void testStackAddsCapacityNotRate() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.SODIUM_BATTERY, BatteryCellSpec.SODIUM_BATTERY });
		check(s.capacity == 1_000_000_000L, "堆叠容量相加");
		check(s.chargeSpeed == 2_500_000L, "堆叠不提速");
		passed++;
	}

	private static void testRateIsMinBattery() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.SODIUM_BATTERY, BatteryCellSpec.LITHIUM_BATTERY });
		check(s.capacity == 550_000_000L, "混合容量");
		check(s.chargeSpeed == 125_000L, "充电取最低(锂)");
		check(s.dischargeSpeed == 41_666L, "放电取最低(锂)");
		passed++;
	}

	/** 金电容：充 1.1×、放 1.3×。锂 base 125000/41666。 */
	private static void testGoldTier() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.GOLD_CAPACITOR });
		check(s.chargeSpeed == 137_500L, "金电容充电 1.1x");
		check(s.dischargeSpeed == 54_165L, "金电容放电 1.3x");
		passed++;
	}

	/** 铌电容：充 1.4×、放 1.8×。 */
	private static void testNiobiumTier() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.NIOBIUM_CAPACITOR });
		check(s.chargeSpeed == 175_000L, "铌电容充电 1.4x");
		check(s.dischargeSpeed == 74_998L, "铌电容放电 1.8x");
		passed++;
	}

	/** 钽电容：充 1.8×、放 2.5×。 */
	private static void testTantaliumTier() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.TANTALIUM_CAPACITOR });
		check(s.chargeSpeed == 225_000L, "钽电容充电 1.8x");
		check(s.dischargeSpeed == 104_165L, "钽电容放电 2.5x");
		passed++;
	}

	/** Sa酸铁电容：充 2.5×、放 5×。 */
	private static void testSchrabidateTier() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.SCHRABIDATE_CAPACITOR });
		check(s.chargeSpeed == 312_500L, "Sa酸铁充电 2.5x");
		check(s.dischargeSpeed == 208_330L, "Sa酸铁放电 5x");
		passed++;
	}

	/** 同级可堆叠：两块金 → 充 1+0.1*2=1.2×、放 1+0.3*2=1.6×。 */
	private static void testStackSameTier() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.GOLD_CAPACITOR, BatteryCellSpec.GOLD_CAPACITOR });
		check(s.chargeSpeed == 150_000L, "两块金充电 1.2x");
		check(s.dischargeSpeed == 66_665L, "两块金放电 1.6x");
		passed++;
	}

	/** 不同级相加：金+铌 → 充 1+0.1+0.4=1.5×、放 1+0.3+0.8=2.1×。 */
	private static void testMixedTiers() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.GOLD_CAPACITOR, BatteryCellSpec.NIOBIUM_CAPACITOR });
		check(s.chargeSpeed == 187_500L, "金+铌充电 1.5x");
		check(s.dischargeSpeed == 87_498L, "金+铌放电 2.1x");
		passed++;
	}

	/** 每一档都必须「充电倍率 < 放电倍率」。 */
	private static void testChargeSlowerThanDischarge() {
		BatteryCellSpec[] caps = {
				BatteryCellSpec.GOLD_CAPACITOR, BatteryCellSpec.NIOBIUM_CAPACITOR,
				BatteryCellSpec.TANTALIUM_CAPACITOR, BatteryCellSpec.SCHRABIDATE_CAPACITOR };
		for(BatteryCellSpec c : caps) {
			check(c.chargeMult > 1.0D && c.dischargeMult > c.chargeMult,
					"电容必须充电倍率<放电倍率: " + c.name);
		}
		passed++;
	}

	private static void testNoBattery() {
		// 新规则：电容也储能，所以全电容结构有容量也有速率（不再全 0）
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{ BatteryCellSpec.GOLD_CAPACITOR });
		check(s.capacity == 5_000_000L, "全电容应有容量");
		check(s.chargeSpeed > 0 && s.dischargeSpeed > 0, "全电容应有速率");
		passed++;
	}

	private static void testEmpty() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{});
		check(s.capacity == 0L && s.chargeSpeed == 0L && s.dischargeSpeed == 0L, "空组全 0");
		passed++;
	}

	private static void check(boolean cond, String msg) {
		if(!cond) throw new AssertionError(msg);
	}

	private BatteryPackStatsTest() {
	}
}