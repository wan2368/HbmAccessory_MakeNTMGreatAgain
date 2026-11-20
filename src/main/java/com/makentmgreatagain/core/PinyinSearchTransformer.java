package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * 把 {@code String.contains(CharSequence)} 换成 {@code PinyinSearch.contains}。
 *
 * <p>覆盖范围：{@code com.hbm.inventory.gui.*}、{@code com.hbm.inventory.container.*}
 * 与 {@code com.hbm.inventory.recipes.loader.GenericRecipe}（配方名）。其它类一律不动。
 * <b>NEI 不覆盖</b>：它走 {@code Pattern.matcher().find()}，无法用同一处替换兼容。</p>
 *
 * <p>只替换签名完全一致的 {@code INVOKEVIRTUAL java/lang/String.contains}；
 * 命中数为 0 或抛异常时原样返回，不影响启动。</p>
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
