package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 含砷金矿（难处理金矿，refractory gold ore）。
 *
 * <p>金以微细粒包裹在毒砂（FeAsS）和黄铁矿（FeS2）里，肉眼看不见，机械分选也分不出来 ——
 * 这正是湿法冶金里说的"难处理矿"。现实里这类矿用常规氰化浸出率只有两三成，
 * 必须先焙烧、加压氧化或细菌氧化把包裹层打开，金才见得到。</p>
 *
 * <p><b>矿辞只注册 {@code oreRefractoryGold}，绝不注册 oreGold</b>。
 * 一旦带 gold 的通用矿辞，HBM 的离心机就会把它当普通金矿吃掉
 * （{@code CentrifugeRecipes} 里 {@code OreDictStack(GOLD.ore())} 那条会给 2~3 个金粉，
 * 折合 2~3 个金锭），这条线立刻失去意义。这和铅锌矿不注册 oreLead 是同一个理由，
 * 只不过那边防的是别的模组，这边防的是 HBM 自己。</p>
 *
 * <h3>两种挖法，两种结局</h3>
 * <p>普通镐砸开只拿到<b>脉石里的硫和铁</b>（硫磺 1~3 + 铁粉 0~2），金一粒也拿不到 ——
 * 金被包在硫化物晶格里，镐子砸碎的是整块岩石，分不出金。<b>必须带精准采集</b>
 * 才能把矿方块整块取下来，送进化工厂做硫代硫酸盐浸出。</p>
 *
 * <p>这个设计同时解决了一个平衡问题：{@code oreRefractoryGold} 不注册 oreGold 之后，
 * 离心机和熔炉对它都没反应，普通挖掘本来会掉方块、等于白送原料；
 * 给成硫铁粉之后，误挖只会得到一堆"炼铁炼硫的散料"，真正值钱的东西仍然锁在矿里。</p>
 *
 * <p>实现上只重写 {@link #getDrops}：精准采集那一支走的是 {@code Block.harvestBlock}
 * 里的 silk 分支（{@code canSilkHarvest()} 为 true、{@code createStackedBlock} 返回自身），
 * 根本不会调到 getDrops。两条路径天然互斥，不用自己判附魔。</p>
 */
public class BlockOreRefractoryGold extends Block {

    public BlockOreRefractoryGold() {
        super(Material.rock);
        this.setStepSound(soundTypeStone);
        this.setHardness(6.0F);
        this.setResistance(15.0F);
    }

    /**
     * 普通挖掘的掉落：硫磺 1~3 + 铁粉 0~2。
     *
     * <p>铁粉下限是 0：毒砂含铁 34%、黄铁矿 47%，但这两样在矿里分布很不均匀，
     * 砸开一块什么都没剩下的情况是正常的。硫则几乎每块都有（硫化物是主相）。</p>
     *
     * <p>时运每级给两种产物各加 0~fortune 个，和铅锌矿保持同一套手感。</p>
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        Random rand = world.rand;

        int sulfur = 1 + rand.nextInt(3);  // 1~3
        int iron   = rand.nextInt(3);      // 0~2

        if(fortune > 0) {
            sulfur += rand.nextInt(fortune + 1);
            iron   += rand.nextInt(fortune + 1);
        }

        drops.add(new ItemStack(com.hbm.items.ModItems.sulfur, sulfur));

        // iron 可能为 0，给出空栈会让漏斗/掉落物那套逻辑多一堆无谓判断
        if(iron > 0) {
            drops.add(new ItemStack(com.hbm.items.ModItems.powder_iron, iron));
        }

        return drops;
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
