[Issues]: https://github.com/CarmJos/quarkdown-for-intellij/issues
[Issue]: https://github.com/CarmJos/quarkdown-for-intellij/issues
[Discussions]: https://github.com/CarmJos/quarkdown-for-intellij/discussions
[Discussion]: https://github.com/CarmJos/quarkdown-for-intellij/discussions
[wiki]: https://quarkdown.com/wiki
[documentation]: https://quarkdown.com/docs
[quarkdown]: https://github.com/iamgio/quarkdown
[IntelliJ Platform SDK]: https://plugins.jetbrains.com/docs/intellij/welcome.html
[Gradle IntelliJ Plugin]: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html

# Contributing to Quarkdown for IntelliJ

Thanks for your interest in contributing to Quarkdown for IntelliJ, the IntelliJ IDEA plugin that brings
[Quarkdown] language support to the JetBrains IntelliJ Platform!

All types of contributions are encouraged and valued.
Please make sure to read the relevant section before making your contribution, as it will make it easier for maintainers to handle it.

> If you like the project, but don't have time to contribute, that's totally fine!
> You can still support us and show your appreciation by doing any of the following:
> - Star :star2: the project.
> - Post the project on social media.
> - Mention the project to others.

## Table of Contents

- [Questions](#questions)
- [Contributing via issues](#contributing-via-issues)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
- [Contributing via PR](#contributing-via-pr)
  - [Your first contribution](#your-first-contribution)
  - [Understanding the architecture](#understanding-the-architecture)
- [Styleguides](#styleguides)

## Questions

Before you ask a question, it is best to search for existing [Issues] or [Discussions] that might help you.

If you then still feel the need to ask a question and need clarification, we recommend the following:

- Open a [Discussion](https://github.com/CarmJos/quarkdown-for-intellij/discussions/new/choose) or [Issue](https://github.com/CarmJos/quarkdown-for-intellij/issues/new), depending on what you feel is more appropriate for your question.
- Provide as much context as you can about what you're running into.
- Provide plugin version, IntelliJ IDEA version, JVM version and OS if relevant.

We will then take care of the issue as soon as possible.

## Contributing via issues

> ### Legal Notice
> When contributing to this project, you must agree that you have authored 100% of the content, that you have the necessary rights to the content and that the content you contribute may be provided under the project license.

### Reporting Bugs

#### Before submitting a bug report

A good bug report shouldn't leave others needing to chase you up for more information. Therefore, we ask you to investigate carefully, collect information and describe the issue in detail in your report. Please complete the following steps in advance to help us fix any potential bug as fast as possible.

- Make sure that you are using the latest version of the plugin.
- Determine if your bug is really a bug and not an error on your side.
- Check if there is not already an issue for your bug in [Issues].

#### Submitting

Open an [Issue] with a clear and descriptive title. The body should contain the following information:

- Your `.qd` source input that triggers the bug
- The output or stack trace
- IntelliJ IDEA version and build number (`Help → About`)
- Plugin version
- Quarkdown CLI version (if relevant)
- Operating system
- JVM version (if relevant)
- Can you reliably reproduce the issue? And can you also reproduce it with older versions?

### Suggesting Enhancements

#### Before submitting an enhancement

- Make sure that you are using the latest version.
- Check the [wiki] and [documentation] carefully to check if the functionality is already present in Quarkdown itself.
- Check [Issues] to see if the enhancement has already been suggested. If it has, add a comment to the existing issue instead of opening a new one.
- Find out whether your idea fits with the scope and aims of the project.

#### Submitting

Open an [Issue] with a clear and descriptive title. The body should contain the following information:

- Provide a step-by-step description of the suggested enhancement in as many details as possible.
- Describe the current behavior and explain which behavior you expected to see instead. At this point you can also tell which alternatives do not work for you.
- Explain why this enhancement would be useful.
- If the enhancement involves Quarkdown core behavior, please consider opening the request in the [Quarkdown project](https://github.com/iamgio/quarkdown/issues) instead.

## Contributing via PR

### Your first contribution

> [!IMPORTANT]
> Please **open a PR only after opening an [Issue]** for the change you want to make, so that maintainers can give you feedback on whether your contribution is likely to be accepted and how it should be implemented.

The following list shows contributions that are highly welcome, in order of importance:

1. [Issues] labeled with `good first issue` or `help wanted`. These issues are usually easier to solve and are a good starting point for new contributors.

2. Improve the **plugin documentation** (README, wiki, inline documentation) or add missing translations.

3. Improve **LSP integration** features, such as diagnostics, completion, hover, and semantic highlighting.

4. Add new **editor features** (preview improvements, table editing, formatting, etc.) following the patterns already present in the codebase.

5. Improve **performance** of highlighting, indexing, or preview.

6. Add support for **new Quarkdown features** introduced in the Quarkdown core project.

### Understanding the architecture

This plugin is built as an **IntelliJ Platform plugin** using the [Gradle IntelliJ Plugin]. The main areas of the codebase are:

- **Language infrastructure** (`src/main/kotlin/.../lang`): lexer, parser, PSI, file type, color settings.
- **LSP integration** (`src/main/kotlin/.../lsp`): integration with the [Quarkdown Language Server](https://github.com/iamgio/quarkdown/tree/main/quarkdown-lsp).
- **Editor features** (`src/main/kotlin/.../editor`): completion, formatting, folding, inlay hints, structure view, breadcrumbs.
- **Preview** (`src/main/kotlin/.../preview`): JCEF-based live preview, external browser preview, build actions.
- **Settings** (`src/main/kotlin/.../settings`): plugin settings UI and persistence.

The plugin communicates with the Quarkdown CLI and its Language Server for code completion and error checking.
When working on features that depend on Quarkdown internals, consult the [Quarkdown project] documentation and the [IntelliJ Platform SDK] docs.

### Setting up the development environment

1. **Prerequisites**:
   - JDK 17 or later
   - [Quarkdown CLI](https://github.com/iamgio/quarkdown) installed and available in `PATH`, or set `QUARKDOWN_HOME` to your Quarkdown installation.
   - IntelliJ IDEA (used for running the plugin in development mode).

2. **Import the project** into IntelliJ IDEA as a Gradle project.

3. **Run the plugin**: use the `runIde` Gradle task, or the corresponding run configuration:
   ```bash
   ./gradlew runIde
   ```

4. **Build the plugin**:
   ```bash
   ./gradlew buildPlugin
   ```

## Tooling

### Building

The project uses Gradle as its build system. To build the project:

```bash
./gradlew build
```

To build the distributable plugin zip:

```bash
./gradlew buildPlugin
```

### Testing

To run the full test suite:

```bash
./gradlew test
```

Note that all tests are automatically run on every PR.

## Styleguides

#### Kotlin code style

The project uses [ktlint](https://github.com/pinterest/ktlint) to ensure a consistent code style is kept across the whole project.

Upon opening a PR, the `ktlintCheck` task is automatically run, and the checks must pass before the PR can be merged. You can also run `./gradlew ktlintFormat` to automatically fix any formatting issues in your code.

#### Commit messages

Please ensure your commit messages use the [imperative tense](https://cbea.ms/git-commit/#imperative)
and follow the [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/) specification, so that they are clear and consistent across the project.

## Attribution

This file was inspired by the [Quarkdown project](https://github.com/iamgio/quarkdown) contributing guidelines and [contributing.md](https://contributing.md/).

