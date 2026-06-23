# Quarkdown 函数语法注册与解析机制

本文档描述 Quarkdown 中所有类似 `.doclang` 语法的函数注册位置和解析方式，供开发代码补全工具参考。

---

## 1. 语法概览

Quarkdown 的函数调用语法如下：

```markdown
.funcname {positional_arg} paramname:{named_arg}
    body argument (indented block)
```

- 以 `.` 开头，标识符为函数名
- `{...}` 包裹内联参数
- `name:{value}` 为命名参数
- 缩进块为 body 参数
- `::` 连接链式调用：`.foo::bar`

---

## 2. 函数定义与注册链路

### 2.1 定义层：Kotlin 原生函数

每个 stdlib 函数在 `quarkdown-stdlib` 模块中以 Kotlin 函数定义：

```
quarkdown-stdlib/src/main/kotlin/com/quarkdown/stdlib/
```

关键注解：

| 注解                     | 作用                      | 示例                                      |
|------------------------|-------------------------|-----------------------------------------|
| `@Name("name")`        | 重命名函数/参数（Quarkdown 用小写） | `@Name("doclang") fun docLanguage(...)` |
| `@LikelyNamed`         | 标记参数大概率以命名形式传递          | `@LikelyNamed width: Size?`             |
| `@LikelyBody`          | 标记参数大概率以 body 形式传递      | `@LikelyBody body: MarkdownContent?`    |
| `@LikelyChained`       | 标记函数大概率用于链式调用           | `@LikelyChained`                        |
| `@Injected`            | 标记注入参数（不暴露给用户）          | `@Injected context: Context`            |
| `@OnlyForDocumentType` | 限制函数仅用于特定文档类型           |                                         |
| `@NotForDocumentType`  | 限制函数不用于特定文档类型           |                                         |

定义示例（Document.kt:383）：

```kotlin
@Name("doclang")
fun docLanguage(
    @Injected context: MutableContext,
    locale: String? = null,
): OutputValue<*>
```

### 2.2 模块聚合层：QuarkdownModule

每个 stdlib 源文件底部通过 `moduleOf(...)` 导出模块：

```
quarkdown-core/src/main/kotlin/com/quarkdown/core/function/library/module/QuarkdownModule.kt
```

```kotlin
val Layout: QuarkdownModule = moduleOf(
    ::container, ::align, ::center, ::row, ::column, ...
)
```

### 2.3 注册层：Stdlib

所有模块在 `Stdlib` 对象中统一加载：

```
quarkdown-stdlib/src/main/kotlin/com/quarkdown/stdlib/Stdlib.kt
```

```kotlin
object Stdlib : LibraryExporter {
    override val library: Library
        get() = MultiFunctionLibraryLoader(name = "stdlib")
            .load(Document, Layout, Text, Primitives, MiscElements,
                   Math, Logical, String, Icon, Emoji, Collection,
                   Dictionary, Optionality, Logger, Flow, TableComputation,
                   Data, Localization, Library, Slides, Ecosystem,
                   Html, Mermaid, Reference, Bibliography, Process)
}
```

### 2.4 反射适配层：KFunctionAdapter

`MultiFunctionLibraryLoader` 通过 `KFunctionAdapter` 将 Kotlin KFunction 桥接为 Quarkdown `Function`：

```
quarkdown-core/src/main/kotlin/com/quarkdown/core/function/reflect/KFunctionAdapter.kt
```

该适配器在构造时缓存所有反射元数据：
- 函数名（`@Name` 或原始名）
- 参数列表（名称、类型、是否可选、是否注入、是否可空）
- 校验器（`@OnlyForDocumentType` / `@NotForDocumentType`）

### 2.5 CLI 初始化

```
quarkdown-cli/src/main/kotlin/com/quarkdown/cli/PipelineInitialization.kt
```

调用 `LibraryExporter.exportAll(Stdlib)` 将所有 stdlib 函数加载到编译管线。

---

## 3. 解析管线

### 3.1 词法分析（Lexer）

| 文件 | 职责 |
|------|------|
| `quarkdown-core/.../lexer/Lexer.kt` | 词法分析器接口 |
| `quarkdown-core/.../lexer/regex/StandardRegexLexer.kt` | 正则词法分析器实现 |
| `quarkdown-core/.../lexer/patterns/FunctionCallPatterns.kt` | 函数调用的词法模式（检测 `.` 开头） |
| `quarkdown-core/.../lexer/patterns/QuarkdownBlockTokenRegexPatterns.kt` | Quarkdown 块级模式 |
| `quarkdown-core/.../lexer/patterns/QuarkdownInlineTokenRegexPatterns.kt` | Quarkdown 内联模式 |
| `quarkdown-core/.../flavor/quarkdown/QuarkdownLexerFactory.kt` | 创建各作用域词法分析器 |

