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
package org.apache.flink.table.planner.runtime.stream.sql

import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction}
import org.apache.flink.configuration.{Configuration, RestartStrategyOptions}
import org.apache.flink.core.execution.CheckpointingMode
import org.apache.flink.table.api.bridge.scala.tableConversions
import org.apache.flink.table.api.config.OptimizerConfigOptions
import org.apache.flink.table.planner.factories.TestValuesTableFactory
import org.apache.flink.table.planner.runtime.utils.{FailingCollectionSource, StreamingWithStateTestBase, TestData, TestingAppendSink}
import org.apache.flink.table.planner.runtime.utils.StreamingWithStateTestBase.ROCKSDB_BACKEND
import org.apache.flink.types.Row

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, RepeatedTest}

import java.time.Duration

/**
 * FLINK-39481 REPRO ONLY (do not merge): recreation of the CI failure conditions of
 * [[WindowDistinctAggregateITCase]] with [SplitDistinct=true, ROCKSDB].
 *
 * The CI failure signature (54 failing runs analysed): unaligned checkpoints with
 * aligned-checkpoint-timeout=0 and interruptible timers; downstream tasks go RUNNING ~200ms after
 * the source, so checkpoint 1 triggers right when the last task starts, captures the in-flight
 * records as channel state, and the FailingCollectionSource failure lands within 0-3ms of
 * checkpoint 1 completing.
 *
 * This test recreates those macro conditions on a fast machine by chaining a throttling map in
 * front of the sink: F39481_OPEN_DELAY_MS delays the sink task's switch to RUNNING (checkpoint 1
 * cannot trigger until then, and the emitted records queue in the network), F39481_ROW_DELAY_MS
 * keeps records in flight during the post-restore unaligned checkpoints. The trigger config is
 * forced via F39481_FORCE (see TestStreamEnvironment.forceConfigurationFromEnv).
 */
class WindowDistinctAggregateReproITCase extends StreamingWithStateTestBase(ROCKSDB_BACKEND) {

  val CumulateWindowRollupExpectedData = Seq(
    "0,a,2020-10-10T00:00,2020-10-10T00:00:05,4,11.10,5.0,1.0,2",
    "0,a,2020-10-10T00:00,2020-10-10T00:00:10,6,19.98,5.0,1.0,3",
    "0,a,2020-10-10T00:00,2020-10-10T00:00:15,6,19.98,5.0,1.0,3",
    "0,b,2020-10-10T00:00,2020-10-10T00:00:10,2,6.66,6.0,3.0,2",
    "0,b,2020-10-10T00:00,2020-10-10T00:00:15,2,6.66,6.0,3.0,2",
    "0,b,2020-10-10T00:00:15,2020-10-10T00:00:20,1,4.44,4.0,4.0,1",
    "0,b,2020-10-10T00:00:15,2020-10-10T00:00:25,1,4.44,4.0,4.0,1",
    "0,b,2020-10-10T00:00:15,2020-10-10T00:00:30,1,4.44,4.0,4.0,1",
    "0,b,2020-10-10T00:00:30,2020-10-10T00:00:35,1,3.33,3.0,3.0,1",
    "0,b,2020-10-10T00:00:30,2020-10-10T00:00:40,1,3.33,3.0,3.0,1",
    "0,b,2020-10-10T00:00:30,2020-10-10T00:00:45,1,3.33,3.0,3.0,1",
    "0,null,2020-10-10T00:00:30,2020-10-10T00:00:35,1,7.77,7.0,7.0,0",
    "0,null,2020-10-10T00:00:30,2020-10-10T00:00:40,1,7.77,7.0,7.0,0",
    "0,null,2020-10-10T00:00:30,2020-10-10T00:00:45,1,7.77,7.0,7.0,0",
    "1,null,2020-10-10T00:00,2020-10-10T00:00:05,4,11.10,5.0,1.0,2",
    "1,null,2020-10-10T00:00,2020-10-10T00:00:10,8,26.64,6.0,1.0,4",
    "1,null,2020-10-10T00:00,2020-10-10T00:00:15,8,26.64,6.0,1.0,4",
    "1,null,2020-10-10T00:00:15,2020-10-10T00:00:20,1,4.44,4.0,4.0,1",
    "1,null,2020-10-10T00:00:15,2020-10-10T00:00:25,1,4.44,4.0,4.0,1",
    "1,null,2020-10-10T00:00:15,2020-10-10T00:00:30,1,4.44,4.0,4.0,1",
    "1,null,2020-10-10T00:00:30,2020-10-10T00:00:35,2,11.10,7.0,3.0,1",
    "1,null,2020-10-10T00:00:30,2020-10-10T00:00:40,2,11.10,7.0,3.0,1",
    "1,null,2020-10-10T00:00:30,2020-10-10T00:00:45,2,11.10,7.0,3.0,1"
  )

