package com.makentmgreatagain.hook;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.tileentity.machine.storage.TileEntityBarrel;
import com.hbm.tileentity.machine.storage.TileEntityMachineBAT9000;
import com.hbm.tileentity.machine.storage.TileEntityMachineBigAssTank;
import com.hbm.tileentity.machine.storage.TileEntityMachineFluidTank;
import com.hbm.tileentity.machine.storage.TileEntityMachineOrbus;
import com.makentmgreatagain.fluid.FT_CoreTank;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;

import net.minecraft.world.World;

/**
 * 地核线流体的容器门槛：带 {@link FT_CoreTank} 的流体只认四种超大容器，
 * 其余容器一接触就被溶穿。
 *
 * <p>为什么不用 HBM 现成的 {@code FT_Amat}：那会让 tooltip 多一行「反物质」，
 * 而且会把「巨尻」和「大型储罐」也一起炸掉 —— 这两台恰恰是要能装的。
 * 所以改成自家标记 + 字节码层的入口拦截（{@code CoreFluidTankTransformer}）。</p>
 *
 * <p>放行名单（注释里是游戏内中文名）：
 * <ul>
 *   <li>{@link TileEntityBat9000Reborn} —— 大型储罐（附属的巨尻-9000 重生版）；</li>
 *   <li>{@link TileEntityMachineBigAssTank} —— 「巨尻」超大型储罐，16,000,000 mB；</li>
 *   <li>{@link TileEntityMachineBAT9000} —— 旧版巨尻-9000，已废弃但方块还在；</li>
 *   <li>{@link TileEntityMachineOrbus} —— 重型反物质储罐，16,000,000 mB 的量级、免疫一切危险流体；</li>
 *   <li>{@code barrel_antimatter} —— 磁约束反物质容器。</li>
 * </ul>
 * 前四者的 {@code checkFluidInteraction} 都是各自重写的，本来就不会走到基类，
 * 这里再列一遍是防御性的：万一 HBM 哪天改成调用 super，名单仍然成立。</p>
 *
 * <p>普通储罐（{@code machine_fluidtank}，256,000 mB）走 HBM 对强腐蚀物质的既有处理：
 * 罐体破裂（{@code explode()}）、停止收发、逐步泄漏，可以用喷枪修复；
 * 小桶则直接无掉落销毁并播放嘶声，与塑料桶装热液体的表现一致。</p>
 */
public final class CoreFluidTankHook {

    private CoreFluidTankHook() { }

    /**
     * 由字节码补丁插在 {@code checkFluidInteraction()} 的最前面。
     *
     * @return true = 已经处理完（容器被毁），调用方应直接 return；
     *         false = 放行或只是让原逻辑继续（普通储罐的破裂要借原逻辑的泄漏分支）
     */
    public static boolean guard(Object tile) {

        if(!(tile instanceof TileEntityBarrel)) return false;

        TileEntityBarrel barrel = (TileEntityBarrel) tile;
        FluidTank tank = barrel.tank;
        if(tank == null) return false;

        FluidType type = tank.getTankType();
        if(type == null || !type.hasTrait(FT_CoreTank.class)) return false;
        if(isAllowed(barrel)) return false;

        World world = barrel.getWorldObj();
        if(world == null || world.isRemote) return false;

        // 普通储罐：交给 HBM 自己的「破裂 + 泄漏」流程，这里只负责点火
        if(barrel instanceof TileEntityMachineFluidTank) {
            TileEntityMachineFluidTank machineTank = (TileEntityMachineFluidTank) barrel;
            if(!machineTank.hasExploded) machineTank.explode();
            return false;
        }

        // 小桶：溶穿，无掉落
        int x = barrel.xCoord;
        int y = barrel.yCoord;
        int z = barrel.zCoord;
        int amount = tank.getFill();

        world.func_147480_a(x, y, z, false);
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.fizz", 1.0F, 0.5F);
        // 溢出的辐射 / 污染由流体自身的 trait 结算（兼容层：旧版无静态 onRelease）
        com.makentmgreatagain.compat.HBMVersionCompat.onFluidRelease(world, x, y, z, type, tank, FluidReleaseType.SPILL, amount);
        return true;
    }

    private static boolean isAllowed(TileEntityBarrel barrel) {

        if(barrel instanceof TileEntityBat9000Reborn) return true;        // 大型储罐
        if(barrel instanceof TileEntityMachineBigAssTank) return true;   // 「巨尻」超大型储罐
        if(barrel instanceof TileEntityMachineBAT9000) return true;      // 旧版巨尻-9000
        if(barrel instanceof TileEntityMachineOrbus) return true;        // 重型反物质储罐

        return barrel.getBlockType() == ModBlocks.barrel_antimatter;      // 磁约束反物质容器
    }
}
