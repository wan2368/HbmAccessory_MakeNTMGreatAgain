package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** 把 ItemBlueprints 的 tooltip 换成分页版本（只影响显示，不影响蓝图本身）。 */
public class AnvilTemplateTransformer implements IClassTransformer {

    private static final String TARGET = "com.hbm.items.machine.ItemBlueprints";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if(basicClass == null || !TARGET.equals(transformedName)) return basicClass;

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            for(int i = 0; i < node.methods.size(); i++) {
                MethodNode old = node.methods.get(i);
                if(!old.name.equals("addInformation") && !old.name.equals("func_77624_a")) continue;
                if(!old.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/util/List;Z)V")) continue;

                MethodNode replacement = new MethodNode(Opcodes.ACC_PUBLIC, old.name, old.desc, null, null);
                replacement.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));   // ItemStack stack
                replacement.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));   // List list
                replacement.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "com/makentmgreatagain/client/AnvilTemplateTooltip",
                        "addInformation",
                        "(Lnet/minecraft/item/ItemStack;Ljava/util/List;)V", false));
                replacement.instructions.add(new InsnNode(Opcodes.RETURN));
                replacement.maxStack = 2;
                replacement.maxLocals = 4;

                node.methods.set(i, replacement);
                break;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();

        } catch(Throwable t) {
            t.printStackTrace();
            return basicClass;
        }
    }
}
