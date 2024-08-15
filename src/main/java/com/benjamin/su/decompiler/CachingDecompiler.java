package com.benjamin.su.decompiler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.IDecompiler;

public abstract class CachingDecompiler implements IDecompiler {

    protected Map<String, String> cache = new HashMap<>();

    @Override
    public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
        String cacheKey = uri.toString();
        String content =this.cache.get(cacheKey);
        if (content == null)
            content = decompileContent(uri, monitor);
        if (content != null)
            this.cache.put(cacheKey, content);
        return content;
    }

    @Override
    public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
        String cacheKey = classFile.getHandleIdentifier();
        String content = (String) this.cache.get(cacheKey);
        if (content == null)
            content = decompileContent(classFile, monitor);
        if (content != null)
            this.cache.put(cacheKey, content);
        return content;
    }

    protected abstract String decompileContent(URI paramURI, IProgressMonitor paramIProgressMonitor)
            throws CoreException;

    protected abstract String decompileContent(IClassFile paramIClassFile, IProgressMonitor paramIProgressMonitor)
            throws CoreException;
}
