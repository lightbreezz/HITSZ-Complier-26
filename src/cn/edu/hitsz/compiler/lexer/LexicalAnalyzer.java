package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.NotImplementedException; // 这个理论上可以不要了
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // TODO: 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
        // 这里我直接读取全部的文件，后面再处理
        sourceCode = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // TODO: 自动机实现的词法分析过程
        tokens = new ArrayList<>();
        // index是当前分析到的字符在源代码中的位置，length是源代码的长度
        var index = 0;
        var length = sourceCode.length();
        while (index < length) {
            // current是当前分析到的字符
            var current = sourceCode.charAt(index);

            if (Character.isWhitespace(current)) {
                // 跳过空白字符
                index++;
                continue;
            }
            // 标识符
            if (isStart(current)) {
                var start = index;
                index++;
                // 取这个标识符
                while (index < length && isPart(sourceCode.charAt(index))) {
                    index++;
                }
                // 词素 (lexeme)
                var lexeme = sourceCode.substring(start, index);
                if ("int".equals(lexeme) || "return".equals(lexeme)) {
                    // int和return是关键字，直接加入token列表，不加入符号表
                    tokens.add(Token.simple(lexeme));
                } else {
                    tokens.add(Token.normal("id", lexeme));
                    if (!symbolTable.has(lexeme)) {
                        symbolTable.add(lexeme);
                    }
                }
                continue;
            }
            // 数字常量
            if (Character.isDigit(current)) {
                var start = index;
                index++;
                while (index < length && Character.isDigit(sourceCode.charAt(index))) {
                    index++;
                }
                var number = sourceCode.substring(start, index);
                tokens.add(Token.normal("IntConst", number));
                continue;
            }

            switch (current) {
                case '=' -> tokens.add(Token.simple("="));
                case ',' -> tokens.add(Token.simple(","));
                case ';' -> tokens.add(Token.simple("Semicolon"));
                case '+' -> tokens.add(Token.simple("+"));
                case '-' -> tokens.add(Token.simple("-"));
                case '*' -> tokens.add(Token.simple("*"));
                case '/' -> tokens.add(Token.simple("/"));
                case '(' -> tokens.add(Token.simple("("));
                case ')' -> tokens.add(Token.simple(")"));
                default -> {
                    // 其他字符暂不处理，直接跳过
                }
            }

            index++;
        }
        // 最后加入 EOF 标记
        tokens.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // TODO: 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }

    private boolean isStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private boolean isPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }
    // 源代码是字符串，tokens是列表
    private String sourceCode = "";
    private List<Token> tokens = new ArrayList<>();


}
