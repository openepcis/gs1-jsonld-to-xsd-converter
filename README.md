# jsonld-xsd-converter

This project converts the provided JSON-LD schema/RDF contents into the respective XSD. This project can be provided with either the JSONLD contents as InputStream or the direct URL where the contents
are present.

Providing the JSON-LD content as InputStream:

```java
final InputStream inputStream=getClass().getResourceAsStream("/fileName.jsonld");
final WebVocabularyParser webVocabularyParser=new WebVocabularyParser();
webVocabularyParser.parseJsonLdData(inputStream);
```

Providing the JSON-LD content from URL:

```java
final String jsonLdURL="URL of the JSON-LD contents";
final WebVocabularyParser webVocabularyParser=new WebVocabularyParser();
webVocabularyParser.parseJsonLdData(jsonLdURL);
```

Based on the provided contents initially a relationship will be established amount the various RDF tuples and then using these relationships XSD will be generated and stored onto the file.

### Generate Java sources from XSD

For generating the Java classes from XSD file run the following command:

```java
mvn generate-sources
```