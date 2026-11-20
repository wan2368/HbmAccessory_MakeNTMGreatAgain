package com.makentmgreatagain.items;

import net.minecraft.item.ItemHoe;
import net.minecraft.item.Item.ToolMaterial;

/**
 * 锄头。仅作为扩展点存在（加说明文字、改耐久显示等），行为与原版 {@code ItemHoe} 一致。
 */
public class ItemHoeReborn extends ItemHoe {

    public ItemHoeReborn(ToolMaterial material) {
        super(material);
    }
}
