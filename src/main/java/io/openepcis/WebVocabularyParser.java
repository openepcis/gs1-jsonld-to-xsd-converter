package io.openepcis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.xsd.XSDGenerator;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.openepcis.constant.Constants.*;

@SuppressWarnings("unchecked")
public class WebVocabularyParser {
    private static final Property SW_TERM_STATUS = ResourceFactory.createProperty("http://www.w3.org/2003/06/sw-vocab-status/ns#", "term_status");
    private Map<Resource, List<Resource>> allUnionClasses = null;

    /**
     * Method to get the JSON-LD data and parse it and generate the XSD
     *
     * @param jsonldStream JSON-LD file contents as InputStream
     * @throws IOException                  IOException associated to Jackson
     * @throws ParserConfigurationException exception during the parsing of the JSON-LD file contents
     * @throws TransformerException         exception during the building relations
     */
    public void parseJsonLdData(final InputStream jsonldStream) throws IOException, ParserConfigurationException, TransformerException {
        // Parse JSON-LD using Apache Jena
        final Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, jsonldStream, RDFLanguages.JSONLD);
        buildRelations(model);
    }


    /**
     * Method to get the JSON-LD data URL and parse it and generate the XSD
     *
     * @param jsonldUrl URL of the JSON-LD file
     * @throws IOException                  IOException associated to Jackson
     * @throws ParserConfigurationException exception during the parsing of the JSON-LD file contents
     * @throws TransformerException         exception during the building relations
     */
    public void parseJsonLdData(final String jsonldUrl) throws IOException, ParserConfigurationException, TransformerException {
        // Create a default model
        final Model model = ModelFactory.createDefaultModel();

        // Read JSON-LD content from the URL
        RDFDataMgr.read(model, jsonldUrl, Lang.JSONLD);

        buildRelations(model);
    }

    /**
     * Method to read the JSON-LD stream data and build relations and generate XSD based on the information in JSON-LD page.
     *
     * @param model Apache Jena Model with all RDF/JSON-LD schema contents
     * @throws IOException                  IOException associated to Jackson
     * @throws ParserConfigurationException exception during the parsing of the JSON-LD file contents
     * @throws TransformerException         exception during the building relations
     */

    private void buildRelations(final Model model) throws IOException, ParserConfigurationException, TransformerException {


        // Retrieve the PrefixMapping from the model
        final PrefixMapping prefixMapping = model.getGraph().getPrefixMapping();
        final Map<String, String> namespaces = prefixMapping.getNsPrefixMap();

        //Map to store all the relations : Class-Properties, TypeCodes, LinkTypes into a single Map
        final Map<String, Object> jsonLDSchema = new LinkedHashMap<>();

        // Get all the Union class with multiple class in JSON-LD schema and store
        allUnionClasses = getUnionClassMembers(model);

        // Get all the LinkTypes present in JSON-LD schema and store
        final List<Map<String, Object>> allLinkTypes = getAllLinkTypes(model);

        // Get all the TypeCodes present in JSON-LD schema and store
        final Map<String, Object> allTypeCodes = getAllTypeCodes(model);

        // Get all the Class-Properties present in JSON-LD schema and store
        final Map<String, Object> allClassProperties = getAllClassProperties(model);

        // Get all the Properties belonging to Union domains _:u1, _:u2, _:u3 etc and append them to existing class-properties
        getUnionDomainProperties(model, allClassProperties);

        // Assign the Type and DataType for each of the property
        typeAssigner(allClassProperties, allTypeCodes);

        // Populate all the Class-Properties, TypeCodes, LinkTypes into a single Map
        jsonLDSchema.put(CLASSES, allClassProperties);
        jsonLDSchema.put(TYPE_CODES, allTypeCodes);
        jsonLDSchema.put(LINK_TYPES, allLinkTypes);
        jsonLDSchema.put(NAMESPACES, namespaces);

        // Build a JSON String and convert to InputStream and finally generate the XSD based on JSON-LD Schema
        final ObjectMapper objectMapper = new ObjectMapper();
        final String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonLDSchema);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File("./JsonLdSchemaRelations.json"), jsonLDSchema);


        // Convert the build JSON-LD schema relations into XSD
        final InputStream jsonStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));
        final XSDGenerator xsdGenerator = new XSDGenerator();
        xsdGenerator.generateXSD(jsonStream);
    }

    /**
     * Method to get all the Union class members such as _:u1, _:u2, etc. and store their respective classes in @list
     *
     * @param model model of the JSON-LD schema
     * @return returns Map with union reference and their respective classes
     */
    private Map<Resource, List<Resource>> getUnionClassMembers(final Model model) {
        final Map<Resource, List<Resource>> unionMemberMap = new HashMap<>();
        final ResIterator unionClassIterator = model.listSubjectsWithProperty(OWL.unionOf);

        while (unionClassIterator.hasNext()) {
            final Resource unionClass = unionClassIterator.nextResource();
            final StmtIterator unionStatements = model.listStatements(unionClass, OWL.unionOf, (RDFNode) null);

            while (unionStatements.hasNext()) {
                final Statement unionStmt = unionStatements.nextStatement();
                final RDFNode unionNode = unionStmt.getObject();

                if (unionNode != null && unionNode.canAs(RDFList.class)) {
                    final RDFList unionList = unionNode.as(RDFList.class);
                    final List<Resource> memberURIs = new ArrayList<>(unionList.iterator().toList().stream()
                            .filter(RDFNode::isResource)
                            .map(RDFNode::asResource)
                            .toList());

                    // Store union class identifier and its member URIs
                    unionMemberMap.put(unionClass, memberURIs);
                }
            }
        }
        return unionMemberMap;
    }

    /**
     * Get all the properties that belong to linkType by filtering and append them to Map
     *
     * @param model model of the JSON-LD schema
     */
    private List<Map<String, Object>> getAllLinkTypes(final Model model) {
        final ResIterator linkTypeIterator = model.listSubjectsWithProperty(RDFS.subPropertyOf);
        final List<Map<String, Object>> linkTypes = new ArrayList<>();

        while (linkTypeIterator.hasNext()) {
            final Resource linkResource = linkTypeIterator.nextResource();
            final String linkName = (String) getPrefixedName(linkResource);
            final Statement subPropertyOfStmt = linkResource.getProperty(RDFS.subPropertyOf);
            final Resource parentResource = subPropertyOfStmt.getObject().asResource();

            //Ignore all non-matching linkType and deprecated LinkTypes from JSON-LD document
            if (Objects.requireNonNull((String) getPrefixedName(parentResource)).contains(LINK_TYPE) && !isDeprecated(parentResource)) {
                final Map<String, Object> linkSchema = new HashMap<>();

                linkSchema.put(LINK_TYPE_ID, linkName);
                linkSchema.put(RANGE_TYPE, getRangeDataType(model, linkResource));
                linkSchema.put(DOMAIN, getDomainName(linkResource));
                linkSchema.put(DATA_TYPE, SIMPLE);
                linkSchema.put(TYPE, SIMPLE);
                linkSchema.put(DESCRIPTION, getDescription(linkResource));
                linkTypes.add(linkSchema);
            }
        }
        // Sort linkTypes based on the "property" key
        sortStringProperties(linkTypes, LINK_TYPE_ID);
        return linkTypes;
    }

    /**
     * Method to get all the TypeCode present within JSON-LD schema by filtering and their children elements
     *
     * @param model model of the JSON-LD schema
     */
    private Map<String, Object> getAllTypeCodes(final Model model) {
        final ResIterator classIterator = model.listSubjectsWithProperty(RDFS.subClassOf);
        final Map<String, Object> allCodes = new TreeMap<>();

        //Get all the TypeCode from the JSON-LD schema
        while (classIterator.hasNext()) {
            final Resource typeCode = classIterator.nextResource();
            final Statement subClassOfStmt = typeCode.getProperty(RDFS.subClassOf);
            final Resource parentResource = subClassOfStmt.getObject().asResource();

            //Check if they belong to TypeCode if so get their children and build a Map
            if (Objects.requireNonNull((String) getPrefixedName(parentResource)).contains(TYPE_CODE)) {
                final String typeCodeName = (String) getPrefixedName(typeCode);
                final List<Map<String, Object>> codes = new ArrayList<>();

                if (typeCodeName != null) {
                    final ResIterator propertyIterator = model.listSubjectsWithProperty(RDF.type, typeCode);

                    while (propertyIterator.hasNext()) {
                        final Resource code = propertyIterator.nextResource();
                        final Map<String, Object> codeSchema = new LinkedHashMap<>();

                        final StmtIterator codeStmtIterator = code.listProperties(SKOS.prefLabel);
                        final String propertyName = codeStmtIterator.nextStatement().getString();

                        //Ignore the deprecated TypeCodes and add all the other elements
                        if (!isDeprecated(parentResource)) {
                            codeSchema.put(PROPERTY, propertyName);
                            codeSchema.put(RANGE_TYPE, typeCodeName);
                            codeSchema.put(DATA_TYPE, SIMPLE);
                            codeSchema.put(TYPE, CODE);
                            codeSchema.put(DESCRIPTION, getDescription(code));
                            codes.add(codeSchema);
                        }
                    }

                    // Sort the codes list based on the "property" value of each codeSchema
                    sortStringProperties(codes, PROPERTY);
                    allCodes.put(typeCodeName, codes);
                }
            }
        }
        return allCodes;
    }


    /**
     * Method to find all the class and their respective properties
     *
     * @param model model of the JSON-LD schema
     * @return returns a Map with all class and their properties as a List
     */
    private Map<String, Object> getAllClassProperties(final Model model) {
        final Map<String, Object> classProperties = new TreeMap<>();
        final ResIterator classIterator = model.listSubjectsWithProperty(RDF.type, OWL.Class);

        //Loop through all the classes from JSON-LD schema
        while (classIterator.hasNext()) {
            final Resource cls = classIterator.nextResource();

            // Skip this class because it belongs to a union class which will be handled later
            final StmtIterator unionStatement = cls.listProperties(model.getProperty(OWL.unionOf.getURI()));
            if (unionStatement.hasNext()) {
                continue;
            }

            final String className = (String) getPrefixedName(cls);
            if (className != null) {
                //Get all the properties associated with the class
                final List<Object> allProperties = new ArrayList<>();
                final ResIterator propertyIterator = model.listSubjectsWithProperty(RDFS.domain, cls);

                //Loop through all the properties associated to class and store info
                while (propertyIterator.hasNext()) {
                    final Resource property = propertyIterator.nextResource();
                    final Statement rangeStmt = property.getProperty(RDFS.range);
                    final String propertyName = (String) getPrefixedName(property);
                    final Object rangeType = getPrefixedName(rangeStmt.getObject().asResource());

                    final Map<String, Object> propertySchema = new LinkedHashMap<>();

                    //Ignore the deprecated properties and add all the other elements
                    if (!isDeprecated(property)) {
                        propertySchema.put(PROPERTY, propertyName);
                        propertySchema.put(RANGE_TYPE, rangeType);
                        propertySchema.put(DOMAIN, getDomainName(property));
                        propertySchema.put(DESCRIPTION, getDescription(property));
                        allProperties.add(propertySchema);
                    }
                }
                // Sort the codes list based on the "property" value of each codeSchema
                sortMapProperties(allProperties);

                // Get the superclass (rdfs:subClassOf) of the current class, if it exists
                final Statement subClassOfStmt = cls.getProperty(RDFS.subClassOf);
                final Map<String, Object> classSchema = new LinkedHashMap<>();
                String superClassName = null; //To add the superclasses one or more
                boolean isSubClass = false;

                if(subClassOfStmt != null){
                    final Resource superClass = subClassOfStmt.getObject().asResource();

                    if (superClass != null && !(superClass.getURI().equals("http://www.w3.org/2002/07/owl#Thing")) && !(superClass.getURI().equals("http://schema.org/MediaObject"))) {
                        superClassName = (String) getPrefixedName(superClass);
                        isSubClass = true;
                    }
                }

                //Based on subClass is present or not add the superClass
                if (isSubClass) {
                    classSchema.put(IS_SUBCLASS, true);  // Indicate that this is a subclass
                    classSchema.put(SUPERCLASS, superClassName);
                    classSchema.put(DESCRIPTION, getDescription(cls));
                } else {
                    // No superclass, handle it accordingly
                    classSchema.put(IS_SUBCLASS, false);
                    classSchema.put(SUPERCLASS, null);
                    classSchema.put(DESCRIPTION, getDescription(cls));
                }

                // Add the properties to the class schema
                classSchema.put(PROPERTIES, allProperties);

                // Add this class schema to the main classProperties map
                classProperties.put(className, classSchema);
            }
        }

        return classProperties;
    }

    /**
     * Method to find all the properties belonging to Union class such as _:u1, _:u2, etc. and accordingly assign them to respective classes
     *
     * @param model           model of the JSON-LD schema
     * @param classProperties existing already build class-properties which have direct domains
     */
    private void getUnionDomainProperties(final Model model, final Map<String, Object> classProperties) {
        // Get all the union classes
        final List<Resource> resourceList = allUnionClasses.keySet().stream().toList();

        // Find the matching property belonging to respective union class
        for (final Resource rs : resourceList) {
            final ResIterator propertyIterator = model.listSubjectsWithProperty(RDFS.domain, rs);
            final List<String> classes = (List<String>) getPrefixedName(rs);
            final List<Object> allProperties = new ArrayList<>();

            while (propertyIterator.hasNext()) {
                final Resource property = propertyIterator.nextResource();
                final Statement rangeStmt = property.getProperty(RDFS.range);
                final String propertyName = (String) getPrefixedName(property);
                final Object rangeType = getPrefixedName(rangeStmt.getObject().asResource());
                final Map<String, Object> propertySchema = new LinkedHashMap<>();

                //Ignore the deprecated properties and add all the other elements
                if (!isDeprecated(property)) {
                    propertySchema.put(PROPERTY, propertyName);
                    propertySchema.put(RANGE_TYPE, rangeType);
                    propertySchema.put(DOMAIN, getDomainName(property));
                    propertySchema.put(DESCRIPTION, getDescription(property));
                    allProperties.add(propertySchema);
                }
            }

            // Append the property to existing properties in the class-property relation
            for (String rrs : classes) {
                final Map<String, Object> existingClassInfo = (LinkedHashMap<String, Object>) classProperties.get(rrs);
                final List<Object> existingProperties = (List<Object>) existingClassInfo.get(PROPERTIES);
                existingProperties.addAll(allProperties);

                // Sort the codes list based on the "property" value of each codeSchema
                sortMapProperties(existingProperties);
            }
        }
    }

    /**
     * Function to assign the dataType and type for each property in class-properties
     *
     * @param allClassProperties All the class-properties that has been built before
     * @param allTypeCodes       All the type codes that has been build to check if the property belongs to TypeCodes
     */
    private void typeAssigner(final Map<String, Object> allClassProperties, final Map<String, Object> allTypeCodes) {
        for (Map.Entry<String, Object> entry : allClassProperties.entrySet()) {
            final Map<String, Object> existingClassInfo = (LinkedHashMap<String, Object>) entry.getValue();
            final List<Object> classProperties = (List<Object>) existingClassInfo.get(PROPERTIES);

            for (Object objectProperty : classProperties) {
                final Map<String, Object> property = (LinkedHashMap<String, Object>) objectProperty;
                final Object propertyNameObj = property.get(RANGE_TYPE);
                final String propertyName = propertyNameObj instanceof String ? propertyNameObj.toString() : null;

                // Check if the property belongs to CLASS type in allClassProperties
                if (propertyNameObj != null && (propertyNameObj instanceof List || allClassProperties.containsKey(propertyName))) {
                    property.put(DATA_TYPE, COMPLEX);
                    property.put(TYPE, CLASS);
                } else if (propertyName != null && (allTypeCodes.containsKey(propertyName) || propertyName.equalsIgnoreCase("Thing"))) {
                    // Check if the property belongs to CODE type in allTypeCodes
                    property.put(DATA_TYPE, COMPLEX);
                    property.put(TYPE, CODE);
                } else {
                    // Default case: assign simple dataType and type
                    property.put(DATA_TYPE, SIMPLE);
                    property.put(TYPE, SIMPLE);
                }
            }
        }
    }


    /**
     * Function to get the @Id value from the provided resource. If @Id of resource is null then check for unionClass
     *
     * @param resource resource whose @ID needs to be found out
     * @return return matching Resource as String or List<String> for union classes
     */
    private Object getPrefixedName(final Resource resource) {
        final String uri = resource.getURI();

        if (uri == null && allUnionClasses.containsKey(resource)) {
            return allUnionClasses.get(resource).stream()
                    .map(Resource::getLocalName).toList();
        }

        return resource.getLocalName() != null ? resource.getLocalName() : uri;
    }


    /**
     * Based on provided model and Resource find its range and return respective type
     *
     * @param model    model of the JSON-LD schema
     * @param property resource node whose range needs to be identified
     * @return returns the rangeDataType of the resource
     */
    private static String getRangeDataType(final Model model, final Resource property) {
        final Statement rangeStmt = property.getProperty(RDFS.range);
        String rangeType = null;

        if (rangeStmt != null) {
            final Resource range = rangeStmt.getResource();
            final String rangeURI = range.getURI();

            if (rangeURI != null) {
                // Extract the fragment from the URI
                final String fragment = rangeURI.substring(rangeURI.lastIndexOf('#') + 1);

                if ("string".equals(fragment) || "langString".equals(fragment) || "anyURI".equals(fragment) || "date".equals(fragment)
                        || "float".equals(fragment) || "boolean".equals(fragment) || "integer".equals(fragment)
                        || "gYear".equals(fragment) || "dateTime".equals(fragment)) {
                    rangeType = "xsd:" + fragment;
                } else {
                    // Check if it's a complex type using namespace prefix
                    final String prefix = model.getNsURIPrefix(property.getNameSpace());
                    if (prefix != null) {
                        rangeType = prefix + ":" + property.getLocalName();
                    }
                }
            }
        }

        return rangeType;
    }

    private String getDomainName(final Resource property) {
        final Statement domainStatement = property.getProperty(RDFS.domain);

        if (domainStatement != null) {
            final Resource domain = domainStatement.getResource();
            final String domainName = domain.getLocalName();
            if (domainName != null) {
                return domainName;
            }
        }
        return "";
    }

    /**
     * Function to get the comment or label value from the provided resource.
     *
     * @param resource Resource node whose comment/description needs to be read
     * @return return the matching comment or label obtained from resource
     */
    private String getDescription(final Resource resource) {
        //Get the comment if present in resource
        final Statement commentStmt = resource.getProperty(RDFS.comment);
        if (commentStmt != null && commentStmt.getObject().isLiteral()) {
            return commentStmt.getLiteral().getString();
        }

        //Get the label if present in resource
        final StmtIterator labelStatement = resource.listProperties(RDFS.label);
        if (labelStatement != null) {
            return labelStatement.nextStatement().getString();
        }

        return "";
    }

    //Sort the Class-Properties Map based on alphabetically
    private void sortMapProperties(final List<Object> allProperties) {
        allProperties.sort(Comparator.comparing(m -> ((String) ((Map<String, Object>) m).get(PROPERTY))));
    }

    //Sort the properties list alphabetically
    private void sortStringProperties(final List<Map<String, Object>> properties, final String sortType) {
        properties.sort(Comparator.comparing(m -> ((String) (m).get(sortType))));
    }

    //Method to check for the field sw:term_status and decide if deprecated or not
    private boolean isDeprecated(final Resource resourceProperty) {
        final String termStatus = Optional.ofNullable(resourceProperty.getProperty(SW_TERM_STATUS))
                .map(Statement::getString)
                .orElse("");

        return DEPRECATED.equalsIgnoreCase(termStatus);
    }
}
