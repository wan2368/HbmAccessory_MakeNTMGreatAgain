package com.makentmgreatagain.items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.Multimap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.FoodStats;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * 塔可夫风格的食物与饮料。
 *
 * <h3>为什么需要单独一个类</h3>
 * <p>直接用 {@code ItemFood} 只能拿到 {@code common} 稀有度，也没法给瓶装饮料补回空瓶。
 * 这个类负责：稀有度、吃完施加效果、喝完还空瓶、风味描述、「也能当武器」的那几件，
 * 以及分次食用（一包五块方糖、一瓶三口酒）和进食时长。
 * 饥饿值、饱和度与音效仍然交给 {@code ItemFood}。</p>
 *
 * <h3>为什么效果要自己施加</h3>
 * <p>{@code ItemFood} 构造里的第三个参数只支持「狼能不能吃」，效果必须走
 * {@link #onFoodEaten}。1.7.10 的进食链路是 {@code EntityPlayer#onItemUseFinish}
 * → {@code ItemStack#onFoodEaten} → 仅当物品是 {@code ItemFood} 时才转调 {@code onEaten}，
 * 所以继承是必须的，不能靠 {@code onItemRightClick}。</p>
 *
 * <h3>近战伤害</h3>
 * <p>1.7.10 的武器伤害不是物品字段，而是加在实体 {@code attackDamage} 属性上的修饰符。
 * 空手基础值是 1，所以「造成 9 点伤害」对应修饰符 {@code +8}。这里用独立 UUID，
 * 不与原版剑共用——同一槽位只会应用一份，共用没有意义，独立反而不会和其他模组打架。</p>
 *
 * <h3>分次食用</h3>
 * <p>方糖一包五块、一瓶酒三口：这类物品用<b>耐久</b>记剩余次数，每吃一口磨损一格，
 * 而不是一次吃掉整件。实现上不改 {@code ItemFood} 的堆叠逻辑，而是在
 * {@link #onFoodEaten} 里把被扣掉的那一个补回来，改由 {@code itemDamage} 记消耗；
 * 只有磨损到上限的最后一口才让它真正消失。</p>
 *
 * <h3>酒为什么补饱和不补饥饿</h3>
 * <p>{@code FoodStats#addStats} 的饱和量是「饥饿值 × 系数 × 2」，饥饿值给 0 就一点饱和也补不上。
 * 所以酒走 {@link #extraSaturation} 单独调用 {@code setFoodSaturationLevel}，
 * 把原来那份口粮折成饱和——喝下去压得住饱腹感，但不当饭吃。</p>
 */
public class ItemTarkovFood extends ItemFood {

	/** 近战伤害修饰符的 UUID，取固定值以保证同一物品每次构造都一致。 */
	private static final UUID ATTACK_MODIFIER_ID =
		UUID.fromString("6f2a1c74-9d38-4e0b-8b5f-2c7d41e9a356");

	/** 吃完施加的效果，构造时一次性给定。 */
	private final List<PotionEffect> effects = new ArrayList<PotionEffect>();
	/** 瓶装饮料：喝完补一个空玻璃瓶。 */
	private boolean returnsBottle;
	/** 稀有度，默认常见。 */
	private EnumRarity rarity = EnumRarity.common;
	/** 风味描述的语言键，null 表示不显示（对应塔可夫里双击物品的那段文字）。 */
	private String descriptionKey;
	/** 近战伤害（含空手基础的 1 点），0 表示不是武器。 */
	private float attackDamage;
	/** 吃完即死的概率，0 表示不赌命。 */
	private float deathChance;
	/** 进食时长（tick）。{@code ItemFood} 的字段是 final 32，只能靠覆盖取值方法改。 */
	private int eatTicks = 32;
	/** 一件能吃几次；1 表示一次吃完。大于 1 时靠耐久记录剩余次数。 */
	private int uses = 1;
	/** 不占饥饿值、直接补的饱和度，用于酒。 */
	private float extraSaturation;

	public ItemTarkovFood(int heal, float saturation) {
		super(heal, saturation, false);
	}

	/** 追加一条食用效果。 */
	public ItemTarkovFood addEffect(PotionEffect effect) {
		this.effects.add(effect);
		return this;
	}

	/** 瓶装饮料：任何时候都能喝，喝完把空瓶还回去，动作也换成「喝」。 */
	public ItemTarkovFood drink() {
		this.returnsBottle = true;
		this.setAlwaysEdible();
		return this;
	}

	public ItemTarkovFood setRarity(EnumRarity rarity) {
		this.rarity = rarity;
		return this;
	}

	/** 风味描述（塔可夫的物品说明），参数是完整语言键。 */
	public ItemTarkovFood setDescription(String key) {
		this.descriptionKey = key;
		return this;
	}

	/** 当武器用：{@code damage} 是最终造成的伤害点数（空手算 1）。 */
	public ItemTarkovFood setAttackDamage(float damage) {
		this.attackDamage = damage;
		this.setFull3D();
		return this;
	}

	/** 吃完有 {@code chance} 的概率直接死亡（瞬间伤害 999）。 */
	public ItemTarkovFood setDeathChance(float chance) {
		this.deathChance = chance;
		return this;
	}

	/**
	 * 分次食用：{@code count} 口吃完。
	 *
	 * <p>耐久上限直接等于口数，第一口之后开始显示耐久条，读作「还剩几口」。
	 * {@code setMaxDamage} 会把堆叠上限压到 1，这正是想要的——一半的糖块没法叠。</p>
	 */
	public ItemTarkovFood setUses(int count) {
		this.uses = Math.max(1, count);
		if(this.uses > 1) {
			this.setMaxDamage(this.uses);
			this.setMaxStackSize(1);
		}
		return this;
	}

	/** 进食时长（tick）。原版食物是 32（1.6 秒）。 */
	public ItemTarkovFood setEatTicks(int ticks) {
		this.eatTicks = Math.max(1, ticks);
		return this;
	}

	/** 不占饥饿值、直接补的饱和度（酒用）。 */
	public ItemTarkovFood setExtraSaturation(float saturation) {
		this.extraSaturation = saturation;
		return this;
	}

	/** 覆盖父类那个写死的 32，让罐头能要 3 秒。 */
	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return this.eatTicks;
	}

	/** 喝酒用喝的动作，吃饭用吃的动作。 */
	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return this.returnsBottle ? EnumAction.drink : EnumAction.eat;
	}

	@Override
	public EnumRarity getRarity(ItemStack stack) {
		return this.rarity != null ? this.rarity : super.getRarity(stack);
	}

	/**
	 * 把近战伤害挂到 {@code attackDamage} 属性上。
	 *
	 * <p>1.7.10 只在手持槽应用这份修饰符，所以食物既是食物又是武器并不冲突：
	 * 右键是吃，左键是打。</p>
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Multimap getAttributeModifiers(ItemStack stack) {
		Multimap map = super.getAttributeModifiers(stack);

		if(this.attackDamage > 0F) {
			map.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(),
				new AttributeModifier(ATTACK_MODIFIER_ID, "Tarkov weapon modifier",
					this.attackDamage - 1.0D, 0));
		}

		return map;
	}

	/**
	 * 父类已经扣了物品、加了饥饿值并放了音效。
	 *
	 * <p>效果、爆炸、空瓶只在服务端处理。耐久磨损两端都做：客户端那次纯属预测——
	 * 否则客户端的 {@code stackSize} 被父类扣成 0 后会把物品从背包里摘掉，
	 * 要等下一 tick 服务端同步才回来，视觉上闪一下。</p>
	 *
	 * <p>顺序是先补饱和、再给效果、再判即死、最后补空瓶：万一这一口把玩家喝死了，
	 * 也不该让空瓶凭空出现在尸体背包里。</p>
	 */
	@Override
	public void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		if(this.uses > 1) {
			preserveStackedUses(stack, world, player);
		}

		if(!world.isRemote) {
			// 酒：饥饿值给 0，靠这里单独补饱和。上限受当前饥饿值约束，和原版 addStats 一致
			if(this.extraSaturation > 0F) {
				FoodStats stats = player.getFoodStats();
				stats.setFoodSaturationLevel(
					Math.min(stats.getSaturationLevel() + this.extraSaturation, (float) stats.getFoodLevel()));
			}

			for(PotionEffect effect : this.effects) {
				// 复制一份再施加：PotionEffect 由玩家实例持有，共用原对象会串状态
				player.addPotionEffect(new PotionEffect(effect));
			}

			// 私酒那一口：1% 直接把人送走，魔法伤害不吃护甲减免
			if(this.deathChance > 0F && world.rand.nextFloat() < this.deathChance) {
				player.attackEntityFrom(DamageSource.magic, 999F);
				wearOut(stack);
				return;
			}

			// 空瓶只在最后一口还：三口一瓶，否则一瓶酒能换三个瓶子
			if(this.returnsBottle && isLastUse(stack) && !player.capabilities.isCreativeMode) {
				player.inventory.addItemStackToInventory(new ItemStack(Items.glass_bottle));
			}
		}

		wearOut(stack);
	}

	/** 这一口是不是最后一口（一次吃完的物品恒为 true）。 */
	private boolean isLastUse(ItemStack stack) {
		return this.uses <= 1 || stack.getItemDamage() + 1 >= this.uses;
	}

	/**
	 * 把旧存档或命令生成的多次食用物堆叠拆成未使用的单件。
	 * ItemFood 已先扣掉正在食用的一件；该件留在原槽位并继续进入耐久逻辑。
	 */
	private void preserveStackedUses(ItemStack stack, World world, EntityPlayer player) {
		int extras = stack.stackSize - 1;
		if(extras <= 0) return;

		stack.stackSize = 1;
		if(world.isRemote) return;

		for(int i = 0; i < extras; i++) {
			ItemStack fresh = new ItemStack(this);
			if(!player.inventory.addItemStackToInventory(fresh)) {
				player.dropPlayerItemWithRandomChoice(fresh, false);
			}
		}
	}

	/**
	 * 分次食用：把这一口从「数量」挪到「耐久」上.
	 *
	 * <p>{@code ItemFood#onEaten} 在调用 {@link #onFoodEaten} 之前已经把这摞扣掉了 1
	 * （字节码偏移 0~7，之后不再碰 {@code stackSize}）。这里把 {@code stackSize} 补回 1，
	 * 改由 {@code itemDamage} 记消耗，直到最后一口才让它停在 0——
	 * {@code EntityPlayer#onItemUseFinish} 随后会把该槽位置空。</p>
	 */
	private void wearOut(ItemStack stack) {
		if(this.uses <= 1) return;

		int damage = stack.getItemDamage() + 1;
		if(damage >= this.uses) {
			stack.stackSize = 0;
			return;
		}

		stack.stackSize = 1;
		stack.setItemDamage(damage);
	}

	/**
	 * tooltip 就是塔可夫里双击物品看到的那段说明，原样照抄官方文本，灰字。
	 *
	 * <p>塔可夫的描述很长（炼乳那条上百字），直接用 {@code list.add} 会变成一条横穿屏幕的长线，
	 * 所以按像素宽度折行。原版 tooltip 没有自动换行，只能自己算。</p>
	 *
	 * <p>不列具体数值：效果在物品栏里本来就有图标，写出来反而像说明书。</p>
	 */
	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		if(this.descriptionKey == null) return;

		String text = StatCollector.translateToLocal(this.descriptionKey);
		// 键不存在时 translateToLocal 会把键原样吐回来，这种情况直接不显示
		if(text == null || text.isEmpty() || text.equals(this.descriptionKey)) return;

		wrapInto(text, 176, list);
	}

	/**
	 * 按像素宽度折行。
	 *
	 * <p>中文没有空格，任一处都能断；英文回溯到最近的空格，避免把单词劈成两半——
	 * 但如果整行大部分都是同一个长单词，就硬断，否则会溢出。</p>
	 */
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	private static void wrapInto(String text, int maxWidth, List list) {
		FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
		StringBuilder line = new StringBuilder();
		int width = 0;
		int lastSpace = -1;

		for(int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int cw = fr.getCharWidth(c);

			if(width + cw > maxWidth && line.length() > 0) {
				if(lastSpace > line.length() / 2) {
					String rest = line.substring(lastSpace + 1);
					list.add(EnumChatFormatting.GRAY + line.substring(0, lastSpace));
					line.setLength(0);
					line.append(rest);
					width = fr.getStringWidth(rest);
				} else {
					list.add(EnumChatFormatting.GRAY + line.toString());
					line.setLength(0);
					width = 0;
				}
				lastSpace = -1;
			}

			line.append(c);
			width += cw;
			if(c == ' ') lastSpace = line.length() - 1;
		}

		if(line.length() > 0) {
			list.add(EnumChatFormatting.GRAY + line.toString());
		}
	}
}
