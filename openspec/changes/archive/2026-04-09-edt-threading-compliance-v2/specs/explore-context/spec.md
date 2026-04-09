## MODIFIED Requirements

### Requirement: Delivery mode routing

The plugin SHALL route the assembled explore prompt through the user's configured delivery mode, supporting Direct API, Editor Tab, and Clipboard delivery.

#### Scenario: Direct API delivery
- **WHEN** the resolved delivery mode is Direct API and an AI provider is configured
- **THEN** the plugin SHALL send the explore prompt to the configured AI provider on a background thread and display the response in the Explore tab

#### Scenario: Editor Tab delivery
- **WHEN** the resolved delivery mode is Editor Tab
- **THEN** the plugin SHALL write the explore prompt to a temporary file on a pooled thread, perform the VFS `refreshAndFindFileByNioFile` lookup on the same pooled thread, and open the file in an editor tab via `invokeLater` on the EDT

#### Scenario: Clipboard delivery
- **WHEN** the resolved delivery mode is Clipboard
- **THEN** the plugin SHALL copy the explore prompt to the system clipboard and show a notification

#### Scenario: Direct API not configured
- **WHEN** the resolved delivery mode is Direct API but no provider is configured
- **THEN** the plugin SHALL fall back to Clipboard delivery with a notification suggesting the user configure an AI provider
