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

# Apache Flink Bazel Workspace
# This workspace defines the Bazel build for Apache Flink

workspace(name = "flink")

# Load Bazel Skylib - utility functions for Bazel
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Skylib
http_archive(
    name = "bazel_skylib",
    sha256 = "74d544d96f4a5bb630d465ca8bbcfe231e3594e5aae57e1edbf17a6eb3ca2506",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

# Bazel Runfiles library for accessing test resources
# This is provided by Bazel itself via @bazel_tools
# We just need to reference it in test dependencies

# Rules for JVM (Java) dependencies
http_archive(
    name = "rules_jvm_external",
    sha256 = "f86fd42a809e1871ca0aabe89db0d440451219c3ce46c58da240c7dcdc00125f",
    strip_prefix = "rules_jvm_external-5.2",
    url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/5.2/rules_jvm_external-5.2.tar.gz",
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

# Scala Rules (6.6.0 supports Scala 2.12.20)
http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "e734eef95cf26c0171566bdc24d83bd82bdaf8ca7873bec6ce9b0d524bdaf05d",
    strip_prefix = "rules_scala-6.6.0",
    url = "https://github.com/bazelbuild/rules_scala/releases/download/v6.6.0/rules_scala-v6.6.0.tar.gz",
)

load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")

scala_config(scala_version = "2.12.20")

load("@io_bazel_rules_scala//scala:scala.bzl", "rules_scala_setup", "scala_repositories")
load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

rules_scala_setup()

# Manually configure Scala 2.12.20 repositories
# This bypasses the version check in rules_scala which only supports 2.12.18
scala_repositories(
    overriden_artifacts = {
        "io_bazel_rules_scala_scala_compiler": {
            "artifact": "org.scala-lang:scala-compiler:2.12.20",
            "sha256": "c88676d75c69721b717ea6c441ece04fff262abab9d210a2936abc2be3731fa2",
        },
        "io_bazel_rules_scala_scala_library": {
            "artifact": "org.scala-lang:scala-library:2.12.20",
            "sha256": "4d8a8f984cce31a329a24f10b0bf336f042cb62aeb435290a1b20243154cfccb",
        },
        "io_bazel_rules_scala_scala_reflect": {
            "artifact": "org.scala-lang:scala-reflect:2.12.20",
            "sha256": "5f1914cdc7a70580ea6038d929ebb25736ecf2234f677e2d47f8a4b2bc81e1fb",
        },
    },
)

scala_register_toolchains()

# ANTLR Rules
http_archive(
    name = "rules_antlr",
    sha256 = "26e6a83c665cf6c1093b628b3a749071322f0f70305d12ede30909695ed85591",
    strip_prefix = "rules_antlr-0.5.0",
    urls = ["https://github.com/marcohu/rules_antlr/archive/0.5.0.tar.gz"],
)

load("@rules_antlr//antlr:repositories.bzl", "rules_antlr_dependencies")

rules_antlr_dependencies("4.7.2")

# Docker Rules
http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "b1e80761a8a8243d03ebca8845e9cc1ba6c82ce7c5179ce2b295cd36f7e394bf",
    urls = ["https://github.com/bazelbuild/rules_docker/releases/download/v0.25.0/rules_docker-v0.25.0.tar.gz"],
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//repositories:deps.bzl", container_deps = "deps")

container_deps()

load(
    "@io_bazel_rules_docker//java:image.bzl",
    _java_image_repos = "repositories",
)

_java_image_repos()

# Node.js Rules for Web UI frontend
http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "709cc0dcb51cf9028dd57c268066e5bc8f03a119ded410a13b5c3925d6e43c48",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/5.8.4/rules_nodejs-5.8.4.tar.gz"],
)

load("@build_bazel_rules_nodejs//:repositories.bzl", "build_bazel_rules_nodejs_dependencies")

build_bazel_rules_nodejs_dependencies()

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories")

node_repositories(
    node_version = "22.16.0",
)

# Protobuf Rules
http_archive(
    name = "rules_pkg",
    sha256 = "8f9ee2dc10c1ae514ee599a8b42ed99fa262b757058f65ad3c384289ff70c4b8",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_pkg/releases/download/0.9.1/rules_pkg-0.9.1.tar.gz",
        "https://github.com/bazelbuild/rules_pkg/releases/download/0.9.1/rules_pkg-0.9.1.tar.gz",
    ],
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

