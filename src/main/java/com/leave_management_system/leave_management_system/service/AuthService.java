package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.RefreshToken;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, JwtUtils jwtUtils,
                       RefreshTokenService refreshTokenService, UserRepository userRepository,
                       EmployeeRepository employeeRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponseDTO login(AuthRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        
        User user = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow();
        String rawRefreshToken = UUID.randomUUID().toString();
        refreshTokenService.createRefreshToken(user.getId(), rawRefreshToken);

        return new AuthResponseDTO(jwt, rawRefreshToken, UserResponseDTO.fromEntity(user));
    }

    public AuthResponseDTO register(RegisterRequestDTO registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        User user = new User();
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setActive(true);

        Role userRole = roleRepository.findByName("EMPLOYEE")
                .orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        user.getRoles().add(userRole);
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setFirstName(registerRequest.getFirstName());
        employee.setLastName(registerRequest.getLastName());
        employee.setPhone(registerRequest.getPhone());
        employee.setEmployeeCode(registerRequest.getEmployeeCode());
        employee.setStatus("ACTIVE");
        employee.setUser(user);
        employeeRepository.save(employee);

        // Auto login after register
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(registerRequest.getEmail(), registerRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String jwt = jwtUtils.generateJwtToken(authentication);
        String rawRefreshToken = UUID.randomUUID().toString();
        refreshTokenService.createRefreshToken(user.getId(), rawRefreshToken);

        return new AuthResponseDTO(jwt, rawRefreshToken, UserResponseDTO.fromEntity(user));
    }

    @Transactional
    public AuthResponseDTO refreshToken(TokenRefreshRequestDTO request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    refreshTokenService.deleteByToken(requestRefreshToken);

                    String token = jwtUtils.generateTokenFromUsername(user.getEmail());
                    
                    String newRawRefreshToken = UUID.randomUUID().toString();
                    refreshTokenService.createRefreshToken(user.getId(), newRawRefreshToken);

                    return new AuthResponseDTO(token, newRawRefreshToken, UserResponseDTO.fromEntity(user));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    public void logout(String refreshToken) {
        refreshTokenService.deleteByToken(refreshToken);
    }
}
