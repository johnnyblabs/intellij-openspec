package com.johnnyb.openspec.services;

public interface GenerateAllListener {
    void onArtifactStarted(String artifactId, int index, int total);
    void onArtifactCompleted(String artifactId);
    void onAllComplete();
    void onError(String artifactId, Exception exception);
    void onCancelled(String artifactId);
}
