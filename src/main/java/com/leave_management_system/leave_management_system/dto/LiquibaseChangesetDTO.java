package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing an individual executed Liquibase changeset
 * recorded in the DATABASECHANGELOG table.
 */
public class LiquibaseChangesetDTO {

    private String id;
    private String author;
    private String filename;
    private LocalDateTime dateExecuted;
    private Integer orderExecuted;
    private String execType;
    private String md5sum;
    private String description;
    private String comments;
    private String tag;
    private String liquibaseVersion;
    private String deploymentId;

    public LiquibaseChangesetDTO() {
    }

    public LiquibaseChangesetDTO(String id, String author, String filename, LocalDateTime dateExecuted,
                                 Integer orderExecuted, String execType, String md5sum, String description,
                                 String comments, String tag, String liquibaseVersion, String deploymentId) {
        this.id = id;
        this.author = author;
        this.filename = filename;
        this.dateExecuted = dateExecuted;
        this.orderExecuted = orderExecuted;
        this.execType = execType;
        this.md5sum = md5sum;
        this.description = description;
        this.comments = comments;
        this.tag = tag;
        this.liquibaseVersion = liquibaseVersion;
        this.deploymentId = deploymentId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public LocalDateTime getDateExecuted() {
        return dateExecuted;
    }

    public void setDateExecuted(LocalDateTime dateExecuted) {
        this.dateExecuted = dateExecuted;
    }

    public Integer getOrderExecuted() {
        return orderExecuted;
    }

    public void setOrderExecuted(Integer orderExecuted) {
        this.orderExecuted = orderExecuted;
    }

    public String getExecType() {
        return execType;
    }

    public void setExecType(String execType) {
        this.execType = execType;
    }

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getLiquibaseVersion() {
        return liquibaseVersion;
    }

    public void setLiquibaseVersion(String liquibaseVersion) {
        this.liquibaseVersion = liquibaseVersion;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }
}

