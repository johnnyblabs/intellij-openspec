## Context

The plugin runs file operations from multiple threads: background tree rebuilds, validation, artifact generation, and file watchers. Write operations already use `WriteAction.runAndWait()` correctly (established in the vfs-refresh-audit change). However, VirtualFile read operations across 5 classes lack `ReadAction` wrappers, and `ConfigService` has a check-then-act race on its non-volatile `config` field.

## Goals / Non-Goals

**Goals:**
- Wrap all VirtualFile read operations in `ReadAction.compute()`
- Eliminate the ConfigService race condition with volatile + synchronized init
- Maintain test compatibility (unit tests run without IntelliJ Application)

**Non-Goals:**
- Refactoring service APIs or changing method signatures
- Adding new caching layers
- Changing the threading model (pooled thread + EDT callback pattern stays)

## Decisions

### 1. Use `ReadAction.compute()` for all VirtualFile reads

Wrap `contentsToByteArray()`, `getInputStream()`, and `refreshAndFindFileByPath()` calls in `ReadAction.compute()`.

**Rationale**: IntelliJ's threading model requires read lock for VFS access. While reads often work without it, concurrent write actions can cause assertion errors or stale data.

**Alternative considered**: `ReadAction.nonBlocking()` — overkill for these small reads, adds complexity with callbacks.

### 2. ApplicationManager null check for test compatibility

Same pattern used in `ArtifactOrchestrationService.writeArtifactResult()`: check `ApplicationManager.getApplication() == null` and fall back to direct NIO access in test context.

**Rationale**: Unit tests don't have an IntelliJ Application instance, so `ReadAction.compute()` would throw NPE. The fallback is safe because tests are single-threaded.

### 3. Volatile + double-checked locking for ConfigService

Make `config` field `volatile` and use synchronized block inside `getConfig()` only on the null path.

**Rationale**: Simple volatile alone doesn't prevent two threads from both calling `reload()`. Double-checked locking ensures exactly one reload while keeping the fast path lock-free.

**Alternative considered**: `synchronized` on every `getConfig()` call — unnecessary contention for the hot read path.

## Risks / Trade-offs

- **[Low] ReadAction contention**: ReadAction is a shared read lock — multiple readers can proceed concurrently. Only blocked briefly during write actions. No meaningful perf impact.
- **[Low] Test fallback divergence**: Test path uses direct NIO while production uses VFS. Mitigated by the fact that tests are single-threaded and don't test VFS behavior.