  @BeforeEach
  override def before(): Unit = {
    super.before()
    env.enableCheckpointing(100, CheckpointingMode.EXACTLY_ONCE)
    val configuration = new Configuration()
    configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "fixeddelay")
    configuration.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, Int.box(1))
    configuration.set(
      RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY,
      Duration.ofMillis(0))
    env.configure(configuration, Thread.currentThread.getContextClassLoader)
    FailingCollectionSource.reset()

    val dataId = TestValuesTableFactory.registerData(TestData.windowDataWithTimestamp)
    tEnv.executeSql(s"""
                       |CREATE TABLE T1 (
                       | `ts` STRING,
                       | `int` INT,
                       | `double` DOUBLE,
                       | `float` FLOAT,
                       | `bigdec` DECIMAL(10, 2),
                       | `string` STRING,
                       | `name` STRING,
                       | `rowtime` AS TO_TIMESTAMP(`ts`),
                       | WATERMARK for `rowtime` AS `rowtime` - INTERVAL '1' SECOND
                       |) WITH (
                       | 'connector' = 'values',
                       | 'data-id' = '$dataId',
                       | 'failing-source' = 'true'
                       |)
                       |""".stripMargin)

    tEnv.getConfig.set(
      OptimizerConfigOptions.TABLE_OPTIMIZER_DISTINCT_AGG_SPLIT_ENABLED,
      Boolean.box(true))
  }

  @RepeatedTest(10)
  def testCumulateWindowRollupWithBackpressure(): Unit = {
    val sql =
      """
        |SELECT
        |  GROUPING_ID(`name`),
        |  `name`,
        |  window_start,
        |  window_end,
        |  COUNT(*),
        |  SUM(`bigdec`),
        |  MAX(`double`),
        |  MIN(`float`),
        |  COUNT(DISTINCT `string`)
        |FROM TABLE(
        |   CUMULATE(
        |     TABLE T1,
        |     DESCRIPTOR(rowtime),
        |     INTERVAL '5' SECOND,
        |     INTERVAL '15' SECOND))
        |GROUP BY ROLLUP(`name`), window_start, window_end
      """.stripMargin

    val sink = new TestingAppendSink
    tEnv
      .sqlQuery(sql)
      .toDataStream
      .map(new ThrottleFunction)
      .name("F39481Throttle")
      .addSink(sink)
    env.execute()

    assertThat(sink.getAppendResults.sorted.mkString("\n"))
      .isEqualTo(CumulateWindowRollupExpectedData.sorted.mkString("\n"))
  }
}

/**
 * Chained in front of the sink, so it lives inside the final GlobalWindowAggregate task: the open
 * delay keeps that task INITIALIZING (checkpoint 1 cannot trigger, upstream records queue in the
 * network input), the per-row delay sustains backpressure during post-restore checkpoints.
 */
class ThrottleFunction extends RichMapFunction[Row, Row] {

  private def envMs(name: String, dflt: Long): Long = {
    val v = System.getenv(name)
    if (v == null || v.trim.isEmpty) dflt else v.trim.toLong
  }

  @transient private var rowDelayMs: Long = 0L

  override def open(openContext: OpenContext): Unit = {
    rowDelayMs = envMs("F39481_ROW_DELAY_MS", 5L)
    val openDelay = envMs("F39481_OPEN_DELAY_MS", 300L)
    if (openDelay > 0) {
      Thread.sleep(openDelay)
    }
  }

  override def map(row: Row): Row = {
    if (rowDelayMs > 0) {
      Thread.sleep(rowDelayMs)
    }
    row
  }
}
