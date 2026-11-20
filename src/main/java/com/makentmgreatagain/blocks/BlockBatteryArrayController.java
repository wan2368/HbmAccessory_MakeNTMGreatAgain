package com.makentmgreatagain.blocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.hbm.util.fauxpointtwelve.BlockPos;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.tileentity.TileEntityBatteryArray;
import com.makentmgreatagain.tileentity.TileEntityBatteryArrayShell;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 电池组中心控制器。右键：未成型→泛洪成型；已成型→打开 GUI（同电池座）。
 *
 * <p>成型逻辑照 PWR：从控制器背面泛洪，外壳/端口为边界（停下），
 * 电池/电容为内部（继续扩散）。收集后把零件替换成成型壳，端口记位置供电。</p>
 */
public class BlockBatteryArrayController extends BlockContainer {

	@SideOnly(Side.CLIENT) private IIcon iconFront;
	/** 成型后正面 */
	@SideOnly(Side.CLIENT) private IIcon iconFormed;
	/** 成型后其余五面：与成型壳同一张 */
	@SideOnly(Side.CLIENT) private IIcon iconShell;

	private static final int MAX_SIZE = 4096;

	public BlockBatteryArrayController() {
		super(Material.iron);
		this.setHardness(5.0F);
		this.setResistance(10.0F);
		this.setCreativeTab(com.makentmgreatagain.ModCreativeTabs.TAB_MACHINES);
		this.setBlockTextureName("makentmgreatagain:battery_array_controller_side");
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityBatteryArray();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		super.registerBlockIcons(reg);
		this.iconFront = reg.registerIcon("makentmgreatagain:battery_array_controller_front");
		this.iconFormed = reg.registerIcon("makentmgreatagain:battery_array_controller_formed");
		this.iconShell = reg.registerIcon("makentmgreatagain:battery_array_shell");
	}

	/**
	 * 取图：成型后正面 formed、其余五面 shell；未成型用摆放期 front/side。
	 *
	 * <p><b>控制器不走连接纹理</b>：CT 渲染器会把每个面切成四象限再从 {@code _ct} 大图取块，
	 * 而控制器这几张是<b>完整图案</b>（不是按 CT 四象限布局画的），切开会拼成乱码碎片。
	 * 所以这里用普通渲染整张贴图，外壳那边仍走 CT 保持无缝。</p>
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(net.minecraft.world.IBlockAccess world, int x, int y, int z, int side) {
		int meta = world.getBlockMetadata(x, y, z);
		TileEntity te = world.getTileEntity(x, y, z);
		boolean formed = te instanceof TileEntityBatteryArray && ((TileEntityBatteryArray) te).assembled;

		if(formed) return side == meta ? iconFormed : iconShell;
		return side == meta ? iconFront : this.blockIcon;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		return side == meta ? iconFront : this.blockIcon;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
		int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
		if(i == 0) world.setBlockMetadataWithNotify(x, y, z, 2, 2);
		if(i == 1) world.setBlockMetadataWithNotify(x, y, z, 5, 2);
		if(i == 2) world.setBlockMetadataWithNotify(x, y, z, 3, 2);
		if(i == 3) world.setBlockMetadataWithNotify(x, y, z, 4, 2);
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
		if(world.isRemote) return true;
		if(player.isSneaking()) return false;

		TileEntityBatteryArray te = (TileEntityBatteryArray) world.getTileEntity(x, y, z);
		if(te == null) return true;

		if(!te.assembled) {
			assemble(world, x, y, z, player, te);
		} else {
			FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_BATTERY_ARRAY, world, x, y, z);
		}
		return true;
	}

	private void assemble(World world, int x, int y, int z, EntityPlayer player, TileEntityBatteryArray te) {
		HashMap<BlockPos, Block> parts = new HashMap<BlockPos, Block>();
		List<BlockPos> ports = new ArrayList<BlockPos>();
		List<BatteryCellSpec> cells = new ArrayList<BatteryCellSpec>();
		List<int[]> bad = new ArrayList<int[]>();

		ForgeDirection dir = ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z)).getOpposite();
		String err = detect(world, x, y, z, x + dir.offsetX, y, z + dir.offsetZ, parts, ports, cells, bad);

		if(err != null) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "[电池矩阵] " + err));
			markBad(world, player, bad);
			te.disassemble();
			return;
		}
		// 全电容也允许成型（容量 0，只作提速/装饰结构）；只要内部有元件即可
		if(cells.isEmpty()) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "[电池矩阵] 内部没有电池或电容。"));
			te.disassemble();
			return;
		}

		// 替换零件为成型壳
		for(java.util.Map.Entry<BlockPos, Block> e : parts.entrySet()) {
			BlockPos p = e.getKey();
			boolean isPort = e.getValue() == ModBlocks.battery_array_port;
			world.setBlock(p.getX(), p.getY(), p.getZ(), ModBlocks.battery_array_shell, isPort ? 1 : 0, 3);
			TileEntity t = world.getTileEntity(p.getX(), p.getY(), p.getZ());
			if(t instanceof TileEntityBatteryArrayShell) {
				TileEntityBatteryArrayShell shell = (TileEntityBatteryArrayShell) t;
				shell.orig = e.getValue();
				shell.coreX = x; shell.coreY = y; shell.coreZ = z;
				shell.markDirty();
			}
		}

		te.setup(ports, BatteryPackStats.compute(cells.toArray(new BatteryCellSpec[0])));
		player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.GREEN + "组装成功"));
	}

	/**
	 * 在不合格格子上画<b>红色方块边框，持续 3 秒</b>。
	 *
	 * <p>复用 HBM 的 {@code marker} 覆盖渲染（{@code RenderOverhead} 画 12 条边框线），
	 * {@code expires} 单位是毫秒，所以 3 秒 = 3000。与 PWR 的错误提示同一套机制。</p>
	 */
	private void markBad(World world, EntityPlayer player, List<int[]> bad) {
		if(!(player instanceof net.minecraft.entity.player.EntityPlayerMP) || bad.isEmpty()) return;

		for(int[] p : bad) {
			NBTTagCompound data = new NBTTagCompound();
			data.setString("type", "marker");
			data.setInteger("color", 0xFF0000);
			data.setInteger("expires", 3_000);
			data.setDouble("dist", 128D);
			com.hbm.handler.threading.PacketThreading.createSendToThreadedPacket(
					new com.hbm.packet.toclient.AuxParticlePacketNT(data, p[0], p[1], p[2]),
					(net.minecraft.entity.player.EntityPlayerMP) player);
		}
	}

