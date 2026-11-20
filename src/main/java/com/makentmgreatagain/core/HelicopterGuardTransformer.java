package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * 耐摔合金全套的「绕过伤害管线」补丁：在血量 / 死亡方法的入口下闸。
 *
 * <h2>补的是哪三个方法</h2>
 * <ul>
 *   <li>{@code EntityLivingBase.setHealth(float)} —— 别的模组直接改血量秒杀
 *       （HBM 的 digamma 处决就是 {@code setHealth(0)} + {@code onDeath(...)}）；</li>
 *   <li>{@code EntityLivingBase.onDeath(DamageSource)} —— 不走伤害管线的直接处决；</li>
 *   <li>{@code Entity.setDead()} —— /kill 与各种强制移除。</li>
 * </ul>
 * Forge 的免伤事件只覆盖 {@code attackEntityFrom → damageEntity}，上面这三条
 * 全在事件链之外，所以只能在字节码层拦。
 *
 * <h2>定位方式（跨环境设计）</h2>
 * 与 {@link BedrockOreGuardTransformer} 同一套思路：<b>不写死方法名</b>。
 * 1.7.10 的 MC 方法名在 dev（srg / mcp）与生产（notch）下不同，写死名字会让补丁
 * 在某一侧静默失效。这里只按「方法形态」定位：
 * <ul>
 *   <li>setHealth：实例方法、void、恰好一个 float 参数；</li>
 *   <li>onDeath：实例方法、void、恰好一个对象参数，且<b>方法体里读 {@code isDead} 字段</b>
 *       —— 这是 onDeath 的可靠特征（{@code if(!this.isDead) ...}），
 *       用来把同形态的其它单参方法排除掉；</li>
 *   <li>setDead：实例方法、void、无参，且<b>方法体里写 {@code isDead} 字段</b>。</li>
 * </ul>
 * 字段名 {@code isDead} 是 MC 字段，混淆后名字会变，所以同时接受 dev 名与
 * notch 名两种候选，命中任一即可；两种都不命中就跳过该方法（宁可不补，不能补错）。
 *
 * <p>类内不 import 任何 net.minecraft / com.hbm 类型：coremod 由系统类加载器加载，
 * 引用 MC 类会导致重复加载。判定全部交给
 * {@code HelicopterGuardHook}，参数用 Object 传。</p>
 */
public class HelicopterGuardTransformer implements IClassTransformer {

    private static final String LIVING = "net.minecraft.entity.EntityLivingBase";
    private static final String ENTITY = "net.minecraft.entity.Entity";
    private static final String PLAYER = "net.minecraft.entity.player.EntityPlayer";
    private static final String NET_HANDLER = "net.minecraft.network.NetHandlerPlayServer";
    private static final String HOOK = "com/makentmgreatagain/hook/HelicopterGuardHook";

    /** Entity.isDead 的候选字段名：dev(srg/mcp) 与生产(notch)。 */
    private static final String[] IS_DEAD_NAMES = { "isDead", "field_70128_L", "aa" };

    /** EntityLivingBase.health 的候选字段名：dev(srg/mcp) 与生产(notch)。 */
    private static final String[] HEALTH_NAMES = { "health", "field_70728_aV", "aV" };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        boolean living = transformedName.equals(LIVING);
        boolean entity = transformedName.equals(ENTITY);
        boolean player = transformedName.equals(PLAYER);
        boolean net = transformedName.equals(NET_HANDLER);
        if (!living && !entity && !player && !net) return basicClass;

