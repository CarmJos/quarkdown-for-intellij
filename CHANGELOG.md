# Changelog

## [Unreleased]

### Fixed

- Floating toolbars (text-selection formatting toolbar and table row/column operation toolbars) now appear again. The toolbars are only shown once their actions have finished populating, matching the platform's own `FloatingToolbar` behaviour, and the table toolbar actions once again resolve the editor context (previous versions could show an empty toolbar because the `ActionUpdateThread.BGT` action update runs asynchronously).
- The Quarkdown installation is now auto-detected on macOS when installed via Homebrew. Dock/Finder-launched IDE instances do not inherit the shell `PATH`, and the detector previously had no Apple-Silicon (`/opt/homebrew`) locations, so the plugin could not find `quarkdown` — leaving function completions, documentation and diagnostics unavailable. The detected launcher is now also resolved back to its installation home so the standard-library function registry loads correctly.
- Fixed an intermittent "Can't remove document listener" error thrown when the status-bar word/paragraph-count widget was disposed (e.g. while closing an editor). The widget no longer removes its document listener twice.
- Fixed the status-bar word/paragraph count not appearing when a `.qd` file was already open when the IDE started (e.g. after restarting with a restored session). The 2025.2+ status bar only re-renders widget text on `updateWidget()`, so the widget now requests its initial render explicitly instead of waiting for the next editor-selection change.

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
- **Centralized settings** page for Quarkdown installation path, compile/preview options and editor appearance.
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

[Unreleased]: https://github.com/CarmJos/quarkdown-for-intellij/compare/1.0.0...HEAD
[1.0.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/0.1.1...1.0.0
[0.1.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/commits/0.1.0
