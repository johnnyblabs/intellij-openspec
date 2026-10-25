# Shell PATH fixtures

Real stdout of the command `CliDetectionService` runs to read the PATH the user's shell sets:

```
$SHELL <flags> -c 'printf "\n%s%s%s\n" "__OPENSPEC_PATH_BEGIN__" "$PATH" "__OPENSPEC_PATH_END__"'
```

Each was run with `env -i`, a throwaway `HOME`, `PATH=/usr/bin:/bin:/usr/sbin:/sbin` (what a Dock-launched app inherits from launchd), `INTELLIJ_ENVIRONMENT_READER=true`, `DISABLE_AUTO_UPDATE=true` and stdin closed. The HOME has an rc file that adds `$HOME/rcbin` to PATH and prints a banner. The throwaway HOME is sanitized to `/fixture/home`. The rest of the PATH is what macOS's `path_helper` adds on the capture machine.

| File | Shell and flags | rc file | Shows |
|---|---|---|---|
| `zsh-ilc-noisy.txt` | `/bin/zsh -l -i -c` | `.zshrc` | the banner before the markers; `/fixture/home/rcbin` first in the marked PATH |
| `bash-ilc-noisy.txt` | `/bin/bash -l -i -c` | `.bashrc`, sourced from `.bash_profile` | the same for bash |
| `zsh-lc-misses.txt` | `/bin/zsh -l -c` (the plugin's old flags) | `.zshrc` | no banner and **no** `rcbin`: a login shell that isn't interactive skips `.zshrc` |

Consumed by `CliDetectionServiceTest`'s shell PATH tests.
