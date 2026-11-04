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
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 集成式换热加热器。
 */
public class BlockHeatexReborn extends BlockDummyable implements ILookOverlay, ITooltipProvider {

	public BlockHeatexReborn() {
		super(Material.iron);
	}

	@Override
	public int[] getDimensions() {
		// {上, 下, 朝向+, 朝向-, 左, 右}
		return new int[] {0, 0, 2, 2, 1, 1};
	}

	@Override
	public int getOffset() {
		return 2;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		if(meta >= 12) return new TileEntityHeatexReborn();
		// 流体口与顶部热面外圈都需要代理：前者接管道，后者供热管从上方抽热
		if(hasExtra(meta)) return new TileEntityProxyCombo().fluid().heatSource();
		return null;
	}

	@Override
	public double[][] getAABBExtras() {
		// 放置高亮时把顶部 3×3 热面画成一个橙色平板（坐标相对核心）
		return new double[][] {
			{1, 1, 1.5, -1.5, 1.5, -1.5, 1}
		};
	}
	
	@Override
	public int[][] getAllPorts(ForgeDirection dir) {
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		int[][] ports = new int[6][4];

		for(int a = -1; a <= 1; a++) {
			ports[a + 1] = new int[] {rot.offsetX * a, 0, rot.offsetZ * a, dir.ordinal()};
			ports[a + 4] = new int[] {
				-dir.offsetX * 4 + rot.offsetX * a,
				0,
				-dir.offsetZ * 4 + rot.offsetZ * a,
				dir.getOpposite().ordinal()
			};
		}

		return ports;
	}

	@Override
	public void fillSpace(World world, int x, int y, int z, ForgeDirection dir, int o) {
		super.fillSpace(world, x, y, z, dir, o);

		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		// 两端各三个流体口（标记为 extra，管道才会认作接口）
		for(int a = -1; a <= 1; a++) {
			this.makeExtra(world, x + rot.offsetX * a, y, z + rot.offsetZ * a);
			this.makeExtra(world, x - dir.offsetX * 4 + rot.offsetX * a, y, z - dir.offsetZ * 4 + rot.offsetZ * a);
		}

		// 顶部 3×3 热面除核心外的八格
		for(int a = -1; a <= 1; a++) {
			for(int b = -1; b <= 1; b++) {
				if(a == 0 && b == 0) continue;
				this.makeExtra(world, x + (b - 2) * dir.offsetX + rot.offsetX * a, y, z + (b - 2) * dir.offsetZ + rot.offsetZ * a);
			}
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return false;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityHeatexReborn)) return false;

		// 机器外部：潜行 + 流体识别器 = 设置「被加热流体」
		if(player.isSneaking() && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof IItemFluidIdentifier) {

			TileEntityHeatexReborn heatex = (TileEntityHeatexReborn) te;
			FluidType type = ((IItemFluidIdentifier) player.getHeldItem().getItem()).getType(world, pos[0], pos[1], pos[2], player.getHeldItem());

			if(type != null && TileEntityHeatexReborn.isHeatable(type)) {
				heatex.tanks[2].setTankType(type);
				heatex.markChanged();
				player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.YELLOW
					+ I18nUtil.resolveKey("info.heatex_reborn.changed") + " " + I18nUtil.resolveKey(type.getUnlocalizedName())));
			} else {
				player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED
					+ I18nUtil.resolveKey("info.heatex_reborn.not_heatable")));
			}

			return true;
		}

		// 其余情况打开 GUI，GUI 里的识别码槽用来切换换热回路的热流体
		player.openGui(MakeNTMGreatAgain.instance, MakeNTMGreatAgain.GUI_HEATEX_REBORN, world, pos[0], pos[1], pos[2]);
		return true;
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {

		int[] pos = this.findCore(world, x, y, z);
		if(pos == null) return;

		TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
		if(!(te instanceof TileEntityHeatexReborn)) return;

		TileEntityHeatexReborn heatex = (TileEntityHeatexReborn) te;
		List<String> text = new ArrayList<String>();

		ForgeDirection dir = ForgeDirection.getOrientation(te.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		int along = (x - pos[0]) * dir.offsetX + (z - pos[2]) * dir.offsetZ;
		int lateral = (x - pos[0]) * rot.offsetX + (z - pos[2]) * rot.offsetZ;

		// 输入口 / 输出口：只显示对应的箭头标签
		if(Math.abs(lateral) <= 1 && along == 2) {
			text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("info.heatex_reborn.output_port"));
			ILookOverlay.printGeneric(event, "", 0xffffff, 0x404000, text);
			return;
		}
		if(Math.abs(lateral) <= 1 && along == -2) {
			text.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("info.heatex_reborn.input_port"));
			ILookOverlay.printGeneric(event, "", 0xffffff, 0x404000, text);
			return;
		}

		// 其余位置：热量 + 加热回路两端
		text.add(EnumChatFormatting.GOLD + String.format(Locale.US, "%,d", heatex.heatEnergy) + " TU");

		if(heatex.tanks[2].getTankType() == Fluids.NONE) {
			text.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey("info.heatex_reborn.heated_in"));
			text.add(EnumChatFormatting.RED + I18nUtil.resolveKey("info.heatex_reborn.heated_out"));
		} else {
			// 两行都带上当前存量，方便直接看出加热回路卡在进料还是出料
			text.add(EnumChatFormatting.GREEN + "→" + I18nUtil.resolveKey(heatex.tanks[2].getTankType().getUnlocalizedName())
				+ " " + amount(heatex.tanks[2]));
			text.add(EnumChatFormatting.RED + "←" + I18nUtil.resolveKey(heatex.tanks[3].getTankType().getUnlocalizedName())
				+ " " + amount(heatex.tanks[3]));
		}

		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}

	/** 「当前存量/上限」的显示文本 */
	private static String amount(FluidTank tank) {
		return String.format(Locale.US, "%,d/%,d mB", tank.getFill(), tank.getMaxFill());
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		this.addStandardInfo(stack, player, list, ext);
	}
}
