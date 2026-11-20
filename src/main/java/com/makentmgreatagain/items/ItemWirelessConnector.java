package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.handler.ArmorModHandler;
import com.hbm.items.armor.ItemArmorMod;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.tileentity.TileEntityWirelessStation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * 无线充电连接器：装进靴子的「靴子专用槽」，记住一台充电站，之后一直替玩家取电。
 *
 * <h3>为什么继承 ItemArmorMod 而不是自己写一套</h3>
 * <p>HBM 护甲改装台的判定只有一条：{@code ArmorModHandler.isApplicable} 要求物品是
 * {@code ItemArmorMod}、目标护甲是对应部位。所以只要 {@code new ItemArmorMod(boots_only, …)}
 * 就自动获得<b>任何能进改装台的靴子</b>的兼容性，不需要改 HBM 的改装台、也不需要白名单。</p>
 *
 * <h3>每 tick 只有一次查表</h3>
 * <p>HBM 的 {@code ModEventHandler#onLivingUpdate} 会对穿着护甲的每个 mod 调 {@link #modUpdate}。
 * 这里据此读 NBT 里的坐标，先 {@code blockExists} 判断区块是否加载（不会强行读盘），
 * 再 {@code getTileEntity} 取<b>那一台</b>，uid 对得上才取电。脱下靴子后回调不再发生，
 * 供电立刻中止——不需要额外的开关状态。</p>
 *
 * <h3>为什么记 uid 而不是坐标</h3>
 * <p>坐标只用来找方块，真正身份是座子的随机 UUID。拆掉原座再放一台新的，新座的 uid 不同，
 * 旧连接器自动失效，必须重新配对；把原座装箱搬走再原样放回也不会「复活」绑定。</p>
 */
public class ItemWirelessConnector extends ItemArmorMod {

	public static final String KEY_DIM = "ws_dim";
	public static final String KEY_X = "ws_x";
	public static final String KEY_Y = "ws_y";
	public static final String KEY_Z = "ws_z";
	public static final String KEY_UID = "ws_uid";

	public ItemWirelessConnector() {
		super(ArmorModHandler.boots_only, false, false, false, true);

		this.setUnlocalizedName("wireless_connector");
		this.setTextureName("makentmgreatagain:wireless_connector");
		this.setCreativeTab(ModCreativeTabs.TAB_GEAR);
	}

	/** 是否已经配过对。 */
	public static boolean isBound(ItemStack stack) {
		NBTTagCompound tag = stack == null ? null : stack.stackTagCompound;
		return tag != null && tag.hasKey(KEY_UID) && !tag.getString(KEY_UID).isEmpty();
	}

	/**
	 * 手持右键方块：目标是自己家的充电站就写入绑定，其余一律提示。
	 *
	 * <p>客户端直接返回 true 拦下，让服务端做权威写入；否则两端都会写一次，
	 * 聊天栏会出现两条重复提示。</p>
	 */
	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
			int side, float hitX, float hitY, float hitZ) {

		if(world.isRemote) return true;

		TileEntity te = world.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityWirelessStation)) {
			player.addChatMessage(new ChatComponentText(
				EnumChatFormatting.GRAY + "对准无线充电站才能配对。"));
			return true;
		}

		TileEntityWirelessStation station = (TileEntityWirelessStation) te;

		NBTTagCompound tag = stack.getTagCompound();
		if(tag == null) {
			tag = new NBTTagCompound();
			stack.setTagCompound(tag);
		}

		tag.setInteger(KEY_DIM, world.provider.dimensionId);
		tag.setInteger(KEY_X, x);
		tag.setInteger(KEY_Y, y);
		tag.setInteger(KEY_Z, z);
		tag.setString(KEY_UID, station.getUid());

		player.addChatMessage(new ChatComponentText(
			EnumChatFormatting.GREEN + "已配对：" + EnumChatFormatting.WHITE
			+ world.provider.getDimensionName()
			+ EnumChatFormatting.GRAY + " (" + x + ", " + y + ", " + z + ")"));

		return true;
	}

	/**
	 * 装着它的靴子每 tick 被调用一次。
	 *
	 * <p>只在服务端取电；客户端那次回调直接返回，否则会重复扣电并造成不同步。</p>
	 */
	@Override
	public void modUpdate(EntityLivingBase entity, ItemStack armor) {

		if(entity.worldObj.isRemote) return;
		if(!(entity instanceof EntityPlayer)) return;

		ItemStack mod = ArmorModHandler.pryMod(armor, ArmorModHandler.boots_only);
		if(mod == null || mod.getItem() != this) return;

		NBTTagCompound tag = mod.stackTagCompound;
		if(tag == null || !tag.hasKey(KEY_UID)) return;

		int dim = tag.getInteger(KEY_DIM);
		if(entity.worldObj.provider.dimensionId != dim) return;

		int x = tag.getInteger(KEY_X);
		int y = tag.getInteger(KEY_Y);
		int z = tag.getInteger(KEY_Z);
		String uid = tag.getString(KEY_UID);

		// 先确认区块已加载：getTileEntity 对未加载区块会顺带读盘，等于强加载
		if(!entity.worldObj.blockExists(x, y, z)) return;

		TileEntity te = entity.worldObj.getTileEntity(x, y, z);
		if(!(te instanceof TileEntityWirelessStation)) return;

		TileEntityWirelessStation station = (TileEntityWirelessStation) te;
		// uid 对不上说明原座已经被拆过，新放的这台不认这个连接器
		if(!station.getUid().equals(uid)) return;

		station.chargePlayer((EntityPlayer) entity);
	}

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		super.addInformation(stack, player, list, advanced);

		NBTTagCompound tag = stack.stackTagCompound;

		if(tag == null || !tag.hasKey(KEY_UID)) {
			list.add(EnumChatFormatting.GRAY + "未配对：手持右键无线充电站");
			return;
		}

		String dim = tag.getInteger(KEY_DIM) == 0 ? "主世界" : "维度 " + tag.getInteger(KEY_DIM);
		list.add(EnumChatFormatting.AQUA + "已配对：" + EnumChatFormatting.GRAY + dim
			+ " (" + tag.getInteger(KEY_X) + ", " + tag.getInteger(KEY_Y) + ", "
			+ tag.getInteger(KEY_Z) + ")");
	}
}
