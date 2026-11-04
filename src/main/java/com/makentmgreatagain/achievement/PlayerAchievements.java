package com.makentmgreatagain.achievement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

/**
 * 玩家成就进度（存档数据）。
 *
 * 双端各有一份：服务端是权威，客户端那份靠登录时的全量同步和解锁时的单条同步更新。
 * 解锁状态用按 AchRegistry 顺序的位图存，id 顺序一旦变化就需要迁移——
 * 所以 AchRegistry 只允许追加，不允许改变已有条目的顺序。
 */
public class PlayerAchievements implements IExtendedEntityProperties {

    public static final String KEY = "MNTGA_ACH";

    private final EntityPlayer player;

    /** 与 AchRegistry.all() 同序的解锁位图 */
    private boolean[] unlocked = new boolean[0];

    /** 「这就是全部了」的累计计数：挖掉的的白钨矿块数 */
    public int scheeliteMined = 0;

    public PlayerAchievements(EntityPlayer player) {
        this.player = player;
    }

    public static PlayerAchievements get(EntityPlayer player) {

        if (player == null) return null;

        IExtendedEntityProperties props = player.getExtendedProperties(KEY);

        // 老存档里的玩家实体在注册事件之前就存在了，这里补注册
        if (props == null) {
            props = new PlayerAchievements(player);
            player.registerExtendedProperties(KEY, props);
        }

        return (PlayerAchievements) props;
    }

    private void ensureSize() {

        int need = AchRegistry.size();

        if (unlocked.length < need) {
            boolean[] bigger = new boolean[need];
            System.arraycopy(unlocked, 0, bigger, 0, unlocked.length);
            unlocked = bigger;
        }
    }

    public boolean has(String id) {

        int idx = indexOf(id);
        if (idx < 0) return false;
        ensureSize();
        return unlocked[idx];
    }

    /**
     * 标记解锁。
     *
     * @return 只有「从没解锁到解锁」这一下才返回 true，用来决定是否弹提示/发包
     */
    public boolean unlock(String id) {

        int idx = indexOf(id);
        if (idx < 0) return false;

        ensureSize();

        if (unlocked[idx]) return false;

        unlocked[idx] = true;
        return true;
    }

    public int unlockedCount() {

        ensureSize();

        int n = 0;
        for (boolean b : unlocked) if (b) n++;
        return n;
    }

    /** 全量位图，登录同步用 */
    public boolean[] snapshot() {

        ensureSize();
        boolean[] copy = new boolean[unlocked.length];
        System.arraycopy(unlocked, 0, copy, 0, unlocked.length);
        return copy;
    }

    /** 客户端收到全量同步后覆盖本地状态 */
    public void applySnapshot(boolean[] data) {

        int need = Math.max(AchRegistry.size(), data.length);
        unlocked = new boolean[need];
        System.arraycopy(data, 0, unlocked, 0, Math.min(data.length, need));
    }

    private static int indexOf(String id) {

        java.util.List<AchDef> list = AchRegistry.all();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    /* ================= 存档 ================= */

    @Override
    public void saveNBTData(NBTTagCompound nbt) {

        ensureSize();

        byte[] bits = new byte[(unlocked.length + 7) / 8];
        for (int i = 0; i < unlocked.length; i++) {
            if (unlocked[i]) bits[i >> 3] |= (byte) (1 << (i & 7));
        }

        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("bits", bits);
        tag.setInteger("size", unlocked.length);
        tag.setInteger("scheeliteMined", scheeliteMined);

        nbt.setTag(KEY, tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {

        NBTTagCompound tag = nbt.getCompoundTag(KEY);
        if (tag == null) return;

        int size = tag.getInteger("size");
        byte[] bits = tag.getByteArray("bits");

        unlocked = new boolean[Math.max(size, AchRegistry.size())];

        for (int i = 0; i < unlocked.length && (i >> 3) < bits.length; i++) {
            unlocked[i] = (bits[i >> 3] & (1 << (i & 7))) != 0;
        }

        scheeliteMined = tag.getInteger("scheeliteMined");
    }

    @Override
    public void init(Entity entity, World world) {
        // 无需额外初始化
    }
}
