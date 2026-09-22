package com.leave_management_system.leave_management_system.aspect;

import com.leave_management_system.leave_management_system.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class AuditAspect {

    @Autowired
    private AuditService auditService;

    @AfterReturning(pointcut = "@annotation(auditAction)", returning = "result")
    public void logAuditActivity(JoinPoint joinPoint, AuditAction auditAction, Object result) {
        try {
            Long entityId = null;
            
            // Try to extract ID from the returned DTO if possible
            if (result != null) {
                try {
                    Method getIdMethod = result.getClass().getMethod("getId");
                    Object idValue = getIdMethod.invoke(result);
                    if (idValue instanceof Long) {
                        entityId = (Long) idValue;
                    }
                } catch (Exception e) {
                    // Ignore, maybe no getId method
                }
            }

            // Fallback to arguments if no ID from return
            if (entityId == null && joinPoint.getArgs().length > 0) {
                Object arg = joinPoint.getArgs()[0];
                if (arg instanceof Long) {
                    entityId = (Long) arg;
                }
            }

            auditService.logAction(
                auditAction.action(),
                auditAction.entityType(),
                entityId,
                null,
                result != null ? result.toString() : null
            );
        } catch (Exception e) {
            System.err.println("Failed to log audit activity: " + e.getMessage());
        }
    }
}
