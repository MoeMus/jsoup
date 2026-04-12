package org.jsoup.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.NodeVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Html5LibConformanceTest {

    // A simple container for a single html5lib test case
    static class TestCase {
        String inputData = "";
        String expectedDocument = "";
        
        @Override
        public String toString() {
            return inputData.replace("\n", " ").trim();
        }
    }

    public static List<TestCase> loadTestCases() throws Exception {
        InputStream in = Html5LibConformanceTest.class.getResourceAsStream("/html5lib/tests1.dat");
        assertNotNull(in, "Could not find tests1.dat in resources!");

        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<TestCase> testCases = new ArrayList<>();
        
        String line;
        TestCase currentTest = null;
        String currentState = "";

        while ((line = reader.readLine()) != null) {
            if (line.equals("")) continue;

            if (line.startsWith("#data")) {
                currentTest = new TestCase();
                testCases.add(currentTest);
                currentState = "data";
                continue;
            } else if (line.startsWith("#errors")) {
                currentState = "errors";
                continue;
            } else if (line.startsWith("#document")) {
                currentState = "document";
                continue;
            }

            if (currentTest != null) {
                if (currentState.equals("data")) {
                    currentTest.inputData += line + "\n";
                } else if (currentState.equals("document")) {
                    currentTest.expectedDocument += line + "\n";
                }
            }
        }
        
        // Remove trailing newlines from inputData because readLine() adds them
        for (TestCase tc : testCases) {
            if (tc.inputData.endsWith("\n")) {
                tc.inputData = tc.inputData.substring(0, tc.inputData.length() - 1);
            }
        }
        return testCases;
    }

    // The Tree Serializer
    static class Html5LibVisitor implements NodeVisitor {
        StringBuilder sb = new StringBuilder();

        @Override
        public void head(Node node, int depth) {
            if (node instanceof Document) return; // Skip root document node

            // Document children start at depth 1 in JSoup, but depth 0 in html5lib output
            int indentLevel = depth - 1;
            String indent = "";
            for (int i = 0; i < indentLevel * 2; i++) {
                indent += " ";
            }

            if (node instanceof Element) {
                Element el = (Element) node;
                sb.append("| ").append(indent).append("<").append(el.tagName()).append(">\n");
                
                // Handle attributes (html5lib expects them sorted alphabetically)
                List<Attribute> attrs = new ArrayList<>();
                el.attributes().forEach(attrs::add);
                attrs.sort(Comparator.comparing(Attribute::getKey));
                for (Attribute attr : attrs) {
                    sb.append("| ").append(indent).append("  ").append(attr.getKey()).append("=\"").append(attr.getValue()).append("\"\n");
                }
            } else if (node instanceof TextNode) {
                TextNode tn = (TextNode) node;
                sb.append("| ").append(indent).append("\"").append(tn.getWholeText()).append("\"\n");
            } else if (node instanceof Comment) {
                Comment c = (Comment) node;
                sb.append("| ").append(indent).append("<!-- ").append(c.getData()).append(" -->\n");
            } else if (node instanceof DocumentType) {
                DocumentType doctype = (DocumentType) node;
                sb.append("| ").append(indent).append("<!DOCTYPE ").append(doctype.name());
                if (!doctype.publicId().isEmpty() || !doctype.systemId().isEmpty()) {
                    sb.append(" \"").append(doctype.publicId()).append("\" \"").append(doctype.systemId()).append("\"");
                }
                sb.append(">\n");
            }
        }

        @Override
        public void tail(Node node, int depth) {}

        public String getSerializedTree() {
            return sb.toString();
        }
    }

    @ParameterizedTest
    @MethodSource("loadTestCases")
    public void runConformanceTest(TestCase tc) {
        Document doc = Jsoup.parse(tc.inputData);
        
        Html5LibVisitor visitor = new Html5LibVisitor();
        doc.traverse(visitor);
        
        String actualTree = visitor.getSerializedTree();
        
        // Assert that JSoup's generated AST perfectly matches the canonical output
        assertEquals(tc.expectedDocument, actualTree);
    }
}