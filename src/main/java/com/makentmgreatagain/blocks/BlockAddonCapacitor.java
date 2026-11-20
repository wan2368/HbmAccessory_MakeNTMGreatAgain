package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.hbm.util.BobMathUtil;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * 附属版「弃用单方块电容」—— <b>完全独立</b>的元件方块，资产与代码都不依赖 HBM 原版。
 *
 * <h2>为什么不继承原版 {@code MachineCapacitor}</h2>
 * <p>最初为了白拿 OBJ 模型渲染而继承了它，但那样有两个摆脱不掉的依赖：
 * 父类 {@code registerBlockIcons} 把贴图命名空间写死成 {@code hbm:}，
 * 且原版 {@code RenderCapacitor} 强转父类型、读的是 {@code ResourceManager.capacitor}
 * 这个原版模型。要「资产全搬进附属、和原版功能脱钩」，就必须自己来。</p>
 *
 * <p>所以本类改为 {@code extends Block}，自带五个图标字段，配一个自己的渲染器
 * {@code RenderAddonCapacitor} + 自己的渲染 ID。OBJ 模型已复制到
 * {@code assets/makentmgreatagain/models/blocks/capacitor.obj}，
 * 五面贴图也在附属的 {@code textures/blocks/} 下。</p>
 *
 * <h2>功能定位</h2>
 * <p>纯元件：<b>无 TE、不存电、无 GUI、不接电网</b>。唯一作用是携带数值
 * （{@link #spec}），供大型多方块电池组成型时反查、参与放电速率计算。
 * 原版构造函数把 power 参数丢弃了，这里通过 spec 真正保留下来。</p>
 *
 * <h2>朝向</h2>
 * <p>{@code onBlockPlaced} 返回点击面作为 meta（与原版一致），渲染器按 meta 决定翻转/旋转。</p>
 */
public class BlockAddonCapacitor extends Block implements ITooltipProvider, IBatteryCell {

    /** 本方块代表的电容规格（数值唯一来源）。 */
    public final BatteryCellSpec spec;

    /** 附属自己的渲染 ID，与原版 {@code MachineCapacitor.renderID} 完全分开。 */
    public static final int renderID = RenderingRegistry.getNextAvailableRenderId();

    @SideOnly(Side.CLIENT) public IIcon iconTop;
    @SideOnly(Side.CLIENT) public IIcon iconSide;
    @SideOnly(Side.CLIENT) public IIcon iconBottom;
    @SideOnly(Side.CLIENT) public IIcon iconInnerTop;
    @SideOnly(Side.CLIENT) public IIcon iconInnerSide;

    public BlockAddonCapacitor(BatteryCellSpec spec) {
        super(Material.iron);
        this.spec = spec;

        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setCreativeTab(com.makentmgreatagain.ModCreativeTabs.TAB_MACHINES);
        this.setBlockTextureName("makentmgreatagain:capacitor_" + spec.materialName + "_side");
    }

    @Override
    public int getRenderType() {
        return renderID;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /**
     * 无论从哪个面放置，都固定 meta=1 —— 渲染器对 meta=1 不做翻转/旋转，
     * 即<b>底座恒在底面</b>。原版返回点击面 side，会让电容侧躺或倒挂。
     */
    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float fX, float fY, float fZ, int meta) {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        super.registerBlockIcons(reg);
        String base = "makentmgreatagain:capacitor_" + spec.materialName + "_";
        this.iconTop = reg.registerIcon(base + "top");
        this.iconSide = reg.registerIcon(base + "side");
        this.iconBottom = reg.registerIcon(base + "bottom");
        this.iconInnerTop = reg.registerIcon(base + "inner_top");
        this.iconInnerSide = reg.registerIcon(base + "inner_side");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
        if (spec.capacity > 0L) {
            list.add(EnumChatFormatting.GOLD + "电容值: " + BobMathUtil.getShortNumber(spec.capacity) + " HE");
            list.add(EnumChatFormatting.AQUA + "单块提速：充电 ×" + spec.chargeMult + " / 放电 ×" + spec.dischargeMult);
            list.add(EnumChatFormatting.GRAY + "可堆叠，同级相加；放电提速高于充电。");
        } else {
            list.add(EnumChatFormatting.GOLD + "电容值: 无（方向连接件）");
            list.add(EnumChatFormatting.GRAY + "不提供提速，仅作连接/装饰件。");
        }
    }

    @Override
    public BatteryCellSpec getSpec() {
        return spec;
    }
}
