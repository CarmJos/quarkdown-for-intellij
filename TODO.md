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
<!-- 这是一个注释，用于添加备注 -->
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
- [X] Editor appearance settings for `.qd` files (font family, size, line height, line wrap; per-component: headings, code blocks, tables, etc.).
- [ ] Completion behavior settings (case sensitivity, auto-insert brackets, etc.).
- [ ] Code formatting settings (indent size, max line width, etc.).
- [ ] Dedicated color scheme configuration for Quarkdown syntax.

## LSP Integration

- [ ] LSP client lifecycle management (auto-start, connect, restart, stop).
- [ ] Diagnostics: real-time syntax checking, type errors, undefined references.
- [ ] Completion: code completion via LSP for function names and parameters.
- [ ] Hover: `Ctrl+Q` documentation tooltips for directives and functions.
- [x] Go to Definition: jump to `.ref {id}` definition, `.include`/`.read` file target with variable resolution, etc.
- [ ] Find References: find all usages of a `.ref {id}` label.
- [ ] Rename refactoring: rename label/id and auto-update all references.
- [ ] Signature Help: parameter hints while typing function arguments.
- [ ] Document Symbols: structured symbol list for Structure View.
- [ ] Folding Ranges: code folding via LSP.
- [ ] Semantic Tokens: enhanced semantic-level syntax highlighting.
- [ ] Inlay Hints: inline parameter name annotations.
- [ ] Code Actions: quick-fix suggestions (e.g., add missing parameters).
- [ ] Document Links: clickable links for `.include`, image paths, etc.

## Editor

- [ ] Auto Code Completion for function names and parameters.
- [ ] Code reformatting for Quarkdown files, also for code blocks based on its languages formatting rules.
- [ ] Syntax error highlighting for invalid directives, missing parameters, etc.
- [x] File completion and navigation for `.include` `.css` `.code`, etc. Also for cross-references `.ref {id}`.
- [x] File path navigation for `.include {path}` and `.read {path}` with variable resolution (e.g. `{.version/file.qd}` resolves `.version` variable value).
- [x] File path completion and navigation for images `!(100%)[](path "label")` and tables.
- [ ] For images, add a panel to preview the image, and give a multiplier sliders and size inputs for width and height.
- [ ] For tables:
    - Add a panel to edit the table in a spreadsheet-like interface.
    - Add a side button to change lines, columns, and alignment.
- [x] Auto-close paired symbols (`{}`, `[]`, `""`, code block, etc.) on typing.
- [x] Structure View (`Alt+7`) showing document outline (chapters, tables, images).
- [x] Breadcrumbs navigation showing current position in document hierarchy.
- [x] Code folding for sections, code blocks, and tables (similar to IntelliJ Markdown plugin).
- [ ] Gutter icons marking image, table, and reference locations in editor margin.
- [ ] Spell checking for prose/paragraph text.
- [ ] Word count and line count in status bar.
- [x] TODO / FIXME highlighting in comments, integrated with IDE TODO panel.
- [x] Drag & drop image files into editor, auto-generating image syntax.
- [x] Paste image from clipboard, auto-save file and generate reference.
- [x] Image path validity check (warn on non-existent paths).
- [X] Image size quick adjustment via drag/slider for `width` and `height`.
- [ ] External content import prompt: provide file suggestions when typing `.include`, `.css`, `.code`, etc.

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

- [ ] Live preview panel using JCEF to render HTML output in a side panel.
    - [ ] Auto-refresh preview on file save or changes.
    - [ ] "Refresh" button for manual refresh.
    - [ ] "Clean & Refresh" option to clear cache and refresh preview.
    - [ ] "Compile PDF" action to generate PDF output using Quarkdown CLI.
- [ ] Split editor preview (editor + preview side by side in the editor area).
- [ ] External browser preview option (configurable: side panel or external browser).
- [ ] Watch mode for auto-compile on save (reference VSCode Quarkdown plugin implementation).
- [ ] Compilation output console as a dedicated tool window.
- [ ] Run Configuration support for `.qd` files (run/debug compilation).

## Tool Windows & UI

- [ ] Quarkdown tool window integrating: Preview.
- [ ] Sidebar action icons on `.qd` files in project tree.
- [ ] Status bar widget showing current document's doctype and Quarkdown version.
- [ ] Notification system for Quarkdown version updates.

## Quality

- [ ] Internationalization (i18n): English / Chinese bilingual UI support.
