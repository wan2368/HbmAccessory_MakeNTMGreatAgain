package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 旧版 HBM 构造适配：RenderPipeAnchorAdvanced 的 super 调用。
 *
 * <p>附属按新版 HBM 编译，{@code RenderPipeAnchorAdvanced} 的构造里是
 * {@code super(item, texture)}（新版 RenderPipeAnchor 有两参构造）。
 * 旧版（如 X5687）的 RenderPipeAnchor 只有无参构造，直接加载会抛
 * {@code NoSuchMethodError}。</p>
 *
 * <p>本转换器在附属类加载时检查宿主 RenderPipeAnchor 是否存在两参构造：
 * 存在 → 不动；不存在 → 把构造里的
 * {@code invokespecial RenderPipeAnchor.<init>(Item, ResourceLocation)}
 * 改写成「弹掉两个参数 + invokespecial 无参构造」。
 * 物品归属与物品形态渲染已由附属自己的字段和覆写接管，行为不受影响。</p>
 *
 * <p>只改附属自己的类，不碰 HBM 字节码；探测失败一律返回原字节码，不崩启动。</p>
 */
public class PipeAnchorCtorTransformer implements IClassTransformer {

    private static final String TARGET = "com.makentmgreatagain.client.RenderPipeAnchorAdvanced";
    private static final String PARENT = "com/hbm/render/tileentity/RenderPipeAnchor";
    private static final String TWO_ARG_DESC =
            "(Lnet/minecraft/item/Item;Lnet/minecraft/util/ResourceLocation;)V";

    private static Boolean parentHasTwoArgCtor;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {

        if(basicClass == null || !TARGET.equals(transformedName)) return basicClass;

        try {
            if(parentHasTwoArgCtor == null) {
                parentHasTwoArgCtor = probeParentCtor();
            }
            if(parentHasTwoArgCtor) return basicClass;   // 新版：两参构造在，无需补丁

            return patch(basicClass);
        } catch(Throwable t) {
            System.out.println("[MakeNTMGreatAgain] 管锚构造适配补丁跳过（与本版本字节码不匹配）");
            t.printStackTrace();
            return basicClass;
        }
    }

    /** 宿主 RenderPipeAnchor 是否有 (Item, ResourceLocation) 构造。
     *  直接读父类 .class 字节码资源用 ASM 解析，不触发类加载，
     *  避免在转换期加载父类带来的时序/循环问题。读不到时保守按「新版」处理（不打补丁）。 */
    private static boolean probeParentCtor() {
        String resource = PARENT + ".class";
        java.io.InputStream in = PipeAnchorCtorTransformer.class.getClassLoader().getResourceAsStream(resource);
        if(in == null) return true;   // 读不到 → 保守不打补丁
        try {
            ClassReader cr = new ClassReader(in);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for(MethodNode m : cn.methods) {
                if(m.name.equals("<init>") && m.desc.equals(TWO_ARG_DESC)) return true;
            }
            return false;
        } catch(Throwable t) {
            return true;   // 解析失败 → 保守不打补丁
        } finally {
            try { in.close(); } catch(Throwable ignored) { }
        }
    }

    private byte[] patch(byte[] bytes) {

        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        int count = 0;

        for(MethodNode m : node.methods) {
            if(!m.name.equals("<init>")) continue;

            for(AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if(!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if(call.getOpcode() != Opcodes.INVOKESPECIAL) continue;
                if(!call.owner.equals(PARENT)) continue;
                if(!call.name.equals("<init>")) continue;
                if(!call.desc.equals(TWO_ARG_DESC)) continue;

                // 栈上此刻是 [this, item, texture]：弹掉两个参数，改调无参构造
                m.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                m.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                call.desc = "()V";
                count++;
            }
        }

        if(count == 0) return bytes;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        System.out.println("[MakeNTMGreatAgain] 旧版 HBM 管锚渲染器构造已适配为无参调用 (" + count + " 处)");
        return writer.toByteArray();
    }
}