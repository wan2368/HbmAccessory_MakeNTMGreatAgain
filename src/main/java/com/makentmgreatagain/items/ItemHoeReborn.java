package com.makentmgreatagain.items;

import net.minecraft.item.ItemHoe;
import net.minecraft.item.Item.ToolMaterial;

/**
 * 锄头物品类。
 *
 * 原版 ItemHoe 的构造函数是 public 的，所以其实也可以直接 new ItemHoe(材质)。
 * 这里仍写一个子类，目的是给将来留扩展点——比如想给锄头加说明文字、
 * 改耐久显示、加特殊行为时，直接在这个类里覆盖对应方法即可，
 * 不必回头改注册代码。HBM 自己的 ModHoe 也是同样的思路（一个只有构造函数的空壳子类）。
 */
public class ItemHoeReborn extends ItemHoe {

    public ItemHoeReborn(ToolMaterial material) {
        super(material);
    }
}
