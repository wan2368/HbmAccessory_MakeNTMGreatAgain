package com.makentmgreatagain.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * PEEK 并行运算升级芯片统一 ASM 注入器（覆盖 6 台目标机器）。
 *
 * <p>仅在目标方法关键入口/完成点插入极简静态调用，全部并行判定、1.25×N 功耗缩放与防吞降级逻辑
 * 均委托给 {@link com.makentmgreatagain.hook.ParallelUpgradeHook}。
 * 若未来本体更新导致个别方法签名变化，只跳过该目标并打印警告日志，保证客户端正常启动。</p>
 */
public class ParallelUpgradeTransformer implements IClassTransformer {

	private static final Logger LOG = LogManager.getLogger("MNGA-ParallelASM");
	private static final String HOOK = "com/makentmgreatagain/hook/ParallelUpgradeHook";

	private static final Set<String> TARGETS = new HashSet<String>(Arrays.asList(
			"com.hbm.tileentity.machine.TileEntityMachineCrystallizer",
			"com.hbm.tileentity.machine.TileEntityMachineMixer",
			"com.hbm.tileentity.machine.TileEntityMachineCompressorBase",
			"com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor",
			"com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier",
			"com.hbm.tileentity.machine.TileEntityMachineElectricFurnace"
	));

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if(basicClass == null || !TARGETS.contains(transformedName)) return basicClass;

		try {
			ClassNode cn = new ClassNode();
			new ClassReader(basicClass).accept(cn, 0);

			boolean changed = false;
			if("com.hbm.tileentity.machine.TileEntityMachineCrystallizer".equals(transformedName)) {
				changed = patchCrystallizer(cn);
			} else if("com.hbm.tileentity.machine.TileEntityMachineMixer".equals(transformedName)) {
				changed = patchMixer(cn);
			} else if("com.hbm.tileentity.machine.TileEntityMachineCompressorBase".equals(transformedName)) {
				changed = patchCompressorBase(cn);
			} else if("com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor".equals(transformedName)) {
				changed = patchLiquefactor(cn);
			} else if("com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier".equals(transformedName)) {
				changed = patchSolidifier(cn);
			} else if("com.hbm.tileentity.machine.TileEntityMachineElectricFurnace".equals(transformedName)) {
				changed = patchElectricFurnace(cn);
			}

			if(!changed) {
				LOG.warn("[MNGA-ParallelASM] 未找到预期的注入点，已安全跳过: " + transformedName);
				return basicClass;
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			return cw.toByteArray();
		} catch(Throwable t) {
			LOG.warn("[MNGA-ParallelASM] 注入失败，已安全回退原类: " + transformedName, t);
			return basicClass;
		}
	}

	private static MethodNode findMethod(ClassNode cn, String name, String desc) {
		for(MethodNode m : cn.methods) {
			if(name.equals(m.name) && (desc == null || desc.equals(m.desc))) return m;
		}
		return null;
	}

	private static MethodNode findUpdateEntity(ClassNode cn) {
		for(MethodNode m : cn.methods) {
			if(("updateEntity".equals(m.name) || "func_145845_h".equals(m.name)) && "()V".equals(m.desc)) return m;
		}
		return null;
	}

