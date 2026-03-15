#!/bin/bash
# Create a demo project for OpenSpec plugin screenshots and testing
# Usage: ./scripts/demo-project.sh [--screenshots] [--cleanup]
#
# Creates a realistic Java project with OpenSpec specs, changes, and @spec annotations
# perfect for marketplace screenshots and first-run testing.

set -e

DEMO_DIR="${DEMO_DIR:-$HOME/working/openspec-demo}"
MODE="${1:-create}"

# --- Cleanup ---
if [ "$MODE" = "--cleanup" ]; then
    echo "Cleaning up demo project at $DEMO_DIR"
    rm -rf "$DEMO_DIR"
    echo "Done."
    exit 0
fi

echo "=== Creating OpenSpec Demo Project ==="
echo "Location: $DEMO_DIR"

rm -rf "$DEMO_DIR"
mkdir -p "$DEMO_DIR"

# --- Gradle project skeleton ---
mkdir -p "$DEMO_DIR/src/main/java/com/example/taskboard"
mkdir -p "$DEMO_DIR/src/test/java/com/example/taskboard"

cat > "$DEMO_DIR/build.gradle" << 'GRADLE'
plugins {
    id 'java'
}

group = 'com.example'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}
GRADLE

cat > "$DEMO_DIR/settings.gradle" << 'SETTINGS'
rootProject.name = 'taskboard'
SETTINGS

# --- Java source files with @spec annotations ---
cat > "$DEMO_DIR/src/main/java/com/example/taskboard/TaskService.java" << 'JAVA'
package com.example.taskboard;

import java.util.*;
import java.time.LocalDateTime;

/**
 * Core task management service.
 */
public class TaskService {

    private final Map<String, Task> tasks = new LinkedHashMap<>();

    // @spec task-management:create-task
    public Task createTask(String title, String description, String assignee) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        Task task = new Task(UUID.randomUUID().toString(), title, description, assignee);
        tasks.put(task.getId(), task);
        return task;
    }

    // @spec task-management:update-status
    public Task updateStatus(String taskId, TaskStatus newStatus) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new NoSuchElementException("Task not found: " + taskId);
        }
        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    // @spec task-management:list-tasks
    public List<Task> listTasks(TaskStatus filter) {
        if (filter == null) {
            return List.copyOf(tasks.values());
        }
        return tasks.values().stream()
                .filter(t -> t.getStatus() == filter)
                .toList();
    }

    // @spec task-management:assign-task
    public Task assignTask(String taskId, String assignee) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new NoSuchElementException("Task not found: " + taskId);
        }
        task.setAssignee(assignee);
        return task;
    }
}
JAVA

cat > "$DEMO_DIR/src/main/java/com/example/taskboard/Task.java" << 'JAVA'
package com.example.taskboard;

import java.time.LocalDateTime;

public class Task {
    private final String id;
    private String title;
    private String description;
    private String assignee;
    private TaskStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Task(String id, String title, String description, String assignee) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.status = TaskStatus.TODO;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAssignee() { return assignee; }
    public TaskStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
JAVA

cat > "$DEMO_DIR/src/main/java/com/example/taskboard/TaskStatus.java" << 'JAVA'
package com.example.taskboard;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE
}
JAVA

cat > "$DEMO_DIR/src/main/java/com/example/taskboard/NotificationService.java" << 'JAVA'
package com.example.taskboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends notifications when task state changes.
 */
public class NotificationService {

    private final List<String> sentNotifications = new ArrayList<>();

    // @spec notifications:status-change-notification
    public void notifyStatusChange(Task task, TaskStatus oldStatus, TaskStatus newStatus) {
        String message = String.format("Task '%s' moved from %s to %s",
                task.getTitle(), oldStatus, newStatus);
        sendNotification(task.getAssignee(), message);
    }

    // @spec notifications:assignment-notification
    public void notifyAssignment(Task task, String newAssignee) {
        String message = String.format("You have been assigned to '%s'", task.getTitle());
        sendNotification(newAssignee, message);
    }

    private void sendNotification(String recipient, String message) {
        sentNotifications.add(recipient + ": " + message);
    }

    public List<String> getSentNotifications() {
        return List.copyOf(sentNotifications);
    }
}
JAVA

# --- README ---
cat > "$DEMO_DIR/README.md" << 'README'
# TaskBoard

A lightweight task management application built with Java 21.

## Features

- Create, update, and assign tasks
- Track task lifecycle (TODO → IN_PROGRESS → IN_REVIEW → DONE)
- Filter and sort tasks by status and priority
- Notifications on status changes and assignments
- Workload and status summary reporting

## Tech Stack

