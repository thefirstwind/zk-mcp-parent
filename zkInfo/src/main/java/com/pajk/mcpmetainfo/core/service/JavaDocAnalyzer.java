package com.pajk.mcpmetainfo.core.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JavaDocAnalyzer {

    public void analyzeSource(File sourceRoot, Map<String, String> interfaceDocs, 
                              Map<String, String> methodDocs, 
                              Map<String, List<String>> paramNames,
                              Map<String, String> methodParamDocs,
                              Map<String, String> methodReturnDocs) {
        if (!sourceRoot.exists()) return;
        
        try {
            // Check if src/main/java exists inside sourceRoot
            File javaSrc = new File(sourceRoot, "src/main/java");
            if (javaSrc.exists()) {
                sourceRoot = javaSrc;
            }

            Files.walk(sourceRoot.toPath())
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(path -> parseJavaFile(path, interfaceDocs, methodDocs, paramNames, methodParamDocs, methodReturnDocs));
        } catch (IOException e) {
            log.error("Failed to walk source directory: {}", sourceRoot, e);
        }
    }

    private void parseJavaFile(Path path, Map<String, String> interfaceDocs,
                               Map<String, String> methodDocs,
                               Map<String, List<String>> paramNames,
                               Map<String, String> methodParamDocs,
                               Map<String, String> methodReturnDocs) {
        try {
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> result = javaParser.parse(path);
            
            result.getResult().ifPresent(cu -> {
                String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                    if (c.isInterface()) { // Only care about interfaces for Dubbo
                        String fullName;
                        if (packageName.isEmpty()) {
                            fullName = c.getNameAsString();
                        } else {
                            fullName = packageName + "." + c.getNameAsString();
                        }
                        
                        // Interface Javadoc
                        c.getJavadoc().ifPresent(javadoc -> {
                            interfaceDocs.put(fullName, javadoc.getDescription().toText());
                        });

                        // Method Javadoc
                        c.getMethods().forEach(m -> {
                            String methodName = m.getNameAsString();
                            String key = fullName + "#" + methodName;
                            
                            m.getJavadoc().ifPresent(javadoc -> {
                                methodDocs.put(key, javadoc.getDescription().toText());
                                
                                // Extract @param and @return
                                javadoc.getBlockTags().forEach(tag -> {
                                    if (tag.getType() == com.github.javaparser.javadoc.JavadocBlockTag.Type.PARAM) {
                                        tag.getName().ifPresent(name -> {
                                            String paramKey = key + "#" + name;
                                            methodParamDocs.put(paramKey, tag.getContent().toText());
                                        });
                                    } else if (tag.getType() == com.github.javaparser.javadoc.JavadocBlockTag.Type.RETURN) {
                                        methodReturnDocs.put(key, tag.getContent().toText());
                                    }
                                });
                            });
                            
                            // Parameter Names
                            List<String> params = m.getParameters().stream()
                                .map(p -> p.getNameAsString())
                                .collect(Collectors.toList());
                            if (!params.isEmpty()) {
                                paramNames.put(key, params);
                            }
                        });
                    }
                });
            });
        } catch (Exception e) {
            log.warn("Failed to parse java file: {}", path, e);
        }
    }
}
