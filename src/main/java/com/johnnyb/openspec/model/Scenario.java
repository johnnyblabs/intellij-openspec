package com.johnnyb.openspec.model;

import java.util.ArrayList;
import java.util.List;

public class Scenario {
    private final String name;
    private final List<String> clauses;

    public Scenario(String name) {
        this.name = name;
        this.clauses = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public List<String> getClauses() {
        return clauses;
    }

    public void addClause(String clause) {
        clauses.add(clause);
    }
}
