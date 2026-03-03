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
import org.apache.flink.core.testutils.FlinkAssertions;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.concurrent.ComponentMainThreadExecutor;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.executiongraph.failover.TestRestartBackoffTimeStrategy;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobGraphBuilder;
import org.apache.flink.runtime.jobgraph.JobGraphTestUtils;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobmaster.JobMasterId;
import org.apache.flink.runtime.jobmaster.slotpool.DeclarativeSlotPoolBridge;
import org.apache.flink.runtime.jobmaster.slotpool.DeclarativeSlotPoolBridgeBuilder;
import org.apache.flink.runtime.jobmaster.slotpool.LocationPreferenceSlotSelectionStrategy;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlotProvider;
import org.apache.flink.runtime.jobmaster.slotpool.PhysicalSlotProviderImpl;
import org.apache.flink.runtime.jobmaster.slotpool.SlotPool;
import org.apache.flink.runtime.jobmaster.slotpool.SlotPoolUtils;
import org.apache.flink.runtime.resourcemanager.ResourceManagerGateway;
import org.apache.flink.runtime.resourcemanager.utils.TestingResourceManagerGateway;
import org.apache.flink.runtime.scheduler.DefaultSchedulerBuilder;
import org.apache.flink.runtime.scheduler.ExecutionSlotAllocatorFactory;
import org.apache.flink.runtime.scheduler.SchedulerBase;
import org.apache.flink.runtime.scheduler.SchedulerTestingUtils;
import org.apache.flink.runtime.testtasks.NoOpInvokable;
import org.apache.flink.runtime.testutils.DirectScheduledExecutorService;
import org.apache.flink.streaming.util.RestartStrategyUtils;
import org.apache.flink.util.concurrent.ManuallyTriggeredScheduledExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the restart behaviour of the {@link ExecutionGraph}. */
class ExecutionGraphRestartTest {

    private static final int NUM_TASKS = 31;

    @RegisterExtension
    static final TestingComponentMainThreadExecutor.Extension MAIN_EXECUTOR_RESOURCE =
            new TestingComponentMainThreadExecutor.Extension();

    private ManuallyTriggeredScheduledExecutor taskRestartExecutor;

    @BeforeEach
    void setUp() {
        taskRestartExecutor = new ManuallyTriggeredScheduledExecutor();
    }

    // ------------------------------------------------------------------------

    private final MainThreadExecutionGraphTestUtils mainThreadUtils =
            new MainThreadExecutionGraphTestUtils(
                    MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor());

