package com.benjamin.su.decompiler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.core.runtime.CoreException;


public class TestMain {
    public static void main(String[] args) throws CoreException, URISyntaxException {
        VineflowerDecompiler pd=new VineflowerDecompiler();
       
        System.out.println(pd.getContent(new URI("file://" + "/home/kubuntu/project/springbot/target/classes/com/bot/chain/wallet/TronWalletUtils.class"), null));
    }
}
