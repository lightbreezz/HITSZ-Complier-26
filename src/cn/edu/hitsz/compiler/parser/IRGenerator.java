package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO
        irStack.push(IRSymbol.fromToken(currentToken));
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO
        // 直接根据产生式的编号完成对应的操作
        switch (production.index()) {
            case 6 -> {
                // S -> id = E
                // E 可以是表达式，所以 rhs
                var rhs = irStack.pop().irValue();
                irStack.pop();
                var idToken = irStack.pop().token();

                var name = idToken.getText();
                if (!symbolTable.has(name)) {
                    throw new RuntimeException("No find in symbol table: %s".formatted(name));
                }
                // 生成中间代码
                var target = IRVariable.named(name);
                instructions.add(Instruction.createMov(target, rhs));
                // 没有返回值，放一个空的符号上去
                irStack.push(IRSymbol.empty());
            }

            case 7 -> {
                // S -> return E
                var returnValue = irStack.pop().irValue();
                irStack.pop();
                instructions.add(Instruction.createRet(returnValue));
                irStack.push(IRSymbol.empty());
            }

            case 8 -> {
                // E -> E + A
                var rhs = irStack.pop().irValue();
                irStack.pop();
                var lhs = irStack.pop().irValue();
                // 创建一个临时变量
                var result = IRVariable.temp();
                instructions.add(Instruction.createAdd(result, lhs, rhs));
                irStack.push(IRSymbol.fromIRValue(result));
            }

            case 9 -> {
                // E -> E - A
                var rhs = irStack.pop().irValue();
                irStack.pop();
                var lhs = irStack.pop().irValue();

                var result = IRVariable.temp();
                instructions.add(Instruction.createSub(result, lhs, rhs));
                irStack.push(IRSymbol.fromIRValue(result));
            }

            case 10, 12 -> {
                // E -> A | A -> B
                final var value = irStack.pop().irValue();
                irStack.push(IRSymbol.fromIRValue(value));
            }

            case 11 -> {
                // A -> A * B
                final var rhs = irStack.pop().irValue();
                irStack.pop();
                final var lhs = irStack.pop().irValue();

                final var result = IRVariable.temp();
                instructions.add(Instruction.createMul(result, lhs, rhs));
                irStack.push(IRSymbol.fromIRValue(result));
            }

            case 13 -> {
                // B -> ( E )
                irStack.pop();
                final var value = irStack.pop().irValue();
                irStack.pop();
                irStack.push(IRSymbol.fromIRValue(value));
            }

            case 14 -> {
                // B -> id
                final var idToken = irStack.pop().token();
                final var name = idToken.getText();
                if (!symbolTable.has(name)) {
                    throw new RuntimeException("No found in symbol table: %s".formatted(name));
                }
                irStack.push(IRSymbol.fromIRValue(IRVariable.named(name)));
            }

            case 15 -> {
                // B -> IntConst
                final var intConstToken = irStack.pop().token();
                // of 是构造函数
                final var immediate = IRImmediate.of(Integer.parseInt(intConstToken.getText()));
                irStack.push(IRSymbol.fromIRValue(immediate));
            }

            default -> {
                final var bodySize = production.body().size();
                for (int i = 0; i < bodySize; i++) {
                    irStack.pop();
                }
                irStack.push(IRSymbol.empty());
            }
        }
    }


    @Override
    public void whenAccept(Status currentStatus) {
        // TODO
        // 不需要处理
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO
        symbolTable = table;
    }

    public List<Instruction> getIR() {
        // TODO
        return instructions;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }

    private SymbolTable symbolTable;
    private List<Instruction> instructions = new ArrayList<>();
    private Deque<IRSymbol> irStack = new ArrayDeque<>();
    private static class IRSymbol {
        static IRSymbol fromToken(Token token) {
            return new IRSymbol(token, null);
        }

        static IRSymbol fromIRValue(IRValue value) {
            return new IRSymbol(null, value);
        }

        static IRSymbol empty() {
            return new IRSymbol(null, null);
        }

        Token token() {
            if (token == null) {
                throw new RuntimeException("IR symbol does not include token");
            }
            return token;
        }

        IRValue irValue() {
            if (value == null) {
                throw new RuntimeException("IR symbol does not include IR value");
            }
            return value;
        }

        private IRSymbol(Token token, IRValue value) {
            this.token = token;
            this.value = value;
        }

        private final Token token;
        private final IRValue value;
    }
}