    @Test
    void testCancelAllPendingRequestWhileCanceling() throws Exception {
        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        DeclarativeSlotPoolBridge slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            final int numTasksExceedSlotPool = 50;
            // create a graph with task count larger than slot pool
            JobVertex sender =
                    ExecutionGraphTestUtils.createJobVertex(
                            "Task", NUM_TASKS + numTasksExceedSlotPool, NoOpInvokable.class);
            JobGraph graph = JobGraphTestUtils.streamingJobGraph(sender);

            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    graph,
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .build();
            ExecutionGraph executionGraph = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));
            offerSlots(slotPool, NUM_TASKS, mainThreadExecutor.getMainThreadExecutor());

            int pendingRequests = mainThreadExecutor.execute(slotPool::getNumPendingRequests);
            assertThat(pendingRequests).isEqualTo(numTasksExceedSlotPool);

            mainThreadExecutor.execute(
                    () -> {
                        scheduler.cancel();
                        assertThat(executionGraph.getState()).isEqualTo(JobStatus.CANCELLING);
                    });
            int finalPendingRequests = mainThreadExecutor.execute(slotPool::getNumPendingRequests);
            assertThat(finalPendingRequests).isZero();
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    @Test
    void testCancelAllPendingRequestWhileFailing() throws Exception {
        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        DeclarativeSlotPoolBridge slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            final int numTasksExceedSlotPool = 50;
            // create a graph with task count larger than slot pool
            JobVertex sender =
                    ExecutionGraphTestUtils.createJobVertex(
                            "Task", NUM_TASKS + numTasksExceedSlotPool, NoOpInvokable.class);
            JobGraph graph = JobGraphTestUtils.streamingJobGraph(sender);

            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    graph,
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .build();
            ExecutionGraph executionGraph = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));
            offerSlots(slotPool, NUM_TASKS, mainThreadExecutor.getMainThreadExecutor());

            int pendingRequests = mainThreadExecutor.execute(slotPool::getNumPendingRequests);
            assertThat(pendingRequests).isEqualTo(numTasksExceedSlotPool);

            mainThreadExecutor.execute(
                    () -> {
                        scheduler.handleGlobalFailure(new Exception("test"));
                        assertThat(executionGraph.getState()).isEqualTo(JobStatus.FAILING);
                    });
            int finalPendingRequests = mainThreadExecutor.execute(slotPool::getNumPendingRequests);
            assertThat(finalPendingRequests).isZero();
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    @Test
    void testCancelWhileRestarting() throws Exception {
        // We want to manually control the restart and delay
        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        SlotPool slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    createJobGraph(),
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .setRestartBackoffTimeStrategy(
                                    new TestRestartBackoffTimeStrategy(true, Long.MAX_VALUE))
                            .setDelayExecutor(taskRestartExecutor)
                            .build();
            ExecutionGraph executionGraph = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));

            final ResourceID taskManagerResourceId =
                    offerSlots(slotPool, NUM_TASKS, mainThreadExecutor.getMainThreadExecutor());

            // Release the TaskManager and wait for the job to restart
            mainThreadExecutor.execute(
                    () -> {
                        slotPool.releaseTaskManager(
                                taskManagerResourceId, new Exception("Test Exception"));
                        assertThat(executionGraph.getState()).isEqualTo(JobStatus.RESTARTING);
                    });

            // Canceling needs to abort the restart
            mainThreadExecutor.execute(
                    () -> {
                        scheduler.cancel();
                        assertThat(executionGraph.getState()).isEqualTo(JobStatus.CANCELED);
                    });

            // Trigger any scheduled restart tasks - they should have no effect since we canceled
            mainThreadExecutor.execute(taskRestartExecutor::triggerScheduledTasks);

            mainThreadExecutor.execute(
                    () -> {
                        assertThat(executionGraph.getState()).isEqualTo(JobStatus.CANCELED);
                        for (ExecutionVertex vertex : executionGraph.getAllExecutionVertices()) {
                            assertThat(vertex.getExecutionState()).isEqualTo(ExecutionState.FAILED);
                        }
                    });
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    private static ResourceID offerSlots(
            SlotPool slotPool, int numSlots, ComponentMainThreadExecutor mainThreadExecutor) {
        return SlotPoolUtils.offerSlots(
                slotPool, mainThreadExecutor, Collections.nCopies(numSlots, ResourceProfile.ANY));
    }

    @Test
    void testCancelWhileFailing() throws Exception {
        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        SlotPool slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    createJobGraph(),
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .setRestartBackoffTimeStrategy(
                                    new TestRestartBackoffTimeStrategy(false, Long.MAX_VALUE))
                            .build();
            ExecutionGraph graph = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));

            offerSlots(slotPool, NUM_TASKS, mainThreadExecutor.getMainThreadExecutor());

            assertThat(mainThreadUtils.execute(graph::getState)).isEqualTo(JobStatus.RUNNING);

            mainThreadUtils.switchAllVerticesToRunning(graph);

            mainThreadUtils.execute(
                    () -> {
                        scheduler.handleGlobalFailure(new Exception("test"));
                        assertThat(graph.getState()).isEqualTo(JobStatus.FAILING);
                    });

            mainThreadUtils.execute(
                    () -> {
                        scheduler.cancel();
                        assertThat(graph.getState()).isEqualTo(JobStatus.CANCELLING);
                    });

            // let all tasks finish cancelling
            mainThreadUtils.completeCancellingForAllVertices(graph);

            assertThat(mainThreadUtils.execute(graph::getState)).isEqualTo(JobStatus.CANCELED);
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    @Test
    void testFailWhileCanceling() throws Exception {
        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        SlotPool slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    createJobGraph(),
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .setRestartBackoffTimeStrategy(
                                    new TestRestartBackoffTimeStrategy(false, Long.MAX_VALUE))
                            .build();
            ExecutionGraph graph = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));

            offerSlots(slotPool, NUM_TASKS, mainThreadExecutor.getMainThreadExecutor());

            assertThat(mainThreadUtils.execute(graph::getState)).isEqualTo(JobStatus.RUNNING);
            mainThreadUtils.switchAllVerticesToRunning(graph);

            mainThreadUtils.execute(
                    () -> {
                        scheduler.cancel();
                        assertThat(graph.getState()).isEqualTo(JobStatus.CANCELLING);
                    });

            mainThreadUtils.execute(
                    () -> {
                        scheduler.handleGlobalFailure(new Exception("test"));
                        assertThat(graph.getState()).isEqualTo(JobStatus.FAILING);
                    });

            // let all tasks finish cancelling
            mainThreadUtils.completeCancellingForAllVertices(graph);

            assertThat(mainThreadUtils.execute(graph::getState)).isEqualTo(JobStatus.FAILED);
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    /**
     * Tests that a failing execution does not affect a restarted job. This is important if a
     * callback handler fails an execution after it has already reached a final state and the job
     * has been restarted.
     */
    @Test
    void testFailingExecutionAfterRestart() throws Exception {
        JobVertex sender = ExecutionGraphTestUtils.createJobVertex("Task1", 1, NoOpInvokable.class);
        JobVertex receiver =
                ExecutionGraphTestUtils.createJobVertex("Task2", 1, NoOpInvokable.class);
        JobGraph jobGraph = JobGraphTestUtils.streamingJobGraph(sender, receiver);

        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        SlotPool slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    jobGraph,
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .setRestartBackoffTimeStrategy(
                                    new TestRestartBackoffTimeStrategy(true, Long.MAX_VALUE))
                            .setDelayExecutor(taskRestartExecutor)
                            .build();
            ExecutionGraph eg = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));

            offerSlots(slotPool, 2, mainThreadExecutor.getMainThreadExecutor());

            Execution finishedExecution =
                    mainThreadExecutor.execute(
                            () -> {
                                Iterator<ExecutionVertex> executionVertices =
                                        eg.getAllExecutionVertices().iterator();
                                return executionVertices.next().getCurrentExecutionAttempt();
                            });
            Execution failedExecution =
                    mainThreadExecutor.execute(
                            () -> {
                                Iterator<ExecutionVertex> executionVertices =
                                        eg.getAllExecutionVertices().iterator();
                                executionVertices.next(); // skip the first
                                return executionVertices.next().getCurrentExecutionAttempt();
                            });

            mainThreadExecutor.execute(() -> finishedExecution.markFinished());

            mainThreadExecutor.execute(
                    () -> {
                        failedExecution.fail(new Exception("Test Exception"));
                        failedExecution.completeCancelling();
                        // Wait for job to be restarting
                        assertThat(eg.getState()).isEqualTo(JobStatus.RESTARTING);
                    });

            // Trigger the restart callback registration
            mainThreadExecutor.execute(taskRestartExecutor::triggerNonPeriodicScheduledTask);

            // Complete cancelling of any canceling vertices
            mainThreadExecutor.execute(
                    () -> {
                        for (ExecutionVertex vertex : eg.getAllExecutionVertices()) {
                            if (vertex.getExecutionState() == ExecutionState.CANCELING) {
                                vertex.getCurrentExecutionAttempt().completeCancelling();
                            }
                        }
                    });

            // Trigger any remaining tasks
            mainThreadExecutor.execute(taskRestartExecutor::triggerScheduledTasks);

            // The restart should have completed now
            mainThreadExecutor.execute(
                    () -> {
                        assertThat(eg.getState()).isEqualTo(JobStatus.RUNNING);

                        // At this point all resources have been assigned
                        for (ExecutionVertex vertex : eg.getAllExecutionVertices()) {
                            assertThat(vertex.getCurrentAssignedResource()).isNotNull();
                            vertex.getCurrentExecutionAttempt().switchToInitializing();
                            vertex.getCurrentExecutionAttempt().switchToRunning();
                        }
                    });

            // fail old finished execution, this should not affect the execution
            mainThreadExecutor.execute(
                    () -> finishedExecution.fail(new Exception("This should have no effect")));

            mainThreadExecutor.execute(
                    () -> {
                        for (ExecutionVertex vertex : eg.getAllExecutionVertices()) {
                            vertex.getCurrentExecutionAttempt().markFinished();
                        }
                        // the state of the finished execution should have not changed since it is
                        // terminal
                        assertThat(finishedExecution.getState()).isEqualTo(ExecutionState.FINISHED);
                        assertThat(eg.getState()).isEqualTo(JobStatus.FINISHED);
                    });
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    /**
     * Tests that a graph is not restarted after cancellation via a call to {@link
     * Execution#fail(Throwable)}. This can happen when a slot is released concurrently with
     * cancellation.
     */
    @Test
    void testFailExecutionAfterCancel() throws Exception {
        final TestingComponentMainThreadExecutor mainThreadExecutor =
                MAIN_EXECUTOR_RESOURCE.getComponentMainThreadTestExecutor();

        SlotPool slotPool =
                createDeclarativeSlotPoolBridge(mainThreadExecutor.getMainThreadExecutor());

        try {
            // Setup slot pool on main thread
            mainThreadExecutor.execute(() -> setupSlotPool(slotPool));

            ExecutionSlotAllocatorFactory slotAllocatorFactory =
                    mainThreadExecutor.execute(
                            () -> createExecutionSlotAllocatorFactoryWithoutSetup(slotPool));

            SchedulerBase scheduler =
                    new DefaultSchedulerBuilder(
                                    createJobGraphToCancel(),
                                    mainThreadExecutor.getMainThreadExecutor(),
                                    new DirectScheduledExecutorService())
                            .setExecutionSlotAllocatorFactory(slotAllocatorFactory)
                            .setRestartBackoffTimeStrategy(
                                    new TestRestartBackoffTimeStrategy(false, Long.MAX_VALUE))
                            .setDelayExecutor(taskRestartExecutor)
                            .build();
            ExecutionGraph eg = scheduler.getExecutionGraph();

            mainThreadExecutor.execute(() -> startScheduling(scheduler));

            offerSlots(slotPool, 1, mainThreadExecutor.getMainThreadExecutor());

            // Fail right after cancel (for example with concurrent slot release)
            mainThreadExecutor.execute(scheduler::cancel);

            mainThreadExecutor.execute(
                    () -> {
                        for (ExecutionVertex v : eg.getAllExecutionVertices()) {
                            v.getCurrentExecutionAttempt().fail(new Exception("Test Exception"));
                        }
                    });

            FlinkAssertions.assertThatFuture(eg.getTerminationFuture())
                    .eventuallySucceeds()
                    .isEqualTo(JobStatus.CANCELED);

            Execution execution =
                    mainThreadExecutor.execute(
                            () ->
                                    eg.getAllExecutionVertices()
                                            .iterator()
                                            .next()
                                            .getCurrentExecutionAttempt());

            mainThreadExecutor.execute(
                    () -> {
                        execution.completeCancelling();
                        assertThat(eg.getState()).isEqualTo(JobStatus.CANCELED);
                    });
        } finally {
            // Close the slot pool on the main thread
            mainThreadExecutor.execute(slotPool::close);
        }
    }

    // ------------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------------

    private static DeclarativeSlotPoolBridge createDeclarativeSlotPoolBridge(
            ComponentMainThreadExecutor mainThreadExecutor) {
        return new DeclarativeSlotPoolBridgeBuilder()
                .setMainThreadExecutor(mainThreadExecutor)
                .build();
    }

    private static void startScheduling(SchedulerBase scheduler) {
        assertThat(scheduler.getExecutionGraph().getState()).isEqualTo(JobStatus.CREATED);
        scheduler.startScheduling();
        assertThat(scheduler.getExecutionGraph().getState()).isEqualTo(JobStatus.RUNNING);
    }

    private static ExecutionSlotAllocatorFactory createExecutionSlotAllocatorFactory(
            SlotPool slotPool) throws Exception {
        setupSlotPool(slotPool);
        return createExecutionSlotAllocatorFactoryWithoutSetup(slotPool);
    }

    private static ExecutionSlotAllocatorFactory createExecutionSlotAllocatorFactoryWithoutSetup(
            SlotPool slotPool) {
        PhysicalSlotProvider physicalSlotProvider =
                new PhysicalSlotProviderImpl(
                        LocationPreferenceSlotSelectionStrategy.createDefault(), slotPool);
        return SchedulerTestingUtils.newSlotSharingExecutionSlotAllocatorFactory(
                physicalSlotProvider);
    }

    private static void setupSlotPool(SlotPool slotPool) {
        try {
            final String jobManagerAddress = "foobar";
            final ResourceManagerGateway resourceManagerGateway =
                    new TestingResourceManagerGateway();
            slotPool.start(JobMasterId.generate(), jobManagerAddress);
            slotPool.connectToResourceManager(resourceManagerGateway);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup slot pool", e);
        }
    }

    private static JobGraph createJobGraph() {
        JobVertex sender =
                ExecutionGraphTestUtils.createJobVertex("Task", NUM_TASKS, NoOpInvokable.class);
        return JobGraphTestUtils.streamingJobGraph(sender);
    }

    private static JobGraph createJobGraphToCancel() throws IOException {
        JobVertex vertex =
                ExecutionGraphTestUtils.createJobVertex("Test Vertex", 1, NoOpInvokable.class);
        JobGraph jobGraph =
                JobGraphBuilder.newStreamingJobGraphBuilder().addJobVertex(vertex).build();
        RestartStrategyUtils.configureFixedDelayRestartStrategy(
                jobGraph, Integer.MAX_VALUE, Integer.MAX_VALUE);
        return jobGraph;
    }
}
