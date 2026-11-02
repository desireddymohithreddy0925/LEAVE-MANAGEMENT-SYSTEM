package com.leave_management_system.leave_management_system.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PostmanCollectionContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Verify Postman collection exists, has valid v2.1.0 schema, and all requests have test assertions")
    void testPostmanCollectionStructureAndCoverage() throws IOException {
        File collectionFile = new File("postman/Leave_Management_System.postman_collection.json");
        assertTrue(collectionFile.exists(), "Postman collection file should exist at postman/Leave_Management_System.postman_collection.json");

        File rootCollectionFile = new File("postman_collection.json");
        assertTrue(rootCollectionFile.exists(), "Root postman_collection.json should exist for root discovery");

        JsonNode root = objectMapper.readTree(collectionFile);
        assertNotNull(root.get("info"));
        assertEquals("https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                root.get("info").get("schema").asText(),
                "Schema must match Postman v2.1.0 schema");

        JsonNode items = root.get("item");
        assertNotNull(items);
        assertTrue(items.isArray());
        assertTrue(items.size() >= 5, "Collection should contain multiple organized feature folders");

        List<JsonNode> allRequests = new ArrayList<>();
        collectRequests(items, allRequests);

        assertTrue(allRequests.size() >= 30, "Collection should contain at least 30 comprehensive API requests, found: " + allRequests.size());

        for (JsonNode item : allRequests) {
            String name = item.get("name").asText();
            JsonNode request = item.get("request");
            assertNotNull(request, "Item '" + name + "' must have a request definition");
            assertNotNull(request.get("method"), "Item '" + name + "' must define HTTP method");
            assertNotNull(request.get("url"), "Item '" + name + "' must define URL");

            // Verify test assertions exist
            JsonNode events = item.get("event");
            boolean hasTestScript = false;
            if (events != null && events.isArray()) {
                for (JsonNode ev : events) {
                    if ("test".equals(ev.path("listen").asText())) {
                        JsonNode exec = ev.path("script").path("exec");
                        if (exec.isArray() && exec.size() > 0) {
                            String scriptBody = exec.toString();
                            if (scriptBody.contains("pm.test")) {
                                hasTestScript = true;
                                break;
                            }
                        }
                    }
                }
            }
            assertTrue(hasTestScript, "Request '" + name + "' must contain at least one pm.test assertion script");
        }
    }

    @Test
    @DisplayName("Verify Postman environment file contains all necessary configuration variables")
    void testPostmanEnvironmentFile() throws IOException {
        File envFile = new File("postman/Leave_Management_System.postman_environment.json");
        assertTrue(envFile.exists(), "Postman environment file must exist");

        JsonNode root = objectMapper.readTree(envFile);
        JsonNode values = root.get("values");
        assertNotNull(values);
        assertTrue(values.isArray());

        List<String> keys = new ArrayList<>();
        for (JsonNode val : values) {
            keys.add(val.get("key").asText());
        }

        assertTrue(keys.contains("baseUrl"), "Environment should define baseUrl");
        assertTrue(keys.contains("adminEmail"), "Environment should define adminEmail");
        assertTrue(keys.contains("adminPassword"), "Environment should define adminPassword");
        assertTrue(keys.contains("adminToken"), "Environment should define adminToken");
        assertTrue(keys.contains("employeeToken"), "Environment should define employeeToken");
        assertTrue(keys.contains("managerToken"), "Environment should define managerToken");
        assertTrue(keys.contains("leaveRequestId"), "Environment should define leaveRequestId");
    }

    private void collectRequests(JsonNode node, List<JsonNode> requests) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectRequests(item, requests);
            }
        } else if (node.isObject()) {
            if (node.has("request")) {
                requests.add(node);
            }
            if (node.has("item")) {
                collectRequests(node.get("item"), requests);
            }
        }
    }
}

