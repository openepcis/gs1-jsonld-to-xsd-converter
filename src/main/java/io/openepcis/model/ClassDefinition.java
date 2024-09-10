package io.openepcis.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ClassDefinition {
    private Boolean isSubclass;
    private String superClass;
    private List<PropertyDefinition> properties;
}