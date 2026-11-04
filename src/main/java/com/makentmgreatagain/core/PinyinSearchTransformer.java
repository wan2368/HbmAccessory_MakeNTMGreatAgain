package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * 把 GUI / Container 里的 String.contains 换成拼音搜索。
 * 只改 com.hbm.inventory.gui 和 com.hbm.inventory.container 下的类，
 * 不会影响其它模组或 HBM 的其它逻辑。
 */
public class PinyinSearchTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if(basicClass == null || transformedName == null) return basicClass;
        if(!transformedName.startsWith("com.hbm.inventory.gui.")
                && !transformedName.startsWith("com.hbm.inventory.container.")
                && !transformedName.equals("com.hbm.inventory.recipes.loader.GenericRecipe")) return basicClass;

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            boolean changed = false;

            for(MethodNode method : node.methods) {
                for(AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if(!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;

                    if(call.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                    if(!call.owner.equals("java/lang/String")) continue;
                    if(!call.name.equals("contains")) continue;
                    if(!call.desc.equals("(Ljava/lang/CharSequence;)Z")) continue;

                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = "com/makentmgreatagain/util/PinyinSearch";
                    call.name = "contains";
                    call.desc = "(Ljava/lang/String;Ljava/lang/CharSequence;)Z";
                    call.itf = false;
                    changed = true;
                }
            }

            if(!changed) return basicClass;

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();

        } catch(Throwable t) {
            t.printStackTrace();
            return basicClass;
        }
    }
}
