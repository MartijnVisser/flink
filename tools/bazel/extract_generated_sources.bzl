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
Custom Bazel rule to extract generated sources from java_library annotation processing.

This rule extracts the -gensrc.jar that Bazel creates when using java_plugin for
annotation processing, making it available as a srcjar that can be consumed by
other compilation targets.
"""

def _extract_generated_sources_impl(ctx):
    """Implementation of the extract_generated_sources rule.

    Since Bazel doesn't expose the -gensrc.jar as a default output, we use
    an aspect to locate it in the java_library's compilation outputs.

    Args:
        ctx: The rule context

    Returns:
        DefaultInfo with the extracted srcjar
    """
    # The java_target was processed by our aspect, which added the -gensrc.jar to OutputGroupInfo
    if OutputGroupInfo in ctx.attr.java_target:
        output_group_info = ctx.attr.java_target[OutputGroupInfo]
        if hasattr(output_group_info, "_gen_sources"):
            gen_sources = output_group_info._gen_sources.to_list()
            if len(gen_sources) > 0:
                gensrc_jar = gen_sources[0]

                # Copy the gensrc jar to our output
                output = ctx.actions.declare_file(ctx.label.name + ".srcjar")
                ctx.actions.run_shell(
                    inputs = [gensrc_jar],
                    outputs = [output],
                    command = "cp {} {}".format(gensrc_jar.path, output.path),
                    mnemonic = "ExtractGeneratedSources",
                    progress_message = "Extracting generated sources from {}".format(gensrc_jar.short_path),
                )

                return [DefaultInfo(files = depset([output]))]

    fail("Could not find generated sources jar from java_library target. " +
         "Make sure the java_library uses a java_plugin for annotation processing.")

def _gen_sources_aspect_impl(target, ctx):
    """Aspect implementation that finds the -gensrc.jar in java_library outputs.

    This aspect looks through the OutputGroupInfo of a java_library target to find
    the -gensrc.jar file created by annotation processing.

    Args:
        target: The target being aspected
        ctx: The aspect context

    Returns:
        OutputGroupInfo with _gen_sources output group
    """
    # Look for the -gensrc.jar in the target's output groups
    if JavaInfo in target:
        # Check if there's an OutputGroupInfo with _direct_source_jars
        if OutputGroupInfo in target:
            output_groups = target[OutputGroupInfo]

            # Try to find -gensrc.jar in _direct_source_jars output group
            if hasattr(output_groups, "_direct_source_jars"):
                for jar in output_groups._direct_source_jars.to_list():
                    if jar.path.endswith("-gensrc.jar"):
                        return [OutputGroupInfo(_gen_sources = depset([jar]))]

        # Alternative: look through compilation_info if available
        # This is where javac stores its outputs
        java_info = target[JavaInfo]
        if hasattr(java_info, "compilation_info"):
            comp_info = java_info.compilation_info
            if hasattr(comp_info, "javac_outputs"):
                for output in comp_info.javac_outputs:
                    if hasattr(output, "generated_source_jar") and output.generated_source_jar:
                        return [OutputGroupInfo(_gen_sources = depset([output.generated_source_jar]))]

    return []

_gen_sources_aspect = aspect(
    implementation = _gen_sources_aspect_impl,
    attr_aspects = [],
)

extract_generated_sources = rule(
    implementation = _extract_generated_sources_impl,
    attrs = {
        "java_target": attr.label(
            doc = "The java_library target that generates sources via annotation processing",
            mandatory = True,
            providers = [JavaInfo],
            aspects = [_gen_sources_aspect],
        ),
    },
    doc = """
    Extracts generated sources from a java_library that uses annotation processing.

    This rule uses an aspect to access the -gensrc.jar file that Bazel automatically
    creates when java_plugin is used. The extracted jar is made available as a srcjar
    that can be consumed by other targets.

    Example:
        java_library(
            name = "lib-with-annotations",
            srcs = ["Foo.java"],
            plugins = [":annotation_processor"],
        )

        extract_generated_sources(
            name = "generated-sources",
            java_target = ":lib-with-annotations",
        )

        scala_library(
            name = "final-lib",
            srcs = ["Bar.scala"] + [":generated-sources"],
        )
    """,
)
