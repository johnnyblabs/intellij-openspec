package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;

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
}