        try {
            return patch(basicClass, living, entity, player, net);
        } catch (Throwable t) {
            // 兜底：换了 MC 版本 / 映射对不上时放弃补丁，返回原字节码，
            // 而不是让异常冒到 LaunchWrapper 导致启动崩溃。
            System.out.println("[MakeNTMGreatAgain] 耐摔合金守卫补丁跳过（与本版本字节码不匹配）: " + transformedName);
            t.printStackTrace();
            return basicClass;
        }
    }

    private byte[] patch(byte[] bytes, boolean living, boolean entity, boolean player, boolean net) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        int count = 0;
        for (MethodNode m : node.methods) {
            if ((m.access & Opcodes.ACC_STATIC) != 0) continue;
            if ((m.access & Opcodes.ACC_ABSTRACT) != 0) continue;
            if (Type.getReturnType(m.desc).getSort() != Type.VOID) continue;

            Type[] args = Type.getArgumentTypes(m.desc);

            if (living) {
                // setHealth(float)：必须同时读写 health 字段，
                // 否则同形态的 setAbsorptionAmount(float) 等会被误补
                if (args.length == 1 && args[0].getSort() == Type.FLOAT
                        && touchesHealth(m)) {
                    insertSetHealthGuard(m);
                    count++;
                    continue;
                }
                // onDeath(DamageSource)：单对象参 + 读 isDead
                if (args.length == 1 && args[0].getSort() == Type.OBJECT
                        && touchesIsDead(m, false)) {
                    insertDeathGuard(m);
                    count++;
                }
            } else if (entity) {
                // setDead()：无参 + 写 isDead
                if (args.length == 0 && touchesIsDead(m, true)) {
                    insertSetDeadGuard(m);
                    count++;
                }
            } else if (player) {
                // setCurrentItemOrArmor(int, ItemStack)：void + (int, 对象) + 写护甲数组
                if (args.length == 2 && args[0].getSort() == Type.INT
                        && args[1].getSort() == Type.OBJECT
                        && touchesArmorArray(m)) {
                    insertArmorGuard(m);
                    count++;
                }
            } else {
                // 服务端网络类：两个目标形态不同，分别定位
                // kickPlayerFromServer(String)：void + 单 String 参 + 方法体引用 netManager
                if (args.length == 1 && args[0].getSort() == Type.OBJECT
                        && args[0].getClassName().equals("java.lang.String")
                        && referencesDisconnectPacket(m)) {
                    if (insertKickGuard(m)) count++;
                    continue;
                }
                // processClickWindow(C0EPacketClickWindow)：void + 单对象参 + 调 slotClick
                if (args.length == 1 && args[0].getSort() == Type.OBJECT
                        && callsSlotClick(m)) {
                    wrapSelfArmorEdit(m);
                    count++;
                }
            }
        }

        if (count == 0) {
            System.out.println("[MakeNTMGreatAgain] 警告: " + (living ? LIVING : entity ? ENTITY : player ? PLAYER : NET_HANDLER)
                    + " 未找到可下闸的方法，耐摔合金的对应防护不生效");
            return bytes;
        }
        System.out.println("[MakeNTMGreatAgain] 耐摔合金守卫已加装 " + count + " 处（"
                + (living ? "setHealth / onDeath" : entity ? "setDead" : player ? "护甲槽写入" : "踢出 / 自身脱甲标记") + "）");

        // 只用 COMPUTE_MAXS：COMPUTE_FRAMES 会调 getCommonSuperClass 触发类加载，
        // 在 coremod 阶段（MC 类还没进 LaunchWrapper）会直接 NoClassDefFoundError 崩启动。
        // 插入的是「入口判断 + 裸 RETURN」，跳转目标帧与方法入口帧一致，
        // 手动补 F_SAME 即可，不需要重算全方法的帧。
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /**
     * 方法体里有没有读写 {@code health} 字段 —— setHealth 的可靠特征。
     *
     * <p>只按「void + 单 float 参」定位是不够的：{@code setAbsorptionAmount(float)}
     * 等方法形态完全一样，误补上去会改变无关行为。加上这条字段判据才唯一。</p>
     */
    private static boolean touchesHealth(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op != Opcodes.PUTFIELD && op != Opcodes.GETFIELD) continue;
            FieldInsnNode f = (FieldInsnNode) insn;
            if (!f.desc.equals("F")) continue;
            for (String candidate : HEALTH_NAMES) {
                if (f.name.equals(candidate)) return true;
            }
        }
        return false;
    }

    /**
     * 方法体里有没有对 {@code isDead} 字段的访问。
     *
     * @param write true 只认写入（PUTFIELD，setDead 的特征），false 只认读取（GETFIELD，onDeath 的特征）
     */
    private static boolean touchesIsDead(MethodNode m, boolean write) {
        int opcode = write ? Opcodes.PUTFIELD : Opcodes.GETFIELD;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != opcode) continue;
            FieldInsnNode f = (FieldInsnNode) insn;
            if (f.desc.equals("Z")) {
                for (String candidate : IS_DEAD_NAMES) {
                    if (f.name.equals(candidate)) return true;
                }
            }
        }
        return false;
    }

    /** setHealth 入口：if (!HelicopterGuardHook.allowSetHealth(this, newHealth)) return; */
    private static void insertSetHealthGuard(MethodNode m) {
        InsnList ins = new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                  // this
        ins.add(new VarInsnNode(Opcodes.FLOAD, 1));                  // 新血量
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "allowSetHealth", "(Ljava/lang/Object;F)Z", false));
        addSkipReturn(m, ins);
    }

    /** onDeath 入口：if (!HelicopterGuardHook.allowDeath(this, source)) return; */
    private static void insertDeathGuard(MethodNode m) {
        InsnList ins = new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                  // this
        ins.add(new VarInsnNode(Opcodes.ALOAD, 1));                  // DamageSource
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "allowDeath", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
        addSkipReturn(m, ins);
    }

    /** setDead 入口：if (!HelicopterGuardHook.allowSetDead(this)) return; */
    private static void insertSetDeadGuard(MethodNode m) {
        InsnList ins = new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                  // this
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "allowSetDead", "(Ljava/lang/Object;)Z", false));
        addSkipReturn(m, ins);
    }

    // ---------------- 新增目标的定位判据 ----------------

    /** EntityPlayer.inventory.armorInventory 的候选字段名（dev 与 notch）。 */
    private static final String[] ARMOR_ARRAY_NAMES = { "armorInventory", "field_70460_b", "b" };

    /**
     * 方法体里有没有写护甲数组（AASTORE 到 armorInventory）——
     * {@code setCurrentItemOrArmor} 的可靠特征，把同形态的其它双参方法排除掉。
     */
    private static boolean touchesArmorArray(MethodNode m) {
        boolean sawArrayStore = false;
        boolean sawItemStackArray = false;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.AASTORE) sawArrayStore = true;
            if (insn.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode f = (FieldInsnNode) insn;
                // 描述符比字段名可靠：ItemStack[] 在 dev 与生产两侧一致，字段名会变
                if (f.desc.equals("[Lnet/minecraft/item/ItemStack;")) sawItemStackArray = true;
                for (String candidate : ARMOR_ARRAY_NAMES) {
                    if (f.name.equals(candidate)) return true;
                }
            }
        }
        // 字段名对不上时（换了映射）退一步：必须同时「写对象数组」且「读 ItemStack[] 字段」。
        // 单看 AASTORE 会把任何写数组的同形态方法误补上去。
        return sawArrayStore && sawItemStackArray;
    }

    /**
     * 方法体里有没有引用断连包 {@code S40PacketDisconnect} ——
     * {@code kickPlayerFromServer} 的可靠特征（它就是发这个包再关通道）。
     *
     * <p>按类名的<b>简单名</b>匹配，不写死包路径：dev 与生产的方法名不同，
     * 但这个 MC 类名两侧一致。</p>
     */
    private static boolean referencesDisconnectPacket(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.NEW && insn.getOpcode() != Opcodes.INVOKESPECIAL) continue;
            TypeInsnNode t = insn instanceof TypeInsnNode ? (TypeInsnNode) insn : null;
            if (t != null && t.desc.endsWith("S40PacketDisconnect")) return true;
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                if (mi.owner.endsWith("S40PacketDisconnect")) return true;
            }
        }
        return false;
    }

    /**
     * 方法体里有没有调 {@code Container.slotClick} ——
     * {@code processClickWindow} 的可靠特征（玩家点容器一定走它）。
     *
     * <p>方法名 dev 是 {@code slotClick}、生产是 {@code func_75144_a}，两种都接受。</p>
     */
    private static boolean callsSlotClick(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode mi = (MethodInsnNode) insn;
            if (mi.name.equals("slotClick") || mi.name.equals("func_75144_a")) return true;
        }
        return false;
    }

    // ---------------- 新增目标的插桩 ----------------

    /** setCurrentItemOrArmor 入口：if (!allowArmorSlotWrite(this, slot)) return; */
    private static void insertArmorGuard(MethodNode m) {
        InsnList ins = new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                  // this
        ins.add(new VarInsnNode(Opcodes.ILOAD, 1));                  // 槽号
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "allowArmorSlotWrite", "(Ljava/lang/Object;I)Z", false));
        addSkipReturn(m, ins);
    }

    /**
     * kickPlayerFromServer 入口：if (!allowKick(this.&lt;玩家字段&gt;)) return;
     *
     * <p><b>字段名不能写死</b>：{@code playerEntity} 在生产环境是 notch 名，
     * 写死会让补丁在整合包里 {@code NoSuchFieldError} 直接崩网络线程。
     * 这里从本方法体里已有的 GETFIELD 指令中找出「类型为 EntityPlayerMP」的那个字段，
     * 用它真实的 name + desc 生成指令，dev 与生产两侧都成立。</p>
     *
     * <p>找不到就跳过这个方法（宁可不补，不能补错）。</p>
     */
    private static boolean insertKickGuard(MethodNode m) {
        FieldInsnNode playerField = findPlayerField(m);
        if (playerField == null) return false;

        InsnList ins = new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                  // this (NetHandlerPlayServer)
        ins.add(new FieldInsnNode(Opcodes.GETFIELD,
                playerField.owner, playerField.name, playerField.desc));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "allowKick", "(Ljava/lang/Object;)Z", false));
        addSkipReturn(m, ins);
        return true;
    }

    /**
     * 从方法体里找出读取「EntityPlayerMP 字段」的那条 GETFIELD。
     *
     * <p>{@code kickPlayerFromServer} 自己就多次读 {@code this.playerEntity}，
     * 所以这条指令一定存在，且带着当前环境下真实的字段名与描述符。</p>
     */
    private static FieldInsnNode findPlayerField(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.GETFIELD) continue;
            FieldInsnNode f = (FieldInsnNode) insn;
            if (f.desc.equals("Lnet/minecraft/entity/player/EntityPlayerMP;")) return f;
        }
        return null;
    }

    /**
     * 给 processClickWindow 包上「玩家自己操作护甲」临界区：
     * 入口 beginSelfArmorEdit()，所有 RETURN 前 endSelfArmorEdit()。
     *
     * <p>为什么不用 try/finally：ASM 手工织异常表容易出错，而本方法只要保证
     * 「正常返回路径都清标记」即可——抛异常时 ThreadLocal 会随包处理线程复用，
     * 所以在入口先无条件清一次，避免上一次的残留标记泄漏到下一次调用。</p>
     */
    private static void wrapSelfArmorEdit(MethodNode m) {
        // 入口：先清残留，再置位
        InsnList head = new InsnList();
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "endSelfArmorEdit", "()V", false));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "beginSelfArmorEdit", "()V", false));

        AbstractInsnNode first = m.instructions.getFirst();
        while (first != null && (first instanceof LabelNode || first instanceof LineNumberNode)) {
            first = first.getNext();
        }
        if (first == null) return;
        m.instructions.insertBefore(first, head);

        // 每个 RETURN 前清标记（正常返回路径全覆盖）
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.RETURN) continue;
            InsnList tail = new InsnList();
            tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                    "endSelfArmorEdit", "()V", false));
            m.instructions.insertBefore(insn, tail);
        }
    }

    /**
     * 把「闸门不放行就裸 RETURN」插到方法最前面。
     *
     * <p>三个目标方法都是 void，直接 RETURN 等于「这次调用什么都没发生」。
     * 必须用 IFNE：放行（非 0）才跳过 RETURN 继续执行原逻辑；写成 IFEQ 会把判据反掉。</p>
     */
    private static void addSkipReturn(MethodNode m, InsnList ins) {
        AbstractInsnNode first = m.instructions.getFirst();
        while (first != null && (first instanceof LabelNode || first instanceof LineNumberNode)) {
            first = first.getNext();
        }
        if (first == null) return;

        LabelNode cont = new LabelNode();
        ins.add(new JumpInsnNode(Opcodes.IFNE, cont));
        ins.add(new InsnNode(Opcodes.RETURN));
        ins.add(cont);
        // 跳转目标帧 = 方法入口帧（局部变量未变、栈为空）。
        // 没用 COMPUTE_FRAMES，这一帧必须手动补，否则生产环境 VerifyError。
        ins.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

        m.instructions.insertBefore(first, ins);
    }
}