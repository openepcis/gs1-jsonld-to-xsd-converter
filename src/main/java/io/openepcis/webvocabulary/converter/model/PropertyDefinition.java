package io.openepcis.webvocabulary.converter.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyDefinition {
    private String property;
    private Object rangeType;
    private String domain;
    private String description;
    private String dataType;
    private String type;
    private boolean deprecated;
}
