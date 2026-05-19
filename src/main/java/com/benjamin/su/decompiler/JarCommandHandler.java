package com.benjamin.su.decompiler;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarCommandHandler implements IDelegateCommandHandler {

    public static final String CMD_BROWSE          = "decompile.browseJar";
    public static final String CMD_DECOMPILE       = "decompile.decompileEntry";
    public static final String CMD_READ            = "decompile.readEntry";
    public static final String CMD_BROWSE_NESTED    = "decompile.browseNestedJar";
    public static final String CMD_DECOMPILE_NESTED = "decompile.decompileNestedEntry";
    public static final String CMD_READ_NESTED      = "decompile.readNestedEntry";

    private static final ProcyonDecompiler decompiler = new ProcyonDecompiler();
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
        switch (commandId) {
            case CMD_BROWSE:
                return browse((String) arguments.get(0));
            case CMD_DECOMPILE:
                return decompile((String) arguments.get(0), (String) arguments.get(1), monitor);
            case CMD_READ:
                return readRaw((String) arguments.get(0), (String) arguments.get(1));
            case CMD_BROWSE_NESTED:
                return browseNested((String) arguments.get(0), (String) arguments.get(1));
            case CMD_DECOMPILE_NESTED:
                return decompileNested((String) arguments.get(0), (String) arguments.get(1), (String) arguments.get(2), monitor);
            case CMD_READ_NESTED:
                return readNested((String) arguments.get(0), (String) arguments.get(1), (String) arguments.get(2));
            default:
                throw new UnsupportedOperationException("Unknown command: " + commandId);
        }
    }

    private List<String> browse(String archivePath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipFile zf = new ZipFile(archivePath)) {
            zf.stream()
              .filter(e -> !e.isDirectory())
              .map(e -> e.getName())
              .sorted()
              .forEach(entries::add);
        }
        return entries;
    }

    private String readRaw(String archivePath, String entryPath) throws IOException {
        try (ZipFile zf = new ZipFile(archivePath)) {
            ZipEntry entry = zf.getEntry(entryPath);
            if (entry == null) return null;
            try (InputStream is = zf.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private List<String> browseNested(String archivePath, String nestedJarEntry) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipFile outer = new ZipFile(archivePath)) {
            ZipEntry nested = outer.getEntry(nestedJarEntry);
            if (nested == null) return entries;
            Path tmp = Files.createTempFile("decompiler-nested-", ".jar");
            try {
                try (InputStream is = outer.getInputStream(nested)) {
                    Files.write(tmp, is.readAllBytes());
                }
                try (ZipFile inner = new ZipFile(tmp.toFile())) {
                    inner.stream()
                         .filter(e -> !e.isDirectory())
                         .map(ZipEntry::getName)
                         .sorted()
                         .forEach(entries::add);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        return entries;
    }

    private String readNested(String archivePath, String nestedJarEntry, String entryPath) throws IOException {
        try (ZipFile outer = new ZipFile(archivePath)) {
            ZipEntry nested = outer.getEntry(nestedJarEntry);
            if (nested == null) return null;
            Path tmp = Files.createTempFile("decompiler-nested-", ".jar");
            try {
                try (InputStream is = outer.getInputStream(nested)) {
                    Files.write(tmp, is.readAllBytes());
                }
                try (ZipFile inner = new ZipFile(tmp.toFile())) {
                    ZipEntry entry = inner.getEntry(entryPath);
                    if (entry == null) return null;
                    try (InputStream is = inner.getInputStream(entry)) {
                        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
    }

    private String decompileNested(String archivePath, String nestedJarEntry, String classEntry, IProgressMonitor monitor) throws Exception {
        String key = archivePath + "!" + nestedJarEntry + "!" + classEntry;
        String cached = cache.get(key);
        if (cached != null) return cached;

        String internalName = classEntry.endsWith(".class")
                ? classEntry.substring(0, classEntry.length() - 6)
                : classEntry;

        try (ZipFile outer = new ZipFile(archivePath)) {
            ZipEntry nested = outer.getEntry(nestedJarEntry);
            if (nested == null) return null;
            Path tmp = Files.createTempFile("decompiler-nested-", ".jar");
            try {
                try (InputStream is = outer.getInputStream(nested)) {
                    Files.write(tmp, is.readAllBytes());
                }
                try (JarTypeLoader loader = new JarTypeLoader(tmp.toString())) {
                    String result = decompiler.getContent(loader, internalName, monitor);
                    if (result != null) cache.put(key, result);
                    return result;
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
    }

    private String decompile(String archivePath, String classEntry, IProgressMonitor monitor) throws Exception {
        String key = archivePath + "!" + classEntry;
        String cached = cache.get(key);
        if (cached != null) return cached;

        String internalName = classEntry.endsWith(".class")
                ? classEntry.substring(0, classEntry.length() - 6)
                : classEntry;
        // WAR 文件中 class 路径带有 WEB-INF/classes/ 前缀，JarTypeLoader 会自动加回
        if (internalName.startsWith("WEB-INF/classes/")) {
            internalName = internalName.substring("WEB-INF/classes/".length());
        }

        try (JarTypeLoader loader = new JarTypeLoader(archivePath)) {
            String result = decompiler.getContent(loader, internalName, monitor);
            if (result != null) cache.put(key, result);
            return result;
        }
    }
}
