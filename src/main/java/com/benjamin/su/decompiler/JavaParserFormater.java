package com.benjamin.su.decompiler;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

public class JavaParserFormater {
    private static Pattern pattern =Pattern.compile("/\\*[A-Z]{1,}:\\d+\\*/");
    
    private static Comparator<Node> comparator = new Comparator<Node>() {
        public int compare(Node n1, Node n2) {
            Matcher n1Matcher=pattern.matcher(n1.toString());
            Matcher n2Matcher=pattern.matcher(n2.toString());
            boolean n1Find=n1Matcher.find();
            boolean n2Find=n2Matcher.find();
            if(n1Find&&!n2Find){return 1;}

            if(!n1Find&&n2Find){return -1;}

            if(!n1Find&&!n2Find){return 0;}

            int n1Line=Integer.valueOf(n1Matcher.group(0).replaceAll("\\*|/|:|[A-Z]", ""));
            int n2Line=Integer.valueOf(n2Matcher.group(0).replaceAll("\\*|/|:|[A-Z]", ""));

            if(n1Line>n2Line){
                return 1;
            }else{
                return -1;
            }
        }
    };

    public static String format(String context) throws Exception{
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        ParseResult<CompilationUnit> parseResult = new JavaParser(parserConfiguration).parse(context);
        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();
            for(Node n:cu.getChildNodes()){
                if(n instanceof ClassOrInterfaceDeclaration){
                    Collections.sort(((ClassOrInterfaceDeclaration)n).getMembers(),comparator);
                }
            }
            BufferedReader reader = new BufferedReader(new StringReader(cu.toString()));
            String line;
            List<String> javaStringList=new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if(line.trim().length()>0){
                    javaStringList.add(line);
                }
            }
            
            for (int i=0;i<javaStringList.size();i++) {
                line=javaStringList.get(i);
                Matcher matcher=pattern.matcher(line);
                if(matcher.find()){
                    String lineNoStr=matcher.group(0);
                    int lineNo=Integer.valueOf(lineNoStr.replaceAll("\\*|/|:|[A-Z]", ""));
                    line=line.replace(lineNoStr, "")+javaStringList.get(i+1).trim()+"//LineNo:"+lineNo;
                    javaStringList.set(i, line);
                    javaStringList.remove(i+1);
                    i--;
                }
            }
            Pattern  tempPattern=Pattern.compile("//LineNo:\\d+");
            for (int i=0;i<javaStringList.size();i++) {
                line=javaStringList.get(i);
                Matcher matcher=tempPattern.matcher(line);
                if(matcher.find()){
                    int lineNo=Integer.valueOf(matcher.group(0).replace("//LineNo:", ""));
                    while(i+1<lineNo){
                        javaStringList.add(i, "");
                        i++;
                    }
                }
            }
            StringBuffer sb=new StringBuffer();
            for (String s : javaStringList) {
                sb.append(s).append("\n");
            }
            return sb.toString();
        }else{
            throw new Exception(parseResult.getProblems().toString());
        }

    }
}
