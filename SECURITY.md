# Security Policy

## Supported Versions

Only the latest release is actively supported with security updates.
Users are strongly encouraged to always upgrade to the newest available version.

| Version        | Supported          |
| -------------- | ------------------ |
| Latest release | :white_check_mark: |
| Older releases | :x:                |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue in
this plugin, please **do not** open a public issue.

Instead, please report it privately so that we can address it before it is
disclosed publicly:

- Open a **private security advisory** on GitHub:
  [github.com/CarmJos/quarkdown-for-intellij/security/advisories](https://github.com/CarmJos/quarkdown-for-intellij/security/advisories/new)
- Or contact the maintainer directly through GitHub.

Please include the following information in your report:

1. The plugin version and IntelliJ IDEA version where the vulnerability occurs.
2. A description of the vulnerability and its potential impact.
3. Steps to reproduce (including any `.qd` file content if relevant).
4. Any suggested fixes, if you have them.

### What to expect

- You will receive an acknowledgement of your report within **72 hours**.
- We will investigate the issue and provide an update on its status and
  expected resolution timeline.
- Once the vulnerability is fixed, a new release will be published and the
  advisory will be disclosed (after a reasonable coordination period if needed).

### Scope

This policy applies to the **Quarkdown for IntelliJ** plugin repository
(`CarmJos/quarkdown-for-intellij`).

Vulnerabilities found in the **Quarkdown core engine**, the **Quarkdown
Language Server**, or other parts of the Quarkdown ecosystem should be reported
to the upstream [Quarkdown project](https://github.com/iamgio/quarkdown/security)
instead.

## Security Best Practices for Users

- Always keep the plugin and your IntelliJ IDEA up to date.
- Only install plugins from trusted sources (JetBrains Marketplace or the
  official [GitHub Releases](https://github.com/CarmJos/quarkdown-for-intellij/releases)).
- Be cautious when opening `.qd` files from untrusted sources, as they may
  reference or include external content at preview/build time.

