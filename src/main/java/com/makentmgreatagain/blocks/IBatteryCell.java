package com.makentmgreatagain.blocks;

/**
 * 「大型电池组元件」标记接口 —— 电池、电容、电容总线三种方块都实现它。
 *
 * <h2>为什么需要它</h2>
 * <p>后续大型多方块电池组成型时，要遍历结构里的每个方块，判断「这是不是一件储能元件、
 * 是哪种、数值多少」，据此累加出整组的容量与放电速率。三种元件是三个不同的类
 * （{@link BlockAddonBattery} / {@link BlockAddonCapacitor}），
 * 没有共同父类（电容还得继承原版 {@code MachineCapacitor} 才能复用 OBJ 渲染）。</p>
 *
 * <p>用这个接口，多方块那边只需一次判定：</p>
 * <pre>
 * Block b = world.getBlock(x, y, z);
 * if (b instanceof IBatteryCell) {
 *     BatteryCellSpec spec = ((IBatteryCell) b).getSpec();
 *     // spec.capacity / spec.chargeSpeed / spec.dischargeSpeed ...
 * }
 * </pre>
 * <p>不必 instanceof 三连，也不必维护 Block→spec 的反查表。</p>
 *
 * <h2>容量公式不在这里</h2>
 * <p>「几块电池 = 多大容量、电容怎么影响速率」是多方块成型逻辑的事，
 * 且具体公式待定。本接口只负责「把元件的原始数值暴露出去」，不做任何累加。</p>
 */
public interface IBatteryCell {

    /** 这件元件的规格（容量与充放速率的唯一来源），永不返回 null。 */
    BatteryCellSpec getSpec();
}