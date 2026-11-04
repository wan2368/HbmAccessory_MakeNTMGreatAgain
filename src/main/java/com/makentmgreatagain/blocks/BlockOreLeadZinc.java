package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 铅锌矿。
 *
 * <p>不继承 {@link com.hbm.blocks.generic.BlockOre}：那个类的 getItemDropped 是一条列全了
 * HBM 自己所有矿石的硬编码 if 链，新方块落在末尾会走 super 分支，反而要多判一次空。</p>
 *
 * <p>矿辞<b>只注册 oreLeadzinc</b>，绝不注册 oreLead。一旦注册成铅矿，
 * 别的模组的铅配方会把这方块当普通铅矿吃掉，镉就永远留在矿石里出不来了。</p>
 *
 * <h3>两种挖法，两种结局</h3>
 * <p>普通镐挖下来只出<b>粉末</b>（铅粉 1~3 + 锌粉 1~2），拿不到矿方块，也拿不到锭。
 * 想要能进浮选—烧结—鼓风炉这条流程的矿方块，镐上<b>必须带精准采集</b>。</p>
 *
 * <p>为什么给粉而不是给锭：镐子砸开矿石顶多是把矿物解离成颗粒，还原成金属还得进炉子。
 * 铅粉在 {@code SmeltingRecipes} 里是 1:1 熔成铅锭，所以这不算削收益，
 * 只是把"必须过一次火"这件事摆回台面上 —— 掉锭等于跳过冶炼，
 * 那 ISP 前面那套烧结、鼓风炉就都没有存在的意义了。</p>
 *
 * <p>这不是为了卡人，而是让"矿"和"金属"分开：挖下来随手熔掉的都是贱金属，
 * 而镉、以及其他伴生的稀有金属全都锁在矿方块里 —— 只有把整块矿送去选矿，
 * 那些东西才出得来。现实里 ISP 厂也确实是"整块矿石进流程"，不是"挖出来就拆成金属"。</p>
 *
 * <p>实现上只重写 {@link #getDrops}：精准采集那一支走的是
 * {@code Block.harvestBlock} 里的 silk 分支（{@code canSilkHarvest()} 默认 true、
 * {@code createStackedBlock} 默认返回自身），根本不会调到 getDrops。
 * 所以不需要自己判附魔，也不会有两条路径同时掉东西的风险。</p>
 */
public class BlockOreLeadZinc extends Block {

    public BlockOreLeadZinc() {
        super(Material.rock);
        this.setStepSound(soundTypeStone);
        this.setHardness(5.0F);
        this.setResistance(15.0F);
    }

    /**
     * 普通挖掘的掉落：铅粉 1~3 + 锌粉 1~2。
     *
     * <p>用 {@code world.rand} 而不是自建 Random，这样掉落序列跟世界种子走，
     * 同一个种子反复测试结果可复现。</p>
     *
     * <p>时运每级给两种产物各加 0~fortune 个，比原版"最多 +1"宽松一点，
     * 因为这里本来就是粉末，本来就不值钱。</p>
     */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        Random rand = world.rand;

        int lead = 1 + rand.nextInt(3);   // 1~3
        int zinc = 1 + rand.nextInt(2);   // 1~2

        if(fortune > 0) {
            lead += rand.nextInt(fortune + 1);
            zinc += rand.nextInt(fortune + 1);
        }

        // 铅粉而不是铅锭：镐子只能解离矿物，还原得进炉子（1 粉 = 1 锭）。
        drops.add(new ItemStack(com.hbm.items.ModItems.powder_lead, lead));

        // 锌粉是本模组物品，理论上 init 顺序保证它已存在；保险起见还是判一下空，
        // 万一哪天初始化顺序变了，宁可少掉一样东西也不要整片矿塌成空指针。
        if(com.makentmgreatagain.init.ModItems.powder_zinc_isp != null) {
            drops.add(new ItemStack(com.makentmgreatagain.init.ModItems.powder_zinc_isp, zinc));
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
