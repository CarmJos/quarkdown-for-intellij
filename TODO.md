# TODOs

## Dependencies

- Use the [Quarkdown language server](https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp) .
- Auto import Quarkdown jars as dependencies for features like code completion and error checking.
- Auto-detect Quarkdown installation from `QUARKDOWN_HOME`, system `PATH`,

## Settings

- [ ] Auto-detect Quarkdown installation on project open.
- [ ] Provide installation guidance with commands for missing Quarkdown.
- [ ] Provide "Auto Install" option for Quarkdown if not found.
- [ ] Documentation button to Quarkdown wiki in settings.

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

## Keyboard Shortcuts

- [ ] `Ctrl+B` for bold text
- [ ] `Ctrl+I` for italic text
- [ ] `Ctrl+Shift+S` for strikethrough
- [ ] `Ctrl+Shift+C` for code block
- [ ] `Ctrl+Shift+L` for link
- [ ] `Ctrl+Shift+I` for image
- [ ] `Ctrl+Shift+T` for table

## Preview

- [ ] Live preview panel using JCEF to render HTML output in a side panel.
    - [ ] Auto-refresh preview on file save or changes.
    - [ ] "Refresh" button for manual refresh.
    - [ ] "Clean & Refresh" option to clear cache and refresh preview.
    - [ ] "Compile PDF" action to generate PDF output using Quarkdown CLI.


