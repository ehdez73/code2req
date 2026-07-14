package com.github.ehdez73.code2req.indexing.domain.analyzer.declaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GlobalDeclarationRegistry {

    public record SuperTypeInfo(String simpleName, String fqn) {}

    private final Map<String, List<DeclarationInfo>> byClassName = new ConcurrentHashMap<>();
    private final Map<String, List<SuperTypeInfo>> superTypes = new ConcurrentHashMap<>();
    private volatile boolean frozen;

    public void register(DeclarationInfo info) {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen after Pass 1");
        }
        byClassName.computeIfAbsent(info.className(), k -> new ArrayList<>()).add(info);
    }

    public void freeze() {
        this.frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void registerSuperType(String className, String simpleName, String fqn) {
        superTypes.computeIfAbsent(className, k -> new ArrayList<>())
            .add(new SuperTypeInfo(simpleName, fqn));
    }

    public List<SuperTypeInfo> getSuperTypes(String className) {
        return superTypes.getOrDefault(className, List.of());
    }

    public List<DeclarationInfo> findByClassName(String className) {
        var result = byClassName.get(className);
        return result != null ? List.copyOf(result) : List.of();
    }

    public boolean hasClass(String className) {
        return byClassName.containsKey(className);
    }

    public List<DeclarationInfo> findMethod(String className, String methodName, int paramCount) {
        var classDecls = byClassName.get(className);
        if (classDecls == null) return List.of();
        return classDecls.stream()
            .filter(d -> d.methodName().equals(methodName) && d.paramTypes().size() == paramCount)
            .toList();
    }

    public List<DeclarationInfo> findMethods(String className, String methodName) {
        var classDecls = byClassName.get(className);
        if (classDecls == null) return List.of();
        return classDecls.stream()
            .filter(d -> d.methodName().equals(methodName))
            .toList();
    }

    public boolean isEmpty() {
        return byClassName.isEmpty();
    }

    public int size() {
        return byClassName.values().stream().mapToInt(List::size).sum();
    }

    public Map<String, List<DeclarationInfo>> allDeclarations() {
        return Collections.unmodifiableMap(byClassName.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> List.copyOf(e.getValue())
            )));
    }
}
