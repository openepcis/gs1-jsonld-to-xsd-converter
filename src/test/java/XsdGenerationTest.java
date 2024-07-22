import io.openepcis.WebVocabularyParser;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdGenerationTest {

    File expectedRelationsFile = new File("./JsonLdSchemaRelations.json"); // Define the expected file path
    File expectedXSDFile = new File("./JsonLdSchemaXSD.xsd"); // Define the expected file path

    @Test
    void generateXsdFromInputStream() throws ParserConfigurationException, IOException, TransformerException {
        final InputStream inputStream = getClass().getResourceAsStream("/gs1Voc_v1_10.jsonld");
        WebVocabularyParser webVocabularyParser = new WebVocabularyParser();
        webVocabularyParser.parseJsonLdData(inputStream);

        assertTrue(expectedRelationsFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
        assertTrue(expectedXSDFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
    }

    @Test
    void generateXsdFromUrl() throws ParserConfigurationException, IOException, TransformerException {
        final String jsonLdURL = "https://www.gs1.org/docs/gs1-smartsearch/gs1Voc_v1_10.jsonld";
        WebVocabularyParser webVocabularyParser = new WebVocabularyParser();
        webVocabularyParser.parseJsonLdData(jsonLdURL);

        assertTrue(expectedRelationsFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
        assertTrue(expectedXSDFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
    }
}
