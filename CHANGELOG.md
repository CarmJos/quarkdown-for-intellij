# Changelog

> 自 1.2.0 起，变更记录遵循[约定式提交](https://www.conventionalcommits.org/zh-hans/v1.0.0/)风格分组。

## [Unreleased]

### Features

- **索引 TODO / FIXME 到 IDE 的 TODO 工具窗口** — 新增 `todoIndexer` 扩展（`QuarkdownTodoIndexer`），注释中的 `TODO` / `FIXME` 不再只是编辑器着色，而是真正出现在 IDE 的 TODO 面板中；同时将名不符实的 `QuarkdownTodoIndexContributor` 文件重命名为 `QuarkdownTodoAnnotator`。
- **设置页一键下载并安装 Quarkdown** — 检测到未安装时，设置页提供「下载并安装」入口，异步获取最新版本号（带缓存），下载官方发行包并自动配置 Quarkdown 主目录。
- **Quarkdown 正文拼写检查** — 新增 `SpellCheckingStrategy`，对 `.qd` 文档的纯文本与标题内容提供拼写检查，自动排除函数调用、代码块、路径与语法标记。
- **Go To Symbol（Ctrl+Alt+Shift+O）** — 新增 `GoToSymbolContributor`，标题文本与 `{#id}` 元素 ID 现在可作为符号在全局搜索中检索并跳转。
- **插入标题 / 插入公式动作与快捷键** — 新增 `InsertHeadingAction` / `InsertEquationAction`（快捷键 `Ctrl+Shift+H` / `Ctrl+Shift+E`），并在对话框支持插入模式；编辑器右键菜单与快捷键即可插入标题与公式。
- **文件路径引用的重命名 / 移动同步** — `.include` / `.read` / `.css` / `.code` 与图片引用的目标文件重命名或移动后，引用路径会自动同步更新。
- **构建产物一键定位** — 预览工具栏新增「在文件管理器中显示输出」动作，PDF 等构建产物所在目录可一键在系统文件管理器中打开。
- **预览端口冲突自动顺延** — 配置的端口被占用时自动向上寻找空闲端口，并通过通知明确提示实际使用的端口。
- **中文（CJK）字数统计** — 中文字符逐字计数而非合并为一个词，状态栏新增「CJK 字符数」显示，中文文档的字数统计更有意义。

### Fixes

- **结构视图标题顺序** — 移除字母排序器，文档大纲遵循章节在文档中的出现顺序（例如 "Chapter 10" 不再排在 "Chapter 2" 之前）。

### Performance Improvements

- **引用锚点文件索引** — 新增 `FileBasedIndexExtension`（`QuarkdownReferenceIndex`），按 id 快速定位包含引用的文件，避免 Go to Declaration / Find Usages 时每次遍历全部 `.qd` 文件。

### Documentation

- **移除未实现的格式化宣传** — README 与插件描述不再声称支持 "Reformat documents"（项目尚未实现 Formatter）。
- **移除不支持的路径补全宣传** — README 不再声称 `.css` / `.code` 路径补全覆盖（当前仅支持 `.include` / `.read` 与图片路径）。
- **修正失效的文档引用** — 代码注释与 plugin.xml 中指向不存在的 `docs/LSP-integration-plan.md` 的引用改为官方 LSP 文档链接。
- **同步 Marketplace 入门文档** — `.doc/getting-started-marketplace.html` 与当前设置页保持一致，并移除其中所有 VS Code 相关内容。

### Tests

- **测试离线模式** — 新增 `-Pquarkdown.test.offline=true`：本地无 Quarkdown 时跳过 LSP 集成测试，其余测试照常运行，网络不可用时整个测试套件不再不可用。
- **补充核心模块测试** — 新增 `QuarkdownCliTest`、`QuarkdownCommenterTest`、`QuarkdownBraceMatcherTest`、`QuarkdownImagePathAnnotatorTest` 与 `QuarkdownSettingsTest`，覆盖命令参数拼接、注释器、括号匹配、图片路径校验与设置序列化。

### Continuous Integration

- **CI 测试矩阵覆盖 Windows / macOS** — `test` job 在 `ubuntu-latest`、`windows-latest`、`macos-latest` 三个平台上运行，覆盖启动器检测与进程终止等平台相关逻辑。

### Chores

- **移除未使用的 Markdown 插件依赖** — 源码未引用 Markdown 插件 API，移除 `org.intellij.plugins.markdown` 依赖声明与 `bundledPlugin` 配置。
- **移除冗余的 dependabot 配置** — 仅保留 `renovate.json`。

## [1.1.0] - 2026-08-13

### Added

- Integrate official Quarkdown Language Server for enhanced semantic features.
- **Completion support for variables declared via `.var`** — declared variables are now offered as completion suggestions when typing a `.name` reference, using a dedicated variable icon so they are visually distinct from function completions.
- **Variable-reference preview folds (CustomFold)** — references to `.var`-declared variables (e.g. `.status`) can be folded to display the variable's assigned value as the fold placeholder. Hovering over the fold shows the original raw reference and clicking expands it. The folds are collapsed by default and each reference folds independently.
- **Cross-reference preview folds (CustomFold)** — `.ref {id}` references can be folded to preview their target: when the target element has a caption (a heading, figure, table, code block or equation), the fold shows its type + caption (e.g. `Table Beverage preferences`); otherwise it falls back to `Reference(id)`. Exact numbering (e.g. `Table 1.1: …`) and localization are not replicated.

## [1.0.1] - 2026-08-12

### Fixed

- Floating toolbars (text-selection formatting toolbar and table row/column operation toolbars) now appear again. The toolbars are only shown once their actions have finished populating, matching the platform's own `FloatingToolbar` behaviour, and the table toolbar actions once again resolve the editor context (previous versions could show an empty toolbar because the `ActionUpdateThread.BGT` action update runs asynchronously).
- The floating formatting toolbar now also appears when selecting text by double-clicking a word (previously it only appeared for drag selections). The behaviour now matches the platform's `FloatingToolbar`, whose `disableForDoubleClickSelection()` default is `false`.
- The preview no longer flashes a blue progress bar and "jumps" on every automatic refresh. The bar is now driven exclusively by the server's busy state (start/restart) and no longer appears for the page reloads that happen during watch-mode auto-refreshes.
- The "View Full Log" dialog now opens scrolled to the bottom, showing the newest output instead of the oldest.
- The Quarkdown installation is now auto-detected on macOS when installed via Homebrew. 
- Fixed an intermittent "Can't remove document listener" error thrown when the status-bar word/paragraph-count widget was disposed (e.g. while closing an editor). The widget no longer removes its document listener twice.
- Fixed the status-bar word/paragraph count not appearing when a `.qd` file was already open when the IDE started (e.g. after restarting with a restored session). The 2025.2+ status bar only re-renders widget text on `updateWidget()`, so the widget now requests its initial render explicitly instead of waiting for the next editor-selection change.
- Fixed auto-detection picking the Windows-only `quarkdown.bat` / `quarkdown.cmd` launchers on macOS and Linux. Launcher name selection is now platform-aware (`quarkdown.cmd`/`quarkdown.bat` on Windows, bare `quarkdown` shell script on Unix), and the default installation locations and well-known launcher paths are detected per OS — Windows (`%LOCALAPPDATA%`, Scoop, Program Files), macOS (Homebrew `/opt/homebrew` + `/usr/local`, including versioned kegs like `2.5.0`), and Linux (Homebrew-Linux, `/opt`, `/usr/local/share`, `/usr/local/lib`).
- The default output directory is now `quarkdown-output` instead of the dot-prefixed `.quarkdown-output`.

### Changed

- Removed the dedicated "Editor Appearance" settings page for Quarkdown files. The settings it exposed (font family/size/line height, soft wrap, line numbers, and per-component styling for headings/code blocks/tables) were **never consumed by any rendering code** — they were dead settings that had no effect. Equivalent functionality is provided by IntelliJ's built-in Editor settings (Font, Soft Wraps, Line Numbers) and the existing Quarkdown Color Scheme page.
- **Auto-save while previewing** (Settings → Quarkdown → Preview, enabled by default): while a preview is running in watch mode, the `.qd` document is written to disk ~100 ms after you stop typing. IntelliJ only saves on focus loss (hence the need for manual Ctrl+S); this debounced auto-save feeds the CLI's `--watch` file watcher so the preview recompiles and hot-reloads automatically, matching VS Code's `files.autoSave: afterDelay` behaviour.

## [1.0.0] - 2026-08-11

🚀 **Quarkdown for IntelliJ 1.0.0** is the first stable release of our official Quarkdown language support for the JetBrains IntelliJ Platform!

After two rounds of pre-release iterations, this version brings a mature, battle-tested editing experience for Quarkdown (`.qd`) documents. Whether you're writing documentation, papers, or technical reports, this plugin gives you a full IDE-grade environment.

### Added

- **Full syntax highlighting** for every Quarkdown directive and Markdown element, with a fully customizable color scheme (Editor | Color Scheme | Quarkdown).
- **Smart code completion** — suggestions for functions, parameters, named arguments and file paths, with documentation tooltips and parameter hints.
- **Real-time error detection** — unknown functions, invalid parameters, missing arguments and undefined references are flagged as you type.
- **Effortless navigation** — jump to definitions, find all usages, rename references everywhere at once, and navigate into `.include` / `.read` / `.css` / `.code` target files with a single click (Ctrl+Click).
- **Document overview** — structure view, breadcrumbs, and code folding for chapters, tables and images.
- **Distinct syntax highlighting for function calls** — method names, argument braces, named parameters and their values are colored independently.
- **Visual table editing** — floating handles and a toolbar to insert/remove/move/select rows & columns and set alignment, without touching raw syntax.
- **Floating formatting toolbar** — Bold, Italic, Strikethrough, Inline Code and Link buttons appear above text selections.
- **Convenient image handling** — drag-and-drop and paste support with automatic syntax generation, visual size adjustment and invalid-path detection.
- **Handy formatting shortcuts** — one-keypress access to bold, italic, strikethrough, code blocks, links, images and tables.
- **Gutter icons for rich editing** — dialogs for editing code blocks, equations and headings (including heading level, content and cross-reference ID with auto-extraction).
- **File path completion** for `.include`, `.read`, `.css` and `.code` — with support for quoted values, directory navigation and `../` traversal.
- **Live word & paragraph count** in the status bar, excluding function calls and code blocks.
- **Quick preview panel** inside the IDE that refreshes on save, plus external browser preview.
- **One-click build** to PDF and other formats via a dedicated Run Configuration, with auto-compile on save.
- **Instant file creation** from the File → New menu with a ready-made template.
- **Centralized settings** page for Quarkdown installation path and compile/preview options.
- **Bilingual interface** — the entire plugin UI is available in both English and Chinese.
- **Auto-closing** for double quotes and triple backticks.
- **Commenting** and code block insertion actions with language completion.

### Changed

- Semantic-level syntax highlighting and inlay hints for Quarkdown.
- Headless-environment compatibility for CI toolbar updates.
- Localized strings for all Quarkdown actions and internationalization support for references and usages.
- Quarkdown SDK auto-download for tests — no local installation required to run the test suite.

### Fixed

- Plugin ID aligned across `plugin.xml` and README for consistency.
- Invalid image paths are now called out in the editor.

## [0.1.1] - 2026-08-11

### Added

- Distinct syntax highlighting for Quarkdown function calls: the method name (`.ref`, `.row`, `.var`, …), the argument braces `{ }`, named parameters (`size:{a4}`, `margin:{…}`) and their values are now colored independently from plain text, configurable under Editor | Color Scheme | Quarkdown.
- File path completion for `.include`, `.read`, `.css` and `.code` — typing `.include {path}` now suggests files and directories reachable from the current document, with support for quoted values, directory navigation and `../` traversal.
- Word & paragraph count in the status bar for `.qd` files, excluding Quarkdown function calls (`.var`, `.read`, `.center`, …), their indented bodies and fenced code blocks.
- Floating table editor bars matching the IntelliJ Markdown plugin: horizontal bars above column separators and vertical bars at row edges. Clicking a bar opens a floating toolbar to insert/remove/move/select rows or columns and set column alignment; double-click selects the row/column; the gutter icon re-aligns the table.
- Floating formatting toolbar shown when selecting text in a `.qd` file (mirrors the Markdown plugin): Bold, Italic, Strikethrough, Inline Code and Link buttons appear above the selection. Each button has its own icon color; the Link button places the caret inside `()` for immediate URL input; the toolbar is suppressed over non-prose content (function arguments, image paths, inline code, front matter).
- Gutter icon for code blocks (fenced ` ```lang "caption" {#id}` and `.code lang:{…} caption:{…} ref:{…}`): clicking it opens a dialog to edit the block's language, caption and cross-reference id.
- Gutter icon for equations (inline `$ … $ {#id}` and fenced `$$$ {#id}`): clicking it opens a dialog to edit the cross-reference id.
- Gutter icon for headings (`# … ###### {#id}`): clicking it opens a dialog to edit the heading level, content and cross-reference id, with a "快速提取" button that derives a default id from the heading content.

## [0.1.0] - 2026-05-29

### Added

- Initial release
- Quarkdown syntax support for IntelliJ IDEA

[Unreleased]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v0.1.1...v1.0.0
[0.1.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/commits/v0.1.0
