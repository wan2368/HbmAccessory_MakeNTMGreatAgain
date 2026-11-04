package com.makentmgreatagain;

import com.makentmgreatagain.init.ModBlocks;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import com.makentmgreatagain.init.ModItems;

import static com.makentmgreatagain.init.ModBlocks.block_advanced_alloy;

public class ModCreativeTabs {

    // 1. 资源栏（放锭、线等）
    public static final CreativeTabs TAB_RESOURCES = new CreativeTabs("makentmgreatagain_resources") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return ModItems.ingot_advanced_alloy;
        }
    };

    // 2. 方块栏
    public static final CreativeTabs TAB_BLOCKS = new CreativeTabs("makentmgreatagain_blocks") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return Item.getItemFromBlock(ModBlocks.block_advanced_alloy);
        }
    };

    // 3. 机器栏
    public static final CreativeTabs TAB_MACHINES = new CreativeTabs("makentmgreatagain_machines") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return Item.getItemFromBlock(com.makentmgreatagain.init.ModBlocks.block_alloy_shredder);
        }
    };

    public static final CreativeTabs TAB_GEAR = new CreativeTabs("makentmgreatagain_gear") {
        @Override
        @SideOnly(Side.CLIENT)
        public Item getTabIconItem() {
            return ModItems.alloy_plate_reborn;
        }
    };
};
