package com.makentmgreatagain.items;

import com.hbm.items.armor.ArmorFSB;

/**
 * 本模组护甲四件的通用物品类。
 *
 * <p>必须继承 HBM 的 {@link ArmorFSB} 而不是原版 {@code ItemArmor}，原因有三：
 * <ol>
 *   <li>HBM 的全套属性（药水效果、脚步声、落地震击、冲刺次数、VATS…）全部是
 *       {@code ArmorFSB} 上的字段，只有它自己的 {@code handleTick} / {@code handleFall}
 *       会去读；</li>
 *   <li>全套判定 {@code ArmorFSB.hasFSBArmor(player)} 要求四件都是 {@code ArmorFSB}
 *       且 {@code getArmorMaterial()} 是同一个实例。四件里只要有一件是普通
 *       {@code ItemArmor}，全套属性就永远不生效；</li>
 *   <li>护甲模型贴图、HUD、模型隐藏这些钩子也都在 {@code ArmorFSB} 里实现好了。</li>
 * </ol>
 *
 * <p>注意：{@code ArmorFSB} 的构造函数会把物品图标默认设成 {@code hbm:armor}，
 * 所以注册时仍要自己调用 {@code setTextureName(...)} 指回本模组的图标。
 */
public class ItemArmorAlloyReborn extends ArmorFSB {

    /**
     * 全套移动速度加成，0.10 = +10%，-0.10 = -10%，0 = 不加速也不减速。
     *
     * <p>HBM 的 {@code ArmorFSB} 里<b>没有</b>速度字段，17 条属性也一条都改不了移动速度，
     * 所以这个值只在本类里存着，真正挂到玩家身上的是
     * {@code com.makentmgreatagain.hook.ArmorSpeedHandler}（每 tick 统一 apply / remove
     * 一个固定 UUID 的 {@code AttributeModifier}）。
     *
     * <p>为什么不在 {@code onArmorTick} 里自己挂：那个方法只在你穿着这一件时才会被调用，
     * 胸甲一脱就不再执行，上一次挂上去的修饰符会永远留在玩家身上（HBM 的 envsuit_plate
     * 就是这个毛病）。放进 PlayerTickEvent 才能做到脱甲、死亡、换维度全部自愈。
     */
    private double setSpeed = 0D;

    /**
     * @param material     护甲材质，决定各部位护甲点、耐久与附魔性
     * @param slot         部位编号：0 头盔、1 胸甲、2 护腿、3 靴子（与 armorInventory 下标相反）
     * @param armorTexture 穿在身上时渲染的模型贴图，形如 "modid:textures/armor/xxx.png"
     *                     （腿部那一层用 *_2 的贴图，其余三层用 *_1）
     */
    public ItemArmorAlloyReborn(ArmorMaterial material, int slot, String armorTexture) {
        // 第二个参数 renderIndex 在 1.7.10 里已无实际作用，沿用 HBM 的写法传 0
        super(material, slot, armorTexture);
    }

    /** 设置“全套穿齐时才生效”的移动速度加成：0.10 = +10%。 */
    public ItemArmorAlloyReborn setSetSpeed(double setSpeed) {
        this.setSpeed = setSpeed;
        return this;
    }

    /** 供 {@code ArmorSpeedHandler} 读取，只读胸甲那一件的值。 */
    public double getSetSpeed() {
        return setSpeed;
    }

    /**
     * HBM 的 cloneStats 不认识本类自己的字段，这里补一份复制逻辑，
     * 保证“属性写在头盔、另外三件 clone”这条规矩对速度同样成立。
     */
    @Override
    public ArmorFSB cloneStats(ArmorFSB original) {
        super.cloneStats(original);
        if (original instanceof ItemArmorAlloyReborn) {
            this.setSpeed = ((ItemArmorAlloyReborn) original).setSpeed;
        }
        return this;
    }
}
