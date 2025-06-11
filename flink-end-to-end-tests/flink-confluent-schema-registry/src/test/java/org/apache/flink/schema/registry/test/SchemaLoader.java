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

package org.apache.flink.schema.registry.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Utility class for loading Avro schema files. */
public class SchemaLoader {

    /**
     * Loads an Avro schema from a resource file, extracting only the JSON content and ignoring any
     * comments.
     *
     * @param resourcePath the path to the schema resource file
     * @return the schema as a JSON string
     */
    public static String loadSchema(String resourcePath) {
        try (InputStream inputStream =
                SchemaLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Schema file not found: " + resourcePath);
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean inJsonBlock = false;
                int braceCount = 0;

                while ((line = reader.readLine()) != null) {
                    String trimmedLine = line.trim();

                    // Skip empty lines and comments
                    if (trimmedLine.isEmpty()
                            || trimmedLine.startsWith("/*")
                            || trimmedLine.startsWith("*")
                            || trimmedLine.startsWith("//")) {
                        continue;
                    }

                    // Start of JSON block
                    if (trimmedLine.startsWith("{")) {
                        inJsonBlock = true;
                    }

                    // If we're in the JSON block, add the line and count braces
                    if (inJsonBlock) {
                        content.append(line).append("\n");

                        // Count opening and closing braces to know when we're done
                        for (char c : line.toCharArray()) {
                            if (c == '{') {
                                braceCount++;
                            } else if (c == '}') {
                                braceCount--;
                            }
                        }

                        // If braceCount reaches 0, we've closed all braces - end of JSON
                        if (braceCount == 0) {
                            break;
                        }
                    }
                }
            }

            String schema = content.toString().trim();
            if (schema.isEmpty()) {
                throw new RuntimeException("No JSON schema found in file: " + resourcePath);
            }

            return schema;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load schema from " + resourcePath, e);
        }
    }
}
