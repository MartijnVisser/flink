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

package org.apache.flink.runtime.executiongraph;

import org.apache.flink.api.common.JobStatus;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobGraphTestUtils;
import org.apache.flink.runtime.scheduler.DefaultSchedulerBuilder;
import org.apache.flink.runtime.scheduler.SchedulerBase;
import org.apache.flink.runtime.testtasks.NoOpInvokable;
import org.apache.flink.runtime.testutils.DirectScheduledExecutorService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the finish behaviour of the {@link ExecutionGraph}. */
class ExecutionGraphFinishTest {

    @RegisterExtension
    static final TestingComponentMainThreadExecutor.Extension MAIN_EXECUTOR_RESOURCE =
            new TestingComponentMainThreadExecutor.Extension();

    private final MainThreadExecutionGraphTestUtils mainThreadUtils =
            new MainThreadExecutionGraphTestUtils(
                    MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor());

    @Test
    void testJobFinishes() throws Exception {

        JobGraph jobGraph =
                JobGraphTestUtils.streamingJobGraph(
                        ExecutionGraphTestUtils.createJobVertex("Task1", 2, NoOpInvokable.class),
                        ExecutionGraphTestUtils.createJobVertex("Task2", 2, NoOpInvokable.class));

        // Use DirectScheduledExecutorService for the IO executor so that deployment
        // pipeline IO operations complete synchronously and their main-thread callbacks
        // are queued before any test-submitted tasks (avoiding races with state transitions).
        SchedulerBase scheduler =
                new DefaultSchedulerBuilder(
                                jobGraph,
                                mainThreadUtils.getMainThreadExecutor(),
                                new DirectScheduledExecutorService())
                        .build();

        ExecutionGraph eg = scheduler.getExecutionGraph();

        mainThreadUtils.execute(scheduler::startScheduling);
        mainThreadUtils.switchAllVerticesToRunning(eg);

        Iterator<ExecutionJobVertex> jobVertices =
                mainThreadUtils.execute(() -> eg.getVerticesTopologically().iterator());

        ExecutionJobVertex sender = jobVertices.next();
        ExecutionJobVertex receiver = jobVertices.next();

        List<ExecutionVertex> senderVertices =
                mainThreadUtils.execute(() -> Arrays.asList(sender.getTaskVertices()));
        List<ExecutionVertex> receiverVertices =
                mainThreadUtils.execute(() -> Arrays.asList(receiver.getTaskVertices()));

        // test getNumExecutionVertexFinished
        mainThreadUtils.execute(
                () -> {
                    senderVertices.get(0).getCurrentExecutionAttempt().markFinished();
                    assertThat(sender.getNumExecutionVertexFinished()).isOne();
                    assertThat(eg.getState()).isEqualTo(JobStatus.RUNNING);
                });

        mainThreadUtils.execute(
                () -> {
                    senderVertices.get(1).getCurrentExecutionAttempt().markFinished();
                    assertThat(sender.getNumExecutionVertexFinished()).isEqualTo(2);
                    assertThat(eg.getState()).isEqualTo(JobStatus.RUNNING);
                });

        // test job finishes
        mainThreadUtils.execute(
                () -> {
                    receiverVertices.get(0).getCurrentExecutionAttempt().markFinished();
                    receiverVertices.get(1).getCurrentExecutionAttempt().markFinished();
                    assertThat(eg.getNumFinishedVertices()).isEqualTo(4);
                    assertThat(eg.getState()).isEqualTo(JobStatus.FINISHED);
                });
    }
}
