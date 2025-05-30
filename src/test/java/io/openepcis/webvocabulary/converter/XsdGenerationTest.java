package io.openepcis.webvocabulary.converter;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdGenerationTest {

    File expectedRelationsFile = new File("src/main/resources/schema/JsonLdSchemaRelations.json"); // Define the expected file path
    File expectedXSDFile = new File("src/main/resources/schema/JsonLdSchemaXSD.xsd"); // Define the expected file path

    @Test
    void generateXsdFromInputStream() throws ParserConfigurationException, IOException, TransformerException {
        final InputStream inputStream = getClass().getResourceAsStream("/gs1Voc_v1_11.jsonld");
        WebVocabularyParser webVocabularyParser = new WebVocabularyParser();
        webVocabularyParser.parseJsonLdData(inputStream);

        assertTrue(expectedRelationsFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
        assertTrue(expectedXSDFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
    }

    @Test
    void generateXsdFromUrl() throws ParserConfigurationException, IOException, TransformerException {
        final String jsonLdURL = "https://raw.githubusercontent.com/gs1/WebVoc/refs/heads/master/v1.11/gs1Voc_v1_11.jsonld";
        WebVocabularyParser webVocabularyParser = new WebVocabularyParser();
        webVocabularyParser.parseJsonLdData(jsonLdURL);

        assertTrue(expectedRelationsFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
        assertTrue(expectedXSDFile.exists(), "Expected file JsonLdSchemaRelations.json to exist"); // Assert that the file exists
    }

    @Test
    void runMavenGenerateSources() throws IOException, InterruptedException {
        //Maven command to be executed
        final ProcessBuilder processBuilder = new ProcessBuilder("mvn", "generate-sources");
        processBuilder.directory(new File(System.getProperty("user.dir"))); // Set the working directory to the project root
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);  // Redirect the output and error streams to the console for debugging purposes
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        final Process process = processBuilder.start(); //Start the Maven process
        final boolean completed = process.waitFor(5, TimeUnit.MINUTES); // Wait for the process to complete, with a timeout of 5 minutes
        final int exitCode = process.exitValue();  // Check if the process completed successfully
        final File generatedSourcesDir = new File("target/generated-sources"); // To check if directory is created

        assertTrue(completed && exitCode == 0, "Maven generate-sources command failed!");
        assertTrue(generatedSourcesDir.exists(), "Generated sources directory does not exist!"); // Verify that the generated sources directory exists

    }
}
