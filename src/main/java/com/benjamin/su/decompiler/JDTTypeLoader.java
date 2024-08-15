package com.benjamin.su.decompiler;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ClassFileReader;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.TypeDefinition;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;

public class JDTTypeLoader extends InputTypeLoader {
    public static final String FAKE_CLASS_NAME = "Fake.class";

    private final byte[] bytes;

    private IMetadataResolver metadataResolver;

    private String internalName;

    public JDTTypeLoader(IClassFile classFile) throws JavaModelException {
        this.bytes = classFile.getBytes();
    }

    public void setMetadataResolver(IMetadataResolver metadataResolver) {
        this.metadataResolver = metadataResolver;
    }

    public boolean tryLoadType(String typeNameOrPath, Buffer buffer) {
        if (typeNameOrPath.equals("Fake.class") || typeNameOrPath.equals(this.internalName)) {
            buffer.reset(this.bytes.length);
            System.arraycopy(this.bytes, 0, buffer.array(), 0, this.bytes.length);
            if (this.internalName == null) {
                TypeDefinition type = ClassFileReader.readClass(0, this.metadataResolver, buffer);
                if (type != null)
                    this.internalName = type.getInternalName();
                buffer.position(0);
            }
            return true;
        }
        return super.tryLoadType(typeNameOrPath, buffer);
    }
}
