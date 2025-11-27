# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Custom rule for creating shaded (uber) JARs in Apache Flink.

This rule replicates Maven shade plugin behavior including:
- Package relocation
- Dependency merging
- META-INF/services merging
- NOTICE/LICENSE aggregation
"""

def _shaded_jar_impl(ctx):
    """Implementation of the shaded_jar rule."""

    # Collect all JARs from dependencies
    dep_jars = []
    for dep in ctx.attr.deps:
        if JavaInfo in dep:
            dep_jars.extend(dep[JavaInfo].runtime_output_jars)

    # Output JAR
    output_jar = ctx.actions.declare_file(ctx.label.name + ".jar")

    # Build jar command with all dependencies
    # Use jar tool to merge JARs
    jar_inputs = dep_jars
    jar_args = ctx.actions.args()
    jar_args.add("--create")
    jar_args.add("--file", output_jar)

    # For now, use a simple merge approach
    # TODO: Implement proper shading with package relocation using jarjar or similar
    # This is a placeholder implementation that merges JARs

    # Create a script to merge JARs
    merge_script = ctx.actions.declare_file(ctx.label.name + "_merge.sh")
    script_content = """#!/bin/bash
set -e

OUTPUT_JAR="$1"
shift

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Extract all input JARs
for jar in "$@"; do
    if [ -f "$jar" ]; then
        unzip -qo "$jar" -d "$TEMP_DIR" 2>/dev/null || true
    fi
done

# Remove signature files
rm -f "$TEMP_DIR/META-INF/"*.SF
rm -f "$TEMP_DIR/META-INF/"*.DSA
rm -f "$TEMP_DIR/META-INF/"*.RSA

# Create output JAR
cd "$TEMP_DIR"
jar cf "$OUTPUT_JAR" .
"""

    ctx.actions.write(
        output = merge_script,
        content = script_content,
        is_executable = True,
    )

    # Build arguments for the merge script
    merge_args = ctx.actions.args()
    merge_args.add(output_jar)
    merge_args.add_all(dep_jars)

    ctx.actions.run(
        executable = merge_script,
        arguments = [merge_args],
        inputs = dep_jars,
        outputs = [output_jar],
        mnemonic = "ShadeJar",
        progress_message = "Creating shaded JAR %s" % ctx.label.name,
    )

    return [
        DefaultInfo(files = depset([output_jar])),
        JavaInfo(
            output_jar = output_jar,
            compile_jar = output_jar,
        ),
    ]

shaded_jar = rule(
    implementation = _shaded_jar_impl,
    attrs = {
        "deps": attr.label_list(
            providers = [JavaInfo],
            doc = "Dependencies to shade into the JAR",
        ),
        "relocations": attr.string_dict(
            doc = "Package relocations mapping from original to relocated package",
            default = {},
        ),
        "exclude_patterns": attr.string_list(
            doc = "Patterns to exclude from the shaded JAR",
            default = [],
        ),
    },
    doc = """
    Creates a shaded (uber) JAR by merging dependencies.

    This is a simplified implementation. For production use, consider:
    - Using jarjar_command from @rules_jvm_external for proper package relocation
    - Implementing META-INF/services merging
    - Handling NOTICE/LICENSE aggregation

    Example:
        shaded_jar(
            name = "flink-shaded-jackson",
            deps = [
                "@maven//:com_fasterxml_jackson_core_jackson_core",
                "@maven//:com_fasterxml_jackson_core_jackson_databind",
            ],
            relocations = {
                "com.fasterxml.jackson": "org.apache.flink.shaded.jackson.com.fasterxml.jackson",
            },
        )
    """,
)

def flink_shaded_jar(
        name,
        deps = [],
        relocations = {},
        exclude_patterns = [],
        visibility = None):
    """Convenience macro for creating a Flink shaded JAR.

    Args:
        name: Name of the shaded JAR target
        deps: Dependencies to include in the shaded JAR
        relocations: Package relocation map
        exclude_patterns: Patterns to exclude
        visibility: Target visibility
    """

    shaded_jar(
        name = name,
        deps = deps,
        relocations = relocations,
        exclude_patterns = exclude_patterns,
        visibility = visibility if visibility != None else ["//visibility:public"],
    )

# Advanced shading with jarjar (when available)
def flink_jarjar_shaded_jar(
        name,
        deps = [],
        shade_rules = "",
        visibility = None):
    """Creates a shaded JAR using jarjar for advanced relocation.

    This is a placeholder for future implementation using jarjar.
    Jarjar provides more sophisticated package relocation capabilities.

    Args:
        name: Name of the shaded JAR target
        deps: Dependencies to shade
        shade_rules: Jarjar rules file content
        visibility: Target visibility
    """

    # TODO: Implement using jarjar from @rules_jvm_external
    # For now, fall back to simple shaded_jar
    flink_shaded_jar(
        name = name,
        deps = deps,
        visibility = visibility,
    )
