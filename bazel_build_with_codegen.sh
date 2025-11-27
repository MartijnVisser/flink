#!/usr/bin/env bash

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

###############################################################################
# Bazel Build with Code Generation
#
# This script handles building flink-table-planner which requires Maven-generated
# Immutables sources due to circular dependencies (Scala ↔ Java ↔ Generated).
#
# Usage:
#   ./bazel_build_with_codegen.sh [bazel build args...]
#
# Examples:
#   ./bazel_build_with_codegen.sh //flink-table/flink-table-planner:flink-table-planner
#   ./bazel_build_with_codegen.sh //flink-dist:flink-distribution
#   ./bazel_build_with_codegen.sh //...
#
# What this script does:
# 1. Checks if Immutables-generated sources exist
# 2. If not, runs Maven to generate them (one-time operation)
# 3. Runs the Bazel build
#
# Note: Generated sources are gitignored and never committed.
###############################################################################

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Check if generated sources exist
GENERATED_DIR="flink-table/flink-table-planner/src/main/java-generated"
EXPECTED_FILES=60

echo -e "${YELLOW}Checking for Immutables-generated sources...${NC}"

if [ ! -d "$GENERATED_DIR" ] || [ "$(find "$GENERATED_DIR" -name '*.java' | wc -l)" -lt "$EXPECTED_FILES" ]; then
    echo -e "${YELLOW}Generated sources not found or incomplete. Generating via Maven...${NC}"
    echo -e "${YELLOW}(This is a one-time operation that takes ~2 minutes)${NC}"

    # Generate sources using Maven
    # Build from flink-table parent to ensure proper dependency resolution
    cd flink-table

    echo -e "${YELLOW}Building flink-sql-parser and flink-table-planner...${NC}"
    if ! mvn compile -DskipTests -Drat.skip=true -Dcheckstyle.skip=true -Denforcer.skip=true \
        -pl flink-sql-parser,flink-table-planner -am 2>&1 | grep -E "(Building|SUCCESS|FAILURE|ERROR)"; then
        echo -e "${RED}❌ Maven code generation failed!${NC}"
        exit 1
    fi

    # Copy generated sources to expected location
    if [ -d "flink-table-planner/target/generated-sources/annotations" ]; then
        echo -e "${GREEN}Copying generated sources...${NC}"
        rm -rf "flink-table-planner/src/main/java-generated"
        cp -r "flink-table-planner/target/generated-sources/annotations" "flink-table-planner/src/main/java-generated"

        GENERATED_COUNT=$(find flink-table-planner/src/main/java-generated -name '*.java' | wc -l)
        echo -e "${GREEN}✅ Generated $GENERATED_COUNT source files${NC}"
    else
        echo -e "${RED}❌ Maven did not generate expected sources!${NC}"
        exit 1
    fi

    cd ..
else
    EXISTING_COUNT=$(find "$GENERATED_DIR" -name '*.java' | wc -l)
    echo -e "${GREEN}✅ Found $EXISTING_COUNT generated source files (skipping Maven)${NC}"
fi

# Run Bazel build
echo -e "${YELLOW}Running Bazel build...${NC}"
if bazel build "$@"; then
    echo -e "${GREEN}✅ Bazel build completed successfully!${NC}"
else
    echo -e "${RED}❌ Bazel build failed!${NC}"
    exit 1
fi
