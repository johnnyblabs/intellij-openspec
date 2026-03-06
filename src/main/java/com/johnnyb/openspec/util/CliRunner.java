package com.johnnyb.openspec.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import java.nio.charset.StandardCharsets;

public final class CliRunner {

    private CliRunner() {
    }

    public static CliResult run(Project project, String... args) throws Exception {
        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setExePath("openspec");
        cmd.addParameters(args);
        cmd.setCharset(StandardCharsets.UTF_8);

        if (project.getBasePath() != null) {
            cmd.setWorkDirectory(project.getBasePath());
        }

        OSProcessHandler handler = new OSProcessHandler(cmd);
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (ProcessOutputTypes.STDOUT.equals(outputType)) {
                    stdout.append(event.getText());
                } else if (ProcessOutputTypes.STDERR.equals(outputType)) {
                    stderr.append(event.getText());
                }
            }
        });

        handler.startNotify();
        handler.waitFor();

        int exitCode = handler.getExitCode() != null ? handler.getExitCode() : -1;
        return new CliResult(exitCode, stdout.toString(), stderr.toString());
    }

    public record CliResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