函数调用词法模式是"标记"模式：检测到 `.` 开头后委托给 Walker。

### 3.2 函数调用语法（Grammar）

核心语法定义使用 `better-parse` 库：

```
quarkdown-core/.../parser/walker/funcall/FunctionCallGrammar.kt
```

关键 token：
- `BEGIN = '.'` — 函数调用起始
- `ARGUMENT_BEGIN = '{'` / `ARGUMENT_END = '}'` — 内联参数界定
- `NAMED_ARGUMENT_DELIMITER = ':'` — 命名参数分隔
- `CHAIN_SEPARATOR = '::'` — 链式调用分隔
- `LINE_CONTINUATION = '\'` — 行续接
- `IDENTIFIER_PATTERN = "[a-zA-Z][a-zA-Z0-9]*|[0-9]+"` — 标识符模式

语法输出为 `WalkedFunctionCall`（名称、参数列表、body、链表 next 指针）。

### 3.3 精炼与 AST 构建

```
quarkdown-core/.../parser/FunctionCallRefiner.kt
```

将 `WalkedFunctionCall` 转换为 `FunctionCallNode` AST 节点，处理链式调用转换（`.foo::bar` → `.bar {.foo}`）。

### 3.4 函数调用展开

```
quarkdown-core/.../parser/FunctionCallNodeExpander.kt
```

通过 `context.resolveUnchecked(node)` 查找函数定义，执行 `invoke(bindings, call)`，将结果通过 `NodeOutputValueVisitor` 映射为 AST 节点。

---

## 4. 已有 LSP 代码补全系统

项目已包含完整的 LSP 服务器和代码补全实现：

### 4.1 LSP 服务器

```
quarkdown-lsp/src/main/kotlin/com/quarkdown/lsp/QuarkdownLanguageServer.kt
```

注册补全触发器：`.`、`::`、`{`

### 4.2 补全供应商工厂

```
quarkdown-lsp/.../completion/CompletionSuppliersFactory.kt
```

默认补全供应商：

| 供应商 | 触发场景 | 文件 |
|--------|----------|------|
| `FunctionNameCompletionSupplier` | `.xyz` 或 `::xyz` | `completion/function/name/` |
| `FunctionParameterNameCompletionSupplier` | `.func pa\|` | `completion/function/parameter/FunctionParameterNameCompletionSupplier.kt` |
| `FunctionParameterAllowedValuesCompletionSupplier` | `.row alignment:{\|` | `completion/function/parameter/FunctionParameterAllowedValuesCompletionSupplier.kt` |

### 4.3 函数数据来源

LSP **不直接读取 Kotlin 源码**，而是读取预生成的 Quarkdoc HTML 文档：

```
quarkdown-lsp/.../cache/CacheableFunctionCatalogue.kt
```

- 通过 `DokkaHtmlWalker` 遍历文档目录
- 提取 `DocumentedFunction`（名称、参数、是否 likely chained）
- 提取 `DocsParameter`（名称、描述、是否可选、是否 likely named/body、允许的枚举值）
- 缓存到 `ConcurrentHashMap<DocsDirectory, Set<DocumentedFunction>>`

数据模型：

```
quarkdown-quarkdoc-reader/.../DocsFunction.kt
quarkdown-quarkdoc-reader/.../DocsWalker.kt
quarkdown-quarkdoc-reader/.../dokka/DokkaHtmlWalker.kt
```

### 4.4 函数调用 Tokenizer（LSP 专用）

```
quarkdown-lsp/.../tokenizer/FunctionCallTokenizer.kt
```

使用 `QuarkdownLexerFactory.newInlineFunctionCallLexer` 进行轻量级 token 化，用于在补全时定位光标所在的函数调用和参数。

### 4.5 其他 LSP 功能

