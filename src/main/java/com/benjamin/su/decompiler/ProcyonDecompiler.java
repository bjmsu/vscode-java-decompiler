package com.benjamin.su.decompiler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.DeobfuscationUtilities;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.ITextOutput;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.TypeDecompilationResults;
import com.strobel.decompiler.languages.java.JavaFormattingOptions;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public class ProcyonDecompiler extends CachingDecompiler {
    public static final String OPTIONS_KEY = "java.decompiler.procyon";

    public static final String DECOMPILED_HEADER = " // Source code is unavailable, and was generated by the Procyon decompiler.\n";

    private DecompilerSettings settings = DecompilerSettings.javaDefaults();

    @Override
    public void setPreferences(Preferences preferences) {
        Object options = MapFlattener.getValue(preferences.asMap(), "java.decompiler.procyon");
        if (options instanceof Map) {
            Map<String, Object> optionsMap = (Map<String, Object>) options;
            for (String key : optionsMap.keySet()) {
                if (!key.startsWith("_")) {
                    optionsMap.put("_" + key, optionsMap.get(key));
                    optionsMap.remove(key);
                }
            }
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(options);
            this.settings = (DecompilerSettings) gson.fromJson(jsonElement, DecompilerSettings.class);
            this.settings.setJavaFormattingOptions(JavaFormattingOptions.createDefault());
            if (!((Map) options).containsKey("forceExplicitImports"))
                this.settings.setForceExplicitImports(true);
        }
    }

    @Override
    protected String decompileContent(URI uri, IProgressMonitor monitor) throws CoreException {
        IClassFile classFile = JDTUtils.resolveClassFile(uri);
        if (classFile != null)
            return decompileContent(classFile, monitor);
        String path = uri.getPath();
        return getContent((ITypeLoader) new InputTypeLoader((ITypeLoader) new ClasspathTypeLoader()), path, monitor);
    }

    @Override
    protected String decompileContent(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
        return getContent((ITypeLoader) new JDTTypeLoader(classFile), "Fake.class", monitor);
    }

    private String getContent(ITypeLoader typeLoader, String path, IProgressMonitor monitor) throws CoreException {
        this.settings.setTypeLoader(typeLoader);
        this.settings.setShowDebugLineNumbers(true);
        DecompilationOptions decompilationOptions = new DecompilationOptions();
        decompilationOptions.setSettings(this.settings);
        decompilationOptions.setFullDecompilation(true);
        MetadataSystem metadataSystem = new NoRetryMetadataSystem(decompilationOptions.getSettings().getTypeLoader());
        metadataSystem.setEagerMethodLoadingEnabled(false);
        if (typeLoader instanceof JDTTypeLoader)
            ((JDTTypeLoader) typeLoader).setMetadataResolver((IMetadataResolver) metadataSystem);
        TypeReference type = metadataSystem.lookupType(path);
        if (type == null)
            return null;
        TypeDefinition resolvedType = type.resolve();
        if (resolvedType == null)
            return null;
        DeobfuscationUtilities.processType(resolvedType);
        try {
            StringWriter writer = new StringWriter();
            PlainTextOutput output = new PlainTextOutput(writer);
            output.setUnicodeOutputEnabled(decompilationOptions.getSettings().isUnicodeOutputEnabled());
            TypeDecompilationResults results=decompilationOptions.getSettings().getLanguage().decompileType(resolvedType, (ITextOutput) output, decompilationOptions);
            writer.flush();
            writer.close();
            return new LineNumberFormatter(writer.toString(), results.getLineNumberPositions(),null).reformatFile()+ " // Source code is unavailable, and was generated by the Procyon decompiler.\n";
        } catch (Throwable t) {
            throw new CoreException(new Status(4, "dg.jdt.ls.decompiler.cfr", "Error decompiling", t));
        }
    }
}
