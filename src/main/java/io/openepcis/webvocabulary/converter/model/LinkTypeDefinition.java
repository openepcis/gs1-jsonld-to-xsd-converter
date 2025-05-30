package io.openepcis.webvocabulary.converter.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinkTypeDefinition {
    private String linkTypeId;
    private Object rangeType;
    private String dataType;
    private String domain;
    private String description;
    private String type;
    private boolean deprecated;
}