http_archive(
    name = "rules_proto",
    sha256 = "303e86e722a520f6f326a50b41cfc16b98fe6d1955ce46642a5b7a67c11c0f5d",
    strip_prefix = "rules_proto-6.0.0",
    url = "https://github.com/bazelbuild/rules_proto/releases/download/6.0.0/rules_proto-6.0.0.tar.gz",
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies")

rules_proto_dependencies()

http_archive(
    name = "com_google_protobuf",
    sha256 = "1535151efbc7893f38b0578e83cac584f2819974f065698976989ec71c1af84a",
    strip_prefix = "protobuf-27.3",
    urls = [
        "https://github.com/protocolbuffers/protobuf/releases/download/v27.3/protobuf-27.3.tar.gz",
    ],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

# Protoc compiler binary (version matching pom.xml protoc.version = 4.32.1)
# Note: protoc version 4.x corresponds to protobuf library version 27.3 in newer releases
# We download platform-specific binaries and select the right one based on the platform

# Linux x86_64 protoc binary
http_archive(
    name = "protoc_binary_linux_x86_64",
    build_file_content = """
exports_files(["bin/protoc"])
""",
    sha256 = "6dab2adab83f915126cab53540d48957c40e9e9023969c3e84d44bfb936c7741",
    urls = [
        "https://github.com/protocolbuffers/protobuf/releases/download/v27.3/protoc-27.3-linux-x86_64.zip",
    ],
)

# macOS ARM64 protoc binary
http_archive(
    name = "protoc_binary_macos_aarch64",
    build_file_content = """
exports_files(["bin/protoc"])
""",
    sha256 = "b22116bd97cdbd7ea25346abe635a9df268515fe5ef5afa93cd9a68fc2513f84",
    urls = [
        "https://github.com/protocolbuffers/protobuf/releases/download/v27.3/protoc-27.3-osx-aarch_64.zip",
    ],
)

# macOS x86_64 protoc binary
http_archive(
    name = "protoc_binary_macos_x86_64",
    build_file_content = """
exports_files(["bin/protoc"])
""",
    sha256 = "ce282648fed0e7fbd6237d606dc9ec168dd2c1863889b04efa0b19c47da65d1b",
    urls = [
        "https://github.com/protocolbuffers/protobuf/releases/download/v27.3/protoc-27.3-osx-x86_64.zip",
    ],
)

# Maven Dependencies
# Note: This section will be populated with all Maven dependencies from pom.xml files
# The dependencies are organized to match the Maven dependency management structure

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        # Version overrides for conflicting transitive dependencies
        "net.minidev:json-smart:2.4.11",  # Resolve conflict between Calcite and Hadoop

        # Core dependencies
        "org.slf4j:slf4j-api:1.7.36",
        "com.google.code.findbugs:jsr305:1.3.9",
        "com.typesafe:config:1.4.2",

        # Logging - Log4j2
        "org.apache.logging.log4j:log4j-slf4j-impl:2.24.3",
        "org.apache.logging.log4j:log4j-api:2.24.3",
        "org.apache.logging.log4j:log4j-core:2.24.3",
        "org.apache.logging.log4j:log4j-1.2-api:2.24.3",
        "org.apache.logging.log4j:log4j-layout-template-json:2.24.3",

        # Apache Commons
        "org.apache.commons:commons-lang3:3.18.0",
        "org.apache.commons:commons-text:1.10.0",
        "commons-io:commons-io:2.15.1",
        "commons-cli:commons-cli:1.5.0",
        "commons-codec:commons-codec:1.15",
        "commons-collections:commons-collections:3.2.2",
        "commons-configuration:commons-configuration:1.7",
        "commons-logging:commons-logging:1.1.3",
        "org.apache.commons:commons-math3:3.6.1",
        "org.apache.commons:commons-compress:1.26.0",

        # Jackson (managed via BOM, explicit versions for Bazel)
        "com.fasterxml.jackson.core:jackson-core:2.18.2",
        "com.fasterxml.jackson.core:jackson-databind:2.18.2",
        "com.fasterxml.jackson.core:jackson-annotations:2.18.2",

        # Compression
        "org.xerial.snappy:snappy-java:1.1.10.7",
        "org.lz4:lz4-java:1.8.0",
        "com.github.luben:zstd-jni:1.4.9-1",

        # Embedded databases
        "com.ververica:frocksdbjni:8.10.0-ververica-1.0",
        "com.ververica:forstjni:0.1.8",

        # Serialization
        "com.esotericsoftware:kryo:5.6.2",
        "com.esotericsoftware:minlog:1.3.1",
        "org.objenesis:objenesis:3.4",
        "org.apache.avro:avro:1.11.4",
        "org.apache.avro:avro-tools:1.11.4",  # For Avro code generation via genrule
        "io.confluent:kafka-schema-registry-client:7.5.3",

        # Zookeeper & Curator
        "org.apache.zookeeper:zookeeper:3.7.2",
        "org.apache.curator:curator-recipes:5.4.0",
        "org.apache.curator:curator-framework:5.4.0",
        "org.apache.curator:curator-client:5.4.0",
        "org.apache.curator:curator-test:5.4.0",

        # Scala
        "org.scala-lang:scala-library:2.12.20",
        "org.scala-lang:scala-reflect:2.12.20",
        "org.scala-lang:scala-compiler:2.12.20",
        "org.scala-lang.modules:scala-xml_2.12:2.2.0",

        # Netty (transitive, explicit for clarity)
        "io.netty:netty-all:4.1.100.Final",
        "io.netty:netty-handler:4.1.100.Final",
        "io.netty:netty-transport:4.1.100.Final",
        "io.netty:netty-common:4.1.100.Final",

        # Apache Pekko (Apache fork of Akka for RPC)
        "org.apache.pekko:pekko-actor_2.12:1.1.2",
        "org.apache.pekko:pekko-remote_2.12:1.1.2",
        "org.apache.pekko:pekko-slf4j_2.12:1.1.2",
        "org.apache.pekko:pekko-stream_2.12:1.1.2",
        "io.netty:netty-codec:4.1.100.Final",
        "io.netty:netty-buffer:4.1.100.Final",

        # Metrics
        "com.github.oshi:oshi-core:6.1.5",

        # HTTP
        "org.apache.httpcomponents:httpcore:4.4.14",
        "org.apache.httpcomponents:httpclient:4.5.13",
        "com.squareup.okhttp3:okhttp:3.14.9",
        "com.squareup.okhttp3:logging-interceptor:3.14.9",

        # Monitoring / Metrics
        "io.dropwizard.metrics:metrics-core:3.2.6",
        "io.dropwizard.metrics:metrics-graphite:3.2.6",
        "io.opentelemetry:opentelemetry-api:1.30.1",
        "io.opentelemetry:opentelemetry-context:1.30.1",
        "io.opentelemetry:opentelemetry-exporter-logging:1.30.1",
        "io.opentelemetry:opentelemetry-exporter-otlp:1.30.1",
        "io.opentelemetry:opentelemetry-sdk:1.30.1",
        "io.opentelemetry:opentelemetry-sdk-common:1.30.1",
        "io.opentelemetry:opentelemetry-sdk-logs:1.30.1",
        "io.opentelemetry:opentelemetry-sdk-metrics:1.30.1",
        "io.opentelemetry:opentelemetry-sdk-trace:1.30.1",
        "io.opentelemetry:opentelemetry-semconv:1.30.1-alpha",
        "io.prometheus:simpleclient:0.8.1",
        "io.prometheus:simpleclient_common:0.8.1",
        "io.prometheus:simpleclient_httpserver:0.8.1",
        "io.prometheus:simpleclient_pushgateway:0.8.1",
        "org.influxdb:influxdb-java:2.17",

        # YAML
        "org.snakeyaml:snakeyaml-engine:2.6",
        "org.yaml:snakeyaml:2.3",

        # Code generation
        "org.antlr:antlr4-runtime:4.7.2",
        "net.sourceforge.fmpp:fmpp:0.9.16",  # FreeMarker-based preprocessor for SQL parser
        "org.freemarker:freemarker:2.3.32",  # FreeMarker template engine (FMPP dependency)
        "org.apache-extras.beanshell:bsh:2.0b6",  # BeanShell (FMPP dependency)
        "net.java.dev.javacc:javacc:4.0",  # JavaCC parser generator

        # Utilities
        "com.ibm.icu:icu4j:67.1",
        "net.bytebuddy:byte-buddy:1.17.6",
        "net.bytebuddy:byte-buddy-agent:1.17.6",
        "com.google.errorprone:error_prone_annotations:2.18.0",
        "net.java.dev.jna:jna:5.12.1",
        "org.objenesis:objenesis:3.4",
        "org.javassist:javassist:3.24.0-GA",
        "joda-time:joda-time:2.5",
        "org.joda:joda-convert:1.7",

        # JAXB/Activation (for Java 11+)
        "javax.xml.bind:jaxb-api:2.3.1",
        "javax.activation:javax.activation-api:1.2.0",
        "jakarta.activation:jakarta.activation-api:1.2.1",
        "jakarta.xml.bind:jakarta.xml.bind-api:2.3.2",

        # Hadoop (optional dependencies)
        # Note: S3 filesystem modules require Hadoop 3.3.4, handled via separate maven_install
        "org.apache.hadoop:hadoop-common:2.10.2",
        "org.apache.hadoop:hadoop-hdfs:2.10.2",
        "org.apache.hadoop:hadoop-mapreduce-client-core:2.10.2",
        "org.apache.hadoop:hadoop-yarn-api:2.10.2",
        "org.apache.hadoop:hadoop-yarn-common:2.10.2",
        "org.apache.hadoop:hadoop-yarn-client:2.10.2",
        "org.apache.hadoop:hadoop-aws:2.10.2",
        "org.apache.hadoop:hadoop-minikdc:3.2.4",

        # AWS SDK (for S3 filesystem support)
        "com.amazonaws:aws-java-sdk-core:1.12.779",
        "com.amazonaws:aws-java-sdk-s3:1.12.779",
        "com.amazonaws:aws-java-sdk-kms:1.12.779",
        "com.amazonaws:aws-java-sdk-dynamodb:1.12.779",
        "com.amazonaws:aws-java-sdk-sts:1.12.779",

        # Testing dependencies
        "junit:junit:4.13.2",
        "org.junit.jupiter:junit-jupiter:5.11.4",
        "org.junit.jupiter:junit-jupiter-api:5.11.4",
        "org.junit.jupiter:junit-jupiter-engine:5.11.4",
        "org.junit.jupiter:junit-jupiter-params:5.11.4",
        "org.junit.platform:junit-platform-commons:1.11.4",
        "org.junit.platform:junit-platform-console:1.11.4",
        "org.junit.platform:junit-platform-engine:1.11.4",
        "org.junit.vintage:junit-vintage-engine:5.11.4",
        "org.opentest4j:opentest4j:1.3.0",
        "org.assertj:assertj-core:3.27.3",
        "org.mockito:mockito-core:5.19.0",
        "org.mockito:mockito-junit-jupiter:5.19.0",
        "org.mockito:mockito-subclass:5.19.0",
        "org.hamcrest:hamcrest-all:1.3",
        "org.hamcrest:hamcrest-core:1.3",
        "org.testcontainers:testcontainers:1.20.2",
        "org.testcontainers:junit-jupiter:1.20.2",
        "org.projectlombok:lombok:1.18.42",

        # Scala testing
        "org.scalatest:scalatest_2.12:3.0.0",

        # Architecture testing
        "com.tngtech.archunit:archunit:1.4.1",
        "com.tngtech.archunit:archunit-junit5:1.4.1",
        "com.tngtech.archunit:archunit-junit5-api:1.4.1",

        # Reflection/Analysis
        "org.reflections:reflections:0.9.10",

        # JCIP Annotations (concurrency annotations)
        "com.github.stephenc.jcip:jcip-annotations:1.0-1",

        # API Guardian (for Calcite API annotations)
        "org.apiguardian:apiguardian-api:1.1.2",

        # Calcite (for Table API)
        "org.apache.calcite:calcite-core:1.36.0",
        "org.apache.calcite:calcite-linq4j:1.36.0",
        "org.apache.calcite.avatica:avatica-core:1.23.0",
        "org.codehaus.janino:janino:3.1.10",
        "org.codehaus.janino:commons-compiler:3.1.10",

        # Checker Framework (required by Guava and Calcite)
        "org.checkerframework:checker-qual:3.37.0",

        # Guava (for Calcite compatibility)
        "com.google.guava:guava:33.4.0-jre",

        # Quartz (for Table API)
        "org.quartz-scheduler:quartz:2.3.2",

        # Immutables (for Table Planner)
        "org.immutables:value:2.11.3",
        "org.immutables:value-annotations:2.11.3",

        # Async Profiler
        "tools.profiler:async-profiler:2.9",

        # Air Compressor
        "io.airlift:aircompressor:0.27",

        # Disruptor
        "com.lmax:disruptor:3.4.2",

        # Parquet
        "org.apache.parquet:parquet-hadoop:1.15.2",
        "org.apache.parquet:parquet-avro:1.15.2",
        "org.apache.parquet:parquet-column:1.15.2",
        "org.apache.parquet:parquet-common:1.15.2",
        "org.apache.parquet:parquet-encoding:1.15.2",
        "org.apache.parquet:parquet-format:2.12.0",
        "org.apache.parquet:parquet-jackson:1.15.2",
        "org.apache.parquet:parquet-protobuf:1.15.2",

        # Protobuf
        "com.google.protobuf:protobuf-java:4.32.1",
        "com.google.protobuf:protobuf-java-util:4.32.1",
        "com.google.api.grpc:proto-google-common-protos:2.9.0",

        # Twitter Elephant Bird (for Parquet protobuf support)
        "com.twitter.elephantbird:elephant-bird-core:4.17",

        # ORC
        "org.apache.orc:orc-core:1.5.6",
        "org.apache.orc:orc-core:jar:nohive:1.5.6",
        "org.apache.orc:orc-shims:1.5.6",
        "org.apache.hive:hive-storage-api:2.7.3",

        # Kubernetes client (Fabric8)
        "io.fabric8:kubernetes-client:7.3.1",
        "io.fabric8:kubernetes-client-api:7.3.1",
        "io.fabric8:kubernetes-httpclient-okhttp:7.3.1",
        "io.fabric8:kubernetes-model-apps:7.3.1",
        "io.fabric8:kubernetes-model-core:7.3.1",
        "io.fabric8:kubernetes-server-mock:7.3.1",  # For testing

        # Python integration
        "net.sf.py4j:py4j:0.10.9.7",
        "net.razorvine:pyrolite:4.13",

        # Apache Arrow
        "org.apache.arrow:arrow-vector:13.0.0",
        "org.apache.arrow:arrow-memory-netty:13.0.0",
        "org.apache.arrow:arrow-memory-core:13.0.0",
        "org.apache.arrow:arrow-format:13.0.0",

        # Apache Beam (version 2.54.0 as specified in Flink parent pom)
        "org.apache.beam:beam-runners-java-fn-execution:2.54.0",
        "org.apache.beam:beam-runners-core-java:2.54.0",
        "org.apache.beam:beam-runners-core-construction-java:2.54.0",
        "org.apache.beam:beam-sdks-java-core:2.54.0",
        "org.apache.beam:beam-sdks-java-fn-execution:2.54.0",
        "org.apache.beam:beam-model-fn-execution:2.54.0",
        "org.apache.beam:beam-model-pipeline:2.54.0",

        # Apache Beam vendor dependencies (shaded versions for flink-python)
        "org.apache.beam:beam-vendor-grpc-1_60_1:0.1",
        "org.apache.beam:beam-vendor-guava-32_1_2-jre:0.1",

        # Alibaba Pemja (Python-Java bridge)
        "com.alibaba:pemja:0.5.5",

        # JLine (CLI for SQL Client)
        "org.jline:jline-terminal:3.21.0",
        "org.jline:jline-reader:3.21.0",

        # OpenAI (for flink-model-openai)
        "com.openai:openai-java:1.6.1",
        "com.openai:openai-java-client-okhttp:1.6.1",
        "com.openai:openai-java-core:1.6.1",
        "com.knuddels:jtokkit:1.1.0",
    ],
    excluded_artifacts = [
        # Exclude log4j 1.x and its bridges to prevent conflicts with log4j 2.x
        "log4j:log4j",
        "org.slf4j:slf4j-log4j12",  # Banned - use log4j-1.2-api if needed
        "ch.qos.reload4j:reload4j",
        "org.slf4j:slf4j-reload4j",
        # Exclude OSGi core to avoid convergence issues
        "org.osgi:org.osgi.core",
        # Exclude jdk.tools (not available in modern JDKs)
        "jdk.tools:jdk.tools",
        # Exclude GPL-licensed hadoop-lzo (not in standard Maven repos)
        "com.hadoop.gplcompression:hadoop-lzo",
        # Exclude Avro trevni (pulled by avro-tools, but test JARs not in Maven Central)
        "org.apache.avro:trevni-avro",
        "org.apache.avro:trevni-core",
    ],
    fetch_sources = True,
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.repository.redhat.com/ga/",
        "https://repository.jboss.org/nexus/content/groups/public/",
        "https://packages.confluent.io/maven/",  # Confluent Schema Registry
    ],
    strict_visibility = True,
    version_conflict_policy = "pinned",
)

