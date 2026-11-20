package com.makentmgreatagain.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * 日落六号遗迹的「每世界仅一个」持久化标记。
 *
 * <p>挂在 {@code world.perWorldStorage} 上，随存档一起落盘。一旦某个世界成功生成过
 * 日落六号，就把 {@link #generated} 置 true 并记下坐标；之后 {@code checkCoordinates}
 * 永远返回 false，于是这个世界再也刷不出第二座——重启、跨维度传送都不会重置。</p>
 *
 * <p>为什么不用内存静态变量：静态变量重启就丢，玩家重进世界会再刷一座。
 * 必须随存档持久化，才能真正做到「每个世界的地狱维度仅生成一个」。</p>
 *
 * <p>用法与 HBM 的 {@code AnnihilatorSavedData} 一致：{@link #get(World)} 取（没有就建），
 * 改完字段调 {@link #markDirty()} 让原版在下次保存时写盘。</p>
 */
public class SunsetSixSavedData extends WorldSavedData {

    /** 存档里的数据键名，全局唯一。 */
    public static final String KEY = "mnga_sunset_six";

    /** 这个世界是否已经生成过日落六号。true 之后永久不再触发。 */
    public boolean generated;

    /** 已生成结构的中心坐标（仅用于日志/排查，未生成时为 0）。 */
    public int centerX;
    public int centerZ;
    public int baseY;

    public SunsetSixSavedData() {
        super(KEY);
    }

    public SunsetSixSavedData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        generated = nbt.getBoolean("generated");
        centerX = nbt.getInteger("centerX");
        centerZ = nbt.getInteger("centerZ");
        baseY = nbt.getInteger("baseY");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("generated", generated);
        nbt.setInteger("centerX", centerX);
        nbt.setInteger("centerZ", centerZ);
        nbt.setInteger("baseY", baseY);
    }

    /** 取（没有就建并登记）。与 HBM AnnihilatorSavedData.getData 同口径。 */
    public static SunsetSixSavedData get(World world) {
        SunsetSixSavedData data = (SunsetSixSavedData) world.perWorldStorage.loadData(SunsetSixSavedData.class, KEY);
        if(data == null) {
            data = new SunsetSixSavedData();
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    /** 记录一次成功生成，并立即标脏等待写盘。 */
    public void markGenerated(int centerX, int centerZ, int baseY) {
        this.generated = true;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.baseY = baseY;
        markDirty();
    }
}