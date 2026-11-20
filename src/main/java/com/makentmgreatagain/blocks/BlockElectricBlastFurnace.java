package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnacePort;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 电力高炉（5×5×7 多方块）。
 *
 * <p>足迹 5×5、高 7，核心位于底面正中，模型原点也在底面正中，
 * 因此渲染时只需要 {@code (x+0.5, y, z+0.5)}。</p>
 *
 * <p>接口全部在最底层（{@code y=0}）的外圈方块上：四个正方向各两个法兰，
 * 法兰中心对齐到方块中心（模型 {@code x = ±1}），所以管线推进来正好落在法兰上。
 * 背面另有一个烟道口对齐模型的高烟囱，顶部正中一个上料口。</p>
 *
 * <p>本阶段不接 GUI：机器可以成型、接口可以接物品物流 / 流体管道 / 电线，
 * 处理逻辑留给界面阶段。</p>
 */
public class BlockElectricBlastFurnace extends BlockDummyable {

	/** 接口相对核心的偏移：{dx, dy, dz, 连接方向 ordinal} */
	public static List<int[]> getPortOffsets(ForgeDirection dir) {

		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
		List<int[]> ports = new ArrayList<int[]>();

		for(int s = -1; s <= 1; s += 2) {
			// 沿朝向轴的两面（正面 / 背面），法兰沿侧向各偏 1 格
			for(int t = -1; t <= 1; t += 2) {
				ports.add(new int[] {dir.offsetX * 2 * s + rot.offsetX * t, 0, dir.offsetZ * 2 * s + rot.offsetZ * t,
						s > 0 ? dir.ordinal() : dir.getOpposite().ordinal()});
			}
			// 左右两面，法兰沿朝向轴各偏 1 格
			for(int t = -1; t <= 1; t += 2) {
				ports.add(new int[] {rot.offsetX * 2 * s + dir.offsetX * t, 0, rot.offsetZ * 2 * s + dir.offsetZ * t,
						s > 0 ? rot.ordinal() : rot.getOpposite().ordinal()});
			}
		}

		// 背面烟道：对齐模型背面的高烟囱
		ports.add(new int[] {dir.offsetX * 2, 5, dir.offsetZ * 2, dir.ordinal()});
		// 顶上料口
		ports.add(new int[] {0, 6, 0, ForgeDirection.UP.ordinal()});

		return ports;
	}

	public BlockElectricBlastFurnace() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityElectricBlastFurnace();
		// 接口格挂「库存 + 电力 + 流体」三合一代理
		if(meta >= 6) return new TileEntityElectricBlastFurnacePort();

		return null;
	}

	/** {上, 下, 朝向+, 朝向-, 左, 右}：5×5×7 */
	@Override
	public int[] getDimensions() {
		return new int[] {6, 0, 2, 2, 2, 2};
	}

	@Override
	public int getOffset() {
		return 2;
	}

	/**
	 * 放置预览与 {@link #fillSpace} 共用的接口表。
	 *
	 * <p>{@link #getPortOffsets(ForgeDirection)} 给的是核心相对坐标，
	 * 而基类的预览和 fillSpace 以「玩家要放的那一格」为原点，两者差一个核心偏移。</p>
	 */
	@Override
	public int[][] getAllPorts(ForgeDirection dir) {

		List<int[]> offsets = getPortOffsets(dir);
		int[][] ports = new int[offsets.size()][4];

		for(int i = 0; i < offsets.size(); i++) {
			int[] p = offsets.get(i);
			ports[i][0] = p[0] - dir.offsetX * getOffset();
			ports[i][1] = p[1];
			ports[i][2] = p[2] - dir.offsetZ * getOffset();
			ports[i][3] = p[3];
		}

		return ports;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		// 整机任何一格（核心/接口/哑块）都找核心开界面
		int[] core = this.findCore(world, x, y, z);
		if(core != null) {
			x = core[0]; y = core[1]; z = core[2];
		}

		TileEntity te = world.getTileEntity(x, y, z);
		if(te instanceof TileEntityElectricBlastFurnace) {
			player.openGui(com.makentmgreatagain.MakeNTMGreatAgain.instance,
					com.makentmgreatagain.MakeNTMGreatAgain.GUI_ELECTRIC_BLAST_FURNACE, world, x, y, z);
		}

		return true;
	}
}
