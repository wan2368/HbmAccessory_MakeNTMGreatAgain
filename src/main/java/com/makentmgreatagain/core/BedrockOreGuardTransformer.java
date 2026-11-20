package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * 「长流程模式」基岩矿闸门补丁。
 *
 * <h2>为什么走 ASM 而不是改源码</h2>
 * 基岩矿的生成方不止一处：地球的 {@code HbmWorldGen}、下界、以及航天版每个星球的
 * {@code com.hbm.dim.WorldGeneratorCelestial}。其中 dim 包不在本工程源码内，
 * 运行时类来自 HBM 发布 jar，改源码只能盖住地球那一份。
 * 但所有路径最终都汇聚到 {@code BedrockOre} 里<b>唯一一处</b>放置
 * {@code ModBlocks.ore_bedrock} 的方法 —— 在这里下闸，一次覆盖全部维度。
 *
 * <h2>定位方式（跨版本设计）</h2>
 * 不写死方法签名，而是找「GETSTATIC com/hbm/blocks/ModBlocks.ore_bedrock」这条指令
 * 所在的方法。字段名 {@code ore_bedrock} 是 HBM 自己的 public 字段，
 * reobf 不会改 mod 自有成员名，所以混淆前后都能命中；
 * 不同 HBM 版本的 {@code generate} 重载数量（7 参 / 8 参）也不用管。
 *
 * 命中后再校验参数表前四位必须是 (World, int, int, ItemStack)，
 * 确认槽位 0 是 world、槽位 3 是待生成的产物 ItemStack，才插守卫调用。
 *
 * <h2>豁免浮冰</h2>
 * 判定全部交给 {@code LongProcessHook.allowBedrockOre(Object, Object)}：
 * 参数用 Object 传，字节码里不需要 MC 类型的混淆名与 CHECKCAST。
 * 航天版浮冰基岩矿走的是同一条 generate、只是产物 ItemStack 为 packed_ice，
 * hook 认物品不认维度，因此浮冰天然放行，其余全灭。
 *
 * <p>类内不 import 任何 net.minecraft / com.hbm 类型：coremod 由系统类加载器加载，
 * 引用 MC 类会导致重复加载。</p>
 */
public class BedrockOreGuardTransformer implements IClassTransformer {

    private static final String TARGET = "com.hbm.world.feature.BedrockOre";
    private static final String HOOK = "com/makentmgreatagain/hook/LongProcessHook";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        if (!transformedName.equals(TARGET)) return basicClass;

        try {
            return patch(basicClass);
        } catch (Throwable t) {
            // 兜底：换了 HBM 版本、字节码模式对不上时放弃补丁，返回原字节码，
            // 而不是让异常冒到 LaunchWrapper 导致启动崩溃。
            System.out.println("[MakeNTMGreatAgain] 基岩矿闸门补丁跳过（与本版本字节码不匹配）: " + transformedName);
            t.printStackTrace();
            return basicClass;
        }
    }

    private byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        int count = 0;
        for (MethodNode m : node.methods) {
            if (!referencesBedrockOreBlock(m)) continue;
            if (!hasExpectedPrefix(m)) {
                System.out.println("[MakeNTMGreatAgain] 警告: BedrockOre 里引用 ore_bedrock 的方法 "
                        + m.name + m.desc + " 参数表与预期不符，跳过该方法");
                continue;
            }
            insertGuard(m);
            count++;
        }

        if (count == 0) {
            System.out.println("[MakeNTMGreatAgain] 警告: BedrockOre 未找到 ore_bedrock 放置点，长流程模式将不屏蔽基岩矿");
            return bytes;
        }
        System.out.println("[MakeNTMGreatAgain] BedrockOre 已加装基岩矿闸门 " + count + " 处（长流程模式生效，浮冰矿豁免）");

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** 方法体里是否有 GETSTATIC com/hbm/blocks/ModBlocks.ore_bedrock —— 即真正放矿石的那一处。 */
    private static boolean referencesBedrockOreBlock(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.GETSTATIC) continue;
            FieldInsnNode f = (FieldInsnNode) insn;
            if (f.owner.equals("com/hbm/blocks/ModBlocks") && f.name.equals("ore_bedrock")) return true;
        }
        return false;
    }

    /**
     * 参数表前四位必须是 (对象, int, int, 对象)，且方法是 static，保证
     * 局部变量槽 0 是 world、槽 3 是产物 ItemStack。
     *
     * <p>这里刻意<b>不比对 MC 类型名</b>：补丁跑在 FML 的反混淆前后，描述符里可能是
     * {@code net/minecraft/world/World}（srg）也可能是 {@code ahb}（notch），
     * 写死名字会让补丁在某些加载顺序下静默失效。判据只用槽位形态，
     * 配合「方法体里出现 ModBlocks.ore_bedrock」这一条已经足够定位。</p>
     */
    private static boolean hasExpectedPrefix(MethodNode m) {
        if ((m.access & Opcodes.ACC_STATIC) == 0) return false;
        // 必须是 void：插的是裸 RETURN，命中同形态的非 void 方法会栈不满，VerifyError
        //（类加载期抛出，外层 try/catch 兜不住）
        if (Type.getReturnType(m.desc).getSort() != Type.VOID) return false;
        Type[] args = Type.getArgumentTypes(m.desc);
        if (args.length < 4) return false;
        if (args[0].getSort() != Type.OBJECT) return false;
        if (args[1].getSort() != Type.INT) return false;
        if (args[2].getSort() != Type.INT) return false;
        return args[3].getSort() == Type.OBJECT;
    }

    /**
     * 方法入口插：if (!LongProcessHook.allowBedrockOre(world, stack)) return;
     *
     * <p>generate / generateAuto 都是 void；世界生成的调用方不看返回值，
     * 直接 RETURN 就等于「这一处什么都不生成」，星球的 depthRock 岩层改造一并跳过。</p>
     */
    private static void insertGuard(MethodNode m) {
        AbstractInsnNode first = m.instructions.getFirst();
        while (first != null && (first instanceof LabelNode || first instanceof LineNumberNode)) {
            first = first.getNext();
        }
        if (first == null) return;

        LabelNode cont = new LabelNode();
        InsnList ins = new InsnList();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                 // world
        ins.add(new VarInsnNode(Opcodes.ALOAD, 3));                 // 产物 ItemStack
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                "allowBedrockOre", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
        // 必须是 IFNE：允许（非 0）才跳过下面的 RETURN 继续生成。
        // 写成 IFEQ 会让判据反掉 —— 被拒的资源照常生成，该放行的浮冰反而被跳过。
        ins.add(new JumpInsnNode(Opcodes.IFNE, cont));
        ins.add(new InsnNode(Opcodes.RETURN));
        ins.add(cont);
        // 跳转目标帧 = 方法入口帧（局部变量未变、栈为空）
        ins.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

        m.instructions.insertBefore(first, ins);
    }
}
