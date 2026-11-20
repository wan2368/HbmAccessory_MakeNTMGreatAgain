package com.makentmgreatagain.hook;

import java.lang.reflect.Field;
import java.util.Collection;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.items.armor.ArmorFSB;
import com.hbm.potion.HbmPotion;
import com.hbm.lib.ModDamageSource;
import com.makentmgreatagain.init.MNGAMaterials;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/**
 * 耐摔合金全套的战斗规则：免伤（摔落除外）、防火、摔死音效、空手震撼。
 *
 * <h2>判定口径</h2>
 * "全套"沿用 HBM 的 {@code ArmorFSB.hasFSBArmor}（四件同材质实例才算），
 * 材质再比对 {@link MNGAMaterials#ARMOR_HELICOPTER}，碳化钨套不会误触发。
 *
 * <h2>为什么用 LivingAttackEvent 而不是 LivingHurtEvent</h2>
 * 需求是"不受到伤害效果，也不掉血"。Hurt 阶段取消只保住血量，击退、
 * 着火、HBM 的 DT 特效在 Attack 阶段已经跑完了；Attack 事件在最前面，
 * 取消等于这次攻击整个不存在。摔落（DamageSource.fall）原样放行，
 * 摔伤按不穿甲结算——原版护甲点本来就不减摔落伤害，所以"减免为0"
 * 不需要额外补偿。
 *
 * <h2>防火说明</h2>
 * 火焰伤害（inFire / onFire / lava）已经走上面的全源取消免掉了；
 * 但"身上着火"是 {@code Entity.fire} 计数（一个状态，不是伤害），事件拦不住，
 * 所以每 tick 把它压到 0。直接反射写 private 的 fire 字段（静默、无"滋滋"音效），
 * 站岩浆里也不会持续冒火苗；取不到字段才退化成 {@code extinguish()}。
 * 火焰相关的副作用（HBM 烧伤状态、点燃效果）由状态清零自然带掉。
 *
 * <h2>秒杀说明</h2>
 * /kill、创造删除这类不走伤害管线的死亡本处理器拦不了；void、岩浆等
 * 走 DamageSource 的"秒杀伤害"会被免掉，这是需求里"只免疫伤害"的字面结果。
 */
public class HelicopterArmorHandler {

    /** 1.7.10 的 Entity.fire 是 private，直接写才能不留熄灭音效地把燃烧清零。 */
    private static final Field FIELD_FIRE = findFireField();