	/**
	 * 两段式检测：
	 * ① 从控制器背面连通收集外壳/端口/电池，得出包围盒；
	 * ② 按 {@link BatteryArrayShape} 严格校验：长宽高≥3 的长方体（含立方体）、
	 *    六面外壳完全封住（端口算外壳）、内部实心全是电池/电容。
	 *
	 * <p>比 PWR 严格：不允许空腔、不允许任意异形。</p>
	 */
	private String detect(World world, int cx, int cy, int cz, int sx, int sy, int sz,
			HashMap<BlockPos, Block> parts, List<BlockPos> ports, List<BatteryCellSpec> cells,
			List<int[]> badOut) {

		// ---- ① 连通收集，定包围盒 ----
		Queue<BlockPos> queue = new ArrayDeque<BlockPos>();
		Set<BlockPos> seen = new HashSet<BlockPos>();
		seen.add(new BlockPos(cx, cy, cz));   // 控制器自身跳过
		BlockPos start = new BlockPos(sx, sy, sz);
		queue.add(start);
		seen.add(start);

		int minX = sx, maxX = sx, minY = sy, maxY = sy, minZ = sz, maxZ = sz;

		while(!queue.isEmpty()) {
			BlockPos p = queue.poll();
			Block b = world.getBlock(p.getX(), p.getY(), p.getZ());

			boolean isCasing = b == ModBlocks.battery_array_casing;
			boolean isPort = b == ModBlocks.battery_array_port;
			boolean isCell = b instanceof IBatteryCell;
			if(!isCasing && !isPort && !isCell) continue;   // 空气/杂方块：不收集、不扩散

			if(parts.size() >= MAX_SIZE) return "结构过大（超过 " + MAX_SIZE + " 格）。";
			parts.put(p, b);
			if(isPort) ports.add(p);
			if(isCell) cells.add(((IBatteryCell) b).getSpec());

			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());

			for(ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
				BlockPos n = new BlockPos(p.getX() + d.offsetX, p.getY() + d.offsetY, p.getZ() + d.offsetZ);
				if(seen.add(n)) queue.add(n);
			}
		}

		if(parts.isEmpty()) return "控制器背面没有贴着外壳，请让控制器嵌在外壳面上、正面朝外。";

		int dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
		if(dx * dy * dz > MAX_SIZE) return "结构过大（超过 " + MAX_SIZE + " 格）。";

		// ---- ② 逐格取样，交给纯逻辑校验 ----
		int[][][] grid = new int[dx][dy][dz];
		for(int ix = 0; ix < dx; ix++) {
			for(int iy = 0; iy < dy; iy++) {
				for(int iz = 0; iz < dz; iz++) {
					int wx = minX + ix, wy = minY + iy, wz = minZ + iz;
					Block b = world.getBlock(wx, wy, wz);
					int code;
					// 控制器嵌在外壳面上，它那一格算外壳，否则封闭校验会把它当缺口
					if(wx == cx && wy == cy && wz == cz) code = BatteryArrayShape.CASING;
					else if(b == ModBlocks.battery_array_casing) code = BatteryArrayShape.CASING;
					else if(b == ModBlocks.battery_array_port) code = BatteryArrayShape.PORT;
					else if(b instanceof IBatteryCell) code = BatteryArrayShape.CELL;
					else code = BatteryArrayShape.AIR;
					grid[ix][iy][iz] = code;
				}
			}
		}

		List<int[]> badLocal = new ArrayList<int[]>();
		String err = BatteryArrayShape.validate(grid, dx, dy, dz, badLocal);
		if(err == null) {
			// 控制器自己也不能落在棱/角上（与 PORT 同规则），否则红框标在控制器上
			err = BatteryArrayShape.validateControllerPos(cx - minX, cy - minY, cz - minZ, dx, dy, dz);
			if(err != null) badOut.add(new int[] { cx, cy, cz });
		}
		if(err != null) {
			// 局部坐标 → 世界坐标，供红边标记
			for(int[] p : badLocal) badOut.add(new int[] { minX + p[0], minY + p[1], minZ + p[2] });
			return err + "（当前 " + dx + "×" + dy + "×" + dz + "）";
		}

		// 端口清单按包围盒重建，保证与校验一致
		ports.clear();
		for(java.util.Map.Entry<BlockPos, Block> e : parts.entrySet()) {
			if(e.getValue() == ModBlocks.battery_array_port) ports.add(e.getKey());
		}
		return null;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
		TileEntity t = world.getTileEntity(x, y, z);
		if(t instanceof TileEntityBatteryArray) ((TileEntityBatteryArray) t).disassemble();
		super.breakBlock(world, x, y, z, block, meta);
	}
}