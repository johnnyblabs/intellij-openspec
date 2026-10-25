## MODIFIED Requirements

### Requirement: Loading and error states

The Explore panel SHALL display appropriate visual feedback during API calls and on errors. Every request SHALL end with the input re-enabled, however it ends.

#### Scenario: Loading state display
- **WHEN** an explore request is sent to the AI provider
- **THEN** the response area SHALL display a loading message in muted foreground color and the topic header SHALL show the topic being explored

#### Scenario: Error state display
- **WHEN** the AI provider returns an error
- **THEN** the response area SHALL display the error message in error styling (red foreground) and the input area SHALL be re-enabled

#### Scenario: Error state allows retry
- **WHEN** an error is displayed and the user submits a new topic
- **THEN** the panel SHALL clear the error and initiate a new explore request

#### Scenario: Provider no longer configured
- **WHEN** the user sends a topic from the Explore panel and no Direct API provider is configured any more
- **THEN** the plugin SHALL copy the prompt to the clipboard
- **AND** the response area SHALL say that no Direct API provider is configured, that the prompt was copied, and where to configure a provider
- **AND** the input area SHALL be re-enabled, and no notification balloon SHALL be shown

#### Scenario: Unexpected failure
- **WHEN** the explore request fails with an error other than a provider error
- **THEN** the response area SHALL display the failure in error styling and the input area SHALL be re-enabled

#### Scenario: Cancelled request
- **WHEN** the user cancels the explore request from the progress indicator
- **THEN** the response area SHALL say the request was cancelled and the input area SHALL be re-enabled
