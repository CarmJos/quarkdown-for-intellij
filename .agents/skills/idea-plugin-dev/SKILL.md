---
name: idea-plugin-dev
description: Develop IntelliJ IDEA plugins. Use when creating new IntelliJ IDEA plugins, setting up plugin projects, implementing actions, settings pages, or other plugin features. Emphasizes documentation-first development by consulting the IntelliJ Platform SDK before implementation.
---
# IntelliJ IDEA Plugin Development

This Skill provides guidance for developing IntelliJ IDEA plugins. **All feature implementation MUST start from the official documentation.**

## Documentation-First Workflow

**CRITICAL**: Before implementing ANY feature, always consult the official IntelliJ Platform SDK documentation first:

- **Homepage**: https://plugins.jetbrains.com/docs/intellij/
- **Sitemap (all available pages)**: https://plugins.jetbrains.com/docs/intellij/sitemap.xml

### Feature-to-Documentation Mapping

For every feature requirement, identify the correct documentation page before writing code:

| Feature / Requirement             | Documentation Page                                                              |
|-----------------------------------|---------------------------------------------------------------------------------|
| Code Completion                   | https://plugins.jetbrains.com/docs/intellij/code-completion.html                |
| Actions                           | https://plugins.jetbrains.com/docs/intellij/action-system.html                  |
| Editor / PSI Files                | https://plugins.jetbrains.com/docs/intellij/psi.html                            |
| Tool Windows                      | https://plugins.jetbrains.com/docs/intellij/tool-windows.html                   |
| Settings (Preferences)            | https://plugins.jetbrains.com/docs/intellij/settings-guide.html                 |
| Notifications                     | https://plugins.jetbrains.com/docs/intellij/notifications.html                  |
| Icons                             | https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html     |
| Internationalization              | https://plugins.jetbrains.com/docs/intellij/localization-guide.html             |
| Plugin Configuration (plugin.xml) | https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html      |
| Services (Application/Project)    | https://plugins.jetbrains.com/docs/intellij/plugin-services.html                |
| Persistent State                  | https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html |
| File Editors                      | https://plugins.jetbrains.com/docs/intellij/file-editors.html                   |
| Inspections                       | https://plugins.jetbrains.com/docs/intellij/code-inspections.html               |
| Intentions                        | https://plugins.jetbrains.com/docs/intellij/intention-actions.html              |
| Live Templates                    | https://plugins.jetbrains.com/docs/intellij/live-templates.html                 |
| Refactoring                       | https://plugins.jetbrains.com/docs/intellij/refactoring-support.html            |
| Testing                           | https://plugins.jetbrains.com/docs/intellij/testing-plugins.html                |
| Build System (Gradle)             | https://plugins.jetbrains.com/docs/intellij/gradle-build-system.html            |
| Run/Debug Configurations          | https://plugins.jetbrains.com/docs/intellij/run-debug-configuration.html        |
| Dialog / Popup / UI               | https://plugins.jetbrains.com/docs/intellij/dialog-wrapper.html                 |

### Development Process

For any feature implementation, follow this strict process:

1. **Identify the feature type** (e.g., "code completion", "tool window", "inspection")
2. **Look up the sitemap** → https://plugins.jetbrains.com/docs/intellij/sitemap.xml to find the relevant documentation page
3. **Read the documentation** thoroughly before writing any code
4. **Implement** following the documented API and patterns
5. If encountering issues, **re-check the documentation first**, then search the SDK docs for related topics

## When to Use This Skill

Use this Skill when:

- Creating a new IntelliJ IDEA plugin project
- Setting up plugin structure and configuration
- Implementing actions, settings pages, or UI components
- Following best practices for IntelliJ plugin development
- Working with the standard plugin template (`template-without-ai`)

## Project Structure

