# 2024.08.15 Update
1. Remove fernflower/cfr
2. Using org.jboss.windup.decompiler.procyon.LineNumberPrintWriter
     and org.jboss.windup.decompiler.procyon.LineNumberFormatter
   to format source code for better debug.
3. Build up the project, will support decomoile class file directly, and jar file.


# 2021.07.18 Update
1. Update procyon to support JDK11.
2. When showDebugLineNumbers of procyon is true, align debug line to original line.


# Decompiler for Java&trade; in Visual Studio Code
*  This extension allows you to decompile Java class files. It requires [Language Support for Java&trade; by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java), version 0.12.0 or greater.

*  To see the decompiler in action, right-click on a Java symbol for which you don't have the source code, and choose Go to Definition (or simply command/ctrl+click on the symbol). You will see the decompiled code.


## Requirements
*  [Language Support for Java&trade; by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java), version 0.12.0 or greater. This extension does not work with older versions.


## Extension Settings
You can use the following settings to customize the decompiler:

* `java.contentProvider.preferred` (settings.json only): the ID of a decompiler to use. Currently, only `procyon` are supported..
* `java.decompiler.procyon` : additional configuration to provide to the decompiler. The format depends on the chosen decompiler. Use the autocomplete functionality of Visual Studio Code's settings to view the possible options and their descriptions.