# Flink Shaded Dependencies
# These are pre-shaded artifacts published by the Flink project
# They will be referenced directly as Maven artifacts until we implement custom shading

maven_install(
    name = "flink_shaded",
    artifacts = [
        "org.apache.flink:flink-shaded-force-shading:20.0",
        "org.apache.flink:flink-shaded-asm-9:9.6-20.0",
        "org.apache.flink:flink-shaded-guava:33.4.0-jre-20.0",
        "org.apache.flink:flink-shaded-jackson:2.18.2-20.0",
        "org.apache.flink:flink-shaded-jackson-module-jsonSchema:2.18.2-20.0",
        "org.apache.flink:flink-shaded-jsonpath:2.9.0-20.0",
        "org.apache.flink:flink-shaded-netty:4.1.100.Final-20.0",
        "org.apache.flink:flink-shaded-netty-tcnative-dynamic:2.0.62.Final-20.0",
        "org.apache.flink:flink-shaded-swagger:20.0",
        "org.apache.flink:flink-shaded-zookeeper-3:3.7.2-20.0",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)

# S3 Filesystem Dependencies with Hadoop 3.3.4
# S3 filesystem modules require Hadoop 3.3.4 for S3A auth classes
# This separate maven_install allows S3 modules to use a different Hadoop version
# while other modules continue using Hadoop 2.10.2

maven_install(
    name = "hadoop_3_3_4",
    artifacts = [
        # Hadoop 3.3.4 for cloud filesystem support (S3, Azure, OSS, GS)
        "org.apache.hadoop:hadoop-common:3.3.4",
        "org.apache.hadoop:hadoop-aws:3.3.4",
        "org.apache.hadoop:hadoop-azure:3.3.4",
        "org.apache.hadoop:hadoop-aliyun:3.3.4",
        "org.apache.hadoop:hadoop-hdfs-client:3.3.4",

        # AWS SDK (same versions as main maven_install for consistency)
        "com.amazonaws:aws-java-sdk-core:1.12.779",
        "com.amazonaws:aws-java-sdk-s3:1.12.779",
        "com.amazonaws:aws-java-sdk-kms:1.12.779",
        "com.amazonaws:aws-java-sdk-dynamodb:1.12.779",
        "com.amazonaws:aws-java-sdk-sts:1.12.779",

        # Presto (for flink-s3-fs-presto)
        "com.facebook.presto:presto-hive:0.272",
        "com.facebook.presto.hadoop:hadoop-apache2:2.7.4-9",

        # Alibaba OSS SDK (for flink-oss-fs-hadoop)
        "com.aliyun.oss:aliyun-sdk-oss:3.17.4",

        # Google Cloud Storage SDK (for flink-gs-fs-hadoop)
        # Using versions from flink-gs-fs-hadoop/pom.xml
        "com.google.cloud:google-cloud-storage:2.29.1",
        "com.google.cloud.bigdataoss:gcs-connector:hadoop3-2.2.18",

        # Commons (needed by Hadoop)
        "commons-io:commons-io:2.15.1",
        "commons-logging:commons-logging:1.1.3",
        "org.apache.commons:commons-lang3:3.18.0",
        "commons-beanutils:commons-beanutils:1.9.4",

        # JAXB for Java 11+
        "javax.xml.bind:jaxb-api:2.3.1",

        # Logging
        "org.slf4j:slf4j-api:1.7.36",

        # JSR305
        "com.google.code.findbugs:jsr305:1.3.9",
    ],
    excluded_artifacts = [
        # Exclude log4j 1.x and its bridges to prevent conflicts with log4j 2.x
        "log4j:log4j",
        "org.slf4j:slf4j-log4j12",  # Banned - use log4j-1.2-api if needed
        "ch.qos.reload4j:reload4j",
        "org.slf4j:slf4j-reload4j",

        # Exclude grpc dependencies with circular dependency issues
        # See: https://github.com/grpc/grpc-java/issues/8853
        # The gcs-connector pulls in gRPC with circular deps, exclude them
        # and let grpc-netty-shaded provide the bundled implementation
        "io.grpc:grpc-core",
        "io.grpc:grpc-api",
        "io.grpc:grpc-context",
        "io.grpc:grpc-util",
        "io.grpc:grpc-stub",
        "io.grpc:grpc-protobuf",
        "io.grpc:grpc-protobuf-lite",
        "io.grpc:grpc-auth",
        "io.grpc:grpc-alts",
        "io.grpc:grpc-grpclb",
    ],
    fetch_sources = True,
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
    # Strict visibility prevents accidental use by non-S3 modules
    strict_visibility = True,
    version_conflict_policy = "pinned",
)
