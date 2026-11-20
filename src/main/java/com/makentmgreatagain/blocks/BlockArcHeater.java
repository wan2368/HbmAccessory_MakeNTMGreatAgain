package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.tileentity.TileEntityProxyCombo;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.tileentity.TileEntityArcHeater;

import api.hbm.block.IToolable;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 电弧加热器：3×3 基座 + 正前方 1 格接线箱，2 格高（正面与顶盖平齐）。
 *
 * <p>实际占地 4×3×2（含接线箱那一格）。核心格在基座的（-1, 下层, -1）角上，
 * 也就是模型坐标的（-1, -1）：模型 +X 指向接线箱，+X = -dir、+Z = -rot。
 * 电源口在接线箱那一格内部，接线箱本体朝 +X 方向伸出电缆。</p>
 *
 * <p>供热与原版电加热器一致：相邻机器从正下方抽 {@code IHeatSource} 的热量，
 * 所以所有 dummy 都挂了 heatSource 代理。</p>
 */
public class BlockArcHeater extends BlockDummyable implements ILookOverlay, ITooltipProvider, IToolable {

	public BlockArcHeater() {
		super(Material.iron);

		// 4×3×2 的完整碰撞/选中框，坐标以核心格（接线箱那格）中心为基准
		this.bounding.add(AxisAlignedBB.getBoundingBox(-3.5D, 0D, -1.5D, 0.5D, 2D, 1.5D));
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 模型+X, 模型-X, 模型-Z, 模型+Z}：
		// 核心格在接线箱上，机体朝模型 -X 方向延伸 3 格，左右各 1 格 = 4×3×2
		return new int[] {1, 0, 0, 3, 1, 1};
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/** 放置时整体再转 180°，让接线箱（模型 +X）朝向玩家 */
	@Override
	protected ForgeDirection getDirModified(ForgeDirection dir) {
		return dir.getOpposite();
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {

		if(meta >= 12) return new TileEntityArcHeater();

		// 电源口那格额外要电力代理；其余 dummy 一律挂热源代理，
		// 原版加热类方块取的是「正下方那格」的 IHeatSource，站在任意一层顶上都能抽到热
		if(hasExtra(meta)) return new TileEntityProxyCombo().power().heatSource();
		return new TileEntityProxyCombo().heatSource();
	}

	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		// 核心格就是接线箱那一格，插座面朝模型 +X（= -dir）
		return rotatePorts(new int[][] {
			{0, 0, 0, ForgeDirection.NORTH.ordinal()}
		}, dir);
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		for(int[] port : getAllPorts(dir)) {
			this.makeExtra(world, x + port[0], y + port[1], z + port[2]);
		}
	}

	/** 螺丝刀切换档位，和原版电加热器一样 0~10 */
	@Override
	public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, int side, float fX, float fY, float fZ, ToolType tool) {

		if(tool != ToolType.SCREWDRIVER) return false;
		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityArcHeater)) return false;

		TileEntityArcHeater heater = (TileEntityArcHeater) te;
		heater.toggleSetting();
		heater.markDirty();

		return true;
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityArcHeater)) return;

		TileEntityArcHeater heater = (TileEntityArcHeater) te;

		// 与原版电加热器完全一致的准星提示
		List<String> text = new ArrayList<String>();
		text.add(String.format(Locale.US, "%,d", heater.heatEnergy) + " TU");
		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + heater.getConsumption() + " HE/t");
		text.add(EnumChatFormatting.RED + "<- " + EnumChatFormatting.RESET + heater.getHeatGen() + " TU/t");

		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}
}
