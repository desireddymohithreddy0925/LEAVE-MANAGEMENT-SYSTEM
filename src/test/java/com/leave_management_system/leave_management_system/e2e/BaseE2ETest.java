package com.leave_management_system.leave_management_system.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseE2ETest {

    private static final Logger log = LoggerFactory.getLogger(BaseE2ETest.class);

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected EmployeeRepository employeeRepository;

    @Autowired
    protected DepartmentRepository departmentRepository;

    @Autowired
    protected LeaveTypeRepository leaveTypeRepository;

    @Autowired
    protected LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    protected LeaveRequestRepository leaveRequestRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected HttpClient httpClient = HttpClient.newHttpClient();
    protected ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Immutable record capturing clear evidence of genuine HTTP exchanges over the wire.
     */
    public record HttpExchangeEvidence(
            String method,
            URI uri,
            int port,
            int statusCode,
            HttpClient.Version protocolVersion,
            long durationMs,
            Map<String, List<String>> responseHeaders
    ) {
        @Override
        public String toString() {
            return String.format("[E2E-HTTP-EVIDENCE] %s %s -> HTTP %d (protocol: %s, duration: %dms, server-port: %d)",
                    method, uri, statusCode, protocolVersion, durationMs, port);
        }
    }

    protected static final List<HttpExchangeEvidence> recordedHttpEvidence = new CopyOnWriteArrayList<>();

    public static List<HttpExchangeEvidence> getRecordedHttpEvidence() {
        return Collections.unmodifiableList(recordedHttpEvidence);
    }

    public static void clearRecordedHttpEvidence() {
        recordedHttpEvidence.clear();
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpResponse<String> post(String path, Object body, String bearerToken) throws Exception {
        return sendRequest("POST", path, body, bearerToken, null);
    }

    protected HttpResponse<String> postWithHeaders(String path, Object body, String bearerToken, String headerName, String headerValue) throws Exception {
        return sendRequest("POST", path, body, bearerToken, new String[]{headerName, headerValue});
    }

    protected HttpResponse<String> get(String path, String bearerToken) throws Exception {
        return sendRequest("GET", path, null, bearerToken, null);
    }

    protected HttpResponse<String> patch(String path, Object body, String bearerToken) throws Exception {
        return sendRequest("PATCH", path, body, bearerToken, null);
    }

    protected HttpResponse<String> put(String path, Object body, String bearerToken) throws Exception {
        return sendRequest("PUT", path, body, bearerToken, null);
    }

    protected HttpResponse<String> delete(String path, String bearerToken) throws Exception {
        return sendRequest("DELETE", path, null, bearerToken, null);
    }

    /**
     * Executes real HTTP request over the network using HttpClient.
     * Fails immediately if the actual HTTP server is unavailable (no fallback to MockMvc).
     */
    protected HttpResponse<String> sendRequest(String method, String path, Object body, String bearerToken, String[] customHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Accept", "application/json");

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (customHeader != null && customHeader.length == 2 && customHeader[0] != null) {
            builder.header(customHeader[0], customHeader[1]);
        }

        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("DELETE".equalsIgnoreCase(method)) {
            if (body != null) {
                builder.header("Content-Type", "application/json");
                HttpRequest.BodyPublisher publisher = (body instanceof String)
                        ? HttpRequest.BodyPublishers.ofString((String) body)
                        : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
                builder.method("DELETE", publisher);
            } else {
                builder.DELETE();
            }
        } else {
            builder.header("Content-Type", "application/json");
            HttpRequest.BodyPublisher publisher;
            if (body == null) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else if (body instanceof String) {
                publisher = HttpRequest.BodyPublishers.ofString((String) body);
            } else {
                publisher = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
            }
            builder.method(method.toUpperCase(), publisher);
        }

        long start = System.currentTimeMillis();
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException ex) {
            throw new AssertionError("E2E HTTP request failed: real HTTP server at " + getBaseUrl() + " is unavailable or unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("E2E HTTP request interrupted while communicating with " + getBaseUrl(), ex);
        }
        long duration = System.currentTimeMillis() - start;

        // Validate that request genuinely reached the running application over HTTP
        assertRealHttpExchangeEvidence(response);

        HttpExchangeEvidence evidence = new HttpExchangeEvidence(
                method.toUpperCase(),
                response.uri(),
                response.uri().getPort(),
                response.statusCode(),
                response.version(),
                duration,
                response.headers().map()
        );
        recordedHttpEvidence.add(evidence);
        log.info("[E2E-HTTP-EVIDENCE] {} {} -> Status {} (protocol: {}, latency: {}ms, server-port: {})",
                evidence.method(), evidence.uri(), evidence.statusCode(), evidence.protocolVersion(),
                evidence.durationMs(), evidence.port());

        return response;
    }

    /**
     * Validates that the response represents a genuine network exchange over HTTP
     * with the embedded servlet container running on the assigned random port.
     */
    protected void assertRealHttpExchangeEvidence(HttpResponse<?> response) {
        assertNotNull(response, "HTTP response must not be null");
        assertNotNull(response.uri(), "HTTP response must contain target URI");
        assertEquals(port, response.uri().getPort(), "E2E request must reach running application on HTTP port " + port);
        assertNotNull(response.version(), "HTTP protocol version must be established over wire");
        assertFalse(response.headers().map().isEmpty(), "HTTP response must contain real wire headers from servlet container");
    }

    protected AuthResponseDTO registerUserOverHttp(String email, String password, String firstName, String lastName) throws Exception {
        RegisterRequestDTO dto = new RegisterRequestDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setPhone("1234567890");
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000));

        HttpResponse<String> response = post("/api/auth/register", dto, null);
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IllegalStateException("Failed to register user " + email + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), AuthResponseDTO.class);
    }

    protected AuthResponseDTO loginUserOverHttp(String email, String password) throws Exception {
        AuthRequestDTO dto = new AuthRequestDTO();
        dto.setEmail(email);
        dto.setPassword(password);

        HttpResponse<String> response = post("/api/auth/login", dto, null);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to login user " + email + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), AuthResponseDTO.class);
    }

    protected void elevateUserRole(String email, String roleName) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Role role = roleRepository.findByName(roleName).orElseGet(() ->
                roleRepository.save(new Role(roleName)));
        user.getRoles().clear();
        user.getRoles().add(role);
        userRepository.save(user);
    }

    protected Permission getOrCreatePermission(String name, String desc) {
        return permissionRepository.findByName(name).orElseGet(() ->
                permissionRepository.save(new Permission(name, desc)));
    }

    protected LocalDate getNextMonday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    protected LocalDate getNextSaturday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() != DayOfWeek.SATURDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDownE2E() {
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        departmentRepository.findAll().forEach(dept -> {
            if (dept.getManager() != null) {
                dept.setManager(null);
                departmentRepository.save(dept);
            }
        });
        employeeRepository.findAll().forEach(emp -> {
            if (emp.getManager() != null || emp.getDepartment() != null) {
                emp.setManager(null);
                emp.setDepartment(null);
                employeeRepository.save(emp);
            }
        });
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        leaveTypeRepository.deleteAll();
    }
}
