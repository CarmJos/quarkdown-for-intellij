# Changelog

> This changelog follows the Conventional Commits specification.
> Each entry starts with a backticked `type(scope)` marker (e.g. `feat(table)`) followed
> by a concise one-sentence summary, with no colon after the marker. Add essential details
> as nested list items below the entry instead of long prose.

## [Unreleased]

## [1.3.0] - 2026-09-05

- `feat(table)` spreadsheet-style table editor with Markdown ⇄ `.tablebyrows` conversion (closes #22)
    - The table gutter icon opens a grid dialog to edit cells, insert/delete/move rows, insert/delete columns, and set alignment.
    - Static `.tablebyrows` calls (including `.var` headers) get a gutter icon too; the `.var` reference is kept until the headers change.
    - Grid UX: no A/B/C letter header, bold header row above a divider, both scrollbars, columns auto-fit to content with cells over 16 characters wrapped into taller rows, and manual width/height resizing by dragging borders.
    - Caption/ID stay a Markdown-table-only feature; the dialog warns when `.tablebyrows` output would drop them.
- `feat(table)` add "Remove Column" to the floating column toolbar
- `fix(table)` table conversion no longer loses content
    - An accidental blank line inside a table no longer truncates the block at that line.
    - Two tables separated by a blank line still stay separate blocks.
    - Data cells beyond the header column count are kept by widening the header instead of being truncated.
- `test(table)` cover `.tablebyrows` syntax, editor conversions, and the truncation regressions

## [1.2.1] - 2026-09-02

- `fix(preview)` live preview loads on IntelliJ 2026.2+ (`NoClassDefFoundError` on `com.intellij.ui.jcef.*`)
    - The embedded browser moved into the bundled *Web Browser (JCEF)* module (`com.intellij.modules.jcef`) in 2026.2; the plugin now depends on it optionally and degrades to "Open in Browser" when JCEF is unavailable.

## [1.2.0] - 2026-08-14

- `refactor(lsp)` integrate the language server through [LSP4IJ](https://github.com/redhat-developer/lsp4ij) instead of the platform LSP module
    - Diagnostics, completion, hover and semantic highlighting now work in **every** IntelliJ-based product (IDEA Community, Android Studio, DataSpell, Gateway, Client, Code With Me Guest).
    - The plugin requires LSP4IJ; the JetBrains Marketplace resolves and installs it automatically.
- `chore(deps)` make spell checking an optional dependency and remove the unused IntelliLang dependency
    - The plugin loads in products without a bundled spell checker; the never-referenced `org.intellij.intelliLang` no longer blocks Gateway/Client/Code With Me Guest installation.

## [1.1.1] - 2026-08-13

- `feat(todo)` index TODO / FIXME markers into the IDE TODO tool window
    - HTML comments are registered via a `todoIndexer`, so markers appear in the TODO panel, not only in the editor.
- `feat(settings)` one-click Quarkdown download & install when no installation is detected
    - Asynchronously fetches the latest release (with caching) and configures the Quarkdown home automatically.
- `feat(spellchecker)` spell checking for Quarkdown prose
    - Checks plain text and headings; excludes function calls, code blocks, paths and syntax markers.
- `feat(structure)` Go To Symbol (Ctrl+Alt+Shift+O) for heading text and `{#id}` anchors
- `feat(editor)` Insert Heading / Insert Equation actions with shortcuts (Ctrl+Shift+H / Ctrl+Shift+E)
- `feat(nav)` rename/move sync for file-path references (`.include`, `.read`, `.css`, `.code`, images)
- `feat(preview)` "Show Output in File Manager" action on the preview toolbar
- `feat(preview)` automatic port-conflict resolution with a notification reporting the port in use
- `feat(statusbar)` accurate CJK word counting, plus a CJK character count
- `fix(structure)` keep document order in the structure view (alphabetical sorter removed)
- `fix(lsp)` resilient language-server startup
    - The Quarkdown home resolves back to the real installation (fixes `ClassNotFoundException` when pointing at a launcher folder); the server auto-retries after a crash, an alarm offers "Open Settings", and changing the home restarts it immediately.
- `perf(nav)` file-based index for reference anchors instead of scanning every `.qd` file per query
- `docs` drop unfulfilled claims (reformat, `.css`/`.code` path completion) from README and plugin description
- `docs` fix stale documentation references and sync the Marketplace getting-started document
- `test` offline mode `-Pquarkdown.test.offline=true` skips LSP integration tests without a local installation
- `test` add core-module tests (CLI, commenter, brace matcher, image-path annotator, settings)
- `ci` extend the test matrix with Windows and macOS runners
- `chore(deps)` remove the unused Markdown plugin dependency
- `chore` remove the redundant dependabot config (only `renovate.json` is kept)

## [1.1.0] - 2026-08-13

- `feat(lsp)` integrate the official Quarkdown Language Server for enhanced semantic features
- `feat(completion)` completion for variables declared via `.var`
    - `.name` references suggest declared variables with a dedicated icon, visually distinct from function completions.
- `feat(folding)` variable-reference preview folds showing the assigned value
- `feat(folding)` cross-reference preview folds for `.ref {id}` targets (type + caption, e.g. `Table Beverage preferences`)

## [1.0.1] - 2026-08-12

- `fix(editor)` floating toolbars appear again
    - Toolbars show only after their actions finish populating (matching the platform `FloatingToolbar`), and table toolbar actions resolve the editor context correctly.
- `fix(editor)` formatting toolbar also appears for double-click word selections
- `fix(preview)` no progress-bar flash or "jump" on watch-mode auto refreshes (the bar tracks the server busy state only)
- `fix(preview)` "View Full Log" dialog opens scrolled to the bottom
- `fix(settings)` auto-detect Homebrew Quarkdown installations on macOS
- `fix(statusbar)` widget no longer removes its document listener twice on disposal
- `fix(statusbar)` word/paragraph count renders when a `.qd` file is already open at IDE start (2025.2+)
- `fix(settings)` platform-aware launcher and installation-path detection
    - `quarkdown.cmd`/`.bat` on Windows, bare `quarkdown` on Unix; per-OS defaults (Scoop / `%LOCALAPPDATA%` / Program Files, Homebrew incl. versioned kegs, `/opt`, `/usr/local`).
- `fix(build)` default output directory is `quarkdown-output` (no leading dot)
- `refactor(settings)` remove the dead "Editor Appearance" settings page
    - Equivalent functionality lives in the IDE's Editor settings and the Quarkdown Color Scheme page.
- `feat(preview)` debounced auto-save while previewing (enabled by default)
    - Saves ~100 ms after typing stops so the CLI's `--watch` recompiles and hot-reloads, mirroring VS Code's `files.autoSave: afterDelay`.

## [1.0.0] - 2026-08-11

🚀 **Quarkdown for IntelliJ 1.0.0** is the first stable release of our official Quarkdown language support for the JetBrains IntelliJ Platform!

After two rounds of pre-release iterations, this version brings a mature, battle-tested editing experience for Quarkdown (`.qd`) documents. Whether you're writing documentation, papers, or technical reports, this plugin gives you a full IDE-grade environment.

- `feat(highlighting)` full syntax highlighting for every Quarkdown directive and Markdown element, with a customizable color scheme (Editor | Color Scheme | Quarkdown)
- `feat(completion)` smart code completion for functions, parameters, named arguments and file paths, with doc tooltips and parameter hints
- `feat(annotator)` real-time error detection for unknown functions, invalid parameters, missing arguments and undefined references
- `feat(nav)` go to definition, find usages, rename-everywhere, and one-click navigation into `.include` / `.read` / `.css` / `.code` targets
- `feat(structure)` document overview: structure view, breadcrumbs and code folding for chapters, tables and images
- `feat(highlighting)` distinct coloring for function-call parts (name, argument braces, named parameters, values)
- `feat(table)` visual table editing via floating handles and a toolbar (insert/remove/move/select, alignment)
- `feat(editor)` floating formatting toolbar (Bold, Italic, Strikethrough, Inline Code, Link) above selections
- `feat(images)` drag-and-drop / paste image handling with syntax generation, visual sizing and invalid-path detection
- `feat(editor)` one-keypress formatting shortcuts (bold, italic, strikethrough, code block, link, image, table)
- `feat(gutter)` gutter dialogs for code blocks, equations and headings (level, content, cross-reference ID with auto-extraction)
- `feat(completion)` file-path completion for `.include`, `.read`, `.css` and `.code` (quoted values, directory navigation, `../`)
- `feat(statusbar)` live word & paragraph count excluding function calls and code blocks
- `feat(preview)` in-IDE live preview panel with refresh-on-save, plus external browser preview
- `feat(build)` one-click PDF build through a dedicated Run Configuration with auto-compile on save
- `feat(templates)` instant `.qd` file creation from File → New with a ready-made template
- `feat(settings)` centralized settings page for installation path and compile/preview options
- `feat(i18n)` bilingual UI (English / Chinese)
- `feat(editor)` auto-closing of double quotes and triple backticks; comment and code-block insertion actions
- `feat(highlighting)` semantic-level highlighting and inlay hints for Quarkdown
- `feat(i18n)` localized strings for all actions and internationalization for references/usages
- `fix(ci)` headless-environment compatibility for CI toolbar updates
- `test` Quarkdown SDK auto-download so the test suite runs without a local installation
- `fix(images)` invalid image paths are called out in the editor
- `chore` align the plugin ID across `plugin.xml` and README

## [0.1.1] - 2026-08-11

- `feat(highlighting)` color function-call parts independently (name, `{ }` braces, named parameters, values)
- `feat(completion)` file-path completion for `.include`, `.read`, `.css` and `.code`
- `feat(statusbar)` word & paragraph count excluding function calls, their bodies and fenced code blocks
- `feat(table)` floating table editor bars (IntelliJ Markdown style) with row/column toolbars and double-click select
- `feat(editor)` floating formatting toolbar above text selections (per-button icons; suppressed over non-prose content)
- `feat(gutter)` code-block gutter icon + dialog (language, caption, cross-reference id)
- `feat(gutter)` equation gutter icon + dialog (cross-reference id)
- `feat(gutter)` heading gutter icon + dialog (level, content, id with auto-extract)

## [0.1.0] - 2026-05-29

- `feat` initial release: Quarkdown syntax support for IntelliJ IDEA

[Unreleased]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v0.1.1...v1.0.0
[0.1.1]: https://github.com/CarmJos/quarkdown-for-intellij/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/CarmJos/quarkdown-for-intellij/commits/v0.1.0
