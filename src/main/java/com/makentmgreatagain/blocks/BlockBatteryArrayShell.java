package com.makentmgreatagain.blocks;

import com.hbm.render.block.ct.CT;
import com.hbm.render.block.ct.CTStitchReceiver;
import com.hbm.render.block.ct.IBlockCT;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityBatteryArray;
import com.makentmgreatagain.tileentity.TileEntityBatteryArrayShell;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * 电池组成型后的统一外壳（连接纹理）。meta 0=外壳、1=端口。
 *
 * <p>CT 贴图：{@code battery_array_shell} / {@code battery_array_shell_port}，
 * 各带 {@code _ct} 大图。canConnect 只认同壳与控制器，保证整片无缝。</p>
 *
 * <p>拆解：打掉任意壳→还原原零件并把控制器标未成型；其余壳每 20 tick 自检，
 * 控制器没了就自动还原（照 PWR）。</p>
 */
public class BlockBatteryArrayShell extends BlockContainer implements IBlockCT, api.hbm.energymk2.IEnergyConnectorBlock {

	@SideOnly(Side.CLIENT) private IIcon iconPort;
	@SideOnly(Side.CLIENT) public CTStitchReceiver rec;
	@SideOnly(Side.CLIENT) public CTStitchReceiver recPort;

	public BlockBatteryArrayShell() {
		super(Material.iron);
		this.setHardness(15.0F);
		this.setResistance(10.0F);
		this.setBlockTextureName("makentmgreatagain:battery_array_shell");
	}

	@Override
	public int getRenderType() {
		return CT.renderID;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityBatteryArrayShell();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		super.registerBlockIcons(reg);
		this.iconPort = reg.registerIcon("makentmgreatagain:battery_array_shell_port");
		this.rec = IBlockCT.primeReceiver(reg, this.blockIcon.getIconName(), this.blockIcon);
		this.recPort = IBlockCT.primeReceiver(reg, this.iconPort.getIconName(), this.iconPort);
	}

	@Override
	public IIcon[] getFragments(IBlockAccess world, int x, int y, int z, int side) {
		return world.getBlockMetadata(x, y, z) == 1 ? recPort.fragCache : rec.fragCache;
	}

	@Override
	public boolean canConnect(IBlockAccess world, int x, int y, int z, Block block) {
		return block == ModBlocks.battery_array_shell || block == ModBlocks.battery_array_controller;
	}

	/**
	 * 线缆连接判定（{@code Library.canConnect} 先看 Block 这个接口）。
	 *
	 * <p>只有<b>端口</b>（meta=1）允许连电缆；普通外壳不连，避免整面外壳都被线缆吸住。
	 * 不实现这个接口时线缆完全认不出成型壳，这就是「端口接不上 HBM 线缆」的原因。</p>
	 */
	@Override
	public boolean canConnect(IBlockAccess world, int x, int y, int z, net.minecraftforge.common.util.ForgeDirection dir) {
		return world.getBlockMetadata(x, y, z) == 1;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		TileEntity t = world.getTileEntity(x, y, z);
		if(t instanceof TileEntityBatteryArrayShell) {
			TileEntityBatteryArrayShell shell = (TileEntityBatteryArrayShell) t;
			world.removeTileEntity(x, y, z);
			if(shell.orig != null) {
				world.setBlock(x, y, z, shell.orig);
				TileEntity core = world.getTileEntity(shell.coreX, shell.coreY, shell.coreZ);
				if(core instanceof TileEntityBatteryArray) ((TileEntityBatteryArray) core).disassemble();
			}
		} else {
			world.removeTileEntity(x, y, z);
		}
		super.breakBlock(world, x, y, z, block, meta);
	}
}