package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.tileentity.TileEntityElectrolyzerReborn;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 小型电解机：2×2×3 多方块，不做 GUI。
 *
 * <p>四个底部插座都是流体 / 电力口：四个接口都可输入或输出流体，也都可输入电力。</p>
 */
public class BlockElectrolyzerReborn extends BlockDummyable implements ILookOverlay, ITooltipProvider {

	public BlockElectrolyzerReborn() {
		super(Material.iron);
		// 2×2×3 的完整碰撞/选中框，坐标以核心方块中心为基准，按 rot 恒等（基准朝向 WEST）书写
		this.bounding.add(AxisAlignedBB.getBoundingBox(-0.5D, 0D, -1.5D, 1.5D, 3D, 0.5D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 朝向+, 朝向-, 左, 右}
		return new int[] {2, 0, 1, 0, 1, 0};
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityElectrolyzerReborn();
		// 底部三格是接口，全部挂流体、电力、物品代理
		if(meta >= 6) return new TileEntityProxyCombo().fluid().power().inventory();
		return null;
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		// 基准朝向 SOUTH：局部 x 轴 = rot、局部 z 轴 = dir，面方向同样用这套轴书写
		// 模型 +X = -dir、+Z = -rot：输入口（模型 +Z）在 -rot 侧，输出口（模型 -Z）在 +rot 侧
		// 输出口：+rot 侧两格；输入口：核心格与 -dir 一格
		return rotatePorts(new int[][] {
			{1, 0, 0, ForgeDirection.EAST.ordinal()},
			{1, 0, -1, ForgeDirection.EAST.ordinal()},
			{0, 0, 0, ForgeDirection.WEST.ordinal()},
			{0, 0, -1, ForgeDirection.WEST.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			// 核心格本身就是接口，不用再升级成 extra
			if(port[0] == 0 && port[1] == 0 && port[2] == 0) continue;
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		// 客户端也吃掉这次右键，避免识别码物品再走一遍 onItemUse
		if(player.isSneaking() && player.getHeldItem() != null
				&& player.getHeldItem().getItem() instanceof IItemFluidIdentifier) {

			if(world.isRemote) return true;

			int[] pos = this.findCore(world, x, y, z);
			if(pos == null) return true;

			TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
			if(!(te instanceof TileEntityElectrolyzerReborn)) return true;

			FluidType type = ((IItemFluidIdentifier) player.getHeldItem().getItem())
				.getType(world, pos[0], pos[1], pos[2], player.getHeldItem());

			if(((TileEntityElectrolyzerReborn) te).setInputType(type)) {
				player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.YELLOW
					+ I18nUtil.resolveKey("info.electrolyzer_reborn.changed") + " "
					+ I18nUtil.resolveKey(type.getUnlocalizedName())));
			} else {
				player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED
					+ I18nUtil.resolveKey("info.electrolyzer_reborn.no_recipe")));
			}

			return true;
		}

		// 没有 GUI，普通右键什么也不做
		return false;
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityElectrolyzerReborn)) return;

		TileEntityElectrolyzerReborn machine = (TileEntityElectrolyzerReborn) te;
		List<String> text = new ArrayList<String>();

		// 电力：放在标题正下方一行；存量和上限都用 k/M 简写，连一次耗电都不够时标红
		EnumChatFormatting powerColor = machine.power < machine.getUsage() ? EnumChatFormatting.RED : EnumChatFormatting.YELLOW;
		text.add(powerColor + I18nUtil.resolveKey("info.electrolyzer_reborn.power") + ": "
			+ shortPower(machine.power) + "/" + shortPower(TileEntityElectrolyzerReborn.MAX_POWER) + " HE");

		// 输入：红色 →
		text.add(EnumChatFormatting.RED + "→ " + fluidName(machine.tanks[0]) + " " + amount(machine.tanks[0]));

		// 输出：绿色 ←；NONE 的第二产物不显示
		if(machine.tanks[1].getTankType() != Fluids.NONE) {
			text.add(EnumChatFormatting.GREEN + "← " + fluidName(machine.tanks[1]) + " " + amount(machine.tanks[1]));
		}
		if(machine.tanks[2].getTankType() != Fluids.NONE) {
			text.add(EnumChatFormatting.GREEN + "← " + fluidName(machine.tanks[2]) + " " + amount(machine.tanks[2]));
		}

		// 有副产才多一行
		String byproducts = byproducts(machine);
		if(byproducts != null) {
			text.add(EnumChatFormatting.GOLD + I18nUtil.resolveKey("info.electrolyzer_reborn.byproduct") + ": " + byproducts);
		}

		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	private static String fluidName(FluidTank tank) {
		FluidType type = tank.getTankType();
		return type == null || type == Fluids.NONE ? "-" : I18nUtil.resolveKey(type.getUnlocalizedName());
	}

	private static String amount(FluidTank tank) {
		return String.format(Locale.US, "%,d/%,d mB", tank.getFill(), tank.getMaxFill());
	}

	/** 电力简写：5k、10k、1M、2M，避免提示行太长 */
	private static String shortPower(long amount) {
		if(amount >= 1_000_000L) {
			return amount % 1_000_000L == 0
				? (amount / 1_000_000L) + "M"
				: String.format(Locale.US, "%.1fM", amount / 1_000_000.0D);
		}
		if(amount >= 1_000L) {
			return amount % 1_000L == 0
				? (amount / 1_000L) + "k"
				: String.format(Locale.US, "%.1fk", amount / 1_000.0D);
		}
		return Long.toString(amount);
	}

	private static String byproducts(TileEntityElectrolyzerReborn machine) {

		StringBuilder builder = null;

		for(ItemStack stack : machine.slots) {
			if(stack == null) continue;
			if(builder == null) builder = new StringBuilder();
			else builder.append(", ");
			builder.append(stack.getDisplayName()).append(" x").append(stack.stackSize);
		}

		return builder == null ? null : builder.toString();
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}
}
