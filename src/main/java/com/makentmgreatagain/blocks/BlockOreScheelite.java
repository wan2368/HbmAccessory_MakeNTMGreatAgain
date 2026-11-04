package com.makentmgreatagain.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;

/**
 * 白钨矿（CaWO₄）。
 *
 * <p>矿辞<b>只注册 oreScheelite</b>，绝不注册 oreTungsten。这条比铅锌矿那条还硬：
 * {@code CrystallizerRecipes:74} 里有一句 {@code new OreDictStack(W.ore())} 直接结晶成
 * {@code crystal_tungsten}，离心机再吐 4 份钨粉。一旦注册进 oreTungsten，
 * 整条碱浸—酸化—焙烧—还原的链当场被跳过，白钨矿就退化成"长得不一样的普通钨矿"。</p>
 *
 * <p>也不给熔炼配方：熔炉里烧白钨矿什么都不会发生。这是它和 HBM 其他所有矿最大的区别，
 * 也是"难处理钨矿"这四个字的落点。矿要变成钨，必须先粉碎成粉再进湿法流程。</p>
 *
 * <h3>掉落</h3>
 * <p>普通挖掘掉方块自身，一个，不受时运影响；精准采集也掉自身（默认行为，不需要额外重写）。
 * 之所以不直接掉粉：粉是粉碎机的产物，镐子砸开只是把矿整块撬下来，
 * 能不能解离成粉是机器的活。这样"粉碎机 1 矿 → 2 粉"这一步才有存在感。</p>
 *
 * <p>硬度和抗性对齐铅锌矿（5.0 / 15.0），需要铁镐（harvestLevel 2）。
 * 白钨矿本身不算特别硬，但它常跟矽卡岩长在一起，挖起来比煤铁费劲一档是合理的。</p>
 */
public class BlockOreScheelite extends Block {

    public BlockOreScheelite() {
        super(Material.rock);
        this.setStepSound(soundTypeStone);
        this.setHardness(5.0F);
        this.setResistance(15.0F);
    }

    /** 精准采集那一支要用：保证掉的是带 meta 的自身。 */
    @Override
    protected ItemStack createStackedBlock(int meta) {
        return new ItemStack(this, 1, meta);
    }

    @Override
    public boolean canSilkHarvest() {
        return true;
    }
}
