package com.makentmgreatagain.init;

import net.minecraft.item.Item.ToolMaterial;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraftforge.common.util.EnumHelper;

public class MNGAMaterials {

    /** 工具材质：参数与 HBM 的 tMatAlloy 完全一致 */
    public static final ToolMaterial TOOL_ALLOY =
            EnumHelper.addToolMaterial("MNGA_ALLOY", 3, 2000, 15.0F, 5.0F, 5);

    /** 护甲材质：参数与 HBM 的 aMatAlloy 完全一致 */
    public static final ArmorMaterial ARMOR_ALLOY =
            EnumHelper.addArmorMaterial("MNGA_ALLOY", 40, new int[] { 3, 8, 6, 3 }, 12);
}
