# Changelog

## [Unreleased]

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
