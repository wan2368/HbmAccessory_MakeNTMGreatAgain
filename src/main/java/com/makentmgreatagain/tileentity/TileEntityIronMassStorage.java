package com.makentmgreatagain.tileentity;

import com.hbm.tileentity.machine.storage.TileEntityMassStorage;

/**
 * 铁质大规模存储单元的方块实体。
 *
 * <p>除容量 1000 和 GUI 标题外，其余逻辑全部继承本体：
 * 输入/输出槽、过滤器、快速插入/取出、红石比较器、锁定、
 * ROR / OpenComputers / AE2 外部存储接口等都不需要重写。</p>
 */
public class TileEntityIronMassStorage extends TileEntityMassStorage {

    public TileEntityIronMassStorage() {
        super(1000);
    }

    /** 库存命名覆写。源码用 MCP 名 getInventoryName，reobf 会自动转成 SRG 名
     *  func_145825_b（运行时宿主用的就是 SRG 名）。
     *  不要再显式写一个 func_145825_b：reobf 会把本方法也转成同名，造成
     *  ClassFormatError: Duplicate method name&signature。 */
    @Override
    public String getInventoryName() {
        return "container.ironMassStorage";
    }
}
