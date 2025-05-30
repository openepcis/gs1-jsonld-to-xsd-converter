package io.openepcis.webvocabulary.converter.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class RelationDefinition {
    private Map<String, ClassDefinition> classes;
    private Map<String, List<PropertyDefinition>> typeCodes;
    private List<LinkTypeDefinition> linkTypes;
    private Map<String, String> namespaces;
}