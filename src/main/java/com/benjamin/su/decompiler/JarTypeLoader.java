package com.benjamin.su.decompiler;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ITypeLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarTypeLoader implements ITypeLoader, AutoCloseable {

    private final ZipFile zipFile;
    private final String classPrefix;
    private final ITypeLoader fallback = new InputTypeLoader();

    public JarTypeLoader(String archivePath) throws IOException {
        this.zipFile = new ZipFile(archivePath);
        boolean isWar = archivePath.toLowerCase().endsWith(".war")
                || zipFile.getEntry("WEB-INF/classes/") != null;
        boolean isSpringBoot = zipFile.getEntry("BOOT-INF/classes/") != null;
        this.classPrefix = isWar ? "WEB-INF/classes/" : isSpringBoot ? "BOOT-INF/classes/" : "";
    }

    @Override
    public boolean tryLoadType(String internalName, Buffer buffer) {
        String entryName = classPrefix + internalName + ".class";
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry != null) {
            try (InputStream is = zipFile.getInputStream(entry)) {
                byte[] bytes = is.readAllBytes();
                buffer.reset(bytes.length);
                System.arraycopy(bytes, 0, buffer.array(), 0, bytes.length);
                return true;
            } catch (IOException e) {
                // fall through to fallback
            }
        }
        return fallback.tryLoadType(internalName, buffer);
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }
}