```
template-without-ai/
├── src/main/java/dev/dong4j/zeka/stack/idea/plugin/example/
│   ├── action/          # Actions
│   ├── icons/           # Icon management
│   ├── settings/        # Settings (State, Configurable, Panel)
│   └── util/            # Utilities (Bundle, Notification)
├── src/main/resources/
│   ├── icons/           # Icon resources (SVG)
│   ├── META-INF/
│   │   └── plugin.xml   # Plugin configuration
│   └── messages*.properties  # Internationalization
├── includes/            # Plugin description and changelog
├── docs/                # User manual
├── build.gradle.kts     # Build configuration
└── gradle.properties    # Plugin properties
```

## Development Steps

### Step 1: Configure Project

1. **Update `gradle.properties`**:
   ```properties
   pluginGroup=your.package.name
   pluginName=Your Plugin Name
   pluginVersion=1.0.0
   rootProjectName=your-plugin-name
   ```

2. **Update `plugin.xml`**:
   - Change plugin ID
   - Update plugin name
   - Register your actions and services

3. **Update package names**:
   - Replace `dev.dong4j.zeka.stack.idea.plugin.example` with your package
   - Update all Java files
   - Update `plugin.xml` references

### Step 2: Implement Actions

> **Documentation**: https://plugins.jetbrains.com/docs/intellij/action-system.html

**Standard Action Pattern**:

```java
public class ExampleAction extends AnAction {
    public ExampleAction() {
        super(
            ExampleBundle.message("action.example.title"),
            ExampleBundle.message("action.example.description"),
            ExampleIcons.EXAMPLE_16
        );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        if (project == null || psiFile == null) {
            NotificationUtil.showError(project, ExampleBundle.message("error.no.file"));
            return;
        }
        
        // Your action logic here
        NotificationUtil.showInfo(project, "Action executed");
    }
}
```

**Register in `plugin.xml`**:

```xml
<action id="your.package.action.ExampleAction"
        class="your.package.action.ExampleAction">
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
</action>
```

### Step 3: Icon Management

> **Documentation**: https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html

```java
public class ExampleIcons {
    @NotNull
    private static Icon load(@NotNull String iconPath) {
        return IconLoader.getIcon(iconPath, ExampleIcons.class);
    }

    public static final Icon EXAMPLE_16 = load("/icons/example_16.svg");
}
```

**Add Icon Resources**:

- Place SVG files in `src/main/resources/icons/`
- Use 16x16 for actions, 24x24 for notifications, 32x32 for dialogs

### Step 4: Internationalization

> **Documentation**: https://plugins.jetbrains.com/docs/intellij/localization-guide.html

**Add Messages**:

`messages.properties` (English):

```properties
action.example.title=Example Action
action.example.description=Execute example action
error.no.file=No file found
```

`messages_zh_CN.properties` (Chinese):

```properties
action.example.title=示例操作
action.example.description=执行示例操作
error.no.file=未找到文件
```

**Use in Code**:

```java
String message = ExampleBundle.message("action.example.title");
```

### Step 5: Settings Page

> **Documentation**: https://plugins.jetbrains.com/docs/intellij/settings-guide.html

**Create SettingsState**:

```java
@State(
    name = "ExamplePluginSettings",
    storages = @Storage("example-settings.xml")
)
public class SettingsState implements PersistentStateComponent<SettingsState> {
    public String exampleSetting = "";

    public static SettingsState getInstance() {
        return ApplicationManager.getApplication().getService(SettingsState.class);
    }
}
```

**Create Settings Panel**:

```java
public class ExampleSettingsPanel {
    private final JPanel rootPanel;
    private final JBTextField exampleField;

    public ExampleSettingsPanel() {
        rootPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Example Setting:", exampleField = new JBTextField())
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
    }

    public JPanel getPanel() {
        return rootPanel;
    }

    public void apply(SettingsState settings) {
        settings.exampleSetting = exampleField.getText();
    }

    public void reset(SettingsState settings) {
        exampleField.setText(settings.exampleSetting);
    }

    public boolean isModified(SettingsState settings) {
        return !Objects.equals(exampleField.getText(), settings.exampleSetting);
    }
}
```

