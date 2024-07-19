import io.openepcis.WebVocabularyParser;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;

public class XsdGenerationTest {

    @Test
    public void generateXsdFromInputStream() throws ParserConfigurationException, IOException, TransformerException {
        final InputStream inputStream = getClass().getResourceAsStream("/gs1Voc_v1_10.jsonld");
        System.out.println(inputStream);
        WebVocabularyParser webVocabularyParser = new WebVocabularyParser();
        webVocabularyParser.parseJsonLdData(inputStream);
    }

    @Test
    public void generateXsdFromUrl() throws ParserConfigurationException, IOException, TransformerException {
        final String jsonLdURL = "https://www.gs1.org/docs/gs1-smartsearch/gs1Voc_v1_10.jsonld";
        System.out.println(jsonLdURL);
        WebVocabularyParser webVocabularyParser = new WebVocabularyParser();
        webVocabularyParser.parseJsonLdData(jsonLdURL);
    }
}
