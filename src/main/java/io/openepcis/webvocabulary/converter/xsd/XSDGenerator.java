package io.openepcis.webvocabulary.converter.xsd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.webvocabulary.converter.model.ClassDefinition;
import io.openepcis.webvocabulary.converter.model.LinkTypeDefinition;
import io.openepcis.webvocabulary.converter.model.PropertyDefinition;
import io.openepcis.webvocabulary.converter.model.RelationDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
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
import java.util.List;
import java.util.Map;

import static io.openepcis.webvocabulary.converter.constant.Constants.*;


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
        relationDefinition.getNamespaces().forEach((key, namespaceUri) -> {
            // Remove the '#' or '/' character at the end of all the namespaces
            if (namespaceUri.endsWith("/") || namespaceUri.endsWith("#")) {
                namespaceUri = namespaceUri.substring(0, namespaceUri.length() - 1);
            }
            schemaRoot.setAttribute("xmlns:" + key, namespaceUri);
        });
        doc.appendChild(schemaRoot);

        // insert the xml:namespace import for language
        final Element xmlImport = doc.createElement("xsd:import");
        xmlImport.setAttribute("namespace", XMLConstants.XML_NS_URI);
        xmlImport.setAttribute("schemaLocation", "https://www.w3.org/2001/xml.xsd");
        schemaRoot.appendChild(xmlImport);

        // Build the generic langType complex type
        addLangStringType(doc, schemaRoot);

        //Process all the class and properties and build XSD
        processClasses(doc, schemaRoot, relationDefinition);

        //Process all the TypeCodes and build the XSD
        processTypeCodes(doc, schemaRoot, relationDefinition);

        //Process all the linkTypes and build the XSD
        processLinkTypes(doc, schemaRoot, relationDefinition);

        //Write all the XSD information to file
        writeDocumentToFile(doc);
        System.out.println("********* XSD Generated onto JsonLdSchemaXSD.xsd *********");
    }

    // Method to generate the XSD for the classes
    private void processClasses(final Document doc, final Element schemaRoot, final RelationDefinition relationDefinition) {
        if (relationDefinition.getClasses() != null) {
            for (Map.Entry<String, ClassDefinition> entry : relationDefinition.getClasses().entrySet()) {
                final Element complexType = createComplexType(doc, entry.getKey(), entry.getValue());
                schemaRoot.appendChild(complexType);
            }
        }
    }


    // Method to generate the XSD for each of the Class - Properties
    private Element createComplexType(final Document doc, final String typeName, final ClassDefinition classDefinition) {
        final Element complexType = doc.createElement("xsd:complexType");
        complexType.setAttribute("name", typeName);

        //Add the description/documentation for the class in XSD for reference purpose.
        final Element annotation = doc.createElement("xsd:annotation");
        final Element documentation = doc.createElement("xsd:documentation");
        documentation.setAttribute("xml:lang", "en");
        documentation.setTextContent(classDefinition.getDescription());
        annotation.appendChild(documentation);
        complexType.appendChild(annotation);

        final Element sequence = doc.createElement("xsd:sequence");
        final List<PropertyDefinition> properties = classDefinition.getProperties();

        //Loop over each property and generate the XSD element
        for (PropertyDefinition property : properties) {
            final Element element = doc.createElement("xsd:element");
            element.setAttribute("name", property.getProperty());

            final String xsdType = getXsdType(property);

            if (LANG_STRING.equalsIgnoreCase(xsdType)) {
                // If the property is of type langString, then create a complex type with simple content
                element.setAttribute("type", "LangString");
                element.setAttribute("maxOccurs", "unbounded");
                element.setAttribute("minOccurs", "0");
            } else {
                element.setAttribute("type", xsdType);
            }

            // If deprecated property then add the additional annotation with documentation
            if (property.isDeprecated()) {
                final Element deprecatedAnnotation = doc.createElement("xsd:annotation");
                final Element deprecatedDocumentation = doc.createElement("xsd:documentation");
                deprecatedDocumentation.setTextContent(property.getDescription());
                deprecatedAnnotation.appendChild(deprecatedDocumentation);
                element.appendChild(deprecatedAnnotation);
            }

            sequence.appendChild(element);
        }

        //If the class is subclass and has the superclass then add the corresponding tags
        if (Boolean.TRUE.equals(classDefinition.getIsSubclass()) && classDefinition.getSuperClass() != null) {
            final Element complexContent = doc.createElement("xsd:complexContent");
            final Element extensionElement = doc.createElement("xsd:extension");
            extensionElement.setAttribute("base", classDefinition.getSuperClass());

            extensionElement.appendChild(sequence);
            complexContent.appendChild(extensionElement);
            complexType.appendChild(complexContent);
        } else {
            //If the class is not subclass then directly add
            complexType.appendChild(sequence);

            // Add the XSD attributes with name and type for the class which are not a super class
            final Element xsdAttribute = doc.createElement("xsd:attribute");
            xsdAttribute.setAttribute("name", "id");
            xsdAttribute.setAttribute("type", "xsd:anyURI");
            complexType.appendChild(xsdAttribute);
        }
        return complexType;
    }

    // Method to generate XSD for each of the Code/TypeCodes
    private void processTypeCodes(final Document doc, final Element schemaRoot, final RelationDefinition relationDefinition) {
        if (relationDefinition.getTypeCodes() != null) {
            for (Map.Entry<String, List<PropertyDefinition>> entry : relationDefinition.getTypeCodes().entrySet()) {
                final String className = entry.getKey();
                final List<PropertyDefinition> properties = entry.getValue();

                if (!relationDefinition.getClasses().containsKey(entry.getKey())) {
                    final Element simpleTypeElement = doc.createElement("xsd:simpleType");
                    simpleTypeElement.setAttribute("name", className);

                    final Element restrictionElement = doc.createElement("xsd:restriction");
                    restrictionElement.setAttribute("base", XSD_STRING);

                    for (PropertyDefinition property : properties) {
                        final Element enumElement = doc.createElement("xsd:enumeration");
                        enumElement.setAttribute("value", property.getProperty());

                        // If deprecated property then add the additional annotation with documentation
                        if (property.isDeprecated()) {
                            final Element deprecatedAnnotation = doc.createElement("xsd:annotation");
                            final Element deprecatedDocumentation = doc.createElement("xsd:documentation");
                            deprecatedDocumentation.setTextContent(property.getDescription());
                            deprecatedAnnotation.appendChild(deprecatedDocumentation);
                            enumElement.appendChild(deprecatedAnnotation);
                        }

                        restrictionElement.appendChild(enumElement);
                    }
                    simpleTypeElement.appendChild(restrictionElement);
                    schemaRoot.appendChild(simpleTypeElement);
                }
            }
        }
    }

    private void processLinkTypes(final Document doc, final Element schemaRoot, final RelationDefinition relationDefinition) {
        final Element linkTypeElement = doc.createElement("xsd:simpleType");
        linkTypeElement.setAttribute("name", "LinkTypeEnum");

        final Element linkTypeRestrictionElement = doc.createElement("xsd:restriction");
        linkTypeRestrictionElement.setAttribute("base", XSD_STRING);

        if (relationDefinition.getLinkTypes() != null) {
            for (final LinkTypeDefinition linkType : relationDefinition.getLinkTypes()) {
                final Element enumElement = doc.createElement("xsd:enumeration");
                enumElement.setAttribute("value", linkType.getLinkTypeId());

                // If deprecated property then add the additional annotation with documentation
                if (linkType.isDeprecated()) {
                    final Element deprecatedAnnotation = doc.createElement("xsd:annotation");
                    final Element deprecatedDocumentation = doc.createElement("xsd:documentation");
                    deprecatedDocumentation.setTextContent(linkType.getDescription());
                    deprecatedAnnotation.appendChild(deprecatedDocumentation);
                    enumElement.appendChild(deprecatedAnnotation);
                }
                linkTypeRestrictionElement.appendChild(enumElement);
            }
        }
        linkTypeElement.appendChild(linkTypeRestrictionElement);
        schemaRoot.appendChild(linkTypeElement);
    }

    //Get the respective XSD type based on the definition. Either simple/complex.
    private String getXsdType(final PropertyDefinition property) {
        if ("simple".equals(property.getDataType())) {
            return mapSimpleType((String) property.getRangeType());
        } else if ("complex".equals(property.getDataType())) {
            if (property.getRangeType() instanceof String rangeType) {
                // For Thing type return the "xsd:anyType" else return rangeType
                return rangeType.equalsIgnoreCase(THING) ? "xsd:anyType" : rangeType;
            } else if (property.getRangeType() instanceof List) {
                //If range is of type List then get the domain
                return property.getDomain();
            }
        }
        return null;
    }

    //Map the datatype to corresponding XSD datatype.
    private String mapSimpleType(final String rangeType) {
        if (rangeType == null) return null;

        return switch (rangeType) {
            case "xsd:date", "date" -> "xsd:date";
            case "xsd:anyURI", "anyURI" -> "xsd:anyURI";
            case "xsd:float", "float" -> "xsd:float";
            case "xsd:boolean", "boolean" -> "xsd:boolean";
            case "xsd:integer", "integer" -> "xsd:integer";
            case "xsd:gYear", "gYear" -> "xsd:gYear";
            case "xsd:dateTime", "dateTime" -> "xsd:dateTime";
            case LANG_STRING -> LANG_STRING;
            default -> XSD_STRING;
        };
    }

    // Method to write all the information to file
    private void writeDocumentToFile(final Document doc) throws TransformerException {
        // Write the XML document to file
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();

        // Disable external entity processing for security
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        final Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        final DOMSource source = new DOMSource(doc);
        final StreamResult result = new StreamResult(new File("src/main/resources/schema/JsonLdSchemaXSD.xsd"));
        transformer.transform(source, result);
    }

    private void addLangStringType(Document doc, Element schemaRoot) {
        final Element lsComplexType = doc.createElement("xsd:complexType");
        lsComplexType.setAttribute("name", "LangString");

        final Element lsSimpleContent = doc.createElement("xsd:simpleContent");
        final Element lsExtension = doc.createElement("xsd:extension");
        lsExtension.setAttribute("base", "xsd:string");

        final Element lsAttribute = doc.createElement("xsd:attribute");
        lsAttribute.setAttribute("ref", "xml:lang");
        lsAttribute.setAttribute("use", "required");

        lsExtension.appendChild(lsAttribute);
        lsSimpleContent.appendChild(lsExtension);
        lsComplexType.appendChild(lsSimpleContent);

        schemaRoot.appendChild(lsComplexType);
    }
}
