package com.johnnyb.openspec.model;

public class ChangeMetadata {
    private String schema;
    private String status;
    private String created;

    public ChangeMetadata() {
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }
}
