package com.makentmgreatagain.items;

/**
 * 早期通用物品基类。
 *
 * <p>它的全部功能（语言文件驱动的说明文字 + 稀有度）已经合并进 {@link ItemWithDesc}，
 * 本类现在只是个空壳，仅用于让 {@code ModItems} 里已有的两处
 * {@code new ItemAdvancedAlloy()} 不改也能编译。</p>
 *
 * <p>新代码请直接用 {@code new ItemWithDesc()}。等你确认这两处也不介意改，
 * 把 ModItems 里 {@code ingot_advanced_alloy}、{@code cmb_electrode} 的类名换掉，
 * 本文件就可以整体删除。</p>
 *
 * <p>历史备注：更早的版本把 {@code EnumRarity.epic} 硬编码在构造函数里，
 * 导致所有用这个类创建的物品都变成紫名，后来改成默认普通、需要时单独设置。
 * 这条语义在 ItemWithDesc 里保持原样（{@code rarity} 默认 null，走父类的普通白名）。</p>
 */
public class ItemAdvancedAlloy extends ItemWithDesc {
}
