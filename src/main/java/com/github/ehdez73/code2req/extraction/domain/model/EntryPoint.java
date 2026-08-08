package com.github.ehdez73.code2req.extraction.domain.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.ehdez73.code2req.common.util.HashUtils;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "epType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HttpEntryPoint.class, name = "HTTP"),
    @JsonSubTypes.Type(value = ScheduledEntryPoint.class, name = "SCHEDULED"),
    @JsonSubTypes.Type(value = KafkaEntryPoint.class, name = "KAFKA"),
    @JsonSubTypes.Type(value = RabbitMqEntryPoint.class, name = "RABBITMQ"),
    @JsonSubTypes.Type(value = ActiveMqEntryPoint.class, name = "ACTIVEMQ"),
    @JsonSubTypes.Type(value = EventListenerEntryPoint.class, name = "EVENT_LISTENER")
})
public sealed interface EntryPoint permits HttpEntryPoint, ScheduledEntryPoint,
    KafkaEntryPoint, RabbitMqEntryPoint, ActiveMqEntryPoint, EventListenerEntryPoint {

    String id();
    EntryPointType type();
    String className();
    String methodName();
    String filePath();
    double priorityScore();
    boolean trivial();
    int startLine();
    int endLine();

    default String shortId() {
        return HashUtils.sha256Hex(id()).substring(0, 8);
    }
}
