package com.redhat.cloud.event.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.cloud.event.apps.advisor.v1.AdvisorRecommendations;
import com.redhat.cloud.event.core.v1.Notification;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventValidationException;
import com.redhat.cloud.event.parser.modules.LocalDateTimeModule;
import com.redhat.cloud.event.parser.modules.OffsetDateTimeModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsoleCloudEventParserTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvisorCloudEvent extends GenericConsoleCloudEvent<AdvisorRecommendations> {
    }

    static class SortingNodeFactory extends JsonNodeFactory {
        @Override
        public ObjectNode objectNode() {
            return new ObjectNode(this, new TreeMap<>());
        }
    }


    @Test
    public void shouldParseCorrectCloudEvents() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        ConsoleCloudEvent consoleCloudEvent = consoleCloudEventParser.fromJsonString(readSchema("cloud-events/advisor.json"));

        assertEquals("com.redhat.console.advisor.new-recommendations", consoleCloudEvent.getType());
        assertEquals("org123", consoleCloudEvent.getOrgId());
        assertNull(consoleCloudEvent.getAccountId());
    }

    @Test
    public void shouldValidateCorrectCloudEvents() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        assertDoesNotThrow(() -> consoleCloudEventParser.validate(readSchema("cloud-events/advisor.json")));
        assertDoesNotThrow(() -> consoleCloudEventParser.validate(readSchema("cloud-events/policies.json")));
    }

    @Test
    public void shouldFailWithWrongDataschema() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        assertThrows(
                ConsoleCloudEventValidationException.class,
                () -> consoleCloudEventParser.validate(readSchema("cloud-events/advisor-with-wrong-dataschema.json"))
        );
    }

    @Test
    public void shouldParseWithCustomClass() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        AdvisorCloudEvent advisorEvent = consoleCloudEventParser.fromJsonString(readSchema("cloud-events/advisor.json"), AdvisorCloudEvent.class);

        assertEquals("com.redhat.console.advisor.new-recommendations", advisorEvent.getType());
        assertEquals("org123", advisorEvent.getOrgId());
        assertNull(advisorEvent.getAccountId());

        assertEquals("rhel8desktop", advisorEvent.getData().getSystem().getDisplayName());
        assertEquals("insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE", advisorEvent.getData().getAdvisorRecommendations()[0].getRuleID());
    }

    @Test
    public void shouldParseAndSerialize() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        String original = readSchema("cloud-events/advisor.json");

        ConsoleCloudEvent consoleCloudEvent = consoleCloudEventParser.fromJsonString(original);
        String other = consoleCloudEventParser.toJson(consoleCloudEvent);

        assertNotNull(other);

        ObjectMapper mapper = new ObjectMapper()
                .setNodeFactory(new SortingNodeFactory())
                .registerModule(new LocalDateTimeModule());

        assertEquals(mapper.readTree(original), mapper.readTree(other));

        assertTrue(other.contains("https://console.redhat.com/api/schemas/events/v1/events.json"));
    }

    @Test
    public void shouldParseAndSerializeWithCustomClass() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        String original = readSchema("cloud-events/advisor.json");

        AdvisorCloudEvent consoleCloudEvent = consoleCloudEventParser.fromJsonString(original, AdvisorCloudEvent.class);
        String other = consoleCloudEventParser.toJson(consoleCloudEvent);

        assertNotNull(other);

        ObjectMapper mapper = new ObjectMapper()
                .setNodeFactory(new SortingNodeFactory())
                .registerModule(new OffsetDateTimeModule())
                .registerModule(new LocalDateTimeModule());

        // It correctly parses dates with offsets as 2021-03-13T18:44:00+00:00 but writes them as "2021-03-13T18:44:00Z"
        original = original.replace("+00:00", "Z");

        assertEquals(mapper.readTree(original), mapper.readTree(other));
        assertTrue(other.contains("https://console.redhat.com/api/schemas/events/v1/events.json"));
    }

    @Test
    public void getWithClassFailsIfNotCompatible() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        String original = readSchema("cloud-events/advisor.json");
        ConsoleCloudEvent consoleCloudEvent = consoleCloudEventParser.fromJsonString(original);

        assertFalse(consoleCloudEvent.getData(Notification.class).isPresent());
    }

    @Test
    public void getWithClassWorksIfCompatibleData() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        String original = readSchema("cloud-events/notification-recipients.json");
        ConsoleCloudEvent consoleCloudEvent = consoleCloudEventParser.fromJsonString(original);
        Notification notification = consoleCloudEvent.getData(Notification.class).orElseThrow();

        assertNotNull(notification);
        assertNotNull(notification.getNotificationRecipients());

        assertTrue(notification.getNotificationRecipients().getOnlyAdmins());
        assertFalse(notification.getNotificationRecipients().getIgnoreUserPreferences());

        assertEquals(3, notification.getNotificationRecipients().getUsers().length);
        assertArrayEquals(new String[]{ "foo", "bar", "x" }, notification.getNotificationRecipients().getUsers());
    }

    @Test
    public void shouldFailOnNonCompliantCloudEvents() {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
        assertThrows(RuntimeException.class, () -> consoleCloudEventParser.fromJsonString(readSchema("cloud-events/advisor-invalid.json")));
    }

    @Test
    public void shouldFailOnInvalidJson() throws IOException {
        ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

        assertThrows(RuntimeException.class, () -> consoleCloudEventParser.fromJsonString("hello world"));
    }

    private String readSchema(String path) throws IOException {
        InputStream content = ConsoleCloudEventParserTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(content, "could not load example cloud event for advisor");
        return new String(content.readAllBytes(), UTF_8);
    }
}
