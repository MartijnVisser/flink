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
Macros for creating Scala libraries in Apache Flink.

This file provides simplified macros for creating scala_library and scala_test
targets that follow Flink's conventions and match Maven scala-maven-plugin behavior.
"""

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_library", "scala_test")

def flink_scala_library(
        name,
        srcs = None,
        deps = [],
        exports = [],
        runtime_deps = [],
        resources = None,
        resource_strip_prefix = None,
        visibility = None,
        testonly = False,
        scalacopts = [],
        javacopts = [],
        **kwargs):
    """Creates a scala_library target for a Flink module.

    This macro simplifies the creation of Scala libraries by:
    - Auto-discovering Scala sources from src/main/scala
    - Auto-discovering Java sources from src/main/java (for mixed modules)
    - Setting standard Scala compiler options matching Maven
    - Configuring visibility appropriately

    Args:
        name: Name of the library target
        srcs: List of source files (both .scala and .java).
              If None, auto-discovers from src/main/scala and src/main/java
        deps: List of dependencies
        exports: List of exported dependencies
        runtime_deps: List of runtime-only dependencies
        resources: List of resource files
        resource_strip_prefix: Prefix to strip from resource paths
        visibility: Visibility specification
        testonly: Whether this library is only for tests
        scalacopts: Additional Scala compiler options
        javacopts: Additional Java compiler options (for Java files in mixed modules)
        **kwargs: Additional arguments passed to scala_library
    """

    # Default source discovery (both Scala and Java for mixed modules)
    if srcs == None:
        scala_srcs = native.glob(
            ["src/main/scala/**/*.scala"],
            exclude = ["src/main/scala/**/*Test.scala"],
        )
        java_srcs = native.glob(
            ["src/main/java/**/*.java"],
            exclude = ["src/main/java/**/*Test.java"],
        )
        srcs = scala_srcs + java_srcs

    # Default resource discovery
    if resources == None:
        resources = native.glob(
            ["src/main/resources/**/*"],
            exclude = ["src/main/resources/**/.*"],
            allow_empty = True,
        )

    # Default visibility
    if visibility == None:
        visibility = ["//visibility:public"]

    # Default resource strip prefix
    if resource_strip_prefix == None and len(resources) > 0:
        resource_strip_prefix = "src/main/resources"

    # Standard Flink scalacopts (matching Maven scala-maven-plugin)
    # -nobootcp: Don't use the boot classpath
    # -Xss2m: Set stack size to 2MB
    standard_scalacopts = [
        "-nobootcp",
        "-encoding",
        "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Xfatal-warnings",
        "-Xlint",
    ]

    # Standard javacopts for Java files in mixed modules
    standard_javacopts = [
        "-source",
        "11",
        "-target",
        "17",
        "-encoding",
        "UTF-8",
    ]

    # Add Scala library dependency
    scala_deps = [
        "@maven//:org_scala_lang_scala_library",
        "@maven//:org_scala_lang_scala_reflect",
    ] + deps

    scala_library(
        name = name,
        srcs = srcs,
        deps = scala_deps,
        exports = exports,
        runtime_deps = runtime_deps,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
        visibility = visibility,
        testonly = testonly,
        scalacopts = standard_scalacopts + scalacopts,
        javacopts = standard_javacopts + javacopts,
        **kwargs
    )

def flink_scala_tests(
        name = "tests",
        srcs = None,
        deps = [],
        runtime_deps = [],
        resources = None,
        resource_strip_prefix = None,
        size = "medium",
        tags = [],
        jvm_flags = [],
        scalacopts = [],
        **kwargs):
    """Creates scala_test targets for a Flink module.

    This macro simplifies Scala test creation by:
    - Auto-discovering test sources from src/test/scala
    - Adding standard test dependencies (ScalaTest, JUnit)
    - Configuring JVM flags matching Maven surefire

    Args:
        name: Name of the test target
        srcs: List of test source files. If None, auto-discovers Scala test files
        deps: List of test dependencies
        runtime_deps: List of runtime-only dependencies for tests
        resources: List of test resource files
        resource_strip_prefix: Prefix to strip from resource paths
        size: Test size (small, medium, large, enormous)
        tags: Tags for the test (e.g., ["unit"], ["integration"])
        jvm_flags: Additional JVM flags
        scalacopts: Additional Scala compiler options
        **kwargs: Additional arguments passed to scala_test
    """

    # Default test source discovery
    if srcs == None:
        srcs = native.glob(
            ["src/test/scala/**/*Test.scala", "src/test/scala/**/*Suite.scala"],
            exclude = [
                "src/test/scala/**/*ITCase.scala",  # Integration tests
                "src/test/scala/**/*TestBase.scala",  # Base classes
            ],
            allow_empty = True,
        )

    # Skip if no test sources found
    if not srcs:
        return

    # Default test resource discovery
    if resources == None:
        resources = native.glob(
            ["src/test/resources/**/*"],
            exclude = ["src/test/resources/**/.*"],
            allow_empty = True,
        )

    # Default resource strip prefix
    if resource_strip_prefix == None and len(resources) > 0:
        resource_strip_prefix = "src/test/resources"

    # Standard test dependencies (ScalaTest + JUnit for compatibility)
    standard_test_deps = [
        "@maven//:org_scala_lang_scala_library",
        "@maven//:org_scalatest_scalatest_2_12",
        "@maven//:junit_junit",
        "@maven//:org_junit_jupiter_junit_jupiter",
        "@maven//:org_assertj_assertj_core",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_apache_logging_log4j_log4j_slf4j_impl",
        "@maven//:org_apache_logging_log4j_log4j_api",
        "@maven//:org_apache_logging_log4j_log4j_core",
    ]

    # Standard JVM flags for tests (matching Maven surefire config)
    standard_jvm_flags = [
        "-XX:+UseG1GC",
        "-Xms256m",
        "-Xmx1536m",
        "-Xss2m",  # Stack size for Scala
        "-XX:+IgnoreUnrecognizedVMOptions",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    ]

    # Standard scalacopts
    standard_scalacopts = [
        "-nobootcp",
        "-encoding",
        "UTF-8",
    ]

    scala_test(
        name = name,
        srcs = srcs,
        deps = standard_test_deps + deps,
        runtime_deps = runtime_deps,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
        size = size,
        tags = ["unit", "scala"] + tags,
        jvm_flags = standard_jvm_flags + jvm_flags,
        scalacopts = standard_scalacopts + scalacopts,
        **kwargs
    )
