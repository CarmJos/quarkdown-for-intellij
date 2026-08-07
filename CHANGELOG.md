# Changelog

## [Unreleased]

### Added
- File path completion for `.include`, `.read`, `.css` and `.code` — typing `.include {path}` now suggests files and directories reachable from the current document, with support for quoted values, directory navigation and `../` traversal.
- Word & paragraph count in the status bar for `.qd` files, excluding Quarkdown function calls (`.var`, `.read`, `.center`, …), their indented bodies and fenced code blocks.

## [0.1.0] - 2026-05-29

### Added
- Initial release
- Quarkdown syntax support for IntelliJ IDEA
