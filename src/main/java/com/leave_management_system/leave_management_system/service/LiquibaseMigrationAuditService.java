package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LiquibaseAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseChangesetDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseLockStatusDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseValidationResultDTO;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.exception.ValidationFailedException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service providing comprehensive verification and auditing for Liquibase database
 * migrations, execution state, changelog locking, and checksum consistency.
 */
@Service
public class LiquibaseMigrationAuditService {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseMigrationAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final SpringLiquibase springLiquibase;

    @Autowired
    public LiquibaseMigrationAuditService(JdbcTemplate jdbcTemplate,
                                         DataSource dataSource,
                                         @Autowired(required = false) SpringLiquibase springLiquibase) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.springLiquibase = springLiquibase;
    }

    /**
     * Performs a full audit of executed Liquibase changesets, checksum consistency,
     * lock table status, and synchronization state against the master changelog.
     */
    public LiquibaseAuditReportDTO auditMigrationStatus() {
        LiquibaseAuditReportDTO report = new LiquibaseAuditReportDTO();

        String rawChangelog = springLiquibase != null ? springLiquibase.getChangeLog() : "classpath:db/changelog/db.changelog-master.yaml";
        report.setChangeLogFile(rawChangelog);
        report.setDatabaseChangeLogTable(springLiquibase != null && springLiquibase.getDatabaseChangeLogTable() != null
                ? springLiquibase.getDatabaseChangeLogTable() : "DATABASECHANGELOG");
        report.setDatabaseChangeLogLockTable(springLiquibase != null && springLiquibase.getDatabaseChangeLogLockTable() != null
                ? springLiquibase.getDatabaseChangeLogLockTable() : "DATABASECHANGELOGLOCK");

        // 1. Audit executed changesets from DATABASECHANGELOG
        List<LiquibaseChangesetDTO> executedChangesets = getExecutedChangesets();
        report.setExecutedChangesets(executedChangesets);
        report.setTotalChangesetsExecuted(executedChangesets.size());

        // 2. Audit Checksums
        boolean allChecksumsPresent = true;
        for (LiquibaseChangesetDTO cs : executedChangesets) {
            if (cs.getMd5sum() == null || cs.getMd5sum().trim().isEmpty()) {
                allChecksumsPresent = false;
                break;
            }
        }
        report.setAllChecksumsPresent(allChecksumsPresent);

        // 3. Audit Lock Status
        LiquibaseLockStatusDTO lockStatus = getLockStatus();
        report.setLockStatus(lockStatus);

        // 4. Validate engine status & unrun changesets via Liquibase API
        LiquibaseValidationResultDTO validationResult = validateChangelogs();
        report.setChecksumDriftDetected(!validationResult.isValid());

        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            report.setDatabaseProductName(database.getDatabaseProductName());

            String changelogPath = sanitizeChangelogPath(rawChangelog);
            try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
                 Liquibase liquibase = new Liquibase(changelogPath, resourceAccessor, database)) {

                Contexts contexts = springLiquibase != null && springLiquibase.getContexts() != null
                        ? new Contexts(springLiquibase.getContexts()) : new Contexts();
                LabelExpression labels = springLiquibase != null && springLiquibase.getLabelFilter() != null
                        ? new LabelExpression(springLiquibase.getLabelFilter()) : new LabelExpression();

                List<ChangeSet> unrun = liquibase.listUnrunChangeSets(contexts, labels);
                report.setUnrunChangesetsCount(unrun != null ? unrun.size() : 0);
            }
        } catch (Exception e) {
            log.warn("Unable to query Liquibase unrun changesets: {}", e.getMessage());
            report.setUnrunChangesetsCount(0);
        }

        // 5. Determine Overall Status
        if (lockStatus != null && lockStatus.isLocked()) {
            report.setOverallStatus("LOCKED");
        } else if (!report.isAllChecksumsPresent() || report.isChecksumDriftDetected()) {
            report.setOverallStatus("DRIFT_DETECTED");
        } else if (report.getUnrunChangesetsCount() > 0) {
            report.setOverallStatus("UNRUN_CHANGESETS");
        } else {
            report.setOverallStatus("HEALTHY");
        }

        return report;
    }

    /**
     * Programmatically validates changelog consistency and checksum integrity.
     */
    public LiquibaseValidationResultDTO validateChangelogs() {
        String rawChangelog = springLiquibase != null ? springLiquibase.getChangeLog() : "classpath:db/changelog/db.changelog-master.yaml";
        String changelogPath = sanitizeChangelogPath(rawChangelog);

        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
                 Liquibase liquibase = new Liquibase(changelogPath, resourceAccessor, database)) {

                liquibase.validate();
                return new LiquibaseValidationResultDTO(true, 0, List.of(), List.of(),
                        "All changesets and checksums are consistent and validated successfully.");
            }
        } catch (Exception e) {
            Throwable cause = e;
            while (cause != null && !(cause instanceof ValidationFailedException)) {
                cause = cause.getCause();
            }
            if (cause instanceof ValidationFailedException vfe) {
                log.error("Liquibase validation failed: {}", vfe.getMessage());
                List<String> errors = new ArrayList<>();
                if (vfe.getInvalidMD5Sums() != null && !vfe.getInvalidMD5Sums().isEmpty()) {
                    errors.addAll(vfe.getInvalidMD5Sums());
                } else {
                    errors.add(vfe.getMessage());
                }
                return new LiquibaseValidationResultDTO(false, errors.size(), errors, List.of(),
                        "Validation failed: Checksum drift or changeset inconsistency detected.");
            }
            log.error("Liquibase execution error during validation: {}", e.getMessage());
            return new LiquibaseValidationResultDTO(false, 1, List.of(e.getMessage()), List.of(),
                    "Validation check encountered an error: " + e.getMessage());
        }
    }

    /**
     * Retrieves all executed changesets from DATABASECHANGELOG in order of execution.
     */
    public List<LiquibaseChangesetDTO> getExecutedChangesets() {
        String sql = "SELECT id, author, filename, dateexecuted, orderexecuted, exectype, " +
                "md5sum, description, comments, tag, liquibase, deployment_id " +
                "FROM databasechangelog ORDER BY orderexecuted ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("dateexecuted");
            LocalDateTime dateExecuted = ts != null ? ts.toLocalDateTime() : null;

            return new LiquibaseChangesetDTO(
                    rs.getString("id"),
                    rs.getString("author"),
                    rs.getString("filename"),
                    dateExecuted,
                    rs.getInt("orderexecuted"),
                    rs.getString("exectype"),
                    rs.getString("md5sum"),
                    rs.getString("description"),
                    rs.getString("comments"),
                    rs.getString("tag"),
                    rs.getString("liquibase"),
                    rs.getString("deployment_id")
            );
        });
    }

    /**
     * Inspects the current lock state from DATABASECHANGELOGLOCK.
     */
    public LiquibaseLockStatusDTO getLockStatus() {
        String sql = "SELECT id, locked, lockgranted, lockedby FROM databasechangeloglock";

        List<LiquibaseLockStatusDTO> locks = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("lockgranted");
            LocalDateTime lockGranted = ts != null ? ts.toLocalDateTime() : null;

            return new LiquibaseLockStatusDTO(
                    rs.getInt("id"),
                    rs.getBoolean("locked"),
                    lockGranted,
                    rs.getString("lockedby"),
                    0
            );
        });

        if (locks.isEmpty()) {
            return new LiquibaseLockStatusDTO(1, false, null, null, 0);
        }

        LiquibaseLockStatusDTO primaryLock = locks.get(0);
        primaryLock.setTotalLocks(locks.size());
        return primaryLock;
    }

    private String sanitizeChangelogPath(String path) {
        if (path == null) {
            return "db/changelog/db.changelog-master.yaml";
        }
        return path.replaceFirst("^classpath\\*?:", "");
    }
}
