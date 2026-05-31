<div align=center>
<img src=".doc/images/banner.png"  alt="Banner"/>

[![version](https://img.shields.io/github/v/release/CarmJos/quarkdown-for-intellij)](https://github.com/CarmJos/quarkdown-for-intellij/releases)
[![License](https://img.shields.io/github/license/CarmJos/quarkdown-for-intellij)](https://www.gnu.org/licenses/gpl-3.0.html)
[![workflow](https://github.com/CarmJos/quarkdown-for-intellij/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/CarmJos/quarkdown-for-intellij/actions/workflows/build.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/carmjos/quarkdown-for-intellij/badge)](https://www.codefactor.io/repository/github/carmjos/quarkdown-for-intellij)
![CodeSize](https://img.shields.io/github/languages/code-size/CarmJos/quarkdown-for-intellij)

README LANGUAGES [ [**English**](README.md) | [中文](README_CN.md)  ]
</div>

# **Quarkdown** for **IntelliJ** 
<img src=".doc/images/logo.svg" width="150px" alt="logo" align="right" style="float: right"/>

_**"Quarkdown syntax support for JetBrains IntelliJ Platform."**_

An IntelliJ IDEA plugin that provides comprehensive support for [Quarkdown](https://github.com/CarmJos/quarkdown)
syntax, enabling developers to write and edit Quarkdown documents with full IDE assistance.

## Features & Advantages

- **Syntax Highlighting**: Full syntax highlighting for Quarkdown directives and markdown elements
- **Code Completion**: Intelligent code completion for Quarkdown directives and parameters
- **Quick Documentation**: Instant documentation lookup for Quarkdown elements
- **Error Detection**: Real-time error detection and highlighting
- **Code Navigation**: Easy navigation between Quarkdown sections and references
- **Integration**: Seamless integration with IntelliJ IDEA's editing features

## Installation

### From JetBrains Marketplace

1. Open IntelliJ IDEA
2. Go to `Settings/Preferences` → `Plugins`
3. Search for "Quarkdown"
4. Click `Install` and restart the IDE

### From Disk

1. Download the latest release from [GitHub Releases][gh:releases]
2. Open IntelliJ IDEA
3. Go to `Settings/Preferences` → `Plugins`
4. Click the gear icon → `Install Plugin from Disk...`
5. Select the downloaded `.zip` file and restart the IDE

## Development

### Preview

To quickly demonstrate the applicability of the project, here are a few practical demonstrations:

- [Basic Quarkdown syntax support](src/main/kotlin/cc/carm/plugin/intellij/)
- [Tool window integration](src/main/kotlin/cc/carm/plugin/intellij/toolWindow/)

Check out all code demonstrations [HERE](src/main/kotlin/cc/carm/plugin/intellij/).
For more examples, see the [Development Guide](.doc/README.md).

### Building from Source

#### Prerequisites

- JDK 21 or later
- IntelliJ IDEA (for development)

#### Build

```bash
# Build the plugin
./gradlew buildPlugin

# Run the plugin in a development IDE instance
./gradlew runIde

# Run tests
./gradlew test
```

### Configuration

The plugin can be configured through:

- `gradle.properties`: Project metadata and build configuration
- `settings.gradle.kts`: Gradle plugin versions and repositories
- `build.gradle.kts`: IntelliJ Platform version and dependencies
- `plugin.xml`: Plugin metadata and extension registrations

## Support and Donation

If you appreciate this plugin, consider supporting me with a [donation](https://github.com/sponsors/CarmJos)!

Thank you for supporting open-source projects!

Many thanks to Jetbrains for kindly providing a license for us to work on this and other open-source projects.

[![](https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg)](https://www.jetbrains.com/?from=https://github.com/CarmJos/)

## Open Source License

This project's source code is licensed under
the [GNU General Public License, Version 3](https://www.gnu.org/licenses/gpl-3.0.html).

[gh:build]: https://github.com/CarmJos/quarkdown-for-intellij/actions?query=workflow%3ABuild

[gh:releases]: https://github.com/CarmJos/quarkdown-for-intellij/releases

[jb:plugin]: https://plugins.jetbrains.com/plugin/cc.carm.plugin.intellij.quarkdown