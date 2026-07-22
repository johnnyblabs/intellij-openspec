package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Requirement {
    private final String name;
    private String body;
    private String keyword;
    private final List<Scenario> scenarios;

    public Requirement(String name) {
        this.name = name;
        this.body = "";
        this.scenarios = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<Scenario> getScenarios() {
        return scenarios;
    }

    public void addScenario(Scenario scenario) {
        scenarios.add(scenario);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Requirement other)) return false;
        return Objects.equals(name, other.name)
                && Objects.equals(body, other.body)
                && Objects.equals(keyword, other.keyword)
                && scenarios.equals(other.scenarios);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, body, keyword, scenarios);
    }

    @Override
    public String toString() {
        return "Requirement{name='" + name + "', keyword='" + keyword + "', scenarios=" + scenarios + '}';
    }
}
