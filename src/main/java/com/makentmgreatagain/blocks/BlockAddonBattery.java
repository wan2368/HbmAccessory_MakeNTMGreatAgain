package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.BobMathUtil;
import com.makentmgreatagain.blocks.BatteryCellSpec.Kind;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * 附属版「弃用单方块电池」—— 从 HBM 的 {@code MachineBattery} 复制过来的<b>纯元件</b>。
 *
 * <h2>和原版的区别（这是重点）</h2>
 * <p>原版 {@code MachineBattery} 是 {@code BlockContainer}，带
 * {@code TileEntityMachineBattery}：会存电、接电网、能右键开 GUI、还能塞电池物品充放电。
 * 本类<b>这些全都没有</b>：</p>
 * <ul>
 *   <li>不是 {@code BlockContainer}，<b>没有 TE</b> —— 所以不存电、不接电网、不参与任何能量结算；</li>
 *   <li>不覆写 {@code onBlockActivated} —— 右键没有任何反应，<b>没有 UI 互动</b>；</li>
 *   <li>没有物品槽、没有红石模式、没有优先级。</li>
 * </ul>
 *
 * <p>它存在的唯一意义是<b>携带数值</b>：放进大型多方块电池组里，成型时数一数
 * 有几块、分别是什么，由 {@link BatteryCellSpec} 反查出容量与充放速率，
 * 算出整组的总容量与放电速率。所以它是个「铭牌」，不是「机器」。</p>
 *
 * <h2>为什么不带 TE 反而是对的</h2>
 * <ol>
 *   <li><b>避开 reobf 陷阱</b>：附属包重混淆时，{@code BlockContainer} 子类重写
 *       {@code createNewTileEntity} 若漏转会在整合包里 {@code AbstractMethodError}
 *       （放下方块瞬间崩）。没有 TE 就没有这个方法，整类风险消失。</li>
 *   <li><b>成型后本来就要被替换掉</b>：PWR 式多方块在成型时把零件全部换成
 *       统一的成型方块，原方块只记在 TE 里当「还原凭据」。零件自己存不存电毫无意义。</li>
 *   <li><b>不会双重供电</b>：要是零件自己也能存电接电网，装进多方块后会和整组
 *       抢电、各算各的账，容量根本对不上。</li>
 * </ol>
 *
 * <h2>贴图直接复用 HBM 原版</h2>
 * <p>用 {@code hbm:} 前缀注册图标，不复制贴图文件。注意原版 {@code machine_battery}
 * 用的是 {@code battery_*_alt} 三张（不遵循材质命名规律），所以贴图名存在
 * {@link BatteryCellSpec} 里显式给出，不能从名字推导 —— 那条特例已有单测锁住。</p>
 *
 * <h2>朝向</h2>
 * <p>沿用原版的四向 meta（2/3/4/5 = 北/南/西/东），正面贴图跟着朝向转，
 * 摆放时按玩家视线定向。原版还有一个 {@code onBlockAdded} 里的
 * {@code setDefaultDirection}（按邻接实心面自动定向），那是给「方块被活塞推动 /
 * 世界生成放置」兜底的，多方块场景用不上，这里不抄。</p>
 */
public class BlockAddonBattery extends Block implements ITooltipProvider, IBatteryCell {

    /** 本方块代表的电池规格（容量与速率的唯一来源）。 */
    public final BatteryCellSpec spec;

    @SideOnly(Side.CLIENT) private IIcon iconTop;
    @SideOnly(Side.CLIENT) private IIcon iconFront;

    public BlockAddonBattery(BatteryCellSpec spec) {
        super(Material.iron);
        this.spec = spec;

        // 与原版同硬度 / 抗爆（5.0 / 10.0），手感一致
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setCreativeTab(com.makentmgreatagain.ModCreativeTabs.TAB_MACHINES);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconRegister) {
        // 贴图已复制进附属自己的资源目录，不依赖 HBM 原版资产
        this.iconFront = iconRegister.registerIcon("makentmgreatagain:" + spec.texFront);
        this.iconTop = iconRegister.registerIcon("makentmgreatagain:" + spec.texTop);
        this.blockIcon = iconRegister.registerIcon("makentmgreatagain:" + spec.texSide);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        // 上下盖用顶面图；四个侧面里朝向那一面用正面图，其余用侧面图（与原版同口径）
        if (side == 0 || side == 1) return iconTop;
        return metadata == 0 && side == 3 ? iconFront : (side == metadata ? iconFront : this.blockIcon);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
        int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

        if (i == 0) world.setBlockMetadataWithNotify(x, y, z, 2, 2);
        if (i == 1) world.setBlockMetadataWithNotify(x, y, z, 5, 2);
        if (i == 2) world.setBlockMetadataWithNotify(x, y, z, 3, 2);
        if (i == 3) world.setBlockMetadataWithNotify(x, y, z, 4, 2);
    }

    /**
     * 提示里写清数值 —— 因为<b>没有 GUI</b>，这是玩家唯一能看到参数的地方。
     *
     * <p>三个数：容量、充电速率（容量/200，快档）、放电速率（容量/600，慢档），
     * 单位均为 HE/tick（电力网每 tick 结算一次）。</p>
     *
     * <p>同时明说「本身不存电」，免得玩家以为把它接上电缆就能当电池用。</p>
     *
     * <p>不加 {@code @SideOnly(CLIENT)}：接口方法本身没有该限定，加了会在专用服务端
     * 被剥离、破坏接口契约（HBM 的 {@code MachineCapacitorBus} 等实现也都不加）。
     * 方法体只用 EnumChatFormatting / BobMathUtil，两端都安全。</p>
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
        list.add(EnumChatFormatting.GOLD + "容量: " + BobMathUtil.getShortNumber(spec.capacity) + " HE");
        list.add(EnumChatFormatting.GOLD + "充电速率: " + BobMathUtil.getShortNumber(spec.chargeSpeed) + " HE/t");
        list.add(EnumChatFormatting.GOLD + "放电速率: " + BobMathUtil.getShortNumber(spec.dischargeSpeed) + " HE/t");
        list.add(EnumChatFormatting.GRAY + "堆叠只增加整组容量，不增加充放电；");
        list.add(EnumChatFormatting.GRAY + "整组充放电按组内最低的那块电池计算。");
    }

    /** 便捷判定：是不是电池元件（多方块统计时用）。 */
    public boolean isBatteryCell() {
        return spec.kind == Kind.BATTERY;
    }

    @Override
    public BatteryCellSpec getSpec() {
        return spec;
    }
}
