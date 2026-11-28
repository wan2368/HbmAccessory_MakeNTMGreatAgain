package com.makentmgreatagain.items;

import com.makentmgreatagain.tileentity.TileEntityAtomicProbe;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.List;

/**
 * 探头绑定器（支持一对多，全双语本地化）。
 */
public class ItemAtomicLinker extends Item {

	public ItemAtomicLinker() {
		this.setMaxStackSize(1);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if(!world.isRemote && player.isSneaking() && stack.stackTagCompound != null && stack.stackTagCompound.hasKey("rx")) {
			stack.stackTagCompound.removeTag("rx");
			stack.stackTagCompound.removeTag("ry");
			stack.stackTagCompound.removeTag("rz");
			stack.stackTagCompound.removeTag("rdim");
			player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.cleared"));
		}
		return stack;
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		TileEntity te = world.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityAtomicProbe)) return false;
		if(world.isRemote) return true;

		TileEntityAtomicProbe probe = (TileEntityAtomicProbe) te;

		if(player.isSneaking()) {
			if(!probe.isReceiver) {
				probe.clearTargets();
				player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.unbound_all"));
			} else {
				player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.is_recv"));
			}
			return true;
		}

		if(probe.isReceiver) {
			if(stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
			stack.stackTagCompound.setInteger("rx", x);
			stack.stackTagCompound.setInteger("ry", y);
			stack.stackTagCompound.setInteger("rz", z);
			stack.stackTagCompound.setInteger("rdim", world.provider.dimensionId);
			player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.saved", x, y, z));
			return true;
		}

		if(stack.stackTagCompound == null || !stack.stackTagCompound.hasKey("rx")) {
			player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.need_recv"));
			return true;
		}

		int rdim = stack.stackTagCompound.getInteger("rdim");
		if(rdim != world.provider.dimensionId) {
			player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.cross_dim"));
			return true;
		}

		int rx = stack.stackTagCompound.getInteger("rx");
		int ry = stack.stackTagCompound.getInteger("ry");
		int rz = stack.stackTagCompound.getInteger("rz");
		if(rx == x && ry == y && rz == z) {
			player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.self"));
			return true;
		}

		boolean added = probe.addTarget(rx, ry, rz);
		if(!added) {
			player.addChatMessage(new ChatComponentTranslation("mnga.linker.chat.exists", TileEntityAtomicProbe.MAX_TARGETS));
			return true;
		}

		double dist = Math.sqrt((rx - x) * (rx - x) + (ry - y) * (ry - y) + (rz - z) * (rz - z));
		String key = dist > probe.getMaxRange() ? "mnga.linker.chat.bound_far" : "mnga.linker.chat.bound_ok";
		player.addChatMessage(new ChatComponentTranslation(key, probe.targets.size(), rx, ry, rz,
				String.format("%.1f", dist), String.format("%.0f", probe.getMaxRange())));
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add(StatCollector.translateToLocal("mnga.linker.tooltip.1"));
		list.add(StatCollector.translateToLocal("mnga.linker.tooltip.2"));
		if(stack.stackTagCompound != null && stack.stackTagCompound.hasKey("rx")) {
			int rx = stack.stackTagCompound.getInteger("rx");
			int ry = stack.stackTagCompound.getInteger("ry");
			int rz = stack.stackTagCompound.getInteger("rz");
			list.add(StatCollector.translateToLocalFormatted("mnga.linker.tooltip.saved", rx, ry, rz));
		}
	}
}
