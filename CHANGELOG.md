# Changelog

## [Unreleased]

### Added
- File path completion for `.include`, `.read`, `.css` and `.code` — typing `.include {path}` now suggests files and directories reachable from the current document, with support for quoted values, directory navigation and `../` traversal.
- Word & paragraph count in the status bar for `.qd` files, excluding Quarkdown function calls (`.var`, `.read`, `.center`, …), their indented bodies and fenced code blocks.
- Floating table editor bars matching the IntelliJ Markdown plugin: horizontal bars above column separators and vertical bars at row edges. Clicking a bar opens a floating toolbar to insert/remove/move/select rows or columns and set column alignment; double-click selects the row/column; the gutter icon re-aligns the table.

## [0.1.0] - 2026-05-29

### Added
- Initial release
- Quarkdown syntax support for IntelliJ IDEA
