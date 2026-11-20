package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 工业燃气轮机：4×13×4 的外观多方块。
 *
 * <p>模型由 Blender 里的 {@code industial_gas_turbine.blend} 整理而来：
 * 导出为 Y 轴向上、1 单位 = 1 格，底面在 y=0，长度沿 Z 轴居中（±6.5），
 * 宽度 4 格在 X 轴上是 [-1.5, 2.5]（偶数宽度的机器必然相对核心格偏半格，
 * 这样四个朝向旋转后足迹都能和模型严丝合缝）。</p>
 *
 * <p>{@link #getDimensions()} 的六个数字是 {上, 下, -Z, +Z, -X, +X}，
 * 即以「朝南」为基准；4 格宽对应 -X 1 格 / +X 2 格。</p>
 *
 * <p>左右各 4 个、共 8 个接口，全部使用 HBM 的 {@link TileEntityProxyCombo}
 * （电力 + 流体），与管道、电线连接方式与 HBM 本体机器一致；实际收发由核心格
 * {@link TileEntityIndustrialGasTurbine} 的 {@code TilePort} 负责。</p>
 *
 * <p>另外在长度方向的前端（进气侧）和后端（尾盖侧）底部各有两个并排的电力接口，
 * 外观直接套用 HBM 的 {@code pwr_port} 接口贴图，只挂电力代理（不带流体），只能输出电力。</p>
 *
 * <p>模型上全部 12 个接口的凸台中心都在 z=0.5，即最底下一格方块的竖向正中，
 * 这样接口才能和相邻方块上的管道 / 电线对准。</p>
 */
public class BlockIndustrialGasTurbine extends BlockDummyable implements com.hbm.blocks.ITooltipProvider {

	public BlockIndustrialGasTurbine() {
		super(Material.iron);
	}

	/** 物品提示：照 HBM MachineTurbofan 的写法逐行列出燃料等级效率（低55/中85/高100/航空90/气体85） */
	@Override
	@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
	public void addInformation(net.minecraft.item.ItemStack stack, EntityPlayer player, java.util.List list, boolean ext) {
		list.add(net.minecraft.util.EnumChatFormatting.YELLOW + "Fuel efficiency:");
		addGrade(list, com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.LOW);
		addGrade(list, com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.MEDIUM);
		addGrade(list, com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.HIGH);
		addGrade(list, com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.AERO);
		addGrade(list, com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.GAS);
	}

	private static void addGrade(java.util.List list, com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade grade) {
		int eff = (int) (TileEntityIndustrialGasTurbine.gradeEfficiency(grade) * 100);
		list.add(net.minecraft.util.EnumChatFormatting.YELLOW + "-" + grade.getLocalizedName() + ": "
				+ net.minecraft.util.EnumChatFormatting.RED + "" + eff + "%");
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityIndustrialGasTurbine();
		if(hasExtra(meta)) {
			// 全部接口都带电力+流体：正背面接口原先只带电力，导致流体无法导入
			return new TileEntityProxyCombo().power().fluid();
		}
		return null;
	}

	@Override
	public int[] getDimensions() {
		return new int[] {3, 0, 6, 6, 1, 2};
	}

	@Override
	public int getOffset() {
		// 面朝玩家时，核心格位于点击格的里侧 6 格处（结构前后各 6 格）
		return 6;
	}

	/**
	 * 8 个接口相对**核心格**的偏移，坐标取自模型未旋转（朝南）时的位置：
	 * 模型侧板在 X 的 ±1.5 / 2.5 面、长度方向落在核心向后的 4 个整格上，
	 * 高度与核心同层。
	 */
	/** 侧接口累计向机器前方(+Z)移动 3 格：z 由 0,-1,-2,-3 → 3,2,1,0 */
	private static final int[][] PORTS_MODEL = {
			{ 2, 0,  3, ForgeDirection.EAST.ordinal()},
			{ 2, 0,  2, ForgeDirection.EAST.ordinal()},
			{ 2, 0,  1, ForgeDirection.EAST.ordinal()},
			{ 2, 0,  0, ForgeDirection.EAST.ordinal()},
			{-1, 0,  3, ForgeDirection.WEST.ordinal()},
			{-1, 0,  2, ForgeDirection.WEST.ordinal()},
			{-1, 0,  1, ForgeDirection.WEST.ordinal()},
			{-1, 0,  0, ForgeDirection.WEST.ordinal()}
	};

	/**
	 * 前后端的 4 个电力接口相对核心格的偏移（朝南时）：
	 * 前端 z=+6、后端 z=-6，每端两个，x 落在核心格与 +X 相邻格上，
	 * 正好并排跨过机体中线；与侧接口一样对准最底下一格（y 偏移 0）的方块中心。
	 */
	private static final int[][] POWER_PORTS_MODEL = {
			{ 0, 0,  6, ForgeDirection.SOUTH.ordinal()},
			{ 1, 0,  6, ForgeDirection.SOUTH.ordinal()},
			{ 0, 0, -6, ForgeDirection.NORTH.ordinal()},
			{ 1, 0, -6, ForgeDirection.NORTH.ordinal()}
	};

	/** 与渲染器的旋转表一致：南 0°、东 90°、北 180°、西 270°。 */
	private static int[] rotateXZ(int x, int z, ForgeDirection dir) {
		switch(dir) {
		case EAST:	return new int[] { z, -x};
		case NORTH:	return new int[] {-x, -z};
		case WEST:	return new int[] {-z,  x};
		case SOUTH:
		default:	return new int[] { x,  z};
		}
	}

	private static ForgeDirection rotateFace(ForgeDirection face, ForgeDirection dir) {
		int[] r = rotateXZ(face.offsetX, face.offsetZ, dir);
		for(ForgeDirection d : new ForgeDirection[] {ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST}) {
			if(d.offsetX == r[0] && d.offsetZ == r[1]) return d;
		}
		return face;
	}

	/** 8 个接口相对核心格的坐标（已按机器朝向旋转），供核心 TE 注册端口。 */
	public int[][] getPortsAtCore(ForgeDirection dir) {
		return rotateModelPorts(PORTS_MODEL, dir);
	}

	/** 4 个电力接口相对核心格的坐标（已按机器朝向旋转）。 */
	public int[][] getPowerPortsAtCore(ForgeDirection dir) {
		return rotateModelPorts(POWER_PORTS_MODEL, dir);
	}

	private static int[][] rotateModelPorts(int[][] model, ForgeDirection dir) {
		int[][] out = new int[model.length][4];
		for(int i = 0; i < model.length; i++) {
			int[] p = rotateXZ(model[i][0], model[i][2], dir);
			ForgeDirection face = rotateFace(ForgeDirection.getOrientation(model[i][3]), dir);
			out[i] = new int[] {p[0], model[i][1], p[1], face.ordinal()};
		}
		return out;
	}

	/** 放置格相对坐标 = 核心相对坐标 - 放置偏移 */
	private static int[][] toPlacementCoords(int[][] ports, ForgeDirection dir, int o) {
		for(int[] port : ports) {
			port[0] -= dir.offsetX * o;
			port[2] -= dir.offsetZ * o;
		}
		return ports;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		int[][] side = getPortsAtCore(dir);
		int[][] power = getPowerPortsAtCore(dir);
		int[][] ports = new int[side.length + power.length][];
		System.arraycopy(side, 0, ports, 0, side.length);
		System.arraycopy(power, 0, ports, side.length, power.length);
		return toPlacementCoords(ports, dir, getOffset());
	}

	/** 右键开操作界面（核心格定位后转发 GUI_INDUSTRIAL_GAS_TURBINE） */
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		if(!(world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityIndustrialGasTurbine))
			return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_INDUSTRIAL_GAS_TURBINE, world, pos[0], pos[1], pos[2]);

		return true;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		// 端口块必须显式以「extra+朝向」元数据放置：super.fillSpace 只把结构填成
		// 普通 dummy（meta=面朝向），makeExtra 对 air/非本方块不生效，导致端口没有
		// 代理 TE，findCore 也走不回来——侧面/背面接管道电缆无反应。
		// 这里按核心坐标+旋转后的端口偏移直接 setBlock，meta=extra+朝向，
		// createNewTileEntity 据 hasExtra 生成电力+流体代理。
		int cx = x + dir.offsetX * o, cy = y + dir.offsetY * o, cz = z + dir.offsetZ * o;

		for(int[] port : getPortsAtCore(dir)) {
			placePort(world, cx + port[0], cy + port[1], cz + port[2], port[3]);
		}
		for(int[] port : getPowerPortsAtCore(dir)) {
			placePort(world, cx + port[0], cy + port[1], cz + port[2], port[3]);
		}
	}

	private void placePort(World world, int px, int py, int pz, int face) {
		BlockDummyable.safeRem = true;
		world.setBlock(px, py, pz, this, extra + face, 3);
		BlockDummyable.safeRem = false;
	}
}
