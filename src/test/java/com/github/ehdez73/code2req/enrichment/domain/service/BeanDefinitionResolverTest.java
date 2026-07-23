package com.github.ehdez73.code2req.enrichment.domain.service;

import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeanDefinitionResolverTest {

    @Mock
    private ExecutionFindingStore executionFindingStore;

    @InjectMocks
    private BeanDefinitionResolver resolver;

    @Test
    void buildRegistryWithXmlBeans() {
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of(
                Map.of("finding_json", "{\"beanId\": \"myBean\", \"className\": \"com.example.MyBean\"}")
            ));
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of());

        var registry = resolver.buildRegistry();
        assertEquals("com.example.MyBean", registry.get("myBean"));
    }

    @Test
    void buildRegistryWithBeanMethods() {
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of(
                Map.of("finding_json", "{\"beanName\": \"dataSource\", \"returnType\": \"javax.sql.DataSource\"}")
            ));
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of());

        var registry = resolver.buildRegistry();
        assertEquals("javax.sql.DataSource", registry.get("dataSource"));
    }

    @Test
    void buildRegistryWithComponents() {
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of(
                Map.of("finding_json", "{\"className\": \"com.example.MyService\"}")
            ));

        var registry = resolver.buildRegistry();
        assertEquals("com.example.MyService", registry.get("myService"));
    }

    @Test
    void componentWithInnerClassIsSkipped() {
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of(
                Map.of("finding_json", "{\"className\": \"com.example.MyService$Inner\"}")
            ));

        var registry = resolver.buildRegistry();
        assertFalse(registry.containsKey("inner"));
    }

    @Test
    void emptyStoreReturnsEmptyRegistry() {
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of());

        assertTrue(resolver.buildRegistry().isEmpty());
    }

    @Test
    void nullFindingJsonIsSkipped() {
        var map = new java.util.HashMap<String, Object>();
        map.put("finding_json", null);
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of(map));
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of());

        assertTrue(resolver.buildRegistry().isEmpty());
    }

    @Test
    void invalidFindingJsonIsSkipped() {
        when(executionFindingStore.findAllByType("XML_BEAN"))
            .thenReturn(List.of(Map.of("finding_json", "not-json")));
        when(executionFindingStore.findAllByType("BEAN_METHOD"))
            .thenReturn(List.of());
        when(executionFindingStore.findAllByType("COMPONENT"))
            .thenReturn(List.of());

        assertTrue(resolver.buildRegistry().isEmpty());
    }
}
