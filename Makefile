PLUGIN_NAME  := intellij-openspec
IDEA_VERSION := IntelliJIdea2025.3
PLUGINS_DIR  := $(HOME)/Library/Application Support/JetBrains/$(IDEA_VERSION)/plugins
DIST_DIR     := build/distributions
BUILD_DIR    := build/libs

SANDBOX_CONFIG := .intellijPlatform/sandbox/intellij-openspec/IC-2024.2/config
DEMO_DIR ?= $(shell echo "$${TMPDIR:-/tmp}")/openspec-lifecycle-demo-$(shell date +%s)

.PHONY: build test compile verify ui-smoke testdrive install uninstall clean reinstall publish help

## Build the plugin zip
build:
	./gradlew buildPlugin

## Run all tests
test:
	./gradlew cleanTest test

## Compile only (fast check)
compile:
	./gradlew compileJava

## Plugin Verifier against the target IDE range (slow on first run — IDE downloads)
verify:
	./gradlew verifyPlugin

## Automated UI walkthrough journeys — boots a real IDE per journey (~4 min warm; windows appear)
ui-smoke:
	./gradlew uiSmoke

## Manual walkthrough: seed a fresh demo project and open it in the sandbox IDE
testdrive:
	./scripts/seed-lifecycle-demo.sh "$(DEMO_DIR)"
	cp .claude/skills/lifecycle-testdrive/walkthrough-template.md "$(DEMO_DIR)/WALKTHROUGH.md"
	@# IDEA 2024.2 sandbox workaround: the bundled Gradle plugin's compatibility updater
	@# downloads data with Java 25 entries its parser can't read (platform noise,
	@# unrelated to this plugin) — disable it in the sandbox.
	@mkdir -p "$(SANDBOX_CONFIG)"
	@printf 'com.intellij.gradle\norg.jetbrains.plugins.gradle\norg.jetbrains.plugins.gradle.maven\n' > "$(SANDBOX_CONFIG)/disabled_plugins.txt"
	./gradlew runIde --args="$(DEMO_DIR)"

## Install plugin to local IntelliJ (requires restart)
install: build
	@echo "Installing $(PLUGIN_NAME) to $(IDEA_VERSION)..."
	@rm -rf "$(PLUGINS_DIR)/$(PLUGIN_NAME)"
	@mkdir -p "$(PLUGINS_DIR)"
	@cd "$(PLUGINS_DIR)" && unzip -qo "$(CURDIR)/$(DIST_DIR)"/$(PLUGIN_NAME)-*.zip
	@echo "Installed. Restart IntelliJ IDEA to load the plugin."

## Uninstall plugin from local IntelliJ
uninstall:
	@rm -rf "$(PLUGINS_DIR)/$(PLUGIN_NAME)"
	@echo "Uninstalled. Restart IntelliJ IDEA to complete removal."

## Clean build artifacts
clean:
	./gradlew clean

## Uninstall, rebuild, and reinstall
reinstall: uninstall build install

## Publishing happens in CI on a v* tag push — never locally (signing + Marketplace
## upload are CI's job; see docs/README.md and the release-prep checklist).
publish:
	@echo "Refusing: publishPlugin never runs locally." >&2
	@echo "Release flow: run the release-prep checklist, then push a v* tag — CI signs and publishes." >&2
	@exit 1

## Show help
help:
	@echo "OpenSpec Plugin - Build & Install"
	@echo ""
	@echo "  make build      Build the plugin zip"
	@echo "  make test       Run all tests"
	@echo "  make compile    Compile only (fast check)"
	@echo "  make verify     Plugin Verifier against the target IDE range"
	@echo "  make ui-smoke   Automated UI walkthrough journeys (real IDE boots)"
	@echo "  make testdrive  Seed a demo project and open it in the sandbox IDE"
	@echo "  make install    Build and install to IntelliJ $(IDEA_VERSION)"
	@echo "  make uninstall  Remove plugin from IntelliJ"
	@echo "  make reinstall  Clean reinstall (uninstall + build + install)"
	@echo "  make clean      Clean build artifacts"
	@echo "  make publish    (refuses — releases publish via CI on v* tags)"
