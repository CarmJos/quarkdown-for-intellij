# TODOs

## References

- https://github.com/quarkdown-labs/quarkdown-vscode (Plugins for VSCode)
- https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp (QuarkDown Language Server)
- https://quarkdown.com/docs/ Official code doc (Auto generated, used for functions references)
- https://quarkdown.com/wiki Official documentation (Used for human reading)

## Dependencies

- [x] Auto-detect Quarkdown installation from `QUARKDOWN_HOME`, system `PATH`,
- [x] Auto import Quarkdown jars as dependencies for features like code completion and error checking.

## Infrastructure

- [x] Create `META-INF/plugin.xml` with all required registrations (fileType, actions, services, extensions).
- [x] Register `.qd` file type (`FileType` + `FileTypeFactory`) with file icon.
- [x] Implement Lexer for syntax highlighting tokenization.
- [x] Implement Parser / PSI tree for structured syntax analysis.
- [x] Create customizable Color Settings Page for `.qd` syntax.
- [x] Support `Ctrl+/` comment/uncomment using Quarkdown comment syntax. 
```markdown
<!-- This is a comment, used for adding notes -->
```

## Basement

- [x] Use `quarkdown.svg` as file icon.
- [x] Use `pluginIcon.svg` as plugin's logo icon.

## Settings

- [x] Auto-detect Quarkdown installation.
- [x] Provide installation guidance with commands for missing Quarkdown.
- [x] Documentation button to Quarkdown wiki in settings.
- [x] Compile parameters configuration (CLI args, output directory, etc.).
- [x] Preview parameters configuration (CLI args, output directory, etc.).
- [X] Editor appearance settings for `.qd` files — **removed**: the settings were never consumed by any rendering code (dead settings), and the equivalent functionality is already provided by IntelliJ's built-in Editor settings (Font, Soft Wraps, Line Numbers) and the Quarkdown Color Scheme page.
- [X] Completion behavior settings (case sensitivity, auto-insert brackets, etc.).
- [X] Code formatting settings (indent size, max line width, etc.).
- [X] Dedicated color scheme configuration for Quarkdown syntax.

## LSP Integration

- [x] Diagnostics: real-time syntax checking, type errors, undefined references (unknown functions, invalid enum values, missing/extra args, positional-after-named).
- [x] Completion: code completion via LSP for function names and parameters (function names, next-argument hints, enum values, file paths).
- [x] Hover: `Ctrl+Q` documentation tooltips for directives and functions (signature, description, parameters, samples).
- [x] Go to Definition: jump to `.ref {id}` definition, `.include`/`.read` file target with variable resolution, etc.
- [x] Find References: find all usages of a `.ref {id}` label.
- [x] Rename refactoring: rename label/id and auto-update all references.
- [X] Signature Help: parameter hints while typing function arguments.
- [X] Document Symbols: structured symbol list for Structure View.
- [X] Folding Ranges: code folding via LSP.
- [x] Semantic Tokens: enhanced semantic-level syntax highlighting (known functions, variable references, valid enum values, parameter names).
- [x] Inlay Hints: inline parameter name annotations for positional arguments.
- [X] Document Links: clickable links for `.include`, image paths, etc.

## Editor

- [X] Auto Code Completion for function names and parameters.
- [X] Code reformatting for Quarkdown files, also for code blocks based on its languages formatting rules.
- [X] Syntax error highlighting for invalid directives, missing parameters, etc.
- [x] File completion and navigation for `.include` `.css` `.code`, etc. Also for cross-references `.ref {id}`.
- [x] File path navigation for `.include {path}` and `.read {path}` with variable resolution (e.g. `{.version/file.qd}` resolves `.version` variable value).
- [x] File path completion and navigation for images `!(100%)[](path "label")` and tables.
- [x] Completion support and fold preview for variables declared via `.var` (distinct variable icon in completion; variable references fold to show their assigned value).
- [x] For tables:
    - Floating bars matching the IntelliJ Markdown plugin: horizontal bars above each column separator, vertical bars at the left edge of every row.
    - Clicking a bar opens a floating toolbar: insert/remove/move/select rows or columns, and set column alignment (left/center/right).
    - Double-clicking a bar selects the whole row/column with multiple carets.
    - Clicking the gutter icon re-aligns / formats the table.
- [x] Auto-close paired symbols (`{}`, `[]`, `""`, code block, etc.) on typing.
- [x] Structure View (`Alt+7`) showing document outline (chapters, tables, images).
- [x] Breadcrumbs navigation showing current position in document hierarchy.
- [x] Code folding for sections, code blocks, and tables (similar to IntelliJ Markdown plugin).
- [X] Gutter icons marking image, table, code block, equation, heading, and reference locations in editor margin.
- [x] Word count and paragraph count in status bar (excluding function calls and code blocks).
- [x] TODO / FIXME highlighting in comments, integrated with IDE TODO panel.
- [x] Drag & drop image files into editor, auto-generating image syntax.
- [x] Paste image from clipboard, auto-save file and generate reference.
- [x] Image path validity check (warn on non-existent paths).
- [X] Image size quick adjustment via drag/slider for `width` and `height`.
- [x] External content import prompt: provide file suggestions when typing `.include`, `.css`, `.code`, etc.

## Keyboard Shortcuts

- [x] `Ctrl+B` for bold text
- [x] `Ctrl+I` for italic text
- [x] `Ctrl+Shift+S` for strikethrough
- [x] `Ctrl+Shift+C` for code block
- [x] `Ctrl+Shift+L` for link
- [x] `Ctrl+Shift+I` for image
- [x] `Ctrl+Shift+T` for table

## Project Management

- [x] "New Quarkdown Document" file template (File -> New menu).

## Preview

- [x] Live preview panel using JCEF to render HTML output in a side panel (server-mode: `quarkdown compile <file> --preview [--watch] --server-port <port> --browser none -o <out>`).
    - [x] Auto-refresh preview on file save or changes (`--watch` mode + browser hot-reload).
    - [x] "Refresh" button for manual refresh (restarts the preview server).
    - [x] "Clean & Refresh" option to clear cache and refresh preview (`--clean`).
    - [x] "Build" action to generate PDF output through the IDE *Run* tool window (base command: `quarkdown compile <file> --pdf`, all other args user-defined).
    - [x] Progress bar / loading indication while the server starts or the page loads.
    - [x] File selector in the toolbar (text field + browse button; empty = "Auto: <current file>" hint).
    - [x] Zoom controls (zoom in/out/reset + percentage) and `Ctrl+wheel` zoom in the bottom bar.
    - [x] "View Full Log" button (bottom-left) showing the complete output of the current preview run.
- [x] External browser preview option (port-based preview: a `View` button shown only while the server is running).
- [x] Watch mode for auto-compile on save (handled by the CLI `--watch` flag, linked to the "Watch changes" setting).
- [X] Compilation output console as a dedicated tool window (build output currently appears in the IDE Run tool window).
- [x] Run Configuration support for `.qd` files (Quarkdown Build run configuration type executed from the Build button).

## Tool Windows & UI

- [x] Quarkdown tool window integrating: Preview.
- [ ] Notification system for Quarkdown version updates.

## Quality

- [x] Internationalization (i18n): English / Chinese bilingual UI support.
