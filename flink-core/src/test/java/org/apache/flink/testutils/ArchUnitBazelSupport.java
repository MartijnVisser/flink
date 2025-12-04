/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Support class for ArchUnit tests running under Bazel.
 *
 * <p>ArchUnit's freeze violation store mechanism expects to find the archunit-violations directory
 * relative to the current working directory. In Bazel, tests run in a sandbox and the files are
 * available in the runfiles tree. This class creates a symlink from the expected location to the
 * runfiles location.
 */
public class ArchUnitBazelSupport {

    static {
        setupBazelSymlink();
    }

    private static void setupBazelSymlink() {
        // Check if we're running under Bazel (TEST_SRCDIR environment variable is set)
        String testSrcDir = System.getenv("TEST_SRCDIR");
        if (testSrcDir == null) {
            // Not running under Bazel, no setup needed
            return;
        }

        try {
            // Detect module from TEST_TARGET environment variable (e.g.,
            // "//flink-formats/flink-json:tests_...")
            String testTarget = System.getenv("TEST_TARGET");
            String modulePath = "flink-core"; // default

            if (testTarget != null && testTarget.startsWith("//")) {
                // Extract module path from target (e.g., "//flink-formats/flink-json:tests" ->
                // "flink-formats/flink-json")
                int colonIndex = testTarget.indexOf(':');
                if (colonIndex > 2) {
                    modulePath = testTarget.substring(2, colonIndex);
                }
            }

            // In Bazel, archunit-violations is in the runfiles at:
            // $TEST_SRCDIR/_main/<module-path>/archunit-violations
            Path runfilesViolations =
                    Paths.get(testSrcDir, "_main", modulePath, "archunit-violations");
            Path workingDirViolations = Paths.get("archunit-violations");

            // Only copy if it doesn't already exist and the runfiles directory exists
            if (Files.exists(runfilesViolations) && !Files.exists(workingDirViolations)) {
                // Copy directory and make files writable (symlinking would make them read-only)
                copyDirectory(runfilesViolations, workingDirViolations);
            }
        } catch (IOException e) {
            // Log but don't fail - tests might still work without the copy
            System.err.println(
                    "Warning: Failed to copy archunit-violations for Bazel: " + e.getMessage());
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .forEach(
                        sourcePath -> {
                            try {
                                Path targetPath = target.resolve(source.relativize(sourcePath));
                                if (Files.isDirectory(sourcePath)) {
                                    if (!Files.exists(targetPath)) {
                                        Files.createDirectory(targetPath);
                                    }
                                } else {
                                    Files.copy(sourcePath, targetPath);
                                    // Make writable so ArchUnit can update violation stores
                                    targetPath.toFile().setWritable(true);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    /** Trigger static initialization to set up Bazel support. */
    public static void init() {
        // Static initializer will run
    }
}
