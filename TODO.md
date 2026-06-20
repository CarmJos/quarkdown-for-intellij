# TODOs

## References

- https://github.com/quarkdown-labs/quarkdown-vscode (Plugins for VSCode)
- https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp (QuarkDown Language Server)
- https://quarkdown.com/docs/ Official code doc (Auto generated, used for functions references)
- https://quarkdown.com/wiki Official documentation (Used for human reading)

## Dependencies

- Use the [Quarkdown language server](https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp) .
- Auto import Quarkdown jars as dependencies for features like code completion and error checking.
- Auto-detect Quarkdown installation from `QUARKDOWN_HOME`, system `PATH`,

Mark: Use jars as library to read/implement auto completions and error checks,

as an example:


```markdown

.doctype {paged}

```

when typing "." will auto suggest all usable commands like `.doctype` `.docname` with `{}`, and put input inner.

Then, will suggest `paged`/`plain`/`slides`/`docs` (based on `.doctype`'s available params).

All of this should base on current jar's definition.

> **Note:** Real-time rendering (charts, math formulas, etc.) should be handled by the LSP's own preview window, not controlled by this plugin.

## Infrastructure

- [ ] Create `META-INF/plugin.xml` with all required registrations (fileType, actions, services, extensions).
- [ ] Register `.qd` file type (`FileType` + `FileTypeFactory`) with file icon.
- [ ] Implement Lexer for syntax highlighting tokenization.
- [ ] Implement Parser / PSI tree for structured syntax analysis.
- [ ] Create customizable Color Settings Page for `.qd` syntax.
- [ ] Support `Ctrl+/` comment/uncomment using Quarkdown `//` syntax.

## Basement

- [ ] Use `quarkdown.svg` as file icon.
- [ ] Use `logo.svg` as plugin's logo icon.

## Settings

- [ ] Auto-detect Quarkdown installation on project open.
- [ ] Provide installation guidance with commands for missing Quarkdown.
- [ ] Provide "Auto Install" option for Quarkdown if not found.
- [ ] Documentation button to Quarkdown wiki in settings.
- [ ] Manual override for Quarkdown CLI and JAR paths.
- [ ] Compile parameters configuration (CLI args, output directory, etc.).
- [ ] Preview settings (auto-refresh interval, default zoom, theme).
- [ ] Editor appearance settings for `.qd` files (font family, size, line height, line wrap; per-component: headings, code blocks, tables, etc.).
- [ ] Completion behavior settings (case sensitivity, auto-insert brackets, etc.).
- [ ] Code formatting settings (indent size, max line width, etc.).
- [ ] Dedicated color scheme configuration for Quarkdown syntax.

## LSP Integration

- [ ] LSP client lifecycle management (auto-start, connect, restart, stop).
- [ ] Diagnostics: real-time syntax checking, type errors, undefined references.
- [ ] Completion: code completion via LSP for function names and parameters.
- [ ] Hover: `Ctrl+Q` documentation tooltips for directives and functions.
- [ ] Go to Definition: jump to `.ref {id}` definition, `.include` file target, etc.
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
- [ ] File completion and navigation for `.include` `.css` `.code`, etc. Also for cross-references `.ref {id}`.
- [ ] File path completion and navigation for images `!(100%)[](path "label")` and tables.
- [ ] For images, add a panel to preview the image, and give a multiplier sliders and size inputs for width and height.
- [ ] For tables:
    - Add a panel to edit the table in a spreadsheet-like interface.
    - Add a side button to change lines, columns, and alignment.
- [ ] Auto-close paired symbols (`{}`, `[]`, `""`, etc.) on typing.
- [ ] Quick documentation (`Ctrl+Q`) popup for directives, with link to wiki.
- [ ] Structure View (`Alt+7`) showing document outline (chapters, tables, images).
- [ ] Breadcrumbs navigation showing current position in document hierarchy.
- [ ] Code folding for sections, code blocks, and tables (similar to IntelliJ Markdown plugin).
- [ ] Gutter icons marking image, table, and reference locations in editor margin.
- [ ] Spell checking for prose/paragraph text.
- [ ] Word count and line count in status bar.
- [ ] TODO / FIXME highlighting in comments, integrated with IDE TODO panel.
- [ ] Drag & drop image files into editor, auto-generating image syntax.
- [ ] Paste image from clipboard, auto-save file and generate reference.
- [ ] Image path validity check (warn on non-existent paths).
- [ ] Image size quick adjustment via drag/slider for `width` and `height`.
- [ ] Image hover preview inline on path.
- [ ] External content import prompt: provide file suggestions when typing `.include`, `.css`, `.code`, etc.

## Keyboard Shortcuts

- [ ] `Ctrl+B` for bold text
- [ ] `Ctrl+I` for italic text
- [ ] `Ctrl+Shift+S` for strikethrough
- [ ] `Ctrl+Shift+C` for code block
- [ ] `Ctrl+Shift+L` for link
- [ ] `Ctrl+Shift+I` for image
- [ ] `Ctrl+Shift+T` for table

## Project Management

- [ ] "New Quarkdown Document" file template (File -> New menu).
- [ ] Quarkdown Project Wizard (New Project -> Quarkdown skeleton).
- [ ] Quarkdown SDK configuration per project (similar to JDK selection).
- [ ] Quarkdown Module type for multi-module projects.

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

- [ ] Quarkdown tool window integrating: document outline + compilation output + error list + preview.
- [ ] Sidebar action icons on `.qd` files in project tree.
- [ ] Status bar widget showing current document's doctype and Quarkdown version.
- [ ] Notification system for Quarkdown version updates.

## Quality

- [ ] Internationalization (i18n): English / Chinese bilingual UI support.


