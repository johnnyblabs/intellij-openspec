package com.johnnyb.openspec.model;

public class ChangeMetadata {
    private String schema;
    private String status;
    private String created;
    private TrackingMetadata tracking;

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

    public TrackingMetadata getTracking() {
        return tracking;
    }

    public void setTracking(TrackingMetadata tracking) {
        this.tracking = tracking;
    }

    public static class TrackingMetadata {
        private ForgejoRef forgejo;
        private PlaneRef plane;

        public ForgejoRef getForgejo() { return forgejo; }
        public void setForgejo(ForgejoRef forgejo) { this.forgejo = forgejo; }
        public PlaneRef getPlane() { return plane; }
        public void setPlane(PlaneRef plane) { this.plane = plane; }
    }

    public static class ForgejoRef {
        private int issueNumber;
        private String issueUrl;

        public int getIssueNumber() { return issueNumber; }
        public void setIssueNumber(int issueNumber) { this.issueNumber = issueNumber; }
        public String getIssueUrl() { return issueUrl; }
        public void setIssueUrl(String issueUrl) { this.issueUrl = issueUrl; }
    }

    public static class PlaneRef {
        private String workItemId;
        private String workItemUrl;

        public String getWorkItemId() { return workItemId; }
        public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
        public String getWorkItemUrl() { return workItemUrl; }
        public void setWorkItemUrl(String workItemUrl) { this.workItemUrl = workItemUrl; }
    }
}
