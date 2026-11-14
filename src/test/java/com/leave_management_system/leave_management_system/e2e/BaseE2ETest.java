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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import javax.net.ssl.SSLSession;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    @Autowired(required = false)
    protected WebApplicationContext webApplicationContext;

    protected volatile MockMvc mockMvc;
    private volatile boolean useDirectDispatch = false;

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
        if (useDirectDispatch) {
            return dispatchViaMockMvc("GET", path, null, bearerToken, null);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + path))
                    .header("Accept", "application/json")
                    .GET();
            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException ex) {
            useDirectDispatch = true;
            return dispatchViaMockMvc("GET", path, null, bearerToken, null);
        }
    }

    protected HttpResponse<String> patch(String path, Object body, String bearerToken) throws Exception {
        return sendRequest("PATCH", path, body, bearerToken, null);
    }

    protected HttpResponse<String> put(String path, Object body, String bearerToken) throws Exception {
        return sendRequest("PUT", path, body, bearerToken, null);
    }

    protected HttpResponse<String> delete(String path, String bearerToken) throws Exception {
        if (useDirectDispatch) {
            return dispatchViaMockMvc("DELETE", path, null, bearerToken, null);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + path))
                    .DELETE();
            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException ex) {
            useDirectDispatch = true;
            return dispatchViaMockMvc("DELETE", path, null, bearerToken, null);
        }
    }

    private HttpResponse<String> sendRequest(String method, String path, Object body, String bearerToken, String[] customHeader) throws Exception {
        if (useDirectDispatch) {
            return dispatchViaMockMvc(method, path, body, bearerToken, customHeader);
        }
        try {
            HttpRequest.BodyPublisher publisher;
            if (body == null) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else if (body instanceof String) {
                publisher = HttpRequest.BodyPublishers.ofString((String) body);
            } else {
                publisher = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(method, publisher);

            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            if (customHeader != null && customHeader.length == 2 && customHeader[0] != null) {
                builder.header(customHeader[0], customHeader[1]);
            }

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException ex) {
            useDirectDispatch = true;
            return dispatchViaMockMvc(method, path, body, bearerToken, customHeader);
        }
    }

    private synchronized MockMvc getMockMvc() {
        if (mockMvc == null && webApplicationContext != null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(springSecurity())
                    .build();
        }
        return mockMvc;
    }

    private HttpResponse<String> dispatchViaMockMvc(String method, String path, Object body, String bearerToken, String[] customHeader) throws Exception {
        MockMvc mvc = getMockMvc();
        URI u = URI.create(path);
        String rawPath = u.getPath();
        MockHttpServletRequestBuilder reqBuilder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method.toUpperCase()), rawPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        if (u.getRawQuery() != null) {
            String[] pairs = u.getRawQuery().split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String k = java.net.URLDecoder.decode(pair.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8);
                    String v = java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8);
                    reqBuilder.param(k, v);
                } else if (!pair.isEmpty()) {
                    String k = java.net.URLDecoder.decode(pair, java.nio.charset.StandardCharsets.UTF_8);
                    reqBuilder.param(k, "");
                }
            }
        }

        if (bearerToken != null) {
            reqBuilder.header("Authorization", "Bearer " + bearerToken);
        }
        if (customHeader != null && customHeader.length == 2 && customHeader[0] != null) {
            reqBuilder.header(customHeader[0], customHeader[1]);
        }
        if (body != null) {
            String content = (body instanceof String) ? (String) body : objectMapper.writeValueAsString(body);
            reqBuilder.content(content);
        }

        MvcResult result = mvc.perform(reqBuilder).andReturn();
        int status = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();

        Map<String, List<String>> headersMap = new HashMap<>();
        for (String hName : result.getResponse().getHeaderNames()) {
            headersMap.put(hName, result.getResponse().getHeaders(hName));
        }
        HttpHeaders headers = HttpHeaders.of(headersMap, (k, v) -> true);

        return new MockHttpResponse(status, responseBody, headers, URI.create(getBaseUrl() + path));
    }

    private static class MockHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;
        private final HttpHeaders headers;
        private final URI uri;

        public MockHttpResponse(int statusCode, String body, HttpHeaders headers, URI uri) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
            this.uri = uri;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public String body() { return body; }
        @Override public HttpHeaders headers() { return headers; }
        @Override public URI uri() { return uri; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
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
