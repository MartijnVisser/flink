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

import org.apache.flink.configuration.{Configuration, RestartStrategyOptions}
import org.apache.flink.core.execution.CheckpointingMode
import org.apache.flink.table.api.bridge.scala.tableConversions
import org.apache.flink.table.api.config.OptimizerConfigOptions
import org.apache.flink.table.planner.factories.TestValuesTableFactory
import org.apache.flink.table.planner.runtime.utils.{FailingCollectionSource, StreamingWithStateTestBase, TestingAppendSink}
import org.apache.flink.table.planner.runtime.utils.StreamingWithStateTestBase.ROCKSDB_BACKEND
import org.apache.flink.types.Row

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, RepeatedTest}

import java.math.{BigDecimal => JBigDecimal}
import java.time.Duration

/**
 * FLINK-39481 REPRO ONLY (do not merge): scaled-up variant of
 * [[WindowDistinctAggregateReproITCase]]. On loaded CI agents the middle
 * GlobalWindowAggregate task's watermark-timer firing bursts are slow enough for the unaligned
 * barrier to land mid-firing (the state interruptible timers exist for). Locally those bursts are
 * microseconds with the original 11-row dataset, so this variant widens them by construction:
 * ~200 keys x 2 rollup grouping sets => hundreds of timers per 5s slice boundary. Correctness is
 * checked against a golden run of the identical topology (failedBefore pre-set => no failure)
 * instead of a hand-written expected list. Rows with ts >= 30s form the trailing window family
 * fired only by the end-of-input MAX_WATERMARK: the CI loss signature.
 */
class WindowDistinctAggregateScaledReproITCase extends StreamingWithStateTestBase(ROCKSDB_BACKEND) {

  val RowsPerSecond = 20
  val LastSecond = 34
  val NumKeys = 200
  val StringCardinality = 17

  def generatedRows(): Seq[Row] = {
    for {
      sec <- 0 to LastSecond
      i <- 0 until RowsPerSecond
    } yield {
      val idx = sec * RowsPerSecond + i
      val name = if (idx % 23 == 0) null else "k%03d".format(idx % NumKeys)
      val str = if (idx % 19 == 0) null else "s" + (idx % StringCardinality)
      rowOf(
        "2020-10-10 00:00:%02d".format(sec),
        Int.box(idx % 10),
        Double.box((idx % 7).toDouble),
        Float.box((idx % 5).toFloat),
        new JBigDecimal("%d.%02d".format(idx % 9, idx % 100)),
        str,
        name)
    }
  }

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
    // NOTE: no FailingCollectionSource.reset() here; the golden run relies on
    // failedBefore=true (set by the base class) so the source does not fail.

    val dataId = TestValuesTableFactory.registerData(generatedRows())
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

  private def runQuery(): Seq[String] = {
    val sink = new TestingAppendSink
    tEnv
      .sqlQuery(sql)
      .toDataStream
      .map(new ThrottleFunction)
      .name("F39481Throttle")
      .addSink(sink)
    env.execute()
    sink.getAppendResults
  }

  @RepeatedTest(5)
  def testScaledCumulateRollupWithBackpressure(): Unit = {
    // golden: identical topology, failedBefore=true => the source never fails
    val expected = runQuery()
    assertThat(expected.size).isGreaterThan(0)

    // failing run: checkpoint 1 + artificial failure + restore with channel-state replay
    FailingCollectionSource.reset()
    val actual = runQuery()

    assertThat(actual.sorted.mkString("\n")).isEqualTo(expected.sorted.mkString("\n"))
  }
}
