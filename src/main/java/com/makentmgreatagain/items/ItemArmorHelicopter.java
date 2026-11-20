package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.makentmgreatagain.entity.EntityItemHelicopter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * 耐摔合金护甲四件专用物品类。
 *
 * <p>在通用 {@link ItemArmorAlloyReborn} 之上做两件事：</p>
 * <ol>
 *   <li>按 HBM 的 {@code ArmorFSB.addInformation} 写法追加本套装自定义的战斗规则说明。免伤、防火、
 *       免负面、辐射全免、摔死音效、空手震撼这些逻辑都在 {@code hook.HelicopterArmorHandler} 里，
 *       HBM 自带的 addInformation 不认识它们，所以在这里照它的格式补上；</li>
 *   <li>把耐久锁死在 {@link #DURABILITY}，并且<b>永远不掉</b>（见下）。</li>
 * </ol>
 *
 * <h2>为什么「不掉耐久」只能靠每 tick 擦回 0</h2>
 * <p>1.7.10 里护甲掉耐久只有一条路：{@code EntityPlayer.damageArmor()} →
 * {@code ItemStack.damageItem()}。链路末端在 {@code ItemStack} 上，不是 {@code Item} 的方法，
 * 无法重写；{@code ItemStack.damageItem} 是把 {@code itemDamage} 加满到 {@code getMaxDamage()}
 * 就把栈删掉，也没有 Forge 事件可拦。所以做法是：{@link #onArmorTick}（穿着时每 tick）与
 * {@link #onUpdate}（在背包里每 tick）把 {@code itemDamage} 擦回 0。</p>
 *
 * <p>实际效果比字面上更好：全套穿着时 {@code HelicopterArmorHandler} 取消除摔落外的所有
 * {@code LivingAttackEvent}，而摔落源 {@code DamageSource.fall} 带
 * {@code setDamageBypassesArmor()}，{@code EntityLivingBase.applyArmorCalculations} 会整个跳过
 * {@code damageArmor} —— 也就是说这套甲本来就没有任何路径能扣耐久，擦 0 只是兜底。</p>
 */
public class ItemArmorHelicopter extends ItemArmorAlloyReborn {

    /**
     * 四件的耐久上限。{@code ModItems} 里四件统一用这个常量，不要各写一份数字。
     *
     * <p>因为永不掉耐久，这个数只影响 tooltip / NEI 里显示的上限，以及「耐久条会不会出现」
     * （原版只在 itemDamage &gt; 0 时才画耐久条，所以这套甲永远不显示耐久条）。</p>
     */
    public static final int DURABILITY = 24;

    public ItemArmorHelicopter(ArmorMaterial material, int slot, String armorTexture) {
        super(material, slot, armorTexture);
    }

    /**
     * 护甲四件<b>不走</b>耐摔系列的升空演出，掉出来就是普通掉落物。
     *
     * <p>系列里锭/部件（{@link ItemHelicopterDrop}）、工具（{@link ItemToolAbilityHelicopter}）、
     * 方块（{@link ItemBlockHelicopter}）都会自旋升空再砸下来，落地处决落点玩家并销毁物品。
     * 护甲必须豁免，原因有两个：</p>
     * <ol>
     *   <li><b>会永久丢装备</b>：这套甲唯一的死法是摔死，而摔死正好会把护甲掉出来
     *       （不开死亡不掉落时）。演出一落地就把甲销毁，等于「摔一次丢一套甲」。</li>
     *   <li><b>会二次砸死自己</b>：护甲掉在尸体脚下，玩家重生后回到原地，
     *       正好站在落点 1 格内 —— 刚复活就被自己的甲砸死，无限循环。</li>
     * </ol>
     *
     * <p>仍然保留自定义实体（{@code hasCustomEntity} 为 true）只是为了让护甲和系列
     * 共用一套渲染与拾取行为，但用 {@code setLiftEnabled(false)} 把整套演出关掉：
     * 不飞、不砸、不碎，可以正常捡起。这也顺带消除了「上升段被地面顶住」时
     * 客户端与服务端位置互相拉扯造成的抽搐。</p>
     */
    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity location, ItemStack stack) {
        return EntityItemHelicopter.fromDropped(world, location, stack).setLiftEnabled(false);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
        // 先让 HBM 打出标题 + 效果名（急迫/力量）+ 硬着陆
        super.addInformation(stack, player, list, bool);

        // 本套自定义规则，照 HBM 的 resolveKey + 颜色写法续在后面
        list.add(EnumChatFormatting.RED + "  " + I18nUtil.resolveKey("mnga.armor.heli.nodamage"));
        list.add(EnumChatFormatting.RED + "  " + I18nUtil.resolveKey("mnga.armor.heli.fireproof"));
        list.add(EnumChatFormatting.LIGHT_PURPLE + "  " + I18nUtil.resolveKey("mnga.armor.heli.no_debuff"));
        list.add(EnumChatFormatting.GOLD + "  " + I18nUtil.resolveKey("mnga.armor.heli.no_rad"));
        list.add(EnumChatFormatting.AQUA + "  " + I18nUtil.resolveKey("mnga.armor.heli.stun_punch"));
        list.add(EnumChatFormatting.DARK_GRAY + "  " + I18nUtil.resolveKey("mnga.armor.heli.manout"));
    }

    /**
     * 穿着时每 tick 把耐久擦回满值 —— 「任何情况都不消耗耐久」的兜底实现，见类注释。
     *
     * <p>放在 {@code onArmorTick} 而不是 {@code PlayerTickEvent}：原版
     * {@code EntityPlayer.onUpdate} 对四个护甲槽是无条件调用 {@code onArmorTick} 的，
     * 单件穿着也会跑到；不需要另外注册 handler，也不会和 {@code HelicopterArmorHandler}
     * 的「全套判定」耦合（否则只穿一件时就没人擦耐久了）。</p>
     */
    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
        keepPristine(stack);
        super.onArmorTick(world, player, stack);
    }

    /** 放在背包 / 容器里被别的模组直接调 {@code damageItem} 时同样压回去。 */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        keepPristine(stack);
        super.onUpdate(stack, world, entity, slot, held);
    }

    /**
     * 把 itemDamage 归零。
     *
     * <p>只在 &gt; 0 时才写，避免每 tick 都触发一次「数据变更」标记；
     * 原版也只在 itemDamage &gt; 0 时绘制耐久条，所以这套甲永远不显示耐久条。</p>
     */
    private static void keepPristine(ItemStack stack) {
        if(stack != null && stack.getItemDamage() != 0) stack.setItemDamage(0);
    }
}
