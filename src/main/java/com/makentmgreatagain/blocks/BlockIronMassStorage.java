package com.makentmgreatagain.blocks;

import java.util.List;

import com.hbm.blocks.machine.BlockMassStorage;
import com.makentmgreatagain.tileentity.TileEntityIronMassStorage;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * 铁质大规模存储单元（1000 存量）。
 *
 * <p>直接继承本体的 {@link BlockMassStorage}，所以开界面、锁定、掉落时保留内容、
 * 比较器输出、扳手/管道交互、TESR 物品悬浮显示等行为全部沿用。</p>
 *
 * <p>与本体不同的地方只有三处：</p>
 * <ul>
 *   <li>只注册一个铁质形态（物品 damage 恒为 0）；</li>
 *   <li>容量固定为 1000（{@link #getCapacity(int)} 与方块实体构造都按 1000）；</li>
 *   <li>直接使用本体铁质大规模存储单元的贴图，外观和原版一致，只有材质/配方不同。</li>
 * </ul>
 *
 * <p>metadata 继续沿用本体布局：{@code damage(0) + 朝向 * getSubCount()}，
 * 即 0/4/8/12，这样本体 {@code RenderMassStorage} 里的
 * {@code getBlockMetadata() / 4} 朝向计算可以直接复用。</p>
 */
public class BlockIronMassStorage extends BlockMassStorage {

    @SideOnly(Side.CLIENT)
    private IIcon iconTop;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;
    @SideOnly(Side.CLIENT)
    private IIcon iconFront;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconRegister) {
        // 本地复制了本体铁质大规模存储单元的材质，外观和原版一致，
        // 但放在附属自己的 textures/blocks/ 下，方便单独修改而不影响 HBM 本体
        this.iconTop = iconRegister.registerIcon("makentmgreatagain:iron_mass_storage_top");
        this.iconSide = iconRegister.registerIcon("makentmgreatagain:iron_mass_storage_side");
        this.iconFront = iconRegister.registerIcon("makentmgreatagain:iron_mass_storage_front");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        // 本体的朝向布局：0/4/8/12 分别对应四个朝向，正面始终是 dir = 朝向 + 2
        int dir = (metadata / getSubCount()) + 2;
        if(side == 0 || side == 1) return iconTop;
        if(side == dir) return iconFront;
        return iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        // 只有铁质一种，damage 0
        list.add(new ItemStack(item, 1, 0));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityIronMassStorage();
    }

    @Override
    public int getCapacity(int meta) {
        return 1000;
    }
}
