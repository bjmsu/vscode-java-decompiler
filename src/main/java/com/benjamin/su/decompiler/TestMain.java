package com.benjamin.su.decompiler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;


public class TestMain {
    public static void main(String[] args) throws CoreException, URISyntaxException {
        ProcyonDecompiler pd=new ProcyonDecompiler();
        System.out.println(pd.getContent(new URI(ArrayList.class.getName()), null));
    }
}
