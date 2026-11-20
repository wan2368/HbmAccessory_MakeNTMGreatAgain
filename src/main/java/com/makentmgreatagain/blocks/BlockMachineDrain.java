package com.makentmgreatagain.blocks;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.ILookOverlay;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.tileentity.TileEntityMachineDrain;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;

/**
 * 排液管（单方块）。
 *
 * <p>外形取本体排液管：1×1×1 底座，正底部内凹 2px（0.125 格）的圆形排口，
 * 贴图与原版 {@code drain.png} 的 UV 完全一致。与本体不同的是省掉了向外伸出的那两格管身。</p>
 *
 * <p>逻辑照抄本体 {@code TileEntityMachineDrain}：罐容量由构造参数决定（钢制 1,000、钨制 2,000 mB），
 * 罐里有液就每 tick 排掉一半；顶面与底面不接流体，四个侧面都可以接。</p>
 */
public class BlockMachineDrain extends BlockContainer implements ILookOverlay {

	/** 罐容量：钢制 1,000，钨制 2,000 */
	protected final int capacity;

	public BlockMachineDrain() {
		this(1000);
	}

	public BlockMachineDrain(int capacity) {
		super(Material.iron);
		this.capacity = capacity;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileEntityMachineDrain(capacity);
	}

	// 模型完全由 TESR 绘制，方块本身不可见（与 BlockDummyable 一致）
	@Override public int getRenderType() { return -1; }
	@Override public boolean isOpaqueCube() { return false; }
	@Override public boolean renderAsNormalBlock() { return false; }

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {

		if(!world.isRemote && !player.isSneaking()) {

			if(player.getHeldItem() != null && player.getHeldItem().getItem() instanceof IItemFluidIdentifier) {

				TileEntity te = world.getTileEntity(x, y, z);
				if(!(te instanceof TileEntityMachineDrain)) return false;

				TileEntityMachineDrain drain = (TileEntityMachineDrain) te;

				FluidType type = ((IItemFluidIdentifier) player.getHeldItem().getItem()).getType(world, x, y, z, player.getHeldItem());
				drain.tank.setTankType(type);
				drain.markDirty();
				player.addChatComponentMessage(new ChatComponentText("Changed type to ")
						.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW))
						.appendSibling(new ChatComponentTranslation(type.getConditionalName()))
						.appendSibling(new ChatComponentText("!")));

				return true;
			}
			return false;

		} else {
			return true;
		}
	}

	@Override
	public void printHook(Pre event, World world, int x, int y, int z) {

		TileEntity te = world.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityMachineDrain)) return;

		TileEntityMachineDrain drain = (TileEntityMachineDrain) te;
		List<String> text = new ArrayList();
		text.add(EnumChatFormatting.GREEN + "-> " + EnumChatFormatting.RESET + drain.tank.getTankType().getLocalizedName()
				+ ": " + drain.tank.getFill() + "/" + drain.tank.getMaxFill() + "mB");
		ILookOverlay.printGeneric(event, I18nUtil.resolveKey(getUnlocalizedName() + ".name"), 0xffff00, 0x404000, text);
	}
}
