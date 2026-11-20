package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * 在 HBM 流体容器的危险品检查入口插入自家闸门，供「地核线」流体限制容器用。
 *
 * <h2>补哪两个类</h2>
 * <ul>
 *   <li>{@code TileEntityBarrel#checkFluidInteraction()} —— 所有小桶（钢/铁/塑料/腐蚀/红桶等）
 *       与磁约束反物质容器都走这一个基类实现；</li>
 *   <li>{@code TileEntityMachineFluidTank#checkFluidInteraction()} —— 普通储罐自己重写了，
 *       且方法里不调 super，必须单独补。</li>
 * </ul>
 * 「巨尻」「旧版巨尻-9000」「重型反物质储罐」以及附属的大型储罐都各自重写了这个方法，
 * 不会被补到，正好等于放行 —— 这正是需求里要放行的几种。
 *
 * <p>方法名是 HBM 自己的，不随 MC 混淆变化（同一套做法见 {@link ArcFurnaceTransformer} 里的
 * {@code damage}），所以按名字 + {@code ()V} 描述符定位即可。</p>
 */
public class CoreFluidTankTransformer implements IClassTransformer {

    private static final String HOOK = "com/makentmgreatagain/hook/CoreFluidTankHook";
    private static final String METHOD = "checkFluidInteraction";

    private static final String[] TARGETS = {
            "com.hbm.tileentity.machine.storage.TileEntityBarrel",
            "com.hbm.tileentity.machine.storage.TileEntityMachineFluidTank"
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {

        if(basicClass == null || transformedName == null) return basicClass;

        boolean target = false;
        for(String t : TARGETS) {
            if(transformedName.equals(t)) { target = true; break; }
        }
        if(!target) return basicClass;

        try {
            return patch(basicClass, transformedName);
        } catch(Throwable t) {
            // 换了 HBM 版本、字节码形态对不上时放弃补丁，返回原字节码，
            // 而不是让异常冒到 LaunchWrapper 崩掉启动
            System.out.println("[MakeNTMGreatAgain] 地核流体容器闸门补丁跳过（与本版本字节码不匹配）: " + transformedName);
            t.printStackTrace();
            return basicClass;
        }
    }

    private byte[] patch(byte[] bytes, String cls) {

        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        int count = 0;

        for(MethodNode m : node.methods) {

            if(!m.name.equals(METHOD)) continue;
            if(!m.desc.equals("()V")) continue;
            if((m.access & Opcodes.ACC_STATIC) != 0) continue;
            if((m.access & Opcodes.ACC_ABSTRACT) != 0) continue;

            AbstractInsnNode first = m.instructions.getFirst();
            while(first != null && (first instanceof LabelNode || first instanceof LineNumberNode)) {
                first = first.getNext();
            }
            if(first == null) continue;

            LabelNode cont = new LabelNode();
            InsnList ins = new InsnList();
            ins.add(new VarInsnNode(Opcodes.ALOAD, 0));                     // this
            ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
                    "guard", "(Ljava/lang/Object;)Z", false));
            ins.add(new JumpInsnNode(Opcodes.IFEQ, cont));                  // 放行 → 继续原逻辑
            ins.add(new InsnNode(Opcodes.RETURN));                          // 已处理 → 直接返回
            ins.add(cont);
            // 跳转目标帧 = 方法入口帧（局部变量未变、栈为空）。
            // 只用 COMPUTE_MAXS，不重算帧，这一帧必须手动补，否则生产环境 VerifyError。
            ins.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

            m.instructions.insertBefore(first, ins);
            count++;
        }

        if(count == 0) {
            System.out.println("[MakeNTMGreatAgain] 警告: " + cls + " 未找到 checkFluidInteraction，地核流体容器限制不生效");
            return bytes;
        }

        System.out.println("[MakeNTMGreatAgain] " + cls + " 已加装地核流体容器闸门（" + count + " 处）");

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
