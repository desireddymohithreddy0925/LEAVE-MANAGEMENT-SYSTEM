package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the database migration lock state
 * from the DATABASECHANGELOGLOCK table.
 */
public class LiquibaseLockStatusDTO {

    private Integer lockId;
    private boolean locked;
    private LocalDateTime lockGranted;
    private String lockedBy;
    private int totalLocks;

    public LiquibaseLockStatusDTO() {
    }

    public LiquibaseLockStatusDTO(Integer lockId, boolean locked, LocalDateTime lockGranted, String lockedBy, int totalLocks) {
        this.lockId = lockId;
        this.locked = locked;
        this.lockGranted = lockGranted;
        this.lockedBy = lockedBy;
        this.totalLocks = totalLocks;
    }

    public Integer getLockId() {
        return lockId;
    }

    public void setLockId(Integer lockId) {
        this.lockId = lockId;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public LocalDateTime getLockGranted() {
        return lockGranted;
    }

    public void setLockGranted(LocalDateTime lockGranted) {
        this.lockGranted = lockGranted;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public int getTotalLocks() {
        return totalLocks;
    }

    public void setTotalLocks(int totalLocks) {
        this.totalLocks = totalLocks;
    }
}

