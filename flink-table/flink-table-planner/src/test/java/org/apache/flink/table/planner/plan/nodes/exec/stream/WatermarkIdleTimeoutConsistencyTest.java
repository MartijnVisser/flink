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

package org.apache.flink.table.planner.plan.nodes.exec.stream;

import org.apache.flink.api.dag.Transformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.transformations.OneInputTransformation;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.runtime.operators.wmassigners.WatermarkAssignerOperatorFactory;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates FINDING N6: {@code scan.watermark.idle-timeout} behaves differently depending on
 * whether the watermark is pushed into the source or applied by the standalone
 * WatermarkAssignerOperator.
 */
class WatermarkIdleTimeoutConsistencyTest {

    private static final String DDL_TEMPLATE =
            "CREATE TABLE MyTable(\n"
                    + "  a INT,\n"
                    + "  b BIGINT,\n"
                    + "  c TIMESTAMP(3),\n"
                    + "  WATERMARK FOR c AS %s\n"
                    + ") WITH (\n"
                    + "  'connector' = 'values',\n"
                    + "  'bounded' = 'false',\n"
                    + "  'disable-lookup' = 'true'%s\n"
                    + ")";

    /**
     * Cell (b): connector does NOT implement SupportsWatermarkPushDown (values connector without
     * 'enable-watermark-push-down'). Per-table 'scan.watermark.idle-timeout' = 60s, global
     * 'table.exec.source.idle-timeout' = 1s. Consistency with the pushdown path requires the
     * per-table option to win (60000 ms). This assertion states the CORRECT expectation; a failure
     * showing 1000 proves that the per-table option is silently ignored.
     */
    @Test
    void testStandaloneAssignerHonorsPerTableIdleTimeout() throws Exception {
        long idleTimeout =
                translateAndGetAssignerIdleTimeout(
                        "c - INTERVAL '5' SECOND",
                        ",\n  'scan.watermark.idle-timeout' = '60s'",
                        null);
        assertThat(idleTimeout)
                .as(
                        "standalone WatermarkAssignerOperator should honor per-table "
                                + "scan.watermark.idle-timeout (60000) over global "
                                + "table.exec.source.idle-timeout (1000)")
                .isEqualTo(60000L);
    }

    /** Same as above but with the idle timeout given as an OPTIONS hint instead of a DDL option. */
    @Test
    void testStandaloneAssignerHonorsIdleTimeoutHint() throws Exception {
        long idleTimeout =
                translateAndGetAssignerIdleTimeout(
                        "c - INTERVAL '5' SECOND",
                        "",
                        "SELECT * FROM MyTable /*+ OPTIONS('scan.watermark.idle-timeout' = '60s') */");
        assertThat(idleTimeout)
                .as(
                        "standalone WatermarkAssignerOperator should honor the "
                                + "scan.watermark.idle-timeout OPTIONS hint (60000) over global "
                                + "table.exec.source.idle-timeout (1000)")
                .isEqualTo(60000L);
    }

    /** Control for cell (b): with no per-table option, the global option is applied. */
    @Test
    void testStandaloneAssignerUsesGlobalIdleTimeout() throws Exception {
        long idleTimeout = translateAndGetAssignerIdleTimeout("c - INTERVAL '5' SECOND", "", null);
        assertThat(idleTimeout).isEqualTo(1000L);
    }

    /**
     * Cell (c): SOURCE_WATERMARK() with SupportsSourceWatermark. Both global and per-table idle
     * timeouts are set; neither a WatermarkAssignerOperator exists nor does the pushdown spec carry
     * any idleness (SourceWatermarkSpec has no idle-timeout member at all).
     */
    @Test
    void testSourceWatermarkIgnoresIdleTimeoutEntirely() {
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment(new Configuration());
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
        tEnv.getConfig()
                .set(
                        ExecutionConfigOptions.TABLE_EXEC_SOURCE_IDLE_TIMEOUT,
                        Duration.ofMillis(1000));
        tEnv.executeSql(
                String.format(
                        DDL_TEMPLATE,
                        "SOURCE_WATERMARK()",
                        ",\n  'enable-watermark-push-down' = 'true'"
                                + ",\n  'scan.watermark.idle-timeout' = '60s'"));
        String explained = tEnv.explainSql("SELECT * FROM MyTable");
        tEnv.toDataStream(tEnv.sqlQuery("SELECT * FROM MyTable"));

        // no standalone assigner operator anywhere in the graph
        List<WatermarkAssignerOperatorFactory> assigners = new ArrayList<>();
        collectAssigners(env.getTransformations(), new HashSet<>(), assigners);
        assertThat(assigners).isEmpty();
        // the source ability digest carries no idle timeout
        assertThat(explained).contains("watermark=[SOURCE_WATERMARK()]");
        assertThat(explained).doesNotContain("idletimeout");
    }

    private static long translateAndGetAssignerIdleTimeout(
            String watermarkExpr, String extraOptions, String query) throws Exception {
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment(new Configuration());
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
        tEnv.getConfig()
                .set(
                        ExecutionConfigOptions.TABLE_EXEC_SOURCE_IDLE_TIMEOUT,
                        Duration.ofMillis(1000));
        tEnv.executeSql(String.format(DDL_TEMPLATE, watermarkExpr, extraOptions));
        String sql = query != null ? query : "SELECT * FROM MyTable";
        // triggers translation to Transformations
        tEnv.toDataStream(tEnv.sqlQuery(sql));

        List<WatermarkAssignerOperatorFactory> assigners = new ArrayList<>();
        collectAssigners(env.getTransformations(), new HashSet<>(), assigners);
        assertThat(assigners)
                .as("expected exactly one standalone WatermarkAssignerOperatorFactory")
                .hasSize(1);
        Field f = WatermarkAssignerOperatorFactory.class.getDeclaredField("idleTimeout");
        f.setAccessible(true);
        return (long) f.get(assigners.get(0));
    }

    private static void collectAssigners(
            List<Transformation<?>> transformations,
            Set<Transformation<?>> visited,
            List<WatermarkAssignerOperatorFactory> out) {
        for (Transformation<?> t : transformations) {
            if (!visited.add(t)) {
                continue;
            }
            if (t instanceof OneInputTransformation
                    && ((OneInputTransformation<?, ?>) t).getOperatorFactory()
                            instanceof WatermarkAssignerOperatorFactory) {
                out.add(
                        (WatermarkAssignerOperatorFactory)
                                ((OneInputTransformation<?, ?>) t).getOperatorFactory());
            }
            collectAssigners(t.getInputs(), visited, out);
        }
    }
}