| 功能         | 文件                                                                   |
|------------|----------------------------------------------------------------------|
| 语法高亮       | `highlight/function/FunctionCallTokensSupplier.kt`                   |
| 悬停文档       | `hover/function/FunctionDocumentationHoverSupplier.kt`               |
| 诊断（重复参数名）  | `diagnostics/function/DuplicateParameterNamesDiagnosticSupplier.kt`  |
| 诊断（未解析参数名） | `diagnostics/function/UnresolvedParameterNamesDiagnosticSupplier.kt` |
| 诊断（无效参数值）  | `diagnostics/function/InvalidParameterValuesDiagnosticSupplier.kt`   |
| 自动缩进       | `ontype/LineContinuationAutoIndentOnTypeFormattingEditSupplier.kt`   |

---

## 5. 全部 stdlib 模块文件

以下文件各导出一个 `QuarkdownModule`，包含该类别的所有函数：

| 模块文件 | 说明 |
|----------|------|
| `Document.kt` | 文档元数据：docType, docName, doclang, theme, numbering, font, pageFormat 等 |
| `Layout.kt` | 布局：container, align, row, column, grid, box, collapse, table 等 |
| `Text.kt` | 文本样式：strong, emphasis, code, link, image, heading 等 |
| `Primitives.kt` | 基础类型：number, string, boolean |
| `MiscElements.kt` | 杂项元素：horizontalrule, linebreak, pagebreak 等 |
| `Math.kt` | 数学：math, inlineMath |
| `Logical.kt` | 逻辑运算：and, or, not, greater, less 等 |
| `String.kt` | 字符串操作：uppercase, lowercase, concat, split 等 |
| `Icon.kt` | 图标：icon |
| `Emoji.kt` | 表情：emoji |
| `Collection.kt` | 集合操作：list, size, get, filter, map 等 |
| `Dictionary.kt` | 字典操作：dict, dictget, dictset 等 |
| `Optionality.kt` | 可选值：isPresent, orElse 等 |
| `Logger.kt` | 日志：log, warn, error |
| `Flow.kt` | 流程控制：if, forEach, repeat, function, variable, let, extend |
| `TableComputation.kt` | 表格计算 |
| `Data.kt` | 数据处理 |
| `Localization.kt` | 本地化：localize, locale 等 |
| `Library.kt` | 库管理：include, importLib 等 |
| `Slides.kt` | 幻灯片：slide, slideTransition 等 |
| `Ecosystem.kt` | 生态系统功能 |
| `Html.kt` | HTML 相关：rawHtml, attribute 等 |
| `Mermaid.kt` | Mermaid 图表：mermaid |
| `Reference.kt` | 引用管理 |
| `Bibliography.kt` | 参考文献 |
| `Process.kt` | 进程执行 |

---

## 6. 开发代码补全工具的关键路径

### 6.1 如果基于现有 LSP

直接扩展 `quarkdown-lsp` 模块：
- 在 `CompletionSuppliersFactory` 中添加新的 `CompletionSupplier`
- 通过 `CacheableFunctionCatalogue` 获取函数元数据
- 使用 `FunctionCallTokenizer` 定位光标上下文

### 6.2 如果开发独立补全工具

1. **获取函数清单**：读取 Quarkdoc HTML 输出（`DokkaHtmlWalker`），或直接解析 Kotlin 源码中的 `@Name` 注解和 `moduleOf(...)` 导出
2. **解析函数调用**：复用 `FunctionCallGrammar`（`better-parse` 语法）或 `FunctionCallTokenizer`
3. **构建补全项**：从 `DocsFunction` / `DocsParameter` 提取名称、类型、描述、允许值
4. **上下文感知**：根据光标位置判断触发场景（函数名 / 参数名 / 参数值）

### 6.3 关键数据结构

```
Function (interface)           — quarkdown-core/.../function/Function.kt
  ├── name: String
  ├── parameters: List<FunctionParameter<*>>
  ├── validators: List<FunctionCallValidator<T>>
  └── invoke: (ArgumentBindings, FunctionCall<T>) -> T

FunctionParameter (data class) — quarkdown-core/.../function/FunctionParameter.kt
  ├── name: String
  ├── type: KClass<T>
  ├── index: Int
  ├── isOptional: Boolean
  ├── isInjected: Boolean
  └── isNullable: Boolean

DocsFunction (data class)      — quarkdown-quarkdoc-reader/.../DocsFunction.kt
  ├── name: String
  ├── parameters: List<DocsParameter>
  └── isLikelyChained: Boolean

DocsParameter (data class)     — quarkdown-quarkdoc-reader/.../DocsFunction.kt
  ├── name: String
  ├── description: String
  ├── isOptional: Boolean
  ├── isLikelyNamed: Boolean
  ├── isLikelyBody: Boolean
  └── allowedValues: List<String>?
```
