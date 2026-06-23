---
name: quarkdown-plugin-dev
description: Develop the Quarkdown language support plugin for IntelliJ IDEA. This is a Custom Language plugin, not a tool plugin. For IntelliJ Platform SDK documentation lookup and general plugin development patterns, delegate to the idea-plugin-dev skill first.
---

# Quarkdown Plugin Development

This project is a **Quarkdown language support plugin** for the JetBrains IntelliJ Platform.

> **Prerequisite**: Delegate all general IntelliJ platform concerns (documentation lookup, API usage, development patterns) to the `idea-plugin-dev` skill first. This skill covers only Quarkdown-specific domain knowledge.

## About Quarkdown

Quarkdown is a Markdown flavor and typesetting system. It compiles `.qd` source files to HTML, PDF, or plaintext, extending CommonMark/GFM with function calls, variables, layouts, math, custom themes, and document types. Think of it as a more readable LaTeX.

## Plugin Type: Custom Language

**This is a Custom Language plugin, not a tool plugin.** Its core goal is to provide first-class language support for `.qd` files in IntelliJ IDE, including:

- **Lexer** — Tokenize source into a token stream
- **Parser + PSI** — Build the Program Structure Interface tree
- **Syntax Highlighting** — Syntax coloring and error annotation (Annotator + ColorSettingsPage)
- **Code Completion** — Context-aware code completion
- **Navigation & References** — Go to definition, find usages
- **LSP Integration** — Compiler-level semantics via the Quarkdown Language Server
- **Live Preview** — Real-time HTML/PDF preview via JCEF

See IntelliJ docs:
- Custom Language Support overview: https://plugins.jetbrains.com/docs/intellij/custom-language-support.html
- Tutorial: https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html

## Recommended Architecture

### Language Pipeline

The core data flow of a Custom Language plugin:

```
.qd source file
  │
  ├─► Lexer       ─── Tokenize characters into tokens (keywords, strings, function names, variables, etc.)
  │
  └─► Parser      ─── Build PSI tree from token stream (IntelliJ's equivalent of AST)
        │
        ▼
      PSI Tree     ─── Foundation data structure for all language features
        │
        ├─► SyntaxHighlighter  ─── Token-type-based syntax coloring
        ├─► Annotator          ─── Semantic error/warning/info annotations
        ├─► CompletionContributor ─── Context-aware completion (keywords, functions, variables)
        ├─► PsiReferenceContributor ─── Reference resolution (go-to-definition, find usages)
        ├─► FoldingBuilder     ─── Code block folding regions
        ├─► StructureViewBuilder ─── File structure outline
        ├─► Commenter          ─── Line/block comment toggling
        └─► Formatter          ─── Code formatting
```

### LSP Integration Strategy

The Quarkdown project provides an official [Language Server](https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp), the best way to obtain compiler-level semantics.

**Capabilities LSP provides** (prefer implementing via LSP):
- Diagnostics (compiler-level errors/warnings)
- Completion (semantic completion suggestions)
- Hover (documentation and type info on hover)
- Go to Definition / Find References
- Signature Help (function parameter hints)
- Document Symbols (symbol list)
- Folding Ranges (semantic folding regions)
- Semantic Tokens (semantic-level coloring)
- Inlay Hints (inline type/parameter hints)
- Code Actions (quick fixes, refactoring)
- Document Links

**Recommended division of responsibilities**:

| Layer | Implementation | Capabilities |
|-------|---------------|--------------|
| Platform | IntelliJ API (Lexer, Parser, PSI) | File type registration, basic syntax parsing, editor actions |
| LSP | Quarkdown LSP Server | Semantic errors, completion, navigation, hover, symbols, semantic tokens |
| Integration | IntelliJ bridge code | LSP lifecycle management, message translation, UI integration |

LSP documentation references:
- IntelliJ LSP API: https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html
- LSP4IJ: https://github.com/redhat-developer/lsp4ij

### Quarkdown SDK Integration

The Quarkdown compiler ships its standard library as JARs. The plugin must be aware of these JARs at two points:

1. **Build time** — `build.gradle.kts` auto-detects `QUARKDOWN_HOME` and imports standard library JARs
2. **Runtime** — On project startup, auto-detect the Quarkdown installation:
   - Environment variable `QUARKDOWN_HOME`
   - `quarkdown` command on system `PATH`
   - Platform default install paths (Windows scoop, macOS brew, Linux `/opt`/`/usr/local`)

At runtime, a custom ClassLoader reflectively loads the standard library to discover function signatures, parameter types, enum values, and other metadata — driving code completion and documentation tooltips.

## Reference Implementations

> **Core references**: These projects are this plugin's "upstream" and "siblings." When unsure how to implement something, consult these projects first.

| Project                     | URL                                                         | Purpose                                                                                   |
|-----------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| **Quarkdown VSCode Plugin** | https://github.com/quarkdown-labs/quarkdown-vscode          | Primary reference implementation: syntax highlighting, completion, LSP client logic       |
| **Quarkdown LSP Server**    | https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp | Language server defining all semantic capabilities (Diagnostics, Completion, Hover, etc.) |
| **Quarkdown Core**          | https://github.com/iamgio/quarkdown                         | Compiler and typesetting engine; understand `.qd` compilation logic                       |
| Function Reference Docs     | https://quarkdown.com/docs/                                 | Auto-generated API docs; data source for function completion                              |
| User Guide Wiki             | https://quarkdown.com/wiki                                  | Human-readable documentation; understand language specification and usage                 |

## Troubleshooting

> For general IntelliJ development issues (plugin.xml configuration, completion not working, PSI debugging, etc.), consult the `idea-plugin-dev` skill's Troubleshooting section and the IntelliJ Platform SDK docs first.

### Quarkdown SDK / LSP Not Found

- Set the `QUARKDOWN_HOME` environment variable pointing to the Quarkdown installation directory
- Ensure the `quarkdown` command is available on system `PATH`
- Manually configure the path in the plugin's Settings page
- The LSP server requires `quarkdown-lsp` JAR under the Quarkdown installation `lib/` directory

## References

- Quarkdown VSCode Plugin: https://github.com/quarkdown-labs/quarkdown-vscode
- Quarkdown Language Server: https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp
- Quarkdown Core: https://github.com/iamgio/quarkdown
- Quarkdown Docs (function reference): https://quarkdown.com/docs/
- Quarkdown Wiki (user guide): https://quarkdown.com/wiki
- IntelliJ Custom Language Support: https://plugins.jetbrains.com/docs/intellij/custom-language-support.html
- IntelliJ LSP Integration: https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html
- LSP4IJ: https://github.com/redhat-developer/lsp4ij
