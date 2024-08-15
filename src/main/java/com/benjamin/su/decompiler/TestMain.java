package com.benjamin.su.decompiler;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;

import com.strobel.assembler.ir.ConstantPool;

public class TestMain {
    public static void main(String[] args) throws CoreException, URISyntaxException {
        ProcyonDecompiler pd=new ProcyonDecompiler();
        System.out.println(pd.getContent(new URI(ConstantPool.class.getName()), null));
    }
}
