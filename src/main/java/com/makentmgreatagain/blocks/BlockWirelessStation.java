package com.makentmgreatagain.blocks;

import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.tileentity.TileEntityWirelessStation;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 无线充电站：一格、可旋转、六面独立贴图。
 *
 * <h3>朝向</h3>
 * <p>meta 存的是<b>正面朝向</b>的 {@link ForgeDirection} 序号（2/3/4/5 = 北/南/西/东），
 * 与 HBM 原版机器一致。背面（{@code getOpposite()}）装电缆，电缆接口的贴图就画在那一面。</p>
 *
 * <h3>第一版没有 GUI</h3>
 * <p>手持连接器右键是配对，空手右键是查表：缓冲电量、最近一次输出、当前状态。
 * 所以这里只实现 {@code onBlockActivated}，不挂 {@code IGUIProvider}。</p>
 */
public class BlockWirelessStation extends BlockContainer {
	private static String formatEnergyM(long value) { return ((value + 500_000L) / 1_000_000L) + " m"; }

	/** 附属自己的渲染 ID，与 HBM 的方块渲染器完全分开。 */
	public static final int RENDER_ID = RenderingRegistry.getNextAvailableRenderId();

	@SideOnly(Side.CLIENT) public static IIcon iconFront;
	@SideOnly(Side.CLIENT) public static IIcon iconSide;
	@SideOnly(Side.CLIENT) public static IIcon iconBack;
	@SideOnly(Side.CLIENT) public static IIcon iconTop;
	@SideOnly(Side.CLIENT) public static IIcon iconBottom;
	/** 0 断电灰 / 1 待机琥珀 / 2 输电青蓝。 */
	@SideOnly(Side.CLIENT) public static IIcon[] lampIcons = new IIcon[3];

	public BlockWirelessStation() {
		super(Material.iron);

		this.setBlockName("wireless_station");
		this.setHardness(5.0F);
		this.setResistance(10.0F);
		this.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityWirelessStation();
	}

	@Override
	public int getRenderType() {
		return RENDER_ID;
	}

	@Override
	public boolean isOpaqueCube() {
		return true;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return true;
	}

	/** 按玩家朝向定正面，与 HBM 机器的 meta 口径相同。 */
	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
		int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

		int meta = 2;
		if(i == 1) meta = 5;
		if(i == 2) meta = 3;
		if(i == 3) meta = 4;

		world.setBlockMetadataWithNotify(x, y, z, meta, 2);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister reg) {
		// 不调 super：本方块每面都是独立图标，不需要统一的 blockIcon；
		// 但 fill 一个兜底值，免得其他代码读到 null
		iconSide = reg.registerIcon("makentmgreatagain:wstation_side");
		this.blockIcon = iconSide;

		iconFront = reg.registerIcon("makentmgreatagain:wstation_front");
		iconBack = reg.registerIcon("makentmgreatagain:wstation_back");
		iconTop = reg.registerIcon("makentmgreatagain:wstation_top");
		iconBottom = reg.registerIcon("makentmgreatagain:wstation_bottom");

		lampIcons[0] = reg.registerIcon("makentmgreatagain:wstation_lamp_off");
		lampIcons[1] = reg.registerIcon("makentmgreatagain:wstation_lamp_idle");
		lampIcons[2] = reg.registerIcon("makentmgreatagain:wstation_lamp_active");
	}

	/**
	 * 把世界的六个面映射到「正面 / 背面 / 顶 / 底 / 侧面」。
	 *
	 * <p>物品栏渲染传入的 meta 是 0，这里兜成一个朝向，免得正面朝下。</p>
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		if(meta < 2 || meta > 5) meta = 2;

		ForgeDirection front = ForgeDirection.getOrientation(meta);
		if(side == front.ordinal()) return iconFront;
		if(side == front.getOpposite().ordinal()) return iconBack;
		if(side == 1) return iconTop;
		if(side == 0) return iconBottom;
		return iconSide;
	}

	/**
	 * 空手右键查看。手持连接器时走的是 {@code ItemWirelessConnector#onItemUse}，
	 * 那个方法返回 true，不会落到这里。
	 */
	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
			int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		TileEntity te = world.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityWirelessStation)) return false;

		TileEntityWirelessStation station = (TileEntityWirelessStation) te;

		player.addChatMessage(new ChatComponentText(
			EnumChatFormatting.GOLD + "无线充电站"));

		player.addChatMessage(new ChatComponentText(
			EnumChatFormatting.GRAY + "缓冲：" + EnumChatFormatting.WHITE
			+ formatEnergyM(station.power) + EnumChatFormatting.GRAY + " / "
			+ formatEnergyM(TileEntityWirelessStation.BUFFER) + " HE"));

		// 只有仍在余辉窗口内，lastMoved 才是最近的合计输出；否则按 0 显示
		long output = station.getLampState() == 2 ? station.lastMoved : 0L;

		player.addChatMessage(new ChatComponentText(
			EnumChatFormatting.GRAY + "输出：" + EnumChatFormatting.WHITE
			+ formatEnergyM(output) + EnumChatFormatting.GRAY
			+ " HE/t（合计；单人上限 "
			+ formatEnergyM(TileEntityWirelessStation.PLAYER_LIMIT) + "）"));

		String state;
		if(station.getLampState() == 2) {
			state = EnumChatFormatting.AQUA + "正在输电";
		} else if(station.getLampState() == 1) {
			state = EnumChatFormatting.YELLOW + "待机";
		} else {
			state = EnumChatFormatting.DARK_GRAY + "无电";
		}
		player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "状态：" + state));

		return true;
	}
}
