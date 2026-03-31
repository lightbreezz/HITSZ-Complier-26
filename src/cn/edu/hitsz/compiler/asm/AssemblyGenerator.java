package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        // TODO: 读入前端提供的中间代码并生成所需要的信息
        loweredInstructions.clear();
        asmLines.clear();
        valueToReg.clear();
        regToValue.clear();

        for (final var instruction : originInstructions) {
            switch (instruction.getKind()) {
                case ADD, SUB, MUL -> lowerBinary(instruction);
                case MOV -> loweredInstructions.add(instruction);
                case RET -> {
                    loweredInstructions.add(instruction);
                    // RET 之后的代码是不可达的, 可直接忽略
                    return;
                }
                default -> throw new RuntimeException("Unknown IR kind: %s".formatted(instruction.getKind()));
            }
        }
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        // TODO: 执行寄存器分配与代码生成
        asmLines.clear();
        asmLines.add(".text");

        for (int index = 0; index < loweredInstructions.size(); index++) {
            final var instruction = loweredInstructions.get(index);

            final String asm = switch (instruction.getKind()) {
                case MOV -> emitMov(instruction, index);
                case ADD, SUB, MUL -> emitBinary(instruction, index);
                case RET -> emitRet(instruction, index);
            };

            asmLines.add("    %s\t\t#  %s".formatted(asm, instruction));
            releaseDeadRegisters(index + 1);

            if (instruction.getKind() == InstructionKind.RET) {
                break;
            }
        }

        System.out.println("Assembly Generate over");
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        // TODO: 输出汇编代码到文件
        FileUtils.writeLines(path, asmLines);
    }

    private void lowerBinary(Instruction instruction) {
        final var kind = instruction.getKind();
        final var result = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        final var lhsImmediate = lhs.isImmediate();
        final var rhsImmediate = rhs.isImmediate();

        if (lhsImmediate && rhsImmediate) {
            final var lv = ((IRImmediate) lhs).getValue();
            final var rv = ((IRImmediate) rhs).getValue();
            final var folded = switch (kind) {
                case ADD -> lv + rv;
                case SUB -> lv - rv;
                case MUL -> lv * rv;
                default -> throw new RuntimeException("Unexpected binary kind: %s".formatted(kind));
            };
            loweredInstructions.add(Instruction.createMov(result, IRImmediate.of(folded)));
            return;
        }

        if (kind == InstructionKind.ADD) {
            if (lhsImmediate && rhs.isIRVariable()) {
                // 加法可交换, 转为 var + imm 便于生成 addi
                loweredInstructions.add(Instruction.createAdd(result, rhs, lhs));
            } else {
                loweredInstructions.add(instruction);
            }
            return;
        }

        if (kind == InstructionKind.SUB) {
            if (lhsImmediate && rhs.isIRVariable()) {
                // imm - var: 需要先把 imm 装入临时变量
                final var temp = IRVariable.temp();
                loweredInstructions.add(Instruction.createMov(temp, lhs));
                loweredInstructions.add(Instruction.createSub(result, temp, rhs));
            } else if (rhsImmediate && lhs.isIRVariable()) {
                // var - imm: 转换为 var + (-imm), 直接映射到 addi
                final var neg = -((IRImmediate) rhs).getValue();
                loweredInstructions.add(Instruction.createAdd(result, lhs, IRImmediate.of(neg)));
            } else {
                loweredInstructions.add(instruction);
            }
            return;
        }

        if (kind == InstructionKind.MUL) {
            if (lhsImmediate && rhs.isIRVariable()) {
                final var temp = IRVariable.temp();
                loweredInstructions.add(Instruction.createMov(temp, lhs));
                loweredInstructions.add(Instruction.createMul(result, temp, rhs));
            } else if (rhsImmediate && lhs.isIRVariable()) {
                final var temp = IRVariable.temp();
                loweredInstructions.add(Instruction.createMov(temp, rhs));
                loweredInstructions.add(Instruction.createMul(result, lhs, temp));
            } else {
                loweredInstructions.add(instruction);
            }
            return;
        }

        throw new RuntimeException("Unsupported binary kind: %s".formatted(kind));
    }

    private String emitMov(Instruction instruction, int index) {
        final var result = instruction.getResult();
        final var dst = ensureRegister(result, index);

        final var from = instruction.getFrom();
        if (from.isImmediate()) {
            return "li %s, %s".formatted(dst, from);
        }

        final var src = ensureRegister((IRVariable) from, index);
        return "mv %s, %s".formatted(dst, src);
    }

    private String emitBinary(Instruction instruction, int index) {
        final var kind = instruction.getKind();
        final var result = instruction.getResult();
        final var dst = ensureRegister(result, index);

        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        if (kind == InstructionKind.ADD && rhs.isImmediate()) {
            final var lhsReg = ensureRegister((IRVariable) lhs, index);
            return "addi %s, %s, %s".formatted(dst, lhsReg, rhs);
        }

        final var lhsReg = ensureRegister((IRVariable) lhs, index);
        final var rhsReg = ensureRegister((IRVariable) rhs, index);
        return switch (kind) {
            case ADD -> "add %s, %s, %s".formatted(dst, lhsReg, rhsReg);
            case SUB -> "sub %s, %s, %s".formatted(dst, lhsReg, rhsReg);
            case MUL -> "mul %s, %s, %s".formatted(dst, lhsReg, rhsReg);
            default -> throw new RuntimeException("Unexpected binary kind: %s".formatted(kind));
        };
    }

    private String emitRet(Instruction instruction, int index) {
        final var returnValue = instruction.getReturnValue();
        if (returnValue.isImmediate()) {
            return "li a0, %s".formatted(returnValue);
        }

        final var src = ensureRegister((IRVariable) returnValue, index);
        return "mv a0, %s".formatted(src);
    }

    private Register ensureRegister(IRVariable value, int index) {
        final var mapped = valueToReg.get(value);
        if (mapped != null) {
            return mapped;
        }

        Register selected = null;
        for (final var reg : Register.values()) {
            if (!regToValue.containsKey(reg)) {
                selected = reg;
                break;
            }
        }

        if (selected == null) {
            for (final var entry : regToValue.entrySet()) {
                if (!isUsedLater(entry.getValue(), index)) {
                    selected = entry.getKey();
                    break;
                }
            }
        }

        if (selected == null) {
            throw new RuntimeException("No free register for %s".formatted(value));
        }

        bind(value, selected);
        return selected;
    }

    private void bind(IRVariable value, Register reg) {
        final var oldReg = valueToReg.get(value);
        if (oldReg != null) {
            regToValue.remove(oldReg);
        }

        final var oldValue = regToValue.get(reg);
        if (oldValue != null) {
            valueToReg.remove(oldValue);
        }

        valueToReg.put(value, reg);
        regToValue.put(reg, value);
    }

    private void releaseDeadRegisters(int startIndex) {
        final var deadValues = new ArrayList<IRVariable>();
        for (final var value : valueToReg.keySet()) {
            if (!isUsedLater(value, startIndex)) {
                deadValues.add(value);
            }
        }

        for (final var value : deadValues) {
            final var reg = valueToReg.remove(value);
            if (reg != null) {
                regToValue.remove(reg);
            }
        }
    }

    private boolean isUsedLater(IRVariable value, int startIndex) {
        for (int i = startIndex; i < loweredInstructions.size(); i++) {
            if (usesValue(loweredInstructions.get(i), value)) {
                return true;
            }
        }
        return false;
    }

    private boolean usesValue(Instruction instruction, IRVariable value) {
        return switch (instruction.getKind()) {
            case MOV -> instruction.getFrom().equals(value);
            case RET -> instruction.getReturnValue().equals(value);
            case ADD, SUB, MUL -> instruction.getLHS().equals(value) || instruction.getRHS().equals(value);
        };
    }

    private enum Register {
        t0, t1, t2, t3, t4, t5, t6
    }

    private final List<Instruction> loweredInstructions = new ArrayList<>();
    private final List<String> asmLines = new ArrayList<>();
    private final Map<IRVariable, Register> valueToReg = new HashMap<>();
    private final Map<Register, IRVariable> regToValue = new HashMap<>();
}

