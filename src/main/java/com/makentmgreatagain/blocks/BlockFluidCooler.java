package com.makentmgreatagain.blocks;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityFluidCooler;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 流体冷却机。
 *
 * <p>注册名 {@code machine_fluid_cooler}，显示名「流体冷却机 / Fluid Cooler」；
 * 方块、TileEntity、渲染器与模型资源统一用 {@code FluidCooler} / {@code fluid_cooler} 命名。</p>
 *
 * <p>外形、尺寸、接口布局完全照搬本体 1.0.27_X5778 / 5852 的紧凑型压缩机
 * （{@code com.hbm.blocks.machine.MachineCompressorCompact}）：
 * 3 格高、沿朝向 3 格、侧向 7 格，六个「电力 + 流体」通用接口，
 * 左右两侧各一个（侧向 ±3、高度 +1），前后两端各两个（沿朝向 ±1 / 侧向 ±1、高度 +1）。</p>
 *
 * <p><b>玩法</b>：GUI 里两个识别码槽分别设定冷却剂与被冷却流体，两侧都有料就开始换热，
 * 冷却剂升温（如 冷却液 → 热冷却液）、被冷却流体降温（如 热四氟甲烷 → 四氟甲烷），
 * 分别从第四个、第三个液柱输出。潜行 + 手持识别码右键可快捷设定冷却剂罐。</p>
 */
public class BlockFluidCooler extends BlockDummyable {

	public BlockFluidCooler() {
		super(Material.iron);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityFluidCooler();
		// dummy 格一律挂「电 + 流体」代理，接口那六格（extra）才能被管道与电线识别
		if(meta >= 6) return new TileEntityProxyCombo().power().fluid();
		return null;
	}

	/** {上, 下, 朝向+, 朝向-, 左, 右}：向上 2 格、沿朝向各 1 格、侧向各 3 格 → 3×3×7 */
	@Override
	public int[] getDimensions() {
		return new int[] {2, 0, 1, 1, 3, 3};
	}

	@Override
	public int getOffset() {
		return 1;
	}

	/**
	 * 六个接口（未旋转，以北方为基准；{@link #rotatePorts(int[][], ForgeDirection)} 会按朝向旋转并换算到核心）。
	 * 数值与本体紧凑型压缩机逐条一致。
	 */
	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		return rotatePorts(new int[][] {
			{3, 1, 0, ForgeDirection.EAST.ordinal()},
			{-3, 1, 0, ForgeDirection.WEST.ordinal()},
			{1, 1, 1, ForgeDirection.SOUTH.ordinal()},
			{-1, 1, 1, ForgeDirection.SOUTH.ordinal()},
			{1, 1, -1, ForgeDirection.NORTH.ordinal()},
			{-1, 1, -1, ForgeDirection.NORTH.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		// 六个接口格标成 extra：TileEntity 挂代理，管道/电线才会认这里为接口
		for(int[] offset : getAllPorts(dir)) {
			this.makeExtra(world, x + offset[0], y + offset[1], z + offset[2]);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityFluidCooler)) return false;

		TileEntityFluidCooler cooler = (TileEntityFluidCooler) te;

		// 潜行 + 流体识别码：不必开 GUI 就能快捷设定「冷却剂」罐（tanks[0]）。
		// 被冷却流体只能在 GUI 的第二个槽里设，避免一键把两侧都改掉。
		if(player.isSneaking() && player.getHeldItem() != null
				&& player.getHeldItem().getItem() instanceof IItemFluidIdentifier) {

			FluidType type = ((IItemFluidIdentifier) player.getHeldItem().getItem())
					.getType(world, pos[0], pos[1], pos[2], player.getHeldItem());

			// 只有配对表里的流体才生效：不认识的流体直接忽略，罐型保持原样，也不给任何提示。
			// 与另一侧同型时同样静默忽略——两侧输入口共用坐标，靠类型区分网络。
			if(type != null && TileEntityFluidCooler.isCoolant(type) && cooler.canSetCoolant(type)) {
				cooler.tanks[0].setTankType(type);
				cooler.markChanged();

				player.addChatComponentMessage(new ChatComponentText(
						EnumChatFormatting.YELLOW + I18nUtil.resolveKey("info.fluid_cooler.coolant_set")
						+ " " + I18nUtil.resolveKey(type.getUnlocalizedName())));
			}

			return true;
		}

		FMLNetworkHandler.openGui(player, MakeNTMGreatAgain.instance,
				MakeNTMGreatAgain.GUI_FLUID_COOLER, world, pos[0], pos[1], pos[2]);

		return true;
	}
}