    private static Field findFireField() {
        for (String name : new String[] { "fire", "field_70151_ae" }) {
            try {
                Field field = net.minecraft.entity.Entity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    /**
     * 玩家是否穿着耐摔合金全套。
     *
     * <p>先只读胸甲一件比对材质：没穿这套（或没穿胸甲）的玩家当场返回，
     * 不进 {@code hasFSBArmor} 的四格循环。防火段是每 tick 对每个玩家都跑的，
     * 这个前置判断把"绝大多数无套装 tick"的开销压到两三次数组读+instanceof。</p>
     */
    static boolean wearsHelicopterSet(EntityPlayer player) {
        if (player == null || player.inventory == null) return false;
        ItemStack chest = player.inventory.armorInventory[2];
        if (chest == null) return false;
        if (!(chest.getItem() instanceof ArmorFSB)) return false;
        if (((ArmorFSB) chest.getItem()).getArmorMaterial() != MNGAMaterials.ARMOR_HELICOPTER) return false;
        return ArmorFSB.hasFSBArmor(player);   // 材质对了才核四件齐全
    }

    /**
     * 供成就判定用的公开入口：是否穿着耐摔合金全套。
     *
     * <p>{@link #wearsHelicopterSet} 是包私有的（只给本包的守卫闸门用），
     * 成就处理器在 {@code achievement} 包里，跨包访问不到，所以转发一层。</p>
     */
    public static boolean wearsFullSetForAchievement(EntityPlayer player) {
        return wearsHelicopterSet(player);
    }

    /**
     * 除摔落外的一切伤害来源取消：不掉血、无受击效果，并把这次攻击原路打回攻击者。
     *
     * <p>用 {@code HIGHEST} 优先级抢在别的模组之前取消，避免它们先把伤害算完。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (!wearsHelicopterSet(player)) return;

        if (isFall(event.source)) {
            // 摔落照常，按不穿甲结算；打标记让字节码闸门放行这一次掉血
            HelicopterGuardHook.markFallPass(player);
            return;
        }

        event.setCanceled(true);
        reflectAttack(player, event.source);
    }

    /**
     * 把这次攻击原路返还给攻击者。
     *
     * <p><b>秒杀原样返还</b>：HBM 的 digamma / radiation 处决、以及别的模组的
     * "一击必杀"往往是直接 {@code setHealth(0)}。这里对攻击者复刻同一手法——
     * 先按原伤害来源打一次（保留击退、着火等表现），若这次伤害属于秒杀级
     * （不可格挡的 outOfWorld / magic / 高倍伤害），再直接把攻击者血量清零并
     * 触发死亡，等价于把对方的秒杀原路送回它自己身上。</p>
     *
     * <p>攻击者就是自己时不反弹，避免自伤；攻击者也穿本套装时同样不反弹，
     * 否则两个穿套装的玩家互殴会陷入反弹递归。</p>
     */
    private static void reflectAttack(EntityPlayer victim, DamageSource source) {
        if (source == null) return;

        // 优先取直接攻击者（箭矢取射手），拿不到再取伤害实体（ TNT 取点燃者）
        Entity attacker = source.getSourceOfDamage();
        if (attacker == null) attacker = source.getEntity();
        if (!(attacker instanceof EntityLivingBase)) return;
        EntityLivingBase target = (EntityLivingBase) attacker;
        if (target == victim) return;                                   // 自伤不反弹
        if (target.worldObj == null || target.worldObj.isRemote) return; // 只在服务端结算
        if (target instanceof EntityPlayer && wearsHelicopterSet((EntityPlayer) target)) return;
        if (target.isDead || target.getHealth() <= 0.0F) return;

        // 反弹递归保护：反弹造成的伤害不会再触发一次反弹
        if (REFLECTING.get() == Boolean.TRUE) return;
        REFLECTING.set(Boolean.TRUE);
        try {
            // 秒杀级：不可格挡的虚空/魔法，以及 HBM 的 digamma / radiation 处决。
            // 这类伤害原本就是"一下带走"，返还时也照原样一下带走。
            boolean lethal = source.isUnblockable()
                    || source == DamageSource.outOfWorld
                    || source == DamageSource.magic
                    || "digamma".equals(source.damageType)
                    || "radiation".equals(source.damageType);

            // 普通攻击按对方血量上限的一击返还（够痛但不无脑秒杀）；
            // 秒杀级直接按其上限给，确保"原路返还"同样致命。
            float amount = lethal ? target.getMaxHealth() : Math.max(1.0F, target.getMaxHealth());

            // 用同一条伤害来源打回去，保留对方原本的属性（穿甲、魔法等）
            target.attackEntityFrom(source, amount);

            if (lethal && !target.isDead && target.getHealth() > 0.0F) {
                // 对方免疫伤害管线（穿甲/免伤）时，直接复刻秒杀手法
                target.setHealth(0.0F);
                target.onDeath(source);
            }
        } catch (Throwable ignored) {
            // 反弹失败绝不影响受害者本身的免伤
        } finally {
            REFLECTING.remove();
        }
    }

    /** 反弹递归保护标记（同线程内有效）。 */
    private static final ThreadLocal<Boolean> REFLECTING = new ThreadLocal<Boolean>();

    /** 摔落判定：比实例 + 比 damageType（别的模组可能自造同类型 source）。 */
    private static boolean isFall(DamageSource source) {
        if (source == DamageSource.fall) return true;
        return source != null && "fall".equals(source.damageType);
    }

    /**
     * 免伤兜底：万一有模组在更低优先级把 Attack 事件的取消状态改回去，
     * 或者绕过 Attack 直接发 Hurt，这里再把伤害清零。摔落原样放行。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        if (!wearsHelicopterSet(player)) return;
        if (isFall(event.source)) return;
        event.ammount = 0.0F;
        event.setCanceled(true);
    }

    /** 全套穿着者每 tick 压灭燃烧状态（含岩浆、火焰方块、燃烧弹点燃）。 */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;               // 在实体逻辑前压，燃烧特效不闪
        if (event.player == null) return;
        EntityPlayer player = event.player;
        if (player.worldObj == null || player.worldObj.isRemote) return; // 服务端权威，清零会同步下去

        // 死亡流程进行中（deathTime > 0）或已被标死：整套兜底全部让路。
        // 原版死亡动画要跑满 20 tick 才会移除实体、弹重生界面，这期间任何
        // 「拉血 / 撤销 isDead / 补护甲」都会把流程打断，玩家卡成倾斜的红色尸体。
        // 护甲兜底尤其危险：不开死亡不掉落时 onDeath 已经把甲掉成物品实体，
        // 这里再补回槽里就等于凭空复制一套。
        // 四条判据都要看：原版 onDeath 只置 dead=true，deathTime 下一 tick 才自增、
        // isDead 要等 20 tick 后 setDead 才置位。只看后两条会漏掉「刚死那一 tick」，
        // 护甲兜底把刚被 dropAllItems 清空的槽补回去 → 摔一次复制一套甲。
        boolean dying = HelicopterGuardRules.isDeathInProgress(
                player.getHealth(), player.dead, player.isDead, player.deathTime);
        if (dying) {
            ARMOR_SNAPSHOT.remove(player.getUniqueID());
            return;
        }

        // 护甲完整性兜底必须在全套判定之前：被扒掉一件后「全套」已不成立，
        // 放在后面会直接 return，被扒的甲永远补不回来。
        restoreStrippedArmor(player);

        if (!wearsHelicopterSet(player)) return;

        if (FIELD_FIRE != null) {
            try {
                if (FIELD_FIRE.getInt(player) > 0) FIELD_FIRE.setInt(player, 0);
            } catch (Throwable ignored) { }
        } else if (player.isBurning()) {
            player.extinguish();
        }

        purgeNegativeEffects(player);

        // 辐射三值每 tick 清零（2026-11-09 实测推翻 40 tick 方案）：
        // 高中子环境里 digamma 每 tick 直灌 +0.1~0.2，40 tick 窗口内就能冲到 9.7 ——
        // 上限按 0.5^digamma 掉到半颗心，跨过 10 时 HBM 直接 setHealth(0)+onDeath
        // 显式击杀（不走伤害管线，免伤事件拦不住）；且复活后残留的 maxHealth 修饰符
        // 会把血量 clamp 回 0 → 二次死亡循环 → 卡重生界面。日志里 digamma 清零采样
        // 后半段失控正是这个链条。radiation/radBuf 一起每 tick 清：都是数组读，开销可忽略。
        if (HbmLivingProps.getRadiation(player) > 0.0F) HbmLivingProps.setRadiation(player, 0.0F);
        if (HbmLivingProps.getRadBuf(player) > 0.0F) HbmLivingProps.setRadBuf(player, 0.0F);
        if (HbmLivingProps.getDigamma(player) > 0.0F) HbmLivingProps.setDigamma(player, 0.0F);

        // 血量兜底：反射 / Unsafe 直接写 health 字段不经过任何方法，字节码闸门
        // 无从下手，只能在这里发现掉血就拉回。
        // 摔落例外：放行标记还在窗口内就不拉回，让摔伤正常结算，
        // 摔死也照常发生 —— 套装设定是「只摔得死」。
        boolean fallPass = HelicopterGuardHook.hasFallPass(player);
        if (HelicopterGuardRules.shouldTopUpHealth(player.getHealth(), player.getMaxHealth(),
                fallPass, true, player.deathTime)) {
            player.setHealth(player.getMaxHealth());
        }

        // 强制标死撤销：别的模组直接写 isDead = true 不经过任何方法（字节码闸门
        // 拦不到字段写），只能在这里发现后撤销。摔死不撤、死亡动画期间也不撤，
        // 交给原版死亡流程走完 —— 否则 deathTime 被清零，永远到不了第 20 tick。
        if (player.isDead && HelicopterGuardRules.shouldRevokeDeadFlag(
                player.getHealth(), fallPass, true, player.deathTime)) {
            player.isDead = false;
            player.deathTime = 0;
        }
    }

    /**
     * 护甲快照：记录上一 tick 四个槽里的耐摔合金件，用于发现「被直接写数组扒掉」。
     *
     * <p>只存本套装的件（别的护甲不干预），按玩家 UUID 索引；玩家退出时由
     * {@code PlayerLoggedOutEvent} 清理，避免长期累积。</p>
     */
    private static final java.util.Map<java.util.UUID, ItemStack[]> ARMOR_SNAPSHOT =
            new java.util.concurrent.ConcurrentHashMap<java.util.UUID, ItemStack[]>();

    /**
     * 护甲完整性兜底：非玩家自身操作导致的护甲丢失，原样补回。
     *
     * <p>与字节码闸门互补——闸门拦 {@code setCurrentItemOrArmor}，这里补
     * 「直接写 {@code armorInventory} 数组 / 反射清槽」那条绕过方法的路径。</p>
     *
     * <p>玩家自己脱甲时闸门放行了写入，快照会同步更新成新状态，所以不会把
     * 玩家主动脱下的甲又硬塞回去。</p>
     */
    private static void restoreStrippedArmor(EntityPlayer player) {
        ItemStack[] armor = player.inventory.armorInventory;
        java.util.UUID id = player.getUniqueID();
        ItemStack[] snapshot = ARMOR_SNAPSHOT.get(id);

        if (snapshot == null) {
            snapshot = new ItemStack[4];
            ARMOR_SNAPSHOT.put(id, snapshot);
        }

        // 玩家自己正在操作护甲：只更新快照，不补回（否则脱甲会被硬塞回去）。
        //
        // 判据有三条，任一成立就算「玩家自身操作」：
        //   1) 字节码临界区标记（服务端交互包 processClickWindow 内）；
        //   2) 玩家此刻开着容器界面 —— 自己脱甲必然在背包/容器界面里点，
        //      而模组扒甲是后台直接改槽，不会让玩家开着界面；
        //   3) 背包里能找到同款护甲 —— 说明是玩家脱下来放进背包的，不是被销毁。
        // 缺了第 2、3 条，玩家自己脱甲会被当成「被扒」硬塞回去（实测缺陷）。
        boolean selfEdit = HelicopterGuardHook.isSelfArmorEdit()
                || player.openContainer != null
                || armorMovedToInventory(player, snapshot);

        for (int i = 0; i < 4; i++) {
            ItemStack now = armor[i];
            ItemStack before = snapshot[i];

            if (selfEdit) {
                snapshot[i] = now == null ? null : now.copy();
                continue;
            }

            // 上一 tick 是本套装、这一 tick 没了或被换成别的东西 → 判定为被扒，补回
            if (isHelicopterPiece(before) && !sameStack(before, now)) {
                armor[i] = before.copy();
                player.inventory.markDirty();
                continue;
            }

            snapshot[i] = now == null ? null : now.copy();
        }
    }

    /**
     * 背包里是不是有「上一 tick 还穿在身上、这一 tick 不在身上」的同款护甲。
     *
     * <p>玩家自己脱甲会把甲放进背包（或直接掉落），模组扒甲一般是销毁或塞进它自己的容器。
     * 这条判据让「玩家脱甲」不被误判成「被扒」。</p>
     */
    private static boolean armorMovedToInventory(EntityPlayer player, ItemStack[] snapshot) {
        ItemStack[] main = player.inventory.mainInventory;
        if (main == null) return false;
        for (int s = 0; s < 4; s++) {
            ItemStack before = snapshot[s];
            if (!isHelicopterPiece(before)) continue;
            if (sameStack(before, player.inventory.armorInventory[s])) continue;  // 还穿着，不算
            for (ItemStack slot : main) {
                if (sameStack(before, slot)) return true;   // 脱下来进背包了
            }
        }
        return false;
    }

    /** 这个 stack 是不是耐摔合金护甲件。 */
    private static boolean isHelicopterPiece(ItemStack stack) {
        if (stack == null) return false;
        if (!(stack.getItem() instanceof ArmorFSB)) return false;
        return ((ArmorFSB) stack.getItem()).getArmorMaterial() == MNGAMaterials.ARMOR_HELICOPTER;
    }

    /** 两个 stack 是否同一件（同物品同耐久即可，护甲不看 NBT 差异）。 */
    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage();
    }

    /** 玩家退出时清掉护甲快照，避免 UUID 表长期累积。 */
    @SubscribeEvent
    public void onLogout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) ARMOR_SNAPSHOT.remove(event.player.getUniqueID());
    }

    /**
     * 清除一切负面效果（原版 + HBM 的全部药水）。
     *
     * <p>负面与否的判定直接用 HBM 自己的 {@code HbmPotion.getIsBadEffect}
     * （内部就是读 Potion 的 isBadEffect 字段），所以凡是 HBM 标成"坏"的效果——
     * 辐射病、磷酸灼伤、抽搐、沉重、恶心之类——和原版中毒凋零等同一路子全清。</p>
     *
     * <p>本套装自己给的急迫/力量不是负面效果，不会被误删；辐射本身不是药水，
     * 由头盔 {@code setRadResist} 的大系数在源头掐掉（见 ModItems 注册处）。</p>
     */
    private static void purgeNegativeEffects(EntityPlayer player) {
        Collection<?> active = player.getActivePotionEffects();
        if (active.isEmpty()) return;   // 没挂任何药水（绝大多数 tick）直接返回，不分配快照
        // getPotionEffects 会并发修改，先快照再删
        Collection<?> snapshot = new java.util.ArrayList(active);
        for (Object obj : snapshot) {
            PotionEffect effect = (PotionEffect) obj;
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion == null) continue;
            if (HbmPotion.getIsBadEffect(potion)) {
                player.removePotionEffect(potion.id);
            }
        }
    }

    /**
     * 死亡拦截 + 摔死音效。
     *
     * <p>穿全套时，除摔落外的一切死亡一律取消并当场把血量拉满 —— 包括 /kill、
     * 虚空、HBM 的 digamma / radiation 处决。字节码闸门已经在 {@code onDeath}
     * 入口挡过一层，这里是事件层的第二道保险（有些模组绕过 onDeath 直接发事件）。</p>
     *
     * <p>摔死照常放行并播 MAN OUT!：套装的设定就是「只摔得死」。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entityLiving;

        if (wearsHelicopterSet(player)) {
            if (isFall(event.source)) {
                player.worldObj.playSoundAtEntity(player, "makentmgreatagain:manout", 1.0F, 1.0F);
            } else {
                // 强制拦截：取消死亡并把血量拉满，避免留下 0 血的活死人
                event.setCanceled(true);
                player.isDead = false;
                player.deathTime = 0;
                player.setHealth(player.getMaxHealth());
                player.extinguish();
                return;
            }
        }
        // digamma 处决的死后残留兜底：HBM 死亡/复活不清 digamma 字段和它那条
        // maxHealth 负修饰符，复活瞬间上限仍≈0.1 → 血量 clamp 回 0 → 反复暴毙、
        // 卡重生界面（2026-11-09 latest.log 实测：digamma 锯齿爬到 9.7 后暴毙退出）。
        // 判全套太苛刻（暴毙时可能来不及检查），所以只要穿着任意一件本套部件就清——
        // 清自己来源的状态，不碰没穿这套的玩家。
        if (event.source == ModDamageSource.digamma && wearsAnyHelicopterPiece(player)) {
            HbmLivingProps.setDigamma(player, 0.0F);
        }
    }

    /**
     * 复活保险：穿任意一件本套部件的玩家，若身上还残留 digamma 状态（死后没清干净的
     * 历史包袱），复活落地瞬间清零并补满血，打断"复活→上限≈0→clamp 死→再复活"循环。
     */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        EntityPlayer player = event.player;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (!wearsAnyHelicopterPiece(player)) return;
        if (HbmLivingProps.getDigamma(player) > 0.0F) HbmLivingProps.setDigamma(player, 0.0F);
        if (player.getHealth() < player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /**
     * 四个护甲槽里有没有任意一件耐摔合金（不看耐久、不要求齐全）。
     *
     * <p>包可见：{@link HelicopterGuardHook} 的防扒甲闸门要用它——被扒的可能正是
     * 最后一件，此时「全套」判定已经不成立，只能按「任意一件」来保护。</p>
     */
    static boolean wearsAnyHelicopterPiece(EntityPlayer player) {
        for (int i = 0; i < 4; i++) {
            ItemStack armor = player.inventory.armorInventory[i];
            if (armor == null) continue;
            if (!(armor.getItem() instanceof ArmorFSB)) continue;
            if (((ArmorFSB) armor.getItem()).getArmorMaterial() == MNGAMaterials.ARMOR_HELICOPTER) return true;
        }
        return false;
    }

    /** 空手（当前手持栏为空）打生物/玩家：附加 HBM 式震撼 24 级，15 秒。 */
    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj.isRemote) return;   // 药水只在服务端挂
        if (!wearsHelicopterSet(player)) return;
        if (player.getCurrentEquippedItem() != null) return;      // 拿东西了就不算空手
        if (!(event.target instanceof EntityLivingBase)) return;
        EntityLivingBase victim = (EntityLivingBase) event.target;
        // HBM 的"震撼"(IWeaponAbility.STUN) 就是缓行+虚弱双效果；24 级 = amplifier 23，
        // 时长取 STUN 最高档 15 秒。
        victim.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 15 * 20, 23));
        victim.addPotionEffect(new PotionEffect(Potion.weakness.id, 15 * 20, 23));
    }
}
