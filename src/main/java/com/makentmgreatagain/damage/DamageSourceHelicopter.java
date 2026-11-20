package com.makentmgreatagain.damage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;

/**
 * 耐摔合金砸落（「牢大肘击」）的伤害来源。
 *
 * <p>必须自己造一个：原版死亡播报不是伤害代码打印的，而是 {@code EntityPlayerMP.onDeath}
 * 从 {@code CombatTracker} 取<b>最后一条伤害记录</b>再调 {@code getDeathMessage}。
 * 想让文案自定义，换 source 比换整段死亡流程便宜得多 —— 把 {@link #INSTANCE} 传进去即可。</p>
 *
 * <p>{@link #func_151519_b(EntityLivingBase)} 自己写单参数版本：父类会先试
 * {@code death.attack.<type>.player} 键，命中就切「A 被 B 杀死」的两参数版；而这里的
 * 「凶手」是掉落物，玩家身上往往还挂着上一个攻击者，别的模组一旦补了这个键文案就会错乱。</p>
 *
 * <p>两个 flag：{@code setDamageBypassesArmor()}（护甲不参与）、
 * {@code setDamageAllowedInCreativeMode()}（创造模式也砸死）。注意真正杀人的是
 * {@code EntityItemHelicopter#smite}：直接把血量置 0 再走 {@code onDeath}，
 * 护甲、抗性、无敌帧都不参与，这里的 flag 只影响日志/统计口径。</p>
 */
public class DamageSourceHelicopter extends DamageSource {

    /** 全局单例，无状态。 */
    public static final DamageSourceHelicopter INSTANCE = new DamageSourceHelicopter();

    private DamageSourceHelicopter() {
        super("mnga.helicopter");
        this.setDamageBypassesArmor();
        this.setDamageAllowedInCreativeMode();
    }

    /**
     * 死亡播报：{@code %1$s} 是死者的显示名，文案在 lang 里
     * （{@code death.attack.mnga.helicopter}）。
     */
    @Override
    public IChatComponent func_151519_b(EntityLivingBase entity) {
        return new ChatComponentTranslation("death.attack.mnga.helicopter", entity.func_145748_c_());
    }
}
