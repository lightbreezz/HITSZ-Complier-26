# 编译原理实验一到实验三实现思路整理

本文档用于快速写实验报告，内容覆盖实验一（词法分析）、实验二（LR 语法分析驱动）和实验三（语义分析与中间代码生成）。

## 1. 总体框架与阶段关系

项目的主流程是典型编译器前端流水线：

1. 词法分析：源代码 -> Token 串 + 初始符号表（仅记录标识符名字）
2. 语法分析：Token 串 + LR(1) 表 -> 规约序列
3. 语义分析：在规约过程中填充符号表类型信息
4. IR 生成：在规约过程中生成三地址中间代码

实验一对应第 1 步，实验二对应第 2 步，实验三对应第 3~4 步。

---

## 2. 实验一：词法分析

### 2.1 目标

从输入代码中识别：

- 关键字：int, return
- 标识符：id
- 整数字面量：IntConst
- 运算符与分隔符：= , ; + - * / ( )
- 文件结束：$

输出两份文件：

- token.txt
- old_symbol_table.txt

### 2.2 关键数据结构

1. 字符串源缓冲区
- 用一个字符串保存整个输入文件内容，便于按下标扫描。

2. Token 列表
- 用 List<Token> 保存词法分析结果。

3. SymbolTable
- 使用 Map<String, SymbolTableEntry> 维护标识符集合。
- 词法阶段只插入名字，type 先为 null。

### 2.3 词法状态机设计

采用手写扫描器（可视作简化 DFA）：

1. 跳过空白字符
2. 若当前字符可作为标识符起始（字母或下划线）：
- 连续读取标识符主体（字母/数字/下划线）
- 若词素为 int 或 return，生成关键字 Token
- 否则生成 id Token，并写入符号表（去重）
3. 若当前字符是数字：
- 连续读取完整数字串，生成 IntConst Token
4. 否则按单字符运算符/分隔符处理：
- = , ; + - * / ( )
5. 扫描结束后追加 EOF Token

### 2.4 SymbolTable 接口行为约定

- has(text)：判断是否存在
- add(text)：不存在时创建条目；若重复可抛异常
- get(text)：不存在时抛异常
- getAllEntries()：供 dump 使用

### 2.5 易错点

1. 忘记追加 EOF（$）会导致语法分析无法接受。
2. 标识符重复出现时不能重复 add，否则会异常。
3. 分号在编码表中名字是 Semicolon，不是字符 ';' 本身。
4. 若扫描时忽略空白不完整，会把换行残留成非法字符。

### 2.6 正确性验证

使用脚本：

- python scripts/check-result.py 1 data/std data/out

预期：token.txt 和 old_symbol_table.txt 均与标准一致。

---

## 3. 实验二：LR(1) 语法分析驱动

### 3.1 目标

实现通用 LR 驱动器，根据 LR 表执行 Shift/Reduce/Accept/Error，并通知观察者。

输出：

- parser_list.txt（规约产生式序列）

### 3.2 核心数据结构

1. tokenList + currentTokenIndex
- 顺序读取输入 token，支持“看一眼但不消费”。

2. statusStack
- 状态栈，初始压入 lrTable.getInit()。

3. symbolStack
- 符号栈，压入终结符 Token 或规约后的非终结符。
- 该实验可用 Object 存储，语义在实验三由观察者自行维护。

4. lrTable
- 提供 getAction(status, token) 与 getGoto(status, nonTerminal)。

### 3.3 算法流程

循环直到 Accept：

1. 取栈顶状态 S，当前输入 token a
2. 查 action = ACTION[S, a]
3. 根据 action 类型处理：

- Shift t
  - 回调 whenShift
  - 状态栈压入 t
  - 符号栈压入 token a
  - 输入指针后移

- Reduce A -> beta
  - 回调 whenReduce
  - 按 |beta| 从状态栈和符号栈各弹出对应数量
  - 符号栈压入非终结符 A
  - 查 GOTO[状态栈新栈顶, A] = u
  - 状态栈压入 u

- Accept
  - 回调 whenAccept
  - 结束

- Error
  - 抛语法错误异常

### 3.4 为什么观察者回调时机要固定

ProductionCollector 依赖 reduce/accept 回调记录产生式。
如果回调时机错位（例如先弹栈再回调或漏掉 accept 回调），parser_list.txt 就会与标准答案不一致。

### 3.5 易错点

