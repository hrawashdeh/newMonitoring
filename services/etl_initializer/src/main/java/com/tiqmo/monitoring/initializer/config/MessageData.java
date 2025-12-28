package com.tiqmo.monitoring.initializer.config;

import lombok.Data;

import java.util.List;

@Data
public class MessageData {
    private MessageMetadata metadata;
    private List<MessageConfig> messages;

    @Data
    public static class MessageMetadata {
        private Integer loadVersion;        // Version being loaded
        private String description;         // Description of this version
    }

    @Data
    public static class MessageConfig {
        private String messageCode;
        private String messageCategory;
        private String messageEn;
        private String messageAr;
        private String description;
        private String createdBy;
    }
}
