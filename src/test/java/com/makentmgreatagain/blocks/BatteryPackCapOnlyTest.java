package com.makentmgreatagain.blocks;

/**
 * 「电容也储能」与「端口连接方向」的独立单元测试（不依赖 Minecraft）。
 *
 * <p>用户反馈：全电容结构容量显示为 0、且无法充放电。根因是聚合时电容只算提速、
 * 完全不计容量，导致 maxPower=0 → 电力网算出可接收量 0 → 永远充不进。</p>
 *
 * <p>新规则：<b>电容的电容值同时计入总容量</b>；速率仍取「最低电池」，
 * 若没有任何电池则以电容自身容量按电池口径折算一个基础速率，保证全电容也能充放。</p>
 */
public final class BatteryPackCapOnlyTest {

	private static int passed = 0;

	public static void main(String[] args) {
		testCapOnlyHasCapacity();
		testCapOnlyHasSpeed();
		testCapAddsToBatteryCapacity();
		testCapOnlyMultiple();
		testMixedStillUsesMinBattery();
		System.out.println("ALL " + passed + " TESTS PASSED");
	}

	/** 单块金电容：容量必须等于其电容值，不能为 0。 */
	private static void testCapOnlyHasCapacity() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{ BatteryCellSpec.GOLD_CAPACITOR });
		check(s.capacity == 5_000_000L, "全电容容量应为电容值，实际 " + s.capacity);
		passed++;
	}

	/** 全电容必须有非零充放速率，否则电力网收不了电。 */
	private static void testCapOnlyHasSpeed() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{ BatteryCellSpec.GOLD_CAPACITOR });
		check(s.chargeSpeed > 0, "全电容充电速率必须 > 0");
		check(s.dischargeSpeed > 0, "全电容放电速率必须 > 0");
		check(s.chargeSpeed > s.dischargeSpeed, "全电容仍应充电快于放电");
		passed++;
	}

	/** 电池 + 电容：容量 = 电池容量 + 电容值。 */
	private static void testCapAddsToBatteryCapacity() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.GOLD_CAPACITOR });
		check(s.capacity == 55_000_000L, "容量应为电池+电容，实际 " + s.capacity);
		passed++;
	}

	/** 多块电容：容量相加，倍率仍叠加。 */
	private static void testCapOnlyMultiple() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.GOLD_CAPACITOR, BatteryCellSpec.GOLD_CAPACITOR });
		check(s.capacity == 10_000_000L, "两块金容量应相加");
		check(s.chargeSpeed > 0 && s.dischargeSpeed > 0, "两块金仍应有速率");
		passed++;
	}

	/** 有电池时，基础速率仍取最低电池（不被电容折算值干扰）。 */
	private static void testMixedStillUsesMinBattery() {
		BatteryPackStats s = BatteryPackStats.compute(new BatteryCellSpec[]{
				BatteryCellSpec.SODIUM_BATTERY, BatteryCellSpec.LITHIUM_BATTERY, BatteryCellSpec.GOLD_CAPACITOR });
		// 锂 base 125000 × 金 1.1 = 137500
		check(s.chargeSpeed == 137_500L, "混合仍取最低电池，实际 " + s.chargeSpeed);
		passed++;
	}

	private static void check(boolean cond, String msg) {
		if(!cond) throw new AssertionError(msg);
	}

	private BatteryPackCapOnlyTest() {
	}
}