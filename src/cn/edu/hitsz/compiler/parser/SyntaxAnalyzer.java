package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Action;
import cn.edu.hitsz.compiler.parser.table.LRTable;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

//TODO: 实验二: 实现 LR 语法分析驱动程序

/**
 * LR 语法分析驱动程序
 * <br>
 * 该程序接受词法单元串与 LR 分析表 (action 和 goto 表), 按表对词法单元流进行分析, 执行对应动作, 并在执行动作时通知各注册的观察者.
 * <br>
 * 你应当按照被挖空的方法的文档实现对应方法, 你可以随意为该类添加你需要的私有成员对象, 但不应该再为此类添加公有接口, 也不应该改动未被挖空的方法,
 * 除非你已经同助教充分沟通, 并能证明你的修改的合理性, 且令助教确定可能被改动的评测方法. 随意修改该类的其它部分有可能导致自动评测出错而被扣分.
 */
public class SyntaxAnalyzer {
    private final SymbolTable symbolTable;
    private final List<ActionObserver> observers = new ArrayList<>();


    public SyntaxAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * 注册新的观察者
     *
     * @param observer 观察者
     */
    public void registerObserver(ActionObserver observer) {
        observers.add(observer);
        observer.setSymbolTable(symbolTable);
    }

    /**
     * 在执行 shift 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param currentToken  当前词法单元
     */
    public void callWhenInShift(Status currentStatus, Token currentToken) {
        for (final var listener : observers) {
            listener.whenShift(currentStatus, currentToken);
        }
    }

    /**
     * 在执行 reduce 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     * @param production    待规约的产生式
     */
    public void callWhenInReduce(Status currentStatus, Production production) {
        for (final var listener : observers) {
            listener.whenReduce(currentStatus, production);
        }
    }

    /**
     * 在执行 accept 动作时通知各个观察者
     *
     * @param currentStatus 当前状态
     */
    public void callWhenInAccept(Status currentStatus) {
        for (final var listener : observers) {
            listener.whenAccept(currentStatus);
        }
    }

    public void loadTokens(Iterable<Token> tokens) {
        // TODO: 加载词法单元
        // 你可以自行选择要如何存储词法单元, 譬如使用迭代器, 或是栈, 或是干脆使用一个 list 全存起来
        // 需要注意的是, 在实现驱动程序的过程中, 你会需要面对只读取一个 token 而不能消耗它的情况,
        // 在自行设计的时候请加以考虑此种情况
        // 我选择使用 list
        tokenList.clear();
        for (Token token : tokens) {
            tokenList.add(token);
        }
    }

    public void loadLRTable(LRTable table) {
        // TODO: 加载 LR 分析表
        // 你可以自行选择要如何使用该表格:
        // 是直接对 LRTable 调用 getAction/getGoto, 抑或是直接将 initStatus 存起来使用
        // 我先赋值给私有变量
        lrTable = table;
    }

    public void run() {
        // TODO: 实现驱动程序
        // 你需要根据上面的输入来实现 LR 语法分析的驱动程序
        // 请分别在遇到 Shift, Reduce, Accept 的时候调用上面的 callWhenInShift, callWhenInReduce, callWhenInAccept
        // 否则用于为实验二打分的产生式输出可能不会正常工作
        // 判断 LR table 是否存在
        if (lrTable == null) {
            throw new RuntimeException("LR Table not loaded");
        }
        // init
        statusStack.clear();
        symbolStack.clear();
        curIndex = 0;
        // 将初始状态入栈
        statusStack.push(lrTable.getInit());
        // enter the loop
        while (true){
            if(curIndex>=tokenList.size()){
                throw new RuntimeException("current index exceeds token list size");
            }
            // 获取当前状态和当前 token
            Status currentStatus = statusStack.peek();
            Token currentToken = tokenList.get(curIndex);
            // 获取 action
            Action action = lrTable.getAction(currentStatus, currentToken);

            // 使用switch判断
            switch (action.getKind()) {
                case Shift -> {
                    // 如果是移进，调用函数，将 action 中的状态入栈，并将当前 token 入符号栈，最后将 curIndex + 1
                    callWhenInShift(currentStatus, currentToken);
                    statusStack.push(action.getStatus());
                    symbolStack.push(currentToken);
                    curIndex++;
                }
                case Reduce -> {
                    // 如果是规约，调用函数，将 action 出栈（产生式右部的符号个数），并将产生式左部入符号栈（多一个），最后根据 goto 表获取新的状态入栈
                    Production production = action.getProduction();
                    callWhenInReduce(currentStatus, production);
                    var bodySize = production.body().size();
                    // 规约时需要弹出栈顶的状态和符号
                    for (int i = 0; i < bodySize; i++) {
                        statusStack.pop();
                        symbolStack.pop();
                    }
                    // 获取规约后的非终结符
                    var nonTerminal = production.head();
                    symbolStack.push(nonTerminal);
                    // 获取规约后的状态
                    Status gotoStatus = lrTable.getGoto(statusStack.peek(), nonTerminal);
                    if (gotoStatus.isError()) {
                        throw new RuntimeException(
                            "Goto error from status %s with non-terminal %s".formatted(statusStack.peek(), nonTerminal)
                        );
                    }
                    statusStack.push(gotoStatus);
                }
                case Accept -> {
                    callWhenInAccept(currentStatus);
                    return;
                }
                case Error -> throw new RuntimeException(
                        "Syntax error at token index %d".formatted(curIndex)
                );
                default -> throw new RuntimeException(
                        "Unknown action kind %s at token index %d".formatted(action.getKind(), curIndex)
                );
            }
        }
    }

    private List<Token> tokenList = new ArrayList<>();
    private int curIndex = 0;
    private LRTable lrTable;
    // 这里使用栈
    // status include action goto and index
    private Deque<Status> statusStack = new ArrayDeque<>(); // 状态栈, 栈顶为当前状态
    private Deque<Object> symbolStack = new ArrayDeque<>(); // 符号栈, 栈顶为当前符号, 可能是终结符也可能是非终结符
}
