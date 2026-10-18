package com.leave_management_system.leave_management_system.aspect;

import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditAspectTest {

    @Mock
    private AuditService auditService;

    @Mock
    private JoinPoint joinPoint;

    @InjectMocks
    private AuditAspect auditAspect;

    private AuditAction createAuditAction(String action, String entityType) {
        return new AuditAction() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AuditAction.class;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String entityType() {
                return entityType;
            }
        };
    }

    @Test
    void logAuditActivity_ExtractsIdFromDtoResult() {
        AuditAction auditAction = createAuditAction("LEAVE_APPLIED", "LeaveRequest");

        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(42L);

        auditAspect.logAuditActivity(joinPoint, auditAction, dto);

        verify(auditService).logAction("LEAVE_APPLIED", "LeaveRequest", 42L, null, dto);
    }

    @Test
    void logAuditActivity_ExtractsIdFromFirstArgument_WhenResultNull() {
        AuditAction auditAction = createAuditAction("DEPARTMENT_DELETED", "Department");

        when(joinPoint.getArgs()).thenReturn(new Object[]{99L});

        auditAspect.logAuditActivity(joinPoint, auditAction, null);

        verify(auditService).logAction("DEPARTMENT_DELETED", "Department", 99L, null, null);
    }

    @Test
    void logAuditActivity_HandlesExceptionsGracefully() {
        AuditAction auditAction = createAuditAction("TEST_ACTION", "TestEntity");
        when(joinPoint.getArgs()).thenReturn(new Object[]{10L});

        doThrow(new RuntimeException("Database down"))
                .when(auditService).logAction(anyString(), anyString(), any(), any(), any());

        assertDoesNotThrow(() -> auditAspect.logAuditActivity(joinPoint, auditAction, null));
    }
}
