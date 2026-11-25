package com.johnnyblabs.openspec.services;

import com.intellij.execution.configurations.GeneralCommandLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Detection's own process runner closes the child's standard input too, so a probe that reads it can't hang. */
class CliDetectionStdinTest {

    @Test
    void aProbeReadingStdinSeesEndOfInputAtOnce(@TempDir Path tmp) throws Exception {
        Path stub = tmp.resolve("Stub.java");
        Files.writeString(stub, "public class Stub { public static void main(String[] a) throws Exception {"
                + " System.out.print(System.in.read() == -1 ? \"eof\" : \"data\"); } }");
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        long start = System.nanoTime();
        String out = CliDetectionService.runAndCapture(new GeneralCommandLine(javaBin, stub.toString()), 30_000);
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        assertEquals("eof", out);
        assertTrue(seconds < 25, "returned before the timeout: " + seconds + "s");
    }
}
