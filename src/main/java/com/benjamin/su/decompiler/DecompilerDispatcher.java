package com.benjamin.su.decompiler;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.IDecompiler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import java.net.URI;

public class DecompilerDispatcher implements IDecompiler {

    private final VineflowerDecompiler vineflower = new VineflowerDecompiler();
    private final ProcyonDecompiler procyon = new ProcyonDecompiler();

    @Override
    public void setPreferences(Preferences preferences) {
        vineflower.setPreferences(preferences);
        procyon.setPreferences(preferences);
    }

    @Override
    public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
        return active().getContent(uri, monitor);
    }

    @Override
    public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
        return active().getSource(classFile, monitor);
    }

    private CachingDecompiler active() {
        return isVineflower() ? vineflower : procyon;
    }

    String decompileFromJar(String archivePath, String internalName, IProgressMonitor monitor) throws Exception {
        if (isVineflower()) return vineflower.decompileFromJar(archivePath, internalName, monitor);
        return procyon.decompileFromJar(archivePath, internalName, monitor);
    }

    String decompileFromNestedJar(String archivePath, String nestedEntry, String internalName, IProgressMonitor monitor) throws Exception {
        if (isVineflower()) return vineflower.decompileFromNestedJar(archivePath, nestedEntry, internalName, monitor);
        return procyon.decompileFromNestedJar(archivePath, nestedEntry, internalName, monitor);
    }

    // 每次实时读取当前配置，供 JarCommandHandler 拼 cache key 使用
    static String currentEngineTag() {
        return isVineflower() ? "vf" : "proc";
    }

    private static boolean isVineflower() {
        try {
            PreferenceManager pm = JavaLanguageServerPlugin.getPreferencesManager();
            if (pm != null) {
                Object v = MapFlattener.getValue(pm.getPreferences().asMap(), "java.decompiler.engine");
                if (v != null) return !"procyon".equalsIgnoreCase(String.valueOf(v));
            }
        } catch (Exception ignored) {}
        return false; // 默认 procyon
    }
}
