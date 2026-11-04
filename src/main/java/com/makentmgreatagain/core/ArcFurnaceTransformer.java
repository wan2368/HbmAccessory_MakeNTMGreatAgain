package com.makentmgreatagain.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;

/**
 * 不 import 任何 net.minecraft / com.hbm 类，只用字符串。
 * coremod 由系统类加载器加载，import MC 类会导致重复加载与 ClassCastException。
 */
public class ArcFurnaceTransformer implements IClassTransformer {

	private static final String HOOK = "com/makentmgreatagain/hook/ElectrodeHook";

	/** 判定逻辑所在类。以后支持航天版，往这个数组加类名即可。 */
	private static final String[] CHECK_CLASSES = {
		"com.hbm.tileentity.machine.TileEntityMachineArcFurnaceLarge",
		"com.hbm.inventory.container.ContainerMachineArcFurnaceLarge"
	};

	private static final String DAMAGE_CLASS = "com.hbm.items.machine.ItemArcElectrode";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (basicClass == null) return null;

		for (String target : CHECK_CLASSES) {
			if (transformedName.equals(target)) return patchChecks(basicClass, transformedName);
		}
		if (transformedName.equals(DAMAGE_CLASS)) return patchDamage(basicClass);
		return basicClass;
	}

	/** 规则一：GETSTATIC arc_electrode + IF_ACMPxx → INVOKESTATIC hook + IFNExx */
	private byte[] patchChecks(byte[] bytes, String cls) {
		ClassReader reader = new ClassReader(bytes);
		ClassNode node = new ClassNode();
		reader.accept(node, 0);

		int count = 0;
		for (MethodNode m : node.methods) {
			ListIterator<AbstractInsnNode> it = m.instructions.iterator();
			while (it.hasNext()) {
				AbstractInsnNode insn = it.next();
				if (insn.getOpcode() != Opcodes.GETSTATIC) continue;

				FieldInsnNode f = (FieldInsnNode) insn;
				// 只匹配 owner + 字段名，不匹配描述符：这样混淆前后都能命中
				if (!f.owner.equals("com/hbm/items/ModItems")) continue;
				if (!f.name.equals("arc_electrode")) continue;

				AbstractInsnNode next = f.getNext();
				if (next == null) continue;
				int op = next.getOpcode();
				if (op != Opcodes.IF_ACMPEQ && op != Opcodes.IF_ACMPNE) continue;

				JumpInsnNode jump = (JumpInsnNode) next;

				m.instructions.set(f, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
					"isArcElectrode", "(Ljava/lang/Object;)Z", false));
				m.instructions.set(jump, new JumpInsnNode(
					op == Opcodes.IF_ACMPEQ ? Opcodes.IFNE : Opcodes.IFEQ, jump.label));
				count++;
			}
		}

		if (count == 0) {
			System.out.println("[MakeNTMGreatAgain] 警告: " + cls + " 未找到电极判定，补丁可能失效");
			return bytes;
		}
		System.out.println("[MakeNTMGreatAgain] " + cls + " 已替换 " + count + " 处电极判定");

		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** 规则二：damage() 开头插入 if (hook.isCmbElectrode(stack)) return false; */
	private byte[] patchDamage(byte[] bytes) {
		ClassReader reader = new ClassReader(bytes);
		ClassNode node = new ClassNode();
		reader.accept(node, 0);

		boolean done = false;
		for (MethodNode m : node.methods) {
			if (!m.name.equals("damage")) continue;
			if (!m.desc.endsWith(")Z")) continue;
			// 不匹配完整 desc，因为 ItemStack 的混淆名在开发/生产环境不同
			if (org.objectweb.asm.Type.getArgumentTypes(m.desc).length != 1) continue;

			AbstractInsnNode first = m.instructions.getFirst();
			while (first != null && (first instanceof LabelNode || first instanceof LineNumberNode)) {
				first = first.getNext();
			}
			if (first == null) continue;

			LabelNode cont = new LabelNode();
			InsnList ins = new InsnList();
			ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
			ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK,
				"isCmbElectrode", "(Ljava/lang/Object;)Z", false));
			ins.add(new JumpInsnNode(Opcodes.IFEQ, cont));
			ins.add(new InsnNode(Opcodes.ICONST_0));
			ins.add(new InsnNode(Opcodes.IRETURN));
			ins.add(cont);
			// 跳转目标的帧 = 方法入口帧（局部变量未变、栈为空）
			ins.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

			m.instructions.insertBefore(first, ins);
			done = true;
			break;
		}

		if (!done) {
			System.out.println("[MakeNTMGreatAgain] 警告: 未找到 ItemArcElectrode.damage，CMB 电极会有耐久");
			return bytes;
		}
		System.out.println("[MakeNTMGreatAgain] ItemArcElectrode.damage 已短路，CMB 电极无耐久");

		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}
}
