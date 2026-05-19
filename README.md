# Decompile Java Pretty

Decompile Java class files using [Procyon](https://github.com/mstrobel/procyon), with source line number alignment and member reordering.

![sample](https://raw.githubusercontent.com/bjmsu/vscode-java-decompiler/master/vsix_build/sample.jpg "sample")

## Features

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

### Windows + WSL support
Windows file paths are automatically converted to WSL-compatible paths (`C:\...` → `/mnt/c/...`).

## Requirements

- [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) (or `redhat.java`), version 0.12.0 or greater.

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| `java.decompiler.procyon.mergeVariables` | `false` | Attempt to merge variables |
| `java.decompiler.procyon.collapseImports` | `false` | Collapse wildcard imports |
| `java.decompiler.procyon.forceExplicitTypeArguments` | `false` | Always print generic type arguments |
| `java.decompiler.procyon.retainRedundantCasts` | `false` | Do not remove redundant casts |
| `java.decompiler.procyon.showSyntheticMembers` | `false` | Show compiler-generated members |
| `java.decompiler.procyon.excludeNestedTypes` | `false` | Exclude nested types when decompiling |
| `java.decompiler.procyon.isUnicodeOutputEnabled` | `false` | Output Unicode characters directly |
| `java.decompiler.procyon.flattenSwitchBlocks` | `false` | Drop braces around switch sections |
| `java.decompiler.procyon.simplifyMemberReferences` | `false` | Simplify type-qualified member references |
| `java.decompiler.procyon.disableForEachTransforms` | `false` | Disable for-each loop transforms |

## Changelog

### 2026.05
- Added JAR / WAR / ZIP archive browser (TreeView in Explorer sidebar)
- Added nested JAR browsing (e.g. `WEB-INF/lib/*.jar` inside a WAR)
- Added support for viewing non-class files inside archives
- Added Windows + WSL path normalization
- Fixed `IndexOutOfBoundsException` in `JavaParserFormater` for edge cases
- Cache now uses content hash (SHA-256) instead of file identifier

### 2024.08.24
- Using JavaParser to format as priority; falls back to default formatter on failure
- JavaParserFormater re-sorts members by line number

### 2024.08.15
- Removed fernflower/cfr decompilers
- Using Procyon `LineNumberFormatter` for better debug line alignment

### 2021.07.18
- Updated Procyon to support JDK 11
- When `showDebugLineNumbers` is enabled, output lines are aligned to original source lines
