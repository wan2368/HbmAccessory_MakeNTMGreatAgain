package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.extprop.HbmLivingProps;
import com.makentmgreatagain.ModCreativeTabs;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * 丰饶之角：只能从战利品中极低概率开出的遗物级食物。
 *
 * <p>仿照 HBM 原版高阶食物（如 {@code loop_stew} / {@code bottle_quantum}）的风格：
 * <ul>
 *   <li>无限使用不消耗（无冷却）；</li>
 *   <li>瞬间回血 + 生命恢复 II + 伤害吸收 II + 抗性提升 I + 速度 I；</li>
 *   <li>清除最多 150 RAD 辐射。</li>
 * </ul></p>
 */
public class ItemHornOfPlenty extends ItemFood {

	public ItemHornOfPlenty() {
		super(8, 1.2F, false);
		this.setMaxStackSize(1);
		this.setAlwaysEdible();
		this.setCreativeTab(ModCreativeTabs.TAB_FOOD);
		this.setUnlocalizedName("horn_of_plenty");
		this.setTextureName("makentmgreatagain:horn_of_plenty");
	}

	@Override
	public EnumRarity getRarity(ItemStack stack) {
		return EnumRarity.epic;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack, int pass) {
		return true;
	}

	@Override
	public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
		// 永久不消耗：跳过 super.onEaten 对 stackSize 的扣减
		player.getFoodStats().func_151686_a(this, stack);
		world.playSoundAtEntity(player, "random.burp", 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
		this.onFoodEaten(stack, world, player);
		return stack;
	}

	@Override
	protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		if(world.isRemote) return;

		player.heal(8.0F);
		player.addPotionEffect(new PotionEffect(Potion.regeneration.id, 10 * 20, 1));
		player.addPotionEffect(new PotionEffect(Potion.field_76444_x.id, 30 * 20, 1));
		player.addPotionEffect(new PotionEffect(Potion.resistance.id, 20 * 20, 0));
		player.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 20 * 20, 0));

		float rad = HbmLivingProps.getRadiation(player);
		if(rad > 0F) {
			HbmLivingProps.incrementRadiation(player, -Math.min(150.0F, rad));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		String raw = StatCollector.translateToLocal("item.horn_of_plenty.desc");
		if(raw != null && !raw.equals("item.horn_of_plenty.desc")) {
			for(String line : raw.split("\\$")) {
				list.add(line);
			}
		}
	}
}
