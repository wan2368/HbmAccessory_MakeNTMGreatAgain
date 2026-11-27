package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.tileentity.TileEntitySupercriticalMHDGenerator;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 超临界磁流体膨胀发电机（17×7×7 无 GUI 多方块发电装置，参考利维坦纯直发模式）。
 *
 * <ul>
 *   <li>前端两侧（x=+4, y=2, z=±3）：2 个超临界蒸汽输入端口；</li>
 *   <li>后端两侧（x=-7/-5, y=2, z=±3）：4 个冷凝回水输出端口；</li>
 *   <li>尾部中心（x=-8, y=3, z=0）：唯一 1 个高压电力输出端口。</li>
 * </ul>
 */
public class BlockSupercriticalMHDGenerator extends BlockDummyable implements ILookOverlay {

	public BlockSupercriticalMHDGenerator() {
		super(Material.iron);
		this.bounding.add(AxisAlignedBB.getBoundingBox(-8.5D, 0D, -3.45D, 8.45D, 7.0D, 3.45D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 北, 南, 西, 东} -> 17×7×7
		return new int[] {6, 0, 8, 8, 3, 3};
	}

	@Override
	public int getOffset() {
		return 8;
	}

	/** 6 个流体接口（前端两侧 2 个超临界蒸汽输入 + 后端两侧 4 个回水输出） */
	private static final int[][] FLUID_PORTS_MODEL = {
		{  4, 2,  3, ForgeDirection.SOUTH.ordinal() },
		{  4, 2, -3, ForgeDirection.NORTH.ordinal() },
		{ -7, 2,  3, ForgeDirection.SOUTH.ordinal() },
		{ -5, 2,  3, ForgeDirection.SOUTH.ordinal() },
		{ -7, 2, -3, ForgeDirection.NORTH.ordinal() },
		{ -5, 2, -3, ForgeDirection.NORTH.ordinal() }
	};

	/** 尾部中心唯一电力输出接口 */
	private static final int[][] POWER_PORTS_MODEL = {
		{ -8, 3, 0, ForgeDirection.WEST.ordinal() }
	};

	private static int[] rotateXZ(int x, int z, ForgeDirection dir) {
		switch(dir) {
		case SOUTH: return new int[] {  z, -x };
		case EAST:  return new int[] { -x, -z };
		case NORTH: return new int[] { -z,  x };
		case WEST:
		default:    return new int[] {  x,  z };
		}
	}

	private static ForgeDirection rotateFace(ForgeDirection face, ForgeDirection dir) {
		int[] r = rotateXZ(face.offsetX, face.offsetZ, dir);
		for(ForgeDirection d : new ForgeDirection[] {
				ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST }) {
			if(d.offsetX == r[0] && d.offsetZ == r[1]) return d;
		}
		return face;
	}

	private static int[][] rotateModelPorts(int[][] model, ForgeDirection dir) {
		int[][] out = new int[model.length][4];
		for(int i = 0; i < model.length; i++) {
			int[] p = rotateXZ(model[i][0], model[i][2], dir);
			ForgeDirection face = rotateFace(ForgeDirection.getOrientation(model[i][3]), dir);
			out[i] = new int[] { p[0], model[i][1], p[1], face.ordinal() };
		}
		return out;
	}

	public int[][] getFluidPortsAtCore(ForgeDirection dir) {
		return rotateModelPorts(FLUID_PORTS_MODEL, dir);
	}

	public int[][] getPowerPortsAtCore(ForgeDirection dir) {
		return rotateModelPorts(POWER_PORTS_MODEL, dir);
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		int[][] f = getFluidPortsAtCore(dir);
		int[][] p = getPowerPortsAtCore(dir);
		int[][] all = new int[f.length + p.length][4];
		int idx = 0;
		int o = getOffset();
		for(int[] port : f) {
			all[idx++] = new int[] { port[0] - dir.offsetX * o, port[1], port[2] - dir.offsetZ * o, port[3] };
		}
		for(int[] port : p) {
			all[idx++] = new int[] { port[0] - dir.offsetX * o, port[1], port[2] - dir.offsetZ * o, port[3] };
		}
		return all;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}

		int cx = x + dir.offsetX * o;
		int cy = y + dir.offsetY * o;
		int cz = z + dir.offsetZ * o;
		world.markBlockForUpdate(cx, cy, cz);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntitySupercriticalMHDGenerator();
		if(hasExtra(meta)) return new TileEntityProxyCombo().power().fluid();
		return null;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
			int side, float hitX, float hitY, float hitZ) {
		return false;
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {
		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntitySupercriticalMHDGenerator)) return;

		TileEntitySupercriticalMHDGenerator gen = (TileEntitySupercriticalMHDGenerator) te;
		List<String> text = new ArrayList<String>();

		FluidTank tankInput = gen.tanks[0];
		FluidTank tankOutput = gen.tanks[1];
		FluidType inputType = tankInput.getTankType();
		FluidType outputType = tankOutput.getTankType();

		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + inputType.getLocalizedName() + ": "
				+ String.format(Locale.US, "%,d", tankInput.getFill()) + "/" + String.format(Locale.US, "%,d", tankInput.getMaxFill()) + "mB");
		text.add(EnumChatFormatting.RED + "<- " + EnumChatFormatting.RESET + outputType.getLocalizedName() + ": "
				+ String.format(Locale.US, "%,d", tankOutput.getFill()) + "/" + String.format(Locale.US, "%,d", tankOutput.getMaxFill()) + "mB");
		text.add(EnumChatFormatting.RED + "<- " + EnumChatFormatting.RESET + BobMathUtil.getShortNumber(gen.powerBuffer) + "HE/t");

		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}
}
