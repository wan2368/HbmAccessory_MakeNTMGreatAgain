package com.makentmgreatagain.items;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/**
 * 高级合金护甲（重生版）的物品类。
 */
public class ItemArmorAlloyReborn extends ItemArmor {

    /** 护甲模型贴图路径：穿在身上时渲染的那层。 */
    private final String armorModelTexture;

    /**
     * @param material 护甲材质，决定耐久与各部位护甲点
     * @param slot     部位编号：0 头盔、1 胸甲、2 护腿、3 靴子
     * @param armorTexture 护甲模型贴图路径（形如 "modid:textures/armor/xxx.png"）
     */
    public ItemArmorAlloyReborn(ArmorMaterial material, int slot, String armorTexture) {
        // 第二个参数 renderIndex 在 1.7.10 里已无实际作用，沿用 HBM 的写法传 0
        super(material, 0, slot);
        this.armorModelTexture = armorTexture;
    }

    /**
     */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, int slot, String layer) {
        return armorModelTexture;
    }
}
