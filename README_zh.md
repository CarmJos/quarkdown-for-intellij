<div align=center>

<img src=".doc/images/banner_V2.png"  alt="Banner"/>

[![版本](https://img.shields.io/github/v/release/CarmJos/quarkdown-for-intellij)](https://github.com/CarmJos/quarkdown-for-intellij/releases)
[![许可证](https://img.shields.io/github/license/CarmJos/quarkdown-for-intellij)](https://www.gnu.org/licenses/gpl-3.0.html)
[![构建状态](https://github.com/CarmJos/quarkdown-for-intellij/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/CarmJos/quarkdown-for-intellij/actions/workflows/build.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/carmjos/quarkdown-for-intellij/badge)](https://www.codefactor.io/repository/github/carmjos/quarkdown-for-intellij)
![代码大小](https://img.shields.io/github/languages/code-size/CarmJos/quarkdown-for-intellij)

README LANGUAGES [ [**English**](README.md) | [**中文**](README_zh.md) ]
</div>

# **Quarkdown** _for IntelliJ_
<img src=".doc/images/logo.svg" width="150px" alt="logo" align="right" style="float: right"/>

_**"为 JetBrains IntelliJ 平台提供 Quarkdown 语言支持。"**_

一款为 IntelliJ IDEA 开发的插件，为 `.qd` 文件提供 [Quarkdown](https://github.com/iamgio/quarkdown) 语言的全面支持，
让开发者能够借助 IDE 的完整功能来编写和编辑 Quarkdown 文档。

> 也可在 Visual Studio Code 中使用 [VS Code 扩展](https://quarkdown.com/vs-code) 来获得 Quarkdown 支持。

## 功能特性

### 编写与编辑

- **实时编写** — 在补全与格式化等功能的辅助下，边写边预览你的 Quarkdown 文档成型。

  <details open>
  <summary>查看演示</summary>

  <img src=".doc/screenshots/writing.gif" alt="编写 Quarkdown 文档" width="80%"/>

  </details>

- **完整语法高亮** — 编辑器会为所有 Quarkdown 指令和 Markdown 元素着色，并配备一套可完全自定义的专属配色方案，让文档风格随心而变。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/editor.png" alt="完整语法高亮的编辑器" width="80%"/>

  </details>

- **智能代码补全** — 输入时自动提示可用的函数、参数与文件路径，并同步显示文档说明与参数提示，写起来更快、更准确。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/code-completion.png" alt="带文档说明的智能代码补全" width="80%"/>

  </details>

- **实时错误检测** — 未知函数、非法参数、缺失参数与未定义引用都会在编辑器中即时标出，问题在生成最终文档前就能被及时发现。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/error-detection.png" alt="编辑器中的实时错误检测" width="80%"/>

  </details>

- **轻松代码导航** — 一键从引用跳转到定义、查找标签的所有使用位置、全局重命名，并可直接进入 `.include`/`.read` 引用的文件。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/reference-usages.png" alt="查找引用使用位置" width="80%"/>

  </details>

- **文档总览** — 结构视图与面包屑清晰展示章节、表格和图片的层级，配合代码折叠，长文档也能快速定位、专注编辑。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/smart-fold.png" alt="智能代码折叠" width="80%"/>

  </details>

### 智能编辑

- **可视化表格编辑** — 通过浮动手柄和工具栏即可插入、删除、移动、选择和对齐表格的行列，无需手写任何表格语法。
- **便捷图片处理** — 将图片拖入或直接粘贴到文档中，语法会自动生成；图片尺寸可直观调整，无效路径也会被及时提示。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/image-dialog.png" alt="调整图片属性的对话框" width="80%"/>

  </details>

- **轻松编辑** — 支持快速注释，输入时配对的 `{}`、`[]`、引号等符号自动闭合。
- **常用格式快捷键** — 为粗体、斜体、删除线、代码块、链接、图片与表格等常用操作提供一键快捷键，让排版更加顺手。
- **文件路径辅助** — 对包含文件、图片和交叉引用提供路径补全与存在性检查，避免拼写错误。
- **实时字数统计** — 状态栏实时显示文档的字数与段落数（不计函数调用与代码块），写作进度一目了然。

### 预览与构建

- **快捷预览面板** — 在 IDE 内直接预览渲染效果，保存即自动刷新，边写边看。
- **外部浏览器预览** — 想在真实浏览器中查看？一键即可在默认浏览器中打开实时预览。
- **一键构建** — 直接从 IDE 的运行窗口将文档生成为 PDF 等最终产物，编译参数自由配置。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/build.png" alt="IDE 中的构建输出" width="80%"/>

  </details>

- **保存自动编译** — 保存时自动重新编译，预览与构建结果始终与最新内容保持同步。
- **专用运行配置** — 为 `.qd` 文件提供专属运行配置，编译与预览只需一次点击。

### 集成与管理

- **一键新建文档** — 从「文件 → 新建」菜单即可基于模板快速创建 Quarkdown 文档。
- **集中式设置** — Quarkdown 安装路径、编译与预览参数均可在一处设置页面中配置。

  <details>
  <summary>查看截图</summary>

  <img src=".doc/screenshots/settings.png" alt="插件设置页面" width="80%"/>

  </details>

- **双语界面** — 插件界面支持中英双语，开箱即用。

## 安装方法

### 从 JetBrains 插件市场安装 _(推荐)_

1. 打开 IntelliJ IDEA；
2. 进入 `设置/首选项` → `插件`；
3. 搜索 "[Quarkdown](https://plugins.jetbrains.com/plugin/33459)"；
4. 点击 `安装` 并重启 IDE。

### 从磁盘安装

1. 从 [GitHub Releases][gh:releases] 下载最新版本；
2. 打开 IntelliJ IDEA；
3. 进入 `设置/首选项` → `插件`；
4. 点击齿轮图标 → `从磁盘安装插件...`；
5. 选择下载的 `.zip` 文件并重启 IDE。

## 系统要求

- 任意 JetBrains IntelliJ Platform 产品 **2025.2 或更高版本** —— IntelliJ IDEA（社区版 & 旗舰版）、Android Studio、PyCharm、WebStorm、PhpStorm、GoLand、RubyMine、CLion、Rider、DataSpell、DataGrip、RustRover、JetBrains Gateway、JetBrains Client 以及 Code With Me Guest；
- [Quarkdown CLI](https://github.com/iamgio/quarkdown) 已安装并可在 `PATH` 中找到，或在插件设置中配置对应路径；
- [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) —— 从 JetBrains 插件市场安装本插件时会自动安装。

## 支持与捐赠
<img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg" width="150px" alt="logo" align="right" style="float: right"/>

如果您喜欢这个插件，欢迎通过以下方式支持我：
[GitHub Sponsors](https://github.com/sponsors/CarmJos) 或
[爱发电](https://www.ifdian.net/a/carmjos/plan)！

**感谢您支持开源项目！**


特别感谢 JetBrains 为我们提供许可证，让我们能够致力于这个和其他开源项目。


## 贡献指南

欢迎贡献！请随时提交 Pull Request。对于重大更改，请先开 issue 讨论您想要更改的内容。

请确保根据需要更新测试。

## 开源许可证

本项目的源代码采用
[GNU 通用公共许可证第 3 版](https://www.gnu.org/licenses/gpl-3.0.html) 授权。

[gh:releases]: https://github.com/CarmJos/quarkdown-for-intellij/releases
