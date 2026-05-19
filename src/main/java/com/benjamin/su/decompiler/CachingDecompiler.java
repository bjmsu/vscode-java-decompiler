package com.benjamin.su.decompiler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.IDecompiler;

public abstract class CachingDecompiler implements IDecompiler {

    protected Map<String, String> cache = new HashMap<>();

    @Override
    public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
        String cacheKey = hashUri(uri);
        String content = this.cache.get(cacheKey);
        if (content == null)
            content = decompileContent(uri, monitor);
        if (content != null)
            this.cache.put(cacheKey, content);
        return content;
    }

    @Override
    public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
        String cacheKey = hashClassFile(classFile);
        String content = this.cache.get(cacheKey);
        if (content == null)
            content = decompileContent(classFile, monitor);
        if (content != null)
            this.cache.put(cacheKey, content);
        return content;
    }

    private String hashClassFile(IClassFile classFile) {
        try {
            byte[] bytes = classFile.getBytes();
            return sha256(bytes);
        } catch (JavaModelException e) {
            return classFile.getHandleIdentifier();
        }
    }

    private String hashUri(URI uri) {
        try (InputStream in = uri.toURL().openStream()) {
            return sha256(in.readAllBytes());
        } catch (IOException e) {
            return uri.toString();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    protected abstract String decompileContent(URI paramURI, IProgressMonitor paramIProgressMonitor)
            throws CoreException;

    protected abstract String decompileContent(IClassFile paramIClassFile, IProgressMonitor paramIProgressMonitor)
            throws CoreException;
}
