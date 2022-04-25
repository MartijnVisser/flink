#!/usr/bin/env bash
################################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

# Create a default go.mod file
pipelinerun="$1"

# Determine if script is called by pipeline to determine correct path for go.mod file
if [[ $pipelinerun == "pipeline" ]]; then
  echo "Setup for pipeline"
  goModFileLocation=$(pwd)/docs
else
  echo "Setup documentation locally"
  goModFileLocation=$PWD
fi

cat <<EOF >$goModFileLocation/go.mod
module github.com/apache/flink

go 1.18
EOF

echo "Created temporary file" $goModFileLocation/go.mod

# Make Hugo retrieve modules which are used for externally hosted documentation
currentBranch=$(git branch --show-current)

if [[ ! "$currentBranch" =~ ^release- ]] || [[ -z "$currentBranch" ]]; then
  # If the current branch is master or not provided, get the documentation from the main branch
  if [[ $pipelinerun == "pipeline" ]]; then
    cd docs
    $(command -v hugo) mod get -u github.com/apache/flink-connector-elasticsearch/docs@main
    cd ..
  else
    $(command -v hugo) mod get -u github.com/apache/flink-connector-elasticsearch/docs@main
  fi
  # Since there's no documentation yet available for a release branch,
  # we only get the documentation from the main branch
fi
