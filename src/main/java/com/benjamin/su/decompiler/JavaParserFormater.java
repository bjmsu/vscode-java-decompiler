package com.benjamin.su.decompiler;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

public class JavaParserFormater {
    private static Pattern pattern =Pattern.compile("/\\*[A-Z]{1,}:\\d+\\*/");
    public static String format(String context) throws Exception{
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        ParseResult<CompilationUnit> parseResult = new JavaParser(parserConfiguration).parse(context);
        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();
            for(Node n:cu.getChildNodes()){
                if(n instanceof ClassOrInterfaceDeclaration){
                    NodeList<BodyDeclaration<?>> members=((ClassOrInterfaceDeclaration)n).getMembers();
                    for(int i=0;i<members.size();i++){
                        BodyDeclaration<?> n1=members.get(i);
                        Matcher n1Matcher=pattern.matcher(n1.toString());
                        if(!n1Matcher.find()){
                            continue;
                        }
                        int n1Line=Integer.valueOf(n1Matcher.group(0).replaceAll("\\*|/|:|[A-Z]", ""));
                        for(int j=i+1;j<members.size();j++){
                            
                            BodyDeclaration<?> n2=members.get(j);
                            Matcher n2Matcher=pattern.matcher(n2.toString());
                            if(!n2Matcher.find()){
                                continue;
                            }
                            int n2Line=Integer.valueOf(n2Matcher.group(0).replaceAll("\\*|/|:|[A-Z]", ""));
                            
                            if(n1Line>n2Line){
                                members.remove(i);
                                members.add(i, n2);
                                members.remove(j);
                                members.add(j, n1);
                                i--;
                                break;
                            }
                        }
                    }
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
                    line=line.replace(lineNoStr, "")+" "+javaStringList.get(i+1).trim()+lineNoStr;
                    javaStringList.set(i, line);
                    javaStringList.remove(i+1);
                }
                if(javaStringList.get(i).trim().endsWith("{")&&javaStringList.get(i+1).trim().equals("}")){
                    line=javaStringList.remove(i);
                    javaStringList.set(i, line+"}");
                }
            }
            for (int i=0;i<javaStringList.size();i++) {
                line=javaStringList.get(i);
                Matcher matcher=pattern.matcher(line);
                if(matcher.find()){
                    int lineNo=Integer.valueOf(matcher.group(0).replaceAll("\\*|/|[A-Z]|:", ""));
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
