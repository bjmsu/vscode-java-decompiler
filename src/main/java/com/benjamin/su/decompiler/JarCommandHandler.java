package com.benjamin.su.decompiler;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
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
    public static final String CMD_BROWSE_NESTED           = "decompile.browseNestedJar";
    public static final String CMD_DECOMPILE_NESTED        = "decompile.decompileNestedEntry";
    public static final String CMD_READ_NESTED             = "decompile.readNestedEntry";
    public static final String CMD_GET_INNER_CLASS_LINE        = "decompile.getInnerClassLine";
    public static final String CMD_GET_NESTED_INNER_CLASS_LINE = "decompile.getNestedInnerClassLine";

    private static final DecompilerDispatcher decompiler = new DecompilerDispatcher();
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
            case CMD_GET_INNER_CLASS_LINE:
                return getInnerClassStartLine((String) arguments.get(0), (String) arguments.get(1));
            case CMD_GET_NESTED_INNER_CLASS_LINE:
                return getNestedInnerClassStartLine((String) arguments.get(0), (String) arguments.get(1), (String) arguments.get(2));
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
        String internalName = classEntry.endsWith(".class")
                ? classEntry.substring(0, classEntry.length() - 6)
                : classEntry;
        // 内部类/匿名类重定向到外部主类
        int dollarIdx = internalName.indexOf('$');
        if (dollarIdx != -1) {
            internalName = internalName.substring(0, dollarIdx);
        }
        String key = DecompilerDispatcher.currentEngineTag() + "!" + archivePath + "!" + nestedJarEntry + "!" + internalName;
        String cached = cache.get(key);
        if (cached != null) return cached;

        String result = decompiler.decompileFromNestedJar(archivePath, nestedJarEntry, internalName, monitor);
        if (result != null) cache.put(key, result);
        return result;
    }

    private String decompile(String archivePath, String classEntry, IProgressMonitor monitor) throws Exception {
        String internalName = classEntry.endsWith(".class")
                ? classEntry.substring(0, classEntry.length() - 6)
                : classEntry;
        // WAR/Spring Boot JAR 中 class 路径带有前缀，JarTypeLoader 会自动加回
        if (internalName.startsWith("WEB-INF/classes/")) {
            internalName = internalName.substring("WEB-INF/classes/".length());
        } else if (internalName.startsWith("BOOT-INF/classes/")) {
            internalName = internalName.substring("BOOT-INF/classes/".length());
        }
        // 内部类/匿名类（Foo$1、Foo$Bar）重定向到外部主类
        int dollarIdx = internalName.indexOf('$');
        if (dollarIdx != -1) {
            internalName = internalName.substring(0, dollarIdx);
        }
        String key = DecompilerDispatcher.currentEngineTag() + "!" + archivePath + "!" + internalName;
        String cached = cache.get(key);
        if (cached != null) return cached;

        String result = decompiler.decompileFromJar(archivePath, internalName, monitor);
        if (result != null) cache.put(key, result);
        return result;
    }

    private int getInnerClassStartLine(String archivePath, String classEntry) throws IOException {
        try (ZipFile zf = new ZipFile(archivePath)) {
            ZipEntry entry = zf.getEntry(classEntry);
            if (entry == null) return -1;
            try (InputStream is = zf.getInputStream(entry)) {
                return parseMinLineNumber(is.readAllBytes());
            }
        }
    }

    private int getNestedInnerClassStartLine(String archivePath, String nestedJarEntry, String classEntry) throws IOException {
        try (ZipFile outer = new ZipFile(archivePath)) {
            ZipEntry nested = outer.getEntry(nestedJarEntry);
            if (nested == null) return -1;
            Path tmp = Files.createTempFile("decompiler-nested-", ".jar");
            try {
                try (InputStream is = outer.getInputStream(nested)) {
                    Files.write(tmp, is.readAllBytes());
                }
                try (ZipFile inner = new ZipFile(tmp.toFile())) {
                    ZipEntry entry = inner.getEntry(classEntry);
                    if (entry == null) return -1;
                    try (InputStream is = inner.getInputStream(entry)) {
                        return parseMinLineNumber(is.readAllBytes());
                    }
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
    }

    /** 解析 class 字节码，返回所有方法 LineNumberTable 中的最小源行号，失败返回 -1。 */
    private int parseMinLineNumber(byte[] classBytes) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (dis.readInt() != 0xCAFEBABE) return -1;
            dis.readUnsignedShort(); dis.readUnsignedShort(); // minor, major

            // 常量池
            int cpCount = dis.readUnsignedShort();
            String[] utf8 = new String[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = dis.readUnsignedByte();
                switch (tag) {
                    case 1: { // Utf8
                        int len = dis.readUnsignedShort();
                        byte[] b = new byte[len];
                        dis.readFully(b);
                        utf8[i] = new String(b, StandardCharsets.UTF_8);
                        break;
                    }
                    case 3: case 4: dis.readInt(); break;       // Integer, Float
                    case 5: case 6: dis.readLong(); i++; break; // Long, Double（占两个槽）
                    case 7: case 8: case 16: case 19: case 20:  // Class, String, MethodType, Module, Package
                        dis.readUnsignedShort(); break;
                    case 9: case 10: case 11: case 12:          // *ref, NameAndType
                    case 17: case 18:                           // Dynamic, InvokeDynamic
                        dis.readUnsignedShort(); dis.readUnsignedShort(); break;
                    case 15:                                    // MethodHandle
                        dis.readUnsignedByte(); dis.readUnsignedShort(); break;
                }
            }

            dis.readUnsignedShort(); dis.readUnsignedShort(); dis.readUnsignedShort(); // flags, this, super
            dis.skipBytes(dis.readUnsignedShort() * 2); // interfaces
            int fieldCount = dis.readUnsignedShort();
            for (int i = 0; i < fieldCount; i++) skipMember(dis);

            int methodCount = dis.readUnsignedShort();
            int minLine = Integer.MAX_VALUE;
            for (int i = 0; i < methodCount; i++) {
                dis.readUnsignedShort(); dis.readUnsignedShort(); dis.readUnsignedShort(); // access, name, desc
                int attrCount = dis.readUnsignedShort();
                for (int j = 0; j < attrCount; j++) {
                    int nameIdx = dis.readUnsignedShort();
                    int attrLen = dis.readInt();
                    String attrName = (nameIdx > 0 && nameIdx < utf8.length) ? utf8[nameIdx] : null;
                    if ("Code".equals(attrName)) {
                        dis.readUnsignedShort(); dis.readUnsignedShort(); // max_stack, max_locals
                        dis.skipBytes(dis.readInt());                     // code[]
                        dis.skipBytes(dis.readUnsignedShort() * 8);      // exception_table[]
                        int codeAttrCount = dis.readUnsignedShort();
                        for (int k = 0; k < codeAttrCount; k++) {
                            int cnIdx = dis.readUnsignedShort();
                            int codeAttrLen = dis.readInt();
                            String codeAttrName = (cnIdx > 0 && cnIdx < utf8.length) ? utf8[cnIdx] : null;
                            if ("LineNumberTable".equals(codeAttrName)) {
                                int tableLen = dis.readUnsignedShort();
                                for (int t = 0; t < tableLen; t++) {
                                    dis.readUnsignedShort(); // start_pc
                                    int line = dis.readUnsignedShort();
                                    if (line > 0 && line < minLine) minLine = line;
                                }
                            } else {
                                dis.skipBytes(codeAttrLen);
                            }
                        }
                    } else {
                        dis.skipBytes(attrLen);
                    }
                }
            }
            return minLine == Integer.MAX_VALUE ? -1 : minLine;
        } catch (IOException e) {
            return -1;
        }
    }

    private void skipMember(DataInputStream dis) throws IOException {
        dis.readUnsignedShort(); dis.readUnsignedShort(); dis.readUnsignedShort(); // access, name, desc
        int attrCount = dis.readUnsignedShort();
        for (int i = 0; i < attrCount; i++) {
            dis.readUnsignedShort();
            dis.skipBytes(dis.readInt());
        }
    }
}
