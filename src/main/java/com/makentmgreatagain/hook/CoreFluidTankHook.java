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
import com.makentmgreatagain.fluid.FT_MagneticTank;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;

import net.minecraft.world.World;

/**
 * 特殊流体的储罐门槛：
 * <ul>
 *   <li>带 {@link FT_CoreTank} 的地核线流体只认超大容器与磁约束容器；</li>
 * </ul>
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
        if(type == null) return false;

        boolean requireMagnetic = type.hasTrait(FT_MagneticTank.class);
        boolean requireCoreTank = !requireMagnetic && type.hasTrait(FT_CoreTank.class);
        if(!requireMagnetic && !requireCoreTank) return false;

        if(requireMagnetic ? isAllowedMagnetic(barrel) : isAllowedCore(barrel)) return false;

        World world = barrel.getWorldObj();
        if(world == null || world.isRemote) return false;

        // 普通储罐：交给 HBM 自己的「破裂 + 泄漏」流程，这里只负责点火
        if(barrel instanceof TileEntityMachineFluidTank) {
            TileEntityMachineFluidTank machineTank = (TileEntityMachineFluidTank) barrel;
            if(!machineTank.hasExploded) machineTank.explode();
            return false;
        }

        int x = barrel.xCoord;
        int y = barrel.yCoord;
        int z = barrel.zCoord;
        int amount = tank.getFill();
        tank.setFill(0);

        world.func_147480_a(x, y, z, false);
        if(requireMagnetic) {
            boolean large = (barrel instanceof TileEntityBat9000Reborn)
                    || (barrel instanceof TileEntityMachineBigAssTank)
                    || (barrel instanceof TileEntityMachineBAT9000);
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.fizz", 1.2F, 0.7F);
            world.newExplosion(null, x + 0.5D, y + 0.5D, z + 0.5D, large ? 8.0F : 4.0F, false, true);
        } else {
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.fizz", 1.0F, 0.5F);
        }
        // 溢出的辐射 / 污染由流体自身的 trait 结算（兼容层：旧版无静态 onRelease）
        com.makentmgreatagain.compat.HBMVersionCompat.onFluidRelease(world, x, y, z, type, tank, FluidReleaseType.SPILL, amount);
        return true;
    }

    private static boolean isAllowedCore(TileEntityBarrel barrel) {

        if(barrel instanceof TileEntityBat9000Reborn) return true;        // 大型储罐
        if(barrel instanceof TileEntityMachineBigAssTank) return true;   // 「巨尻」超大型储罐
        if(barrel instanceof TileEntityMachineBAT9000) return true;      // 旧版巨尻-9000
        if(barrel instanceof TileEntityMachineOrbus) return true;        // 重型磁约束储罐

        return barrel.getBlockType() == ModBlocks.barrel_antimatter;      // 磁约束反物质容器
    }

    private static boolean isAllowedMagnetic(TileEntityBarrel barrel) {

        if(barrel instanceof TileEntityMachineOrbus) return true;        // 重型磁约束储罐

        return barrel.getBlockType() == ModBlocks.barrel_antimatter;      // 磁约束反物质容器
    }
}
