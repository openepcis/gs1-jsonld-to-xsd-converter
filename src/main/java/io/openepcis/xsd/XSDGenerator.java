package io.openepcis.xsd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.model.LinkTypeDefinition;
import io.openepcis.model.PropertyDefinition;
import io.openepcis.model.RelationDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XSDGenerator {

    final ObjectMapper objectMapper = new ObjectMapper();

    public final void generateXSD(final InputStream jsonInputStream) throws IOException, TransformerException, ParserConfigurationException {
        // Create a new XML document for storing the XSD
        final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        final Document doc = docBuilder.newDocument();

        final RelationDefinition relationDefinition = objectMapper.readValue(jsonInputStream, RelationDefinition.class);

        // Create XSD schema root element with all the namespaces
        final Element schemaRoot = doc.createElement("xsd:schema");
        relationDefinition.getNamespaces().entrySet().stream().forEach(entry -> {
            // Remove the '#' character from the value if it is present in the XMLSchema namespace
            String namespaceUri = entry.getValue();
            if(namespaceUri.endsWith("/") || namespaceUri.endsWith("#")){
                namespaceUri = namespaceUri.substring(0, namespaceUri.length() -1);
            }
            schemaRoot.setAttribute("xmlns:" + entry.getKey(), namespaceUri);
        });
        doc.appendChild(schemaRoot);

        final List<Element> deferredComplexTypes = new ArrayList<>();
        processClasses(doc, schemaRoot, relationDefinition, deferredComplexTypes);
        processTypeCodes(doc, schemaRoot, relationDefinition, deferredComplexTypes);
        appendDeferredComplexTypes(schemaRoot, deferredComplexTypes);
        processLinkTypes(doc, schemaRoot, relationDefinition);

        writeDocumentToFile(doc, "JsonLdSchemaXSD.xsd");
        System.out.println("********* XSD Generated onto JsonLdSchemaXSD.xsd *********");
    }

    private void processClasses(final Document doc, final Element schemaRoot, final RelationDefinition relationDefinition, final List<Element> deferredComplexTypes) {
        if (relationDefinition.getClasses() != null) {
            for (Map.Entry<String, List<PropertyDefinition>> entry : relationDefinition.getClasses().entrySet()) {
                Element complexType = createComplexType(doc, schemaRoot, entry.getKey(), entry.getValue(), relationDefinition, deferredComplexTypes);
                schemaRoot.appendChild(complexType);
            }
        }
    }

    // Function to create the XSD element for all the class and their properties
    private void processTypeCodes(final Document doc, final Element schemaRoot, final RelationDefinition relationDefinition, final List<Element> deferredComplexTypes) {
        if (relationDefinition.getTypeCodes() != null) {
            for (Map.Entry<String, List<PropertyDefinition>> entry : relationDefinition.getTypeCodes().entrySet()) {
                final String className = entry.getKey();
                final List<PropertyDefinition> properties = entry.getValue();

                if (!relationDefinition.getClasses().containsKey(entry.getKey())) {
                    final Element simpleTypeElement = doc.createElement("xsd:simpleType");
                    simpleTypeElement.setAttribute("name", className);

                    final Element restrictionElement = doc.createElement("xsd:restriction");
                    restrictionElement.setAttribute("base", "xsd:string");

                    for (PropertyDefinition property : properties) {
                        final Element enumElement = doc.createElement("xsd:enumeration");
                        enumElement.setAttribute("value", property.getProperty());
                        restrictionElement.appendChild(enumElement);
                    }
                    simpleTypeElement.appendChild(restrictionElement);
                    schemaRoot.appendChild(simpleTypeElement);
                }
            }
        }
    }

    private void appendDeferredComplexTypes(final Element schemaRoot, final List<Element> deferredComplexTypes) {
        for (Element complexType : deferredComplexTypes) {
            schemaRoot.appendChild(complexType);
        }
    }

    private void processLinkTypes(final Document doc, final Element schemaRoot, final RelationDefinition relationDefinition) {
        if (relationDefinition.getLinkTypes() != null) {
            for (LinkTypeDefinition linkType : relationDefinition.getLinkTypes()) {
                final Element element = doc.createElement("xsd:element");

                // Explicitly set the attributes in the desired order
                element.setAttributeNS(null, "name", linkType.getLinkTypeId());
                element.setAttributeNS(null, "type", mapSimpleType((String) linkType.getRangeType()));
                schemaRoot.appendChild(element);
            }
        }
    }

    private void writeDocumentToFile(final Document doc, final String filePath) throws TransformerException {
        // Write the XML document to file
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        final Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        final DOMSource source = new DOMSource(doc);
        final StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);
    }

    private Element createComplexType(final Document doc, final Element schemaRoot, String typeName, List<PropertyDefinition> properties, RelationDefinition relationDefinition, List<Element> deferredComplexTypes) {
        Element complexType = doc.createElement("xsd:complexType");
        complexType.setAttribute("name", typeName);
        Element sequence = doc.createElement("xsd:sequence");

        for (PropertyDefinition property : properties) {
            Element element = doc.createElement("xsd:element");
            element.setAttribute("name", property.getProperty());
            String xsdType = getXsdType(property);
            if (xsdType != null) {
                element.setAttribute("type", xsdType);
            } else {
                element.appendChild(createComplexElement(doc, schemaRoot, property, relationDefinition, deferredComplexTypes));
            }
            sequence.appendChild(element);
        }

        complexType.appendChild(sequence);
        return complexType;
    }

    private String getXsdType(final PropertyDefinition property) {
        if ("simple".equals(property.getDataType())) {
            return mapSimpleType((String) property.getRangeType());
        } else if ("complex".equals(property.getDataType())) {
            if (property.getRangeType() instanceof String rangeType) {
                return rangeType;
            } else if (property.getRangeType() instanceof List) {
                //If range is of type List then get the domain
                return property.getDomain();
            }
        }
        return null;
    }

    private Element createComplexElement(final Document doc, final Element schemaRoot, PropertyDefinition property, RelationDefinition relationDefinition, List<Element> deferredComplexTypes) {
        final Element choice = doc.createElement("xsd:choice");

        if (property.getRangeType() instanceof List) {
            final List<String> rangeTypes = (List<String>) property.getRangeType();
            for (String rangeType : rangeTypes) {
                choice.appendChild(createComplexType(doc, schemaRoot, rangeType, relationDefinition.getClasses().get(rangeType), relationDefinition, deferredComplexTypes));
            }
        } else if ("complex".equals(property.getDataType())) {
            String rangeType = (String) property.getRangeType();
            choice.appendChild(createComplexType(doc, schemaRoot, rangeType, relationDefinition.getTypeCodes().get(rangeType), relationDefinition, deferredComplexTypes));
        }
        return choice;
    }

    private String mapSimpleType(final String rangeType) {
        return switch (rangeType) {
            case "xsd:string", "rdf:langString" -> "xsd:string";
            case "xsd:date" -> "xsd:date";
            case "xsd:anyURI" -> "xsd:anyURI";
            case "xsd:float" -> "xsd:float";
            case "xsd:boolean" -> "xsd:boolean";
            case "xsd:integer" -> "xsd:integer";
            case "xsd:gYear" -> "xsd:gYear";
            case "xsd:dateTime" -> "xsd:dateTime";
            default -> "xsd:string";
        };
    }
}
