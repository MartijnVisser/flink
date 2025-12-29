---
title: Learn the Table API
weight: 3
type: docs
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Learn the Table API

{{< hint warning >}}
**Note:** This page is a placeholder. Content will be added as part of the documentation restructuring effort.
{{< /hint >}}

The Table API is Flink's declarative API for expressing relational operations on data streams. It provides a unified interface for batch and stream processing, allowing you to write queries that work seamlessly on both bounded and unbounded data.

## Why Use the Table API?

The Table API offers several advantages:

- **Declarative**: Focus on *what* you want to compute, not *how* to compute it
- **Optimized**: Flink's optimizer automatically selects efficient execution plans
- **Unified**: The same API works for both batch and streaming workloads
- **Interoperable**: Easily convert between Table API and DataStream API when needed

## Getting Started

For a comprehensive introduction to the Table API, see the [Table API documentation]({{< ref "docs/dev/table/overview" >}}).

## Next Steps

After learning the basics of the Table API, continue with:

- [Data Pipelines & ETL]({{< ref "docs/learn-flink/etl" >}}) - Learn how to build data pipelines
- [Intro to the DataStream API]({{< ref "docs/learn-flink/datastream_api" >}}) - For lower-level control over your streaming applications
