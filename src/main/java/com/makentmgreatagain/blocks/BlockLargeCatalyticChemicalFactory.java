package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactoryPort;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 大型催化化工厂（8×4×8 大型多方块）。
 *
 * <p>尺寸、核心偏移、接口布局全部照搬 1.0.27_X4980 的遗留机型
 * （{@code MachineChemfac}，dims {@code {3,0,4,3,4,3}}、offset 3）：</p>
 *
 * <ul>
 *   <li>顶面两排各 6 个接口（{@code y+3}），对应模型顶盖上那两排米色方块；</li>
 *   <li>前后两面各两排、每排 6 个接口（{@code y+1} 与 {@code y+2}），
 *       对应模型上每面三扇舱体、每扇上下各两个接口的原始布局。</li>
 * </ul>
 *
 * <p>原版的接口分「顶部连接」和「侧面连接」两类，这里统一成 {@link TileEntityLargeCatalyticChemicalFactoryPort}：
 * 每一个接口方块都能同时接<b>流体管道</b>、<b>物品物流</b>和<b>电力线</b>。</p>
 *
 * <p>本阶段不加 GUI：机器可以成型、可以被管线和电线识别，但配方选择留到界面阶段。</p>
 */
public class BlockLargeCatalyticChemicalFactory extends BlockDummyable {

	/** 接口相对核心的偏移：{dx, dy, dz, 连接方向 ordinal} */
	public static List<int[]> getPortOffsets(ForgeDirection dir) {

		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
		List<int[]> ports = new ArrayList<int[]>();

		for(int i = -3; i < 3; i++) {
			int ox = dir.offsetX * i;
			int oz = dir.offsetZ * i;

			// 顶面：靠 rot 正侧一排、负侧一排
			ports.add(new int[] {rot.offsetX * 2 + ox, 3, rot.offsetZ * 2 + oz, ForgeDirection.UP.ordinal()});
			ports.add(new int[] {-rot.offsetX * 3 + ox, 3, -rot.offsetZ * 3 + oz, ForgeDirection.UP.ordinal()});

			// 侧面：上下两排接口，与原版模型一一对应
			for(int y = 1; y <= 2; y++) {
				ports.add(new int[] {rot.offsetX * 3 + ox, y, rot.offsetZ * 3 + oz, rot.ordinal()});
				ports.add(new int[] {-rot.offsetX * 4 + ox, y, -rot.offsetZ * 4 + oz, rot.getOpposite().ordinal()});
			}
		}

		return ports;
	}

	public BlockLargeCatalyticChemicalFactory() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityLargeCatalyticChemicalFactory();
		// 接口格挂「库存 + 电力 + 流体」三合一代理
		if(meta >= 6) return new TileEntityLargeCatalyticChemicalFactoryPort();

		return null;
	}

	/** {上, 下, 朝向+, 朝向-, 左, 右} */
	@Override
	public int[] getDimensions() {
		return new int[] {3, 0, 4, 3, 4, 3};
	}

	@Override
	public int getOffset() {
		return 3;
	}

	/**
	 * 放置预览与 {@link #fillSpace} 共用的接口表。
	 *
	 * <p>{@link #getPortOffsets(ForgeDirection)} 给的是<b>核心相对</b>坐标，
	 * 而基类的放置预览和 fillSpace 都是以「玩家要放的那一格」为原点，
	 * 两者正好差一个核心偏移，所以这里统一减掉。</p>
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

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;
		if(!(world.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityLargeCatalyticChemicalFactory)) return false;

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_LARGE_CATALYTIC_CHEMICAL_FACTORY, world, pos[0], pos[1], pos[2]);

		return true;
	}
}