1. reduce 时弹栈个数应是“产生式右部长度”。
2. reduce 后必须先压入 head，再查 goto。
3. 忘记在 Accept 时回调会少最后一条开始产生式。
4. token 流尽前未 accept 应视为错误，不应静默结束。

### 3.6 正确性验证

使用脚本：

- python scripts/check-result.py 2 data/std data/out

预期：parser_list.txt 与标准一致（脚本会顺便检查 lab1）。

---

## 4. 实验三：语义分析与 IR 生成

实验三的关键点是“同一个 LR 规约过程驱动两个观察者”：

- SemanticAnalyzer：维护类型信息并写回符号表
- IRGenerator：按产生式生成三地址代码

### 4.1 语义分析设计（SemanticAnalyzer）

#### 4.1.1 数据结构

- SymbolTable symbolTable
- semanticStack（语义栈）
  - 栈元素可携带 Token、Type 或空占位

#### 4.1.2 行为

1. whenShift
- 把当前 Token 压入语义栈。

2. whenReduce
- 对关键产生式做专门动作：

- 产生式 4：S -> D id
  - 取出 id token 和 D.type
  - symbolTable.get(id).setType(D.type)
  - 压回空占位

- 产生式 5：D -> int
  - 把 int 规约为类型 Int
  - 压回携带 SourceCodeType.Int 的栈元素

- 其他产生式
  - 弹出右部个数后压空占位

3. whenAccept
- 无动作

### 4.2 IR 生成设计（IRGenerator）

#### 4.2.1 数据结构

- SymbolTable symbolTable
- List<Instruction> instructions
- irStack（IR 语义栈）
  - 栈元素可携带 Token、IRValue 或空占位

#### 4.2.2 产生式到 IR 动作映射

- 6: S -> id = E
  - 生成 MOV(id, E.val)

- 7: S -> return E
  - 生成 RET(E.val)

- 8: E -> E + A
  - t = temp(); 生成 ADD(t, E1.val, A.val); 传递 t

- 9: E -> E - A
  - t = temp(); 生成 SUB(t, E1.val, A.val); 传递 t

- 10: E -> A
- 12: A -> B
  - 直接传递综合属性

- 11: A -> A * B
  - t = temp(); 生成 MUL(t, A1.val, B.val); 传递 t

- 13: B -> ( E )
  - 传递 E.val

- 14: B -> id
  - 传递 IRVariable.named(id)

- 15: B -> IntConst
  - 传递 IRImmediate.of(value)

- 其他产生式
  - 弹右部并压空占位

### 4.3 语义与 IR 共存的原因

两者都注册在同一个 SyntaxAnalyzer 上：

- 语法分析负责决定何时 shift/reduce
- 语义分析负责类型绑定
- IR 生成负责代码产出

这种“驱动器 + 观察者”结构实现了解耦：

- 语法控制流程只有一份
- 不同实验可独立实现不同观察者

### 4.4 易错点

1. 规约编号必须与 grammar.txt 一一对应，错一位会全错。
2. 处理 id 时要先检查符号表存在性。
3. B -> ( E ) 要正确弹掉两侧括号占位。
4. E -> A / A -> B 不是生成新指令，而是属性透传。
5. setType 只能设置一次，重复声明会触发异常（符合框架约束）。

### 4.5 正确性验证

使用脚本：

- python scripts/check-result.py 3 data/std data/out

预期新增一致文件：

- new_symbol_table.txt
- ir_emulate_result.txt

另外可人工检查 intermediate_code.txt 是否结构合理（临时变量链、RET 末尾返回值）。

---

## 5. 可直接写进报告的方法论总结

1. 分层实现
- 先实现词法和符号表，再实现 LR 驱动，再挂语义和 IR 观察者。

2. 先保证流程正确，再做细节增强
- 先对齐标准输出，再优化数据结构与错误信息。

3. 用脚本回归
- 每完成一层就跑 check-result.py 对应 lab，避免后续定位困难。

4. 明确“公共控制流”和“阶段私有逻辑”边界
- SyntaxAnalyzer 只管驱动，不掺杂具体语义/IR 规则。

---

## 6. 本项目实操命令备忘

在当前环境中建议显式使用同一套 JDK：

1. 编译
- D:\JAVA\JDK\bin\javac.exe -encoding UTF-8 -d out <java-files>

2. 运行对拍
- python scripts/check-result.py 1 data/std data/out
- python scripts/check-result.py 2 data/std data/out
- python scripts/check-result.py 3 data/std data/out

注意：系统默认 java 可能是 Java 8，而 javac 是更高版本，混用会触发 class version 错误。
