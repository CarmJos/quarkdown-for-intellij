# Changelog

## [Unreleased]

### Changed

- **LSP integration migrated to LSP4IJ for full product compatibility** — the language server is now integrated through the [LSP4IJ](https://github.com/redhat-developer/lsp4ij) client instead of the platform LSP module (`com.intellij.modules.lsp`). This makes the Quarkdown language server (diagnostics, completion, hover, semantic highlighting) available in **every** IntelliJ-based product — including IntelliJ IDEA Community, Android Studio, DataSpell, JetBrains Gateway, JetBrains Client and Code With Me Guest — instead of only the commercial IDEs.
- **Spell checking is now an optional dependency** — the `com.intellij.modules.spellchecker` module is declared optional so the plugin still loads in products that do not bundle the spell checker (e.g. Android Studio, Code With Me Guest).
- **Removed the unused IntelliLang dependency** — `org.intellij.intelliLang` was declared but never referenced in code, and its hard requirement blocked installation in JetBrains Gateway / Client / Code With Me Guest.

### Requirements

- The plugin now requires [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) to be installed. The JetBrains Marketplace resolves and installs this dependency automatically.

## [1.1.1] - 2026-08-13

### Features

- **Index TODO / FIXME markers into the IDE TODO tool window** — a new `todoIndexer` extension (`QuarkdownTodoIndexer`) registers HTML comments so `TODO` / `FIXME` markers are picked up by the IDE's TODO panel, not just highlighted in the editor; the mismatched `QuarkdownTodoIndexContributor` file was also renamed to `QuarkdownTodoAnnotator` to match its class.
- **One-click Quarkdown download & install in Settings** — when no installation is detected, the settings page now offers a "Download & Install" entry that fetches the latest version asynchronously (with caching), downloads the official distribution and configures the Quarkdown home automatically.
- **Spell checking for Quarkdown prose** — a new `SpellCheckingStrategy` checks plain text and heading content of `.qd` documents while automatically excluding function calls, code blocks, paths and syntax markers.
- **Go To Symbol (Ctrl+Alt+Shift+O)** — a new `GoToSymbolContributor` registers heading text and `{#id}` element IDs as searchable symbols that can be located and navigated to from the global search.
- **Insert Heading / Insert Equation actions and shortcuts** — new `InsertHeadingAction` / `InsertEquationAction` (`Ctrl+Shift+H` / `Ctrl+Shift+E`), with insert-mode support in the dialogs; headings and equations can now be inserted from the editor context menu and via shortcuts.
- **Rename / move sync for file-path references** — references to `.include` / `.read` / `.css` / `.code` and image targets are updated automatically when the referenced file is renamed or moved.
- **Reveal build output in the file manager** — a new "Show Output in File Manager" action on the preview toolbar opens the directory containing the build artifacts (e.g. the generated PDF) in the OS file manager.
- **Automatic port conflict resolution for the preview** — when the configured port is occupied, the preview server automatically shifts to the next free port and a notification clearly reports the port actually in use.
- **Accurate CJK word counting** — CJK characters are counted individually instead of as a single run, and the status bar now also reports the CJK character count, making word statistics meaningful for Chinese documents.

### Fixes

- **Structure view keeps heading order** — the alphabetical sorter was removed so the outline follows the document order of sections (e.g. "Chapter 10" no longer sorts before "Chapter 2").
- **Resilient LSP server startup** — the Quarkdown home path is now resolved back to the real installation, fixing `ClassNotFoundException: com.quarkdown.cli.QuarkdownCliKt` when the setting pointed at a launcher folder (e.g. `/opt/homebrew/bin`). The language server is also automatically retried after an unexpected crash, an alarm balloon with an "Open Settings" action appears when it cannot start, and changing the Quarkdown home in Settings now restarts the LSP server against the new path immediately.

### Performance Improvements

- **File-based index of reference anchors** — a new `FileBasedIndexExtension` (`QuarkdownReferenceIndex`) locates files containing a given reference id directly, instead of scanning every `.qd` file on each Go to Declaration / Find Usages query.

### Documentation

- **Remove the unfulfilled formatter claim** — the README and plugin description no longer advertise "Reformat documents" (no Formatter is implemented yet).
- **Remove the unsupported path-completion claim** — the README no longer advertises `.css` / `.code` path completion (only `.include` / `.read` and image paths are currently supported).
- **Fix stale documentation references** — references to the non-existent `docs/LSP-integration-plan.md` in comments and plugin.xml now point to the official LSP documentation.
- **Sync the Marketplace getting-started document** — `.doc/getting-started-marketplace.html` now matches the current settings page, and all VS Code content has been removed from it.

### Tests

- **Offline test mode** — a new `-Pquarkdown.test.offline=true` flag skips the LSP integration tests when no local Quarkdown installation is available, so the rest of the suite runs normally even without network access.
- **Additional core-module tests** — added `QuarkdownCliTest`, `QuarkdownCommenterTest`, `QuarkdownBraceMatcherTest`, `QuarkdownImagePathAnnotatorTest` and `QuarkdownSettingsTest`, covering command-line construction, the commenter, brace matching, image-path validation and settings serialization.

### Continuous Integration

- **CI test matrix covers Windows / macOS** — the `test` job now runs on `ubuntu-latest`, `windows-latest` and `macos-latest`, covering platform-specific logic such as launcher detection and process termination.

### Chores

- **Remove the unused Markdown plugin dependency** — no source code references the Markdown plugin API, so the `org.intellij.plugins.markdown` dependency and its `bundledPlugin` entry were removed.
- **Remove the redundant dependabot config** — only `renovate.json` is kept.

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
- Gutter icon for headings (`# … ###### {#id}`): clicking it opens a dialog to edit the heading level, content and cross-reference id, with an "extract" button that derives a default id from the heading content.

## [0.1.0] - 2026-05-29

### Added

- Initial release
- Quarkdown syntax support for IntelliJ IDEA

[Unreleased]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v0.1.1...v1.0.0
[0.1.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/commits/v0.1.0