	private static void injectBeforeAllReturnsInt(MethodNode m, String hookMethod, String desc) {
		ListIterator<AbstractInsnNode> it = m.instructions.iterator();
		while(it.hasNext()) {
			AbstractInsnNode insn = it.next();
			if(insn.getOpcode() == Opcodes.IRETURN) {
				InsnList list = new InsnList();
				list.add(new VarInsnNode(Opcodes.ALOAD, 0));
				list.add(new InsnNode(Opcodes.SWAP));
				list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hookMethod, desc, false));
				m.instructions.insertBefore(insn, list);
			}
		}
	}

	private static boolean hookAfterFieldPut(MethodNode m, String owner, String fieldName, String fieldDesc,
			int occurrence, String hookMethod, String hookDesc) {
		int seen = 0;
		ListIterator<AbstractInsnNode> it = m.instructions.iterator();
		while(it.hasNext()) {
			AbstractInsnNode insn = it.next();
			if(insn.getOpcode() == Opcodes.PUTFIELD && insn instanceof FieldInsnNode) {
				FieldInsnNode fi = (FieldInsnNode) insn;
				if(owner.equals(fi.owner) && fieldName.equals(fi.name) && fieldDesc.equals(fi.desc)) {
					if(seen == occurrence) {
						InsnList list = new InsnList();
						list.add(new VarInsnNode(Opcodes.ALOAD, 0));
						list.add(new VarInsnNode(Opcodes.ALOAD, 0));
						list.add(new VarInsnNode(Opcodes.ALOAD, 0));
						list.add(new FieldInsnNode(Opcodes.GETFIELD, owner, fieldName, fieldDesc));
						list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hookMethod, hookDesc, false));
						list.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, fieldName, fieldDesc));
						m.instructions.insert(insn, list);
						return true;
					}
					seen++;
				}
			}
		}
		return false;
	}

	private static boolean hookAfterMethodCall(MethodNode m, String targetOwner, String targetName, String targetDesc,
			String hookMethod, String hookDesc) {
		boolean hooked = false;
		ListIterator<AbstractInsnNode> it = m.instructions.iterator();
		while(it.hasNext()) {
			AbstractInsnNode insn = it.next();
			if(insn instanceof MethodInsnNode) {
				MethodInsnNode mi = (MethodInsnNode) insn;
				if(targetOwner.equals(mi.owner) && targetName.equals(mi.name) && targetDesc.equals(mi.desc)) {
					InsnList list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, 0));
					list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hookMethod, hookDesc, false));
					m.instructions.insert(insn, list);
					hooked = true;
				}
			}
		}
		return hooked;
	}

	/* ========================== 6 台目标机注入实现 ========================== */

	private static boolean patchCrystallizer(ClassNode cn) {
		String owner = "com/hbm/tileentity/machine/TileEntityMachineCrystallizer";
		MethodNode mPow = findMethod(cn, "getPowerRequired", "()I");
		MethodNode mUpd = findUpdateEntity(cn);
		if(mPow == null || mUpd == null) return false;

		injectBeforeAllReturnsInt(mPow, "scaleCrystallizerPower", "(L" + owner + ";I)I");
		return hookAfterMethodCall(mUpd, owner, "processItem", "()V",
				"completeCrystallizerExtra", "(L" + owner + ";)V");
	}

	private static boolean patchMixer(ClassNode cn) {
		String owner = "com/hbm/tileentity/machine/TileEntityMachineMixer";
		MethodNode mCon = findMethod(cn, "getConsumption", "()I");
		MethodNode mUpd = findUpdateEntity(cn);
		if(mCon == null || mUpd == null) return false;

		injectBeforeAllReturnsInt(mCon, "scaleMixerConsumption", "(L" + owner + ";I)I");
		return hookAfterMethodCall(mUpd, owner, "process", "()V",
				"completeMixerExtra", "(L" + owner + ";)V");
	}

	private static boolean patchCompressorBase(ClassNode cn) {
		String owner = "com/hbm/tileentity/machine/TileEntityMachineCompressorBase";
		MethodNode m = findUpdateEntity(cn);
		if(m == null) return false;

		// powerRequirement *= ((overLevel * 2) + 1) 是第 2 次对 powerRequirement 的 PUTFIELD
		boolean p1 = hookAfterFieldPut(m, owner, "powerRequirement", "I", 1,
				"scaleCompressorPower", "(L" + owner + ";I)I");
		boolean p2 = hookAfterMethodCall(m, owner, "process", "()V",
				"completeCompressorExtra", "(L" + owner + ";)V");
		return p1 && p2;
	}

	private static boolean patchLiquefactor(ClassNode cn) {
		String owner = "com/hbm/tileentity/machine/oil/TileEntityMachineLiquefactor";
		MethodNode mUpd = findUpdateEntity(cn);
		MethodNode mProc = findMethod(cn, "process", "()V");
		if(mUpd == null || mProc == null) return false;

		boolean p1 = hookAfterFieldPut(mUpd, owner, "usage", "I", 0,
				"scaleLiquefactorUsage", "(L" + owner + ";I)I");
		boolean p2 = false;
		ListIterator<AbstractInsnNode> it = mProc.instructions.iterator();
		while(it.hasNext()) {
			AbstractInsnNode insn = it.next();
			if(insn instanceof MethodInsnNode
					&& ("markDirty".equals(((MethodInsnNode) insn).name) || "func_70296_d".equals(((MethodInsnNode) insn).name))) {
				InsnList list = new InsnList();
				list.add(new VarInsnNode(Opcodes.ALOAD, 0));
				list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "completeLiquefactorExtra", "(L" + owner + ";)V", false));
				mProc.instructions.insert(insn, list);
				p2 = true;
				break;
			}
		}
		return p1 && p2;
	}

	private static boolean patchSolidifier(ClassNode cn) {
		String owner = "com/hbm/tileentity/machine/oil/TileEntityMachineSolidifier";
		MethodNode mUpd = findUpdateEntity(cn);
		MethodNode mProc = findMethod(cn, "process", "()V");
		if(mUpd == null || mProc == null) return false;

		boolean p1 = hookAfterFieldPut(mUpd, owner, "usage", "I", 0,
				"scaleSolidifierUsage", "(L" + owner + ";I)I");
		boolean p2 = false;
		ListIterator<AbstractInsnNode> it = mProc.instructions.iterator();
		while(it.hasNext()) {
			AbstractInsnNode insn = it.next();
			if(insn instanceof MethodInsnNode
					&& ("markDirty".equals(((MethodInsnNode) insn).name) || "func_70296_d".equals(((MethodInsnNode) insn).name))) {
				InsnList list = new InsnList();
				list.add(new VarInsnNode(Opcodes.ALOAD, 0));
				list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "completeSolidifierExtra", "(L" + owner + ";)V", false));
				mProc.instructions.insert(insn, list);
				p2 = true;
				break;
			}
		}
		return p1 && p2;
	}

	private static boolean patchElectricFurnace(ClassNode cn) {
		String owner = "com/hbm/tileentity/machine/TileEntityMachineElectricFurnace";
		MethodNode m = findUpdateEntity(cn);
		if(m == null) return false;

		// consumption -= powerLevel * 15 是第 3 次对 consumption 的 PUTFIELD
		boolean p1 = hookAfterFieldPut(m, owner, "consumption", "I", 2,
				"scaleElectricFurnaceConsumption", "(L" + owner + ";I)I");
		boolean p2 = hookAfterMethodCall(m, owner, "processItem", "()V",
				"completeElectricFurnaceExtra", "(L" + owner + ";)V");
		return p1 && p2;
	}
}