**Register in `plugin.xml`**:

```xml
<applicationService serviceImplementation="your.package.settings.SettingsState"/>
<applicationConfigurable
    parentId="tools"
    instance="your.package.settings.ExampleSettingsConfigurable"
    id="your.package.settings.ExampleSettingsConfigurable"
    displayName="Your Plugin"/>
```

### Step 6: Build Configuration

**`build.gradle.kts`**:

```kotlin
dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"),
               providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
    }
}
```

### Step 7: Testing

**Build plugin**:
   ```bash
   ./gradlew buildPlugin
   ```

## Best Practices

### Code Organization

1. **Package Structure**:
    - `action/` - User actions
    - `icons/` - Icon management
    - `settings/` - Settings
    - `util/` - Utilities (Bundle, Notification)

2. **Naming Conventions**:
    - Actions: `*Action.java`
    - Settings: `*SettingsState.java`, `*SettingsConfigurable.java`, `*SettingsPanel.java`
    - Icons: `*Icons.java`
    - Bundles: `*Bundle.java`

### UI Components

1. **Use IntelliJ UI Components**:
    - `JBLabel`, `JBTextField`, `JBCheckBox` (not JLabel, JTextField)
    - `FormBuilder` for layouts
    - `ToolbarDecorator` for tables
    - `JBTable` for data tables

2. **Settings Page**:
   - Use `FormBuilder` for consistent layout
   - Use `JBTabbedPane` for tabbed settings

### Internationalization

1. **Always use Bundle**:
    - Never hardcode strings
    - Use `ExampleBundle.message(key, params...)`
    - Provide both English and Chinese

2. **Key Naming**:
    - `action.*` - Action labels
    - `settings.*` - Settings labels
    - `error.*` - Error messages
    - `success.*` - Success messages

### Configuration

1. **Persistent State**:
    - Use `@State` annotation
    - Implement `PersistentStateComponent`
    - Initialize collections to avoid null

2. **Settings UI**:
    - Implement `Configurable` or `SearchableConfigurable`
    - Check `isModified()` before save
    - Reset UI in `reset()` method

## Common Patterns

### Notification Pattern

```java
// Success
NotificationUtil.showInfo(project, ExampleBundle.message("success.action.executed"));

// Error
NotificationUtil.showError(project, ExampleBundle.message("error.no.file"));

// Warning
NotificationUtil.showWarning(project, ExampleBundle.message("warning.message"));
```

### Action Update Pattern

```java
@Override
public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    e.getPresentation().setEnabled(project != null && file != null);
}
```

### Settings Validation Pattern

```java
@Override
public void apply() throws ConfigurationException {
    if (!validateSettings()) {
        throw new ConfigurationException("Invalid settings");
    }
    settingsPanel.apply(settings);
}
```

## Troubleshooting

> Always check https://plugins.jetbrains.com/docs/intellij/ first when encountering issues. Use the sitemap at https://plugins.jetbrains.com/docs/intellij/sitemap.xml to find relevant pages.

### Plugin doesn't load

- Check `plugin.xml` syntax
- Verify package names match
- Check for missing dependencies

### Settings not persisting

- Verify `@State` annotation
- Check `getState()` and `loadState()` methods
- Ensure fields are `public`

### Icons not showing

- Verify icon path starts with `/icons/`
- Check SVG file exists in resources
- Ensure `IconLoader.getIcon()` path is correct

## References

- IntelliJ Platform SDK: https://plugins.jetbrains.com/docs/intellij/
- Sitemap (all documentation pages): https://plugins.jetbrains.com/docs/intellij/sitemap.xml
- Template project: `template-without-ai/`

## Examples

### Creating a New Plugin

1. Copy `template-without-ai` to your project
2. Update `gradle.properties` with your plugin info
3. Rename package from `example` to your package
4. Implement your action in `action/` package
5. Add icons and internationalization
6. Build and test with `./gradlew buildPlugin`
