---
title: 学习 Table API
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

# 学习 Table API

{{< hint warning >}}
**注意:** 此页面是占位符。内容将作为文档重构工作的一部分添加。
{{< /hint >}}

Table API 是 Flink 用于在数据流上表达关系操作的声明式 API。它为批处理和流处理提供了统一的接口，允许您编写可在有界和无界数据上无缝工作的查询。

## 为什么使用 Table API？

Table API 提供了以下几个优势：

- **声明式**: 专注于*想要*计算什么，而不是*如何*计算
- **优化**: Flink 的优化器自动选择高效的执行计划
- **统一**: 相同的 API 可用于批处理和流处理工作负载
- **可互操作**: 在需要时可以轻松地在 Table API 和 DataStream API 之间转换

## 入门

有关 Table API 的全面介绍，请参阅 [Table API 文档]({{< ref "docs/dev/table/overview" >}})。

## 下一步

学习 Table API 基础知识后，继续：

- [数据管道 & ETL]({{< ref "docs/learn-flink/etl" >}}) - 学习如何构建数据管道
- [DataStream API 简介]({{< ref "docs/learn-flink/datastream_api" >}}) - 对流处理应用程序进行更低级别的控制
