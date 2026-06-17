package com.github.ehdez73.code2req.analyzer.event.link;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record TopicLink(
    String brokerType,
    String topic,
    String producerClassName,
    String producerFilePath,
    String consumerClassName,
    String consumerFilePath,
    String resolvedStatus
) implements AnalysisFinding {

    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_PENDING = "PENDING";

    @Override
    public String className() {
        return producerClassName != null ? producerClassName : consumerClassName;
    }

    @Override
    public String filePath() {
        return producerFilePath != null ? producerFilePath : consumerFilePath;
    }

    public static TopicLink resolved(String brokerType, String topic,
                                      String producerClassName, String producerFilePath,
                                      String consumerClassName, String consumerFilePath) {
        return new TopicLink(brokerType, topic, producerClassName, producerFilePath,
                             consumerClassName, consumerFilePath, STATUS_RESOLVED);
    }

    public static TopicLink orphanProducer(String brokerType, String topic,
                                            String producerClassName, String producerFilePath) {
        return new TopicLink(brokerType, topic, producerClassName, producerFilePath,
                             null, null, STATUS_PENDING);
    }

    public static TopicLink orphanConsumer(String brokerType, String topic,
                                            String consumerClassName, String consumerFilePath) {
        return new TopicLink(brokerType, topic, null, null,
                             consumerClassName, consumerFilePath, STATUS_PENDING);
    }
}