- **Language:** Java 21
- **Build:** Gradle
- **Specs:** [OpenSpec](https://github.com/fission-ai/openspec) for spec-driven development

## Project Structure

```
src/main/java/com/example/taskboard/
├── Task.java              # Task model
├── TaskStatus.java        # Status enum (TODO, IN_PROGRESS, IN_REVIEW, DONE)
├── TaskService.java       # Core CRUD operations
└── NotificationService.java  # Event notifications

openspec/
├── config.yaml            # OpenSpec configuration
├── specs/                 # Living specifications
│   ├── task-management/   # Core task CRUD and lifecycle
│   ├── notifications/     # Notification rules
│   └── reporting/         # Reports and dashboards
└── changes/               # Change proposals
    ├── add-priority-field/ # Active: adding priority to tasks
    └── archive/           # Completed changes
```

## Getting Started

```bash
./gradlew build
```

## OpenSpec Workflow

This project uses [OpenSpec](https://github.com/fission-ai/openspec) for spec-driven development:

1. **Propose** a change with requirements
2. **Design** the implementation approach
3. **Implement** against a task checklist
4. **Archive** when complete — specs update automatically
README

# --- OpenSpec structure ---
mkdir -p "$DEMO_DIR/openspec/specs/task-management"
mkdir -p "$DEMO_DIR/openspec/specs/notifications"
mkdir -p "$DEMO_DIR/openspec/specs/reporting"
mkdir -p "$DEMO_DIR/openspec/changes/add-priority-field/specs/task-management"
mkdir -p "$DEMO_DIR/openspec/changes/archive/2026-03-10-add-notifications"

cat > "$DEMO_DIR/openspec/config.yaml" << 'YAML'
schema: spec-driven
version: "1.2"

context: |
  Task management application built with Java 21.
  Uses in-memory storage for simplicity.
YAML

# --- Main specs ---
cat > "$DEMO_DIR/openspec/specs/task-management/spec.md" << 'SPEC'
# Task Management

## Purpose
Core task CRUD operations and lifecycle management.

## Requirements

### Requirement: Create task
The system SHALL allow creating tasks with a title, description, and assignee.

#### Scenario: Valid task creation
- **WHEN** a user creates a task with title "Fix login bug" and assignee "alice"
- **THEN** the system SHALL return a task with a unique ID and status TODO

#### Scenario: Missing title
- **WHEN** a user creates a task without a title
- **THEN** the system SHALL reject the request with a validation error

### Requirement: Update status
The system SHALL allow updating a task's status through the lifecycle: TODO → IN_PROGRESS → IN_REVIEW → DONE.

#### Scenario: Valid status transition
- **WHEN** a user updates a TODO task to IN_PROGRESS
- **THEN** the task status SHALL be updated and the timestamp refreshed

### Requirement: List tasks
The system SHALL allow listing all tasks, optionally filtered by status.

#### Scenario: Filter by status
- **WHEN** a user requests tasks filtered by IN_PROGRESS
- **THEN** only tasks with status IN_PROGRESS SHALL be returned

### Requirement: Assign task
The system SHALL allow reassigning a task to a different user.

#### Scenario: Valid assignment
- **WHEN** a user assigns task "T-1" to "bob"
- **THEN** the task's assignee SHALL be updated to "bob"
SPEC

cat > "$DEMO_DIR/openspec/specs/notifications/spec.md" << 'SPEC'
# Notifications

## Purpose
Notify users when tasks are updated or assigned.

## Requirements

### Requirement: Status change notification
The system SHALL notify the assignee when a task's status changes.

#### Scenario: Task moves to IN_REVIEW
- **WHEN** a task moves from IN_PROGRESS to IN_REVIEW
- **THEN** the assignee SHALL receive a notification with the old and new status

### Requirement: Assignment notification
The system SHALL notify a user when they are assigned to a task.

#### Scenario: New assignment
- **WHEN** a task is assigned to "alice"
- **THEN** "alice" SHALL receive a notification with the task title
SPEC

cat > "$DEMO_DIR/openspec/specs/reporting/spec.md" << 'SPEC'
# Reporting

## Purpose
Generate reports on task progress and team workload.

## Requirements

### Requirement: Status summary report
The system SHALL provide a summary count of tasks grouped by status.

#### Scenario: Mixed statuses
- **WHEN** a report is generated with tasks in various statuses
- **THEN** the report SHALL show the count per status (TODO: 3, IN_PROGRESS: 2, etc.)

### Requirement: Assignee workload report
The system SHALL provide a per-assignee breakdown of open tasks.

#### Scenario: Multiple assignees
- **WHEN** a workload report is generated
- **THEN** each assignee SHALL be listed with their count of non-DONE tasks
SPEC

# --- Active change (in progress) ---
cat > "$DEMO_DIR/openspec/changes/add-priority-field/.openspec.yaml" << 'YAML'
schema: spec-driven
status: proposed
YAML

cat > "$DEMO_DIR/openspec/changes/add-priority-field/proposal.md" << 'MD'
## Why

Users need to prioritize tasks but there's no priority field. High-priority bugs get lost among low-priority feature requests.

## What Changes

- Add a `Priority` enum (LOW, MEDIUM, HIGH, CRITICAL) to the Task model
- Allow setting priority during task creation and via update
- Sort task lists by priority descending by default

## Capabilities

### New Capabilities
_None_

### Modified Capabilities
- `task-management`: Adding priority field to task creation and listing

## Impact

- `Task.java` — new priority field
- `TaskService.java` — updated create and list methods
- `TaskStatus.java` — no change
MD

cat > "$DEMO_DIR/openspec/changes/add-priority-field/design.md" << 'MD'
## Context

Tasks currently have no priority. Users sort mentally which leads to missed deadlines on critical items.

## Goals / Non-Goals

**Goals:**
- Add priority to task model
- Default sort by priority

**Non-Goals:**
- Priority-based notifications (separate change)
- SLA tracking

## Decisions

### Use an enum for priority levels

Four levels: LOW, MEDIUM, HIGH, CRITICAL. Default is MEDIUM for new tasks.

**Alternative considered**: Numeric priority (1-10) — rejected because discrete levels are clearer for users.

## Risks / Trade-offs

- **[Low]** Existing tasks will default to MEDIUM priority on migration
MD

cat > "$DEMO_DIR/openspec/changes/add-priority-field/tasks.md" << 'MD'
## 1. Model changes

- [x] 1.1 Create Priority enum (LOW, MEDIUM, HIGH, CRITICAL)
- [x] 1.2 Add priority field to Task class with default MEDIUM
- [ ] 1.3 Update TaskService.createTask() to accept optional priority parameter

## 2. Sorting

- [ ] 2.1 Update TaskService.listTasks() to sort by priority descending
- [ ] 2.2 Add priority to task display output

## 3. Verify

- [ ] 3.1 Add unit tests for priority filtering and sorting
MD

cat > "$DEMO_DIR/openspec/changes/add-priority-field/specs/task-management/spec.md" << 'MD'
## MODIFIED Requirements

### Requirement: Create task
The system SHALL allow creating tasks with a title, description, assignee, and optional priority (default MEDIUM).

#### Scenario: Task with priority
- **WHEN** a user creates a task with priority HIGH
- **THEN** the task SHALL be created with priority HIGH

#### Scenario: Task without priority
- **WHEN** a user creates a task without specifying priority
- **THEN** the task SHALL default to MEDIUM priority

## ADDED Requirements

### Requirement: Priority-based listing
The system SHALL sort task lists by priority descending (CRITICAL first) by default.

#### Scenario: Mixed priority listing
- **WHEN** a user lists all tasks
- **THEN** CRITICAL tasks SHALL appear before HIGH, which appear before MEDIUM and LOW
MD

# --- Archived change ---
cat > "$DEMO_DIR/openspec/changes/archive/2026-03-10-add-notifications/.openspec.yaml" << 'YAML'
schema: spec-driven
status: archived
YAML

cat > "$DEMO_DIR/openspec/changes/archive/2026-03-10-add-notifications/proposal.md" << 'MD'
## Why

Users aren't aware when tasks are assigned to them or when status changes. They have to manually check.

## What Changes

- Add NotificationService for status change and assignment events
- Integrate with TaskService lifecycle

## Capabilities

### New Capabilities
- `notifications`: Status change and assignment notifications

### Modified Capabilities
_None_

## Impact
- New `NotificationService.java`
- `TaskService.java` — emit events on status/assignment changes
MD

cat > "$DEMO_DIR/openspec/changes/archive/2026-03-10-add-notifications/tasks.md" << 'MD'
## 1. Notification service

- [x] 1.1 Create NotificationService with status change and assignment methods
- [x] 1.2 Integrate with TaskService

## 2. Verify

- [x] 2.1 Add tests for notification delivery
MD

# --- Git init ---
cd "$DEMO_DIR"
git init -q
git add -A
git commit -q -m "Initial project with OpenSpec specs and changes"

echo ""
echo "=== Demo Project Ready ==="
echo "Location: $DEMO_DIR"
echo ""
echo "Contents:"
echo "  Java source: 4 files (TaskService, Task, TaskStatus, NotificationService)"
echo "  Specs: 3 domains (task-management, notifications, reporting)"
echo "  Active change: add-priority-field (2/6 tasks done)"
echo "  Archived change: add-notifications (complete)"
echo "  @spec annotations: 6 references across 2 files"
echo ""
echo "To open in IDE:"
echo "  ./gradlew runIde"
echo "  Then: File → Open → $DEMO_DIR"
echo ""
echo "To capture screenshots:"
echo "  ./scripts/capture-screenshots.sh"
echo ""
echo "To clean up:"
echo "  ./scripts/demo-project.sh --cleanup"
