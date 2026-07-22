package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Scenario other)) return false;
        return Objects.equals(name, other.name) && clauses.equals(other.clauses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, clauses);
    }

    @Override
    public String toString() {
        return "Scenario{name='" + name + "', clauses=" + clauses + '}';
    }
}
