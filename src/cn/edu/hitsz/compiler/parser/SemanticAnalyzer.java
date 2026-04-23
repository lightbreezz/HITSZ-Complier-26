package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayDeque;
import java.util.Deque;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {

    @Override
    public void whenAccept(Status currentStatus) {
        // TODO: 该过程在遇到 Accept 时要采取的代码动作
        // 不需要执行任何动作
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        // TODO: 该过程在遇到 reduce production 时要采取的代码动作
        switch (production.index()) {
            case 4 -> {
                // S -> D id
                // int x;
                final var idToken = semanticStack.pop().token();
                final var Dtype = semanticStack.pop().type();
                // 在符号表中找到 x 的符号表项, 将它的类型设置为 Dtype
                symbolTable.get(idToken.getText()).setType(Dtype);
                // 由于 S （没有返回值：粗糙理解）, 就直接放一个空的语义符号上去
                semanticStack.push(SemanticSymbol.empty());
            }

            case 5 -> {
                // D -> int
                final var token = semanticStack.pop().token();
                if (!"int".equals(token.getKindId())) {
                    throw new RuntimeException("Unexpected token for production D -> int: %s".formatted(token));
                }
                semanticStack.push(SemanticSymbol.fromType(SourceCodeType.Int));
            }

            default -> {
                // 其他产生式不需要进行语义分析, 直接将对应数量的语义符号弹出
                final var bodySize = production.body().size();
                for (int i = 0; i < bodySize; i++) {
                    semanticStack.pop();
                }
                // 然后放一个空的语义符号上去
                semanticStack.push(SemanticSymbol.empty());
            }
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        // TODO: 该过程在遇到 shift 时要采取的代码动作
        semanticStack.push(SemanticSymbol.fromToken(currentToken));
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // TODO: 设计你可能需要的符号表存储结构
        // 如果需要使用符号表的话, 可以将它或者它的一部分信息存起来, 比如使用一个成员变量存储
        symbolTable = table;
    }

    private SymbolTable symbolTable;
    private Deque<SemanticSymbol> semanticStack = new ArrayDeque<>();

    private static class SemanticSymbol {
        static SemanticSymbol fromToken(Token token) {
            return new SemanticSymbol(token, null);
        }

        static SemanticSymbol fromType(SourceCodeType type) {
            return new SemanticSymbol(null, type);
        }

        static SemanticSymbol empty() {
            return new SemanticSymbol(null, null);
        }

        Token token() {
            if (token == null) {
                throw new RuntimeException("Semantic symbol does not include token");
            }
            return token;
        }

        SourceCodeType type() {
            if (type == null) {
                throw new RuntimeException("Semantic symbol does not include type");
            }
            return type;
        }

        private SemanticSymbol(Token token, SourceCodeType type) {
            this.token = token;
            this.type = type;
        }

        private Token token;
        private SourceCodeType type;
    }
}

