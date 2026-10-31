package com.leave_management_system.leave_management_system.e2e;

import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.dto.TokenRefreshRequestDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationAndLockoutE2ETest extends BaseE2ETest {

    @BeforeEach
    public void setupPermissions() {
        Permission lockManage = getOrCreatePermission("USER_LOCK_MANAGE", "Lock and unlock user accounts");
        Permission secEvent = getOrCreatePermission("SECURITY_EVENT_MANAGE", "Manage security events");
        Permission empView = getOrCreatePermission("EMPLOYEE_VIEW", "View employee profiles");

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        adminRole.getPermissions().addAll(Set.of(lockManage, secEvent, empView));
        roleRepository.save(adminRole);
    }

    @Test
    void testAuthenticationLifecycleAndTokenRefreshOverHttp() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        String email = "auth.lifecycle." + ts + "@test.com";
        String password = "StrongPass123!";

        // 1. Register User over HTTP
        AuthResponseDTO regResp = registerUserOverHttp(email, password, "Auth", "Tester");
        assertNotNull(regResp.getAccessToken());

        // 2. Login User over HTTP
        AuthResponseDTO loginResp = loginUserOverHttp(email, password);
        String accessToken = loginResp.getAccessToken();
        String refreshToken = loginResp.getRefreshToken();
        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // 3. Access authenticated resource (GET /api/employees/me) over HTTP
        HttpResponse<String> meResp = get("/api/employees/me", accessToken);
        assertEquals(200, meResp.statusCode());
        assertEquals(email, JsonPath.parse(meResp.body()).read("$.email", String.class));

        // 4. Access without token should return 401 Unauthorized
        HttpResponse<String> unauthResp = get("/api/employees/me", null);
        assertEquals(401, unauthResp.statusCode());

        // 5. Token Refresh over HTTP
        TokenRefreshRequestDTO refreshDto = new TokenRefreshRequestDTO();
        refreshDto.setRefreshToken(refreshToken);
        HttpResponse<String> refreshResp = post("/api/auth/refresh", refreshDto, null);
        assertEquals(200, refreshResp.statusCode());
        String newAccessToken = JsonPath.parse(refreshResp.body()).read("$.accessToken", String.class);
        assertNotNull(newAccessToken);

        // 6. Access authenticated resource with new token
        HttpResponse<String> meWithNewToken = get("/api/employees/me", newAccessToken);
        assertEquals(200, meWithNewToken.statusCode());
        assertEquals(email, JsonPath.parse(meWithNewToken.body()).read("$.email", String.class));

        // 7. Logout over HTTP (requires authenticated user)
        HttpResponse<String> logoutResp = post("/api/auth/logout", refreshDto, newAccessToken);
        assertEquals(200, logoutResp.statusCode());

        // 8. Reusing invalidated refresh token should fail
        HttpResponse<String> reuseRefreshResp = post("/api/auth/refresh", refreshDto, null);
        assertTrue(reuseRefreshResp.statusCode() == 400 || reuseRefreshResp.statusCode() == 401 || reuseRefreshResp.statusCode() == 403);
    }

    @Test
    void testAccountLockoutThresholdAndAdminUnlockOverHttp() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        String userEmail = "lockout.victim." + ts + "@test.com";
        String correctPassword = "CorrectPassword123!";

        // 1. Register User
        registerUserOverHttp(userEmail, correctPassword, "Lockout", "Target");
        User targetUser = userRepository.findByEmail(userEmail).orElseThrow();
        Long targetUserId = targetUser.getId();

        // 2. Setup Admin and obtain token
        String adminEmail = "admin.security." + ts + "@test.com";
        registerUserOverHttp(adminEmail, "AdminPassword123!", "Sec", "Admin");
        elevateUserRole(adminEmail, "ADMIN");
        String adminToken = loginUserOverHttp(adminEmail, "AdminPassword123!").getAccessToken();

        // 3. Perform 5 consecutive failed login attempts over HTTP
        AuthRequestDTO badCredentials = new AuthRequestDTO();
        badCredentials.setEmail(userEmail);
        badCredentials.setPassword("IncorrectPassword999!");

        for (int i = 1; i <= 5; i++) {
            HttpResponse<String> failedResp = post("/api/auth/login", badCredentials, null);
            assertTrue(failedResp.statusCode() == 401 || failedResp.statusCode() == 423);
        }

        // 4. Attempt login with the CORRECT password - must be blocked because account is locked
        AuthRequestDTO correctCredentials = new AuthRequestDTO();
        correctCredentials.setEmail(userEmail);
        correctCredentials.setPassword(correctPassword);

        HttpResponse<String> blockedLoginResp = post("/api/auth/login", correctCredentials, null);
        assertTrue(blockedLoginResp.statusCode() == 401 || blockedLoginResp.statusCode() == 423);
        assertTrue(blockedLoginResp.body().toLowerCase().contains("lock")
                || blockedLoginResp.body().toLowerCase().contains("invalid")
                || blockedLoginResp.body().toLowerCase().contains("bad credentials"));

        // 5. Admin queries locked accounts list over HTTP
        HttpResponse<String> lockedUsersResp = get("/api/users?locked=true", adminToken);
        assertEquals(200, lockedUsersResp.statusCode());
        List<Integer> lockedIds = JsonPath.parse(lockedUsersResp.body()).read("$.content[*].id");
        assertTrue(lockedIds.contains(targetUserId.intValue()));

        // 6. Admin unlocks user account over HTTP
        HttpResponse<String> unlockResp = put("/api/users/" + targetUserId + "/unlock", null, adminToken);
        assertEquals(200, unlockResp.statusCode());
        assertTrue(JsonPath.parse(unlockResp.body()).read("$.accountNonLocked", Boolean.class));
        assertEquals(0, (int) JsonPath.parse(unlockResp.body()).read("$.failedLoginAttempts", Integer.class));

        // 7. User now successfully logs in with correct password over HTTP
        AuthResponseDTO recoveredLogin = loginUserOverHttp(userEmail, correctPassword);
        assertNotNull(recoveredLogin.getAccessToken());
    }
}
