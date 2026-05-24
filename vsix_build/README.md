# Decompile Java Pretty

Decompile Java class files using [Procyon](https://github.com/mstrobel/procyon) (default) or [Vineflower](https://github.com/Vineflower/vineflower), with source line number alignment.

## Features

### 2026.05 (0.1.3)
- **Dual decompiler engine: Procyon (default) + Vineflower** — switch with a single setting
- **[Vineflower](https://github.com/Vineflower/vineflower)** — better Java 17+ support, sealed classes, records, pattern matching; line numbers aligned via native bytecode-to-source mapping
- Added JAR / WAR / ZIP archive browser (TreeView in Explorer sidebar)
- Added nested JAR browsing (e.g. `WEB-INF/lib/*.jar` inside a WAR)
- Added support for viewing non-class files inside archives
- Added Spring Boot fat JAR support (`BOOT-INF/classes/` + loader classes at root)
- Added Windows + WSL path normalization
- Cache now uses content hash (SHA-256) instead of file identifier

### Switch decompiler engine

Open **Settings** and search for `java.decompiler.engine`:

| Value | Engine | Best for |
|---|---|---|
| `procyon` *(default)* | Procyon 0.6.0 | General use, stable output |
| `vineflower` | Vineflower 1.11.1 | Java 17+ features, records, sealed classes |

The switch takes effect immediately — no restart required.

### Decompile `.class` files
Open any `.class` file in a Java project — the decompiled source is shown automatically with line numbers aligned to the original bytecode.

To see it in action, right-click on a Java symbol for which you don't have the source code and choose **Go to Definition** (or Ctrl/Cmd+click). The decompiled code is displayed.

### Browse and decompile JAR / WAR / ZIP archives
Drag a `.jar`, `.war`, or `.zip` file into VS Code, or right-click it in the Explorer and choose **Open as Decompiled JAR/WAR**.

- The file listing is shown in the editor
- The **JAR / WAR Contents** panel in the Explorer sidebar shows the full directory tree
- Click any `.class` entry to decompile it
- Click other files (`.xml`, `.properties`, `MANIFEST.MF`, etc.) to view their raw content

### Nested JAR support
JARs inside a WAR's `WEB-INF/lib/` directory are shown as expandable folders — contents are loaded lazily on expand.

### Spring Boot fat JAR support
Spring Boot executable JARs are fully supported — application classes under `BOOT-INF/classes/` and Spring Boot loader classes at the archive root are both handled correctly.

### Windows + WSL support
Windows file paths are automatically converted to WSL-compatible paths (`C:\...` → `/mnt/c/...`).

## Requirements

- [Language Support for Java&trade; by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java), version 0.12.0 or greater.

## Extension Settings

| Setting | Type | Default | Description |
|---|---|---|---|
| `java.decompiler.engine` | `string` | `procyon` | Decompiler engine: `procyon` or `vineflower` |

## Changelog

![sample](https://raw.githubusercontent.com/bjmsu/vscode-java-decompiler/master/vsix_build/sample.jpg "sample")

### 0.1.3 (2026.05)
- **Dual engine: Procyon (default) + [Vineflower](https://github.com/Vineflower/vineflower)**
- `java.decompiler.engine` setting to switch engines at runtime
- Vineflower: line numbers aligned using `bytecode-source-mapping` + `__dump_original_lines__`
- Spring Boot fat JAR support (`BOOT-INF/classes/` and loader classes)
- Inner class decompilation fixed for both engines

### 0.1.2 (2026.05)
- Added JAR / WAR / ZIP archive browser
- Added nested JAR browsing
- Added Windows + WSL path support
- Cache uses SHA-256 content hash

### 0.1.1 (2024.08)
- Using JavaParser to format as priority; falls back to default formatter on failure

### 0.1.0 (2024.08)
- Removed fernflower/cfr decompilers
- Using Procyon `LineNumberFormatter` for debug line alignment

### 0.0.1 (2021.07)
- Initial release with Procyon decompiler
