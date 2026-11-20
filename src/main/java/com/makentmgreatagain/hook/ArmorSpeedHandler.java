package com.makentmgreatagain.hook;

import java.util.UUID;

import com.hbm.items.armor.ArmorFSB;
import com.makentmgreatagain.items.ItemArmorAlloyReborn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 全套移动速度加成（加速或减速）的统一处理器。
 *
 * <h2>为什么需要这个类</h2>
 * HBM 的 {@code ArmorFSB} 没有移动速度字段，17 条属性里没有一条改得了速度；
 * 移动速度是实体属性 {@code SharedMonsterAttributes.movementSpeed}（玩家基础值 0.1），
 * 只能挂 {@link AttributeModifier}。
 *
 * <p>而且“全套才生效”这件事不能写在 {@code onArmorTick} 里：那个方法只在你穿着那一件时
 * 被调用，胸甲一脱就不再执行，上一次挂的修饰符会永远留在玩家身上（HBM 的 envsuit_plate
 * 就有这个问题）。这里改成每 tick 对每个玩家算一次“现在应该加多少”，不一致就重设，
 * 于是脱甲、死亡、换维度、掉线重连全部自愈。
 *
 * <h2>数值约定</h2>
 * <ul>
 *   <li>{@code 0.10} → operation 2 → 总速度 ×1.10（+10%）</li>
 *   <li>{@code -0.10} → operation 2 → 总速度 ×0.90（-10%）</li>
 * </ul>
 * operation 2 是“乘总值”，与敏捷药水、冲刺等加成叠乘，不会因为基础值变化而失真；
 * 若想要 HBM 减速甲那种“每件 -10% 基础”的效果，则应改用 operation 1。
 */
public class ArmorSpeedHandler {

    /**
     * 固定 UUID。★ 绝对不能随手 UUID.randomUUID()：
     * 那样每次重设都会视作一条新的修饰符，越挂越多，速度会离谱。
     */
    private static final UUID SET_SPEED_UUID = UUID.fromString("7c3f2b1e-5a44-4c07-9a5e-1d2f8b6c0913");

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Phase.START 时属性表还没结算，放 END 更稳
        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayer player = event.player;
        if (player == null || player.worldObj == null) return;
        // 只在服务端算：AttributeModifier 会经 S20 同步给客户端，双端各算一遍是纯浪费
        if (player.worldObj.isRemote) return;

        double target = 0D;

        // 只认“四件齐全（同一材质）+ 胸甲是本模组的护甲”这一种情况
        if (ArmorFSB.hasFSBArmor(player)) {
            ItemStack chest = player.inventory.armorInventory[2];
            if (chest != null && chest.getItem() instanceof ItemArmorAlloyReborn) {
                target = ((ItemArmorAlloyReborn) chest.getItem()).getSetSpeed();
            }
        }

        IAttributeInstance attr = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (attr == null) return;

        AttributeModifier current = attr.getModifier(SET_SPEED_UUID);

        // 目标是 0：摘掉残留的修饰符就结束
        if (target == 0D) {
            if (current != null) attr.removeModifier(current);
            return;
        }

        // 已经挂着且数值一致：什么都不用做（绝大多数 tick 走这条分支）
        if (current != null && current.getAmount() == target) return;

        // 数值变了（换了套）或首次穿上：先摘后挂
        if (current != null) attr.removeModifier(current);
        attr.applyModifier(new AttributeModifier(SET_SPEED_UUID, "MNGA full set speed", target, 2));
    }
}
