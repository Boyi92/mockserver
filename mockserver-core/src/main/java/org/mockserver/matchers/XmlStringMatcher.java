package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class XmlStringMatcher extends BodyMatcher<NottableString> {
    private static final String[] excludedFields = {"mockServerLogger", "stringToXmlDocumentParser"};
    private final MockServerLogger mockServerLogger;
    private DiffBuilder diffBuilder;
    private NottableString matcher = string("THIS SHOULD NEVER MATCH");
    private StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();

    public XmlStringMatcher(MockServerLogger mockServerLogger, final String matcher) {
        this(mockServerLogger, string(matcher));
    }

    public XmlStringMatcher(MockServerLogger mockServerLogger, final NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        try {
            this.matcher = normaliseXmlNottableString(matcher);
            this.diffBuilder = DiffBuilder.compare(Input.fromString(this.matcher.getValue()))
                .ignoreComments()
                .ignoreWhitespace()
                .checkForSimilar();
        } catch (Exception e) {
            mockServerLogger.error("Error while creating xml string matcher for [" + matcher + "]" + e.getMessage(), e);
        }
    }

    public String normaliseXmlString(final String input) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        return stringToXmlDocumentParser.normaliseXmlString(input, new StringToXmlDocumentParser.ErrorLogger() {
            @Override
            public void logError(final String matched, final Exception exception) {
                mockServerLogger.error("SAXParseException while parsing [" + input + "]", exception);
            }
        });
    }

    public NottableString normaliseXmlNottableString(final NottableString input)
        throws IOException, SAXException, ParserConfigurationException, TransformerException {
        return string(normaliseXmlString(input.getValue()), input.isNot());
    }

    public boolean matches(String matched) {
        return matches(null, string(matched));
    }

    public boolean matches(final HttpRequest context, NottableString matched) {
        boolean result = false;

        if (diffBuilder != null) {
            try {
                Diff diff = diffBuilder.withTest(Input.fromString(normaliseXmlString(matched.getValue()))).build();
                result = !diff.hasDifferences();

                if (!result) {
                    mockServerLogger.trace("Failed to match [{}] with schema [{}] because [{}]", matched, this.matcher, diff.toString());
                }

            } catch (Exception e) {
                mockServerLogger.trace(context, "Failed to match [{}] with schema [{}] because [{}]", matched, this.matcher, e.getMessage());
            }
        }

        return matcher.isNot() != (not != result);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
