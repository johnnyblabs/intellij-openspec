PLUGIN_NAME  := OpenSpecPlugin
IDEA_VERSION := IntelliJIdea2025.3
PLUGINS_DIR  := $(HOME)/Library/Application Support/JetBrains/$(IDEA_VERSION)/plugins
DIST_DIR     := build/distributions
BUILD_DIR    := build/libs

.PHONY: build test install uninstall clean reinstall publish

## Build the plugin zip
build:
	./gradlew buildPlugin

## Run all tests
test:
	./gradlew cleanTest test

## Compile only (fast check)
compile:
	./gradlew compileJava

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

## Publish to JetBrains Marketplace (requires JETBRAINS_MARKETPLACE_TOKEN in scripts/.env)
publish:
	@export $$(grep -v '^#' scripts/.env | xargs) && ./gradlew clean publishPlugin

## Show help
help:
	@echo "OpenSpec Plugin - Build & Install"
	@echo ""
	@echo "  make build      Build the plugin zip"
	@echo "  make test       Run all tests"
	@echo "  make compile    Compile only (fast check)"
	@echo "  make install    Build and install to IntelliJ $(IDEA_VERSION)"
	@echo "  make uninstall  Remove plugin from IntelliJ"
	@echo "  make reinstall  Clean reinstall (uninstall + build + install)"
	@echo "  make clean      Clean build artifacts"
	@echo "  make publish    Publish to JetBrains Marketplace"
