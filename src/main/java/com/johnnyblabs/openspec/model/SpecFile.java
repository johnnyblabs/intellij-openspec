package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SpecFile {
    private final String domain;
    private String title;
    private final List<Requirement> requirements;
    private final String filePath;

    public SpecFile(String domain, String filePath) {
        this.domain = domain;
        this.filePath = filePath;
        this.requirements = new ArrayList<>();
    }

    public String getDomain() {
        return domain;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public void addRequirement(Requirement requirement) {
        requirements.add(requirement);
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpecFile other)) return false;
        return Objects.equals(domain, other.domain)
                && Objects.equals(title, other.title)
                && Objects.equals(filePath, other.filePath)
                && requirements.equals(other.requirements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, title, filePath, requirements);
    }
}
