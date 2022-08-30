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

package org.apache.flink.runtime.rpc;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.testutils.ManuallyTriggeredScheduledExecutorService;
import org.apache.flink.util.TestLoggerExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Tests for the RpcEndpoint, its self gateways and MainThreadExecutor scheduling command. */
@ExtendWith(TestLoggerExtension.class)
public class RpcEndpointTest {

    private static RpcService rpcService = null;

    @BeforeAll
    public static void setup() throws Exception {
        rpcService = RpcSystem.load().localServiceBuilder(new Configuration()).createAndStart();
    }

    @AfterAll
    public static void teardown() throws Exception {
        rpcService.stopService().get();
    }

    /**
     * Tests that we can obtain the self gateway from a RpcEndpoint and can interact with it via the
     * self gateway.
     */
    @Test
    public void testSelfGateway() throws Exception {
        int expectedValue = 1337;
        BaseEndpoint baseEndpoint = new BaseEndpoint(rpcService, expectedValue);

        try {
            baseEndpoint.start();

            BaseGateway baseGateway = baseEndpoint.getSelfGateway(BaseGateway.class);

            CompletableFuture<Integer> foobar = baseGateway.foobar();

            assertEquals(Integer.valueOf(expectedValue), foobar.get());
        } finally {
            RpcUtils.terminateRpcEndpoint(baseEndpoint);

            baseEndpoint.validateResourceClosed();
        }
    }

    /**
     * Tests that we cannot accidentally obtain a wrong self gateway type which is not implemented
     * by the RpcEndpoint.
     */
    @Test
    public void testWrongSelfGateway() {
        assertThrows(
                RuntimeException.class,
                () -> {
                    int expectedValue = 1337;
                    BaseEndpoint baseEndpoint = new BaseEndpoint(rpcService, expectedValue);

                    try {
                        baseEndpoint.start();

                        DifferentGateway differentGateway =
                                baseEndpoint.getSelfGateway(DifferentGateway.class);

                        fail(
                                "Expected to fail with a RuntimeException since we requested the wrong gateway type.");
                    } finally {
                        RpcUtils.terminateRpcEndpoint(baseEndpoint);

                        baseEndpoint.validateResourceClosed();
                    }
                });
    }

    /**
     * Tests that we can extend existing RpcEndpoints and can communicate with them via the self
     * gateways.
     */
    @Test
    public void testEndpointInheritance() throws Exception {
        int foobar = 1;
        int barfoo = 2;
        String foo = "foobar";

        ExtendedEndpoint endpoint = new ExtendedEndpoint(rpcService, foobar, barfoo, foo);

        try {
            endpoint.start();

            BaseGateway baseGateway = endpoint.getSelfGateway(BaseGateway.class);
            ExtendedGateway extendedGateway = endpoint.getSelfGateway(ExtendedGateway.class);
            DifferentGateway differentGateway = endpoint.getSelfGateway(DifferentGateway.class);

            assertEquals(Integer.valueOf(foobar), baseGateway.foobar().get());
            assertEquals(Integer.valueOf(foobar), extendedGateway.foobar().get());

            assertEquals(Integer.valueOf(barfoo), extendedGateway.barfoo().get());
            assertEquals(foo, differentGateway.foo().get());
        } finally {
            RpcUtils.terminateRpcEndpoint(endpoint);
            endpoint.validateResourceClosed();
        }
    }

    /** Tests that the RPC is running after it has been started. */
    @Test
    public void testRunningState()
            throws InterruptedException, ExecutionException, TimeoutException {
        RunningStateTestingEndpoint endpoint =
                new RunningStateTestingEndpoint(
                        rpcService, CompletableFuture.completedFuture(null));
        RunningStateTestingEndpointGateway gateway =
                endpoint.getSelfGateway(RunningStateTestingEndpointGateway.class);

        try {
            endpoint.start();
            assertTrue(gateway.queryIsRunningFlag().get());
        } finally {
            RpcUtils.terminateRpcEndpoint(endpoint);
            endpoint.validateResourceClosed();
        }
    }

    /** Tests that the RPC is not running if it is being stopped. */
    @Test
    public void testNotRunningState()
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> stopFuture = new CompletableFuture<>();
        RunningStateTestingEndpoint endpoint =
                new RunningStateTestingEndpoint(rpcService, stopFuture);
        RunningStateTestingEndpointGateway gateway =
                endpoint.getSelfGateway(RunningStateTestingEndpointGateway.class);

        endpoint.start();
        CompletableFuture<Void> terminationFuture = endpoint.closeAndWaitUntilOnStopCalled();

        assertFalse(gateway.queryIsRunningFlag().get());

        stopFuture.complete(null);
        terminationFuture.get();
        endpoint.validateResourceClosed();
    }

    public interface BaseGateway extends RpcGateway {
        CompletableFuture<Integer> foobar();
    }

    public interface ExtendedGateway extends BaseGateway {
        CompletableFuture<Integer> barfoo();
    }

    public interface DifferentGateway extends RpcGateway {
        CompletableFuture<String> foo();
    }

    public static class BaseEndpoint extends RpcEndpoint implements BaseGateway {

        private final int foobarValue;

        protected BaseEndpoint(RpcService rpcService) {
            super(rpcService);
            this.foobarValue = Integer.MAX_VALUE;
        }

        protected BaseEndpoint(RpcService rpcService, int foobarValue) {
            super(rpcService);

            this.foobarValue = foobarValue;
        }

        @Override
        public CompletableFuture<Integer> foobar() {
            return CompletableFuture.completedFuture(foobarValue);
        }
    }

    public static class ExtendedEndpoint extends BaseEndpoint
            implements ExtendedGateway, DifferentGateway {

        private final int barfooValue;

        private final String fooString;

        protected ExtendedEndpoint(
                RpcService rpcService, int foobarValue, int barfooValue, String fooString) {
            super(rpcService, foobarValue);

            this.barfooValue = barfooValue;
            this.fooString = fooString;
        }

        @Override
        public CompletableFuture<Integer> barfoo() {
            return CompletableFuture.completedFuture(barfooValue);
        }

        @Override
        public CompletableFuture<String> foo() {
            return CompletableFuture.completedFuture(fooString);
        }
    }

    public interface RunningStateTestingEndpointGateway extends RpcGateway {
        CompletableFuture<Boolean> queryIsRunningFlag();
    }

    private static final class RunningStateTestingEndpoint extends RpcEndpoint
            implements RunningStateTestingEndpointGateway {
        private final CountDownLatch onStopCalled;
        private final CompletableFuture<Void> stopFuture;

        RunningStateTestingEndpoint(RpcService rpcService, CompletableFuture<Void> stopFuture) {
            super(rpcService);
            this.stopFuture = stopFuture;
            this.onStopCalled = new CountDownLatch(1);
        }

        @Override
        public CompletableFuture<Void> onStop() {
            onStopCalled.countDown();
            return stopFuture;
        }

        CompletableFuture<Void> closeAndWaitUntilOnStopCalled() throws InterruptedException {
            CompletableFuture<Void> terminationFuture = closeAsync();
            onStopCalled.await();
            return terminationFuture;
        }

        public CompletableFuture<Boolean> queryIsRunningFlag() {
            return CompletableFuture.completedFuture(isRunning());
        }
    }

    /** Tests executing the runnable in the main thread of the underlying RPC endpoint. */
    @Test
    public void testExecute() throws InterruptedException, ExecutionException, TimeoutException {
        final RpcEndpoint endpoint = new BaseEndpoint(rpcService);
        final CompletableFuture<Void> asyncExecutionFuture = new CompletableFuture<>();
        try {
            endpoint.start();
            endpoint.getMainThreadExecutor()
                    .execute(
                            () -> {
                                endpoint.validateRunsInMainThread();
                                asyncExecutionFuture.complete(null);
                            });
            asyncExecutionFuture.get();
        } finally {
            RpcUtils.terminateRpcEndpoint(endpoint);
            endpoint.validateResourceClosed();
        }
    }

    @Test
    public void testScheduleRunnableWithDelayInMilliseconds() throws Exception {
        testScheduleWithDelay(
                (mainThreadExecutor, expectedDelay) ->
                        mainThreadExecutor.schedule(
                                () -> {}, expectedDelay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Test
    public void testScheduleRunnableWithDelayInSeconds() throws Exception {
        testScheduleWithDelay(
                (mainThreadExecutor, expectedDelay) ->
                        mainThreadExecutor.schedule(
                                () -> {}, expectedDelay.toMillis() / 1000, TimeUnit.SECONDS));
    }

    @Test
    public void testScheduleRunnableAfterClose() throws Exception {
        testScheduleAfterClose(
                (mainThreadExecutor, expectedDelay) ->
                        mainThreadExecutor.schedule(
                                () -> {}, expectedDelay.toMillis() / 1000, TimeUnit.SECONDS));
    }

    @Test
    public void testCancelScheduledRunnable() throws Exception {
        testCancelScheduledTask(
                (mainThreadExecutor, future) -> {
                    final Duration delayDuration = Duration.ofMillis(2);
                    return mainThreadExecutor.schedule(
                            () -> {
                                future.complete(null);
                            },
                            delayDuration.toMillis(),
                            TimeUnit.MILLISECONDS);
                });
    }

    @Test
    public void testScheduleCallableWithDelayInMilliseconds() throws Exception {
        testScheduleWithDelay(
                (mainThreadExecutor, expectedDelay) ->
                        mainThreadExecutor.schedule(
                                () -> 1, expectedDelay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Test
    public void testScheduleCallableWithDelayInSeconds() throws Exception {
        testScheduleWithDelay(
                (mainThreadExecutor, expectedDelay) ->
                        mainThreadExecutor.schedule(
                                () -> 1, expectedDelay.toMillis() / 1000, TimeUnit.SECONDS));
    }

    @Test
    public void testScheduleCallableAfterClose() throws Exception {
        testScheduleAfterClose(
                (mainThreadExecutor, expectedDelay) ->
                        mainThreadExecutor.schedule(
                                () -> 1, expectedDelay.toMillis() / 1000, TimeUnit.SECONDS));
    }

    @Test
    public void testCancelScheduledCallable() {
        testCancelScheduledTask(
                (mainThreadExecutor, future) -> {
                    final Duration delayDuration = Duration.ofMillis(2);
                    return mainThreadExecutor.schedule(
                            () -> {
                                future.complete(null);
                                return null;
                            },
                            delayDuration.toMillis(),
                            TimeUnit.MILLISECONDS);
                });
    }

    private static void testScheduleWithDelay(
            BiConsumer<RpcEndpoint.MainThreadExecutor, Duration> scheduler) throws Exception {
        final CompletableFuture<Void> taskCompletedFuture = new CompletableFuture<>();
        final String endpointId = "foobar";

        final MainThreadExecutable mainThreadExecutable =
                new TestMainThreadExecutable((runnable) -> taskCompletedFuture.complete(null));

        final RpcEndpoint.MainThreadExecutor mainThreadExecutor =
                new RpcEndpoint.MainThreadExecutor(mainThreadExecutable, () -> {}, endpointId);

        final Duration expectedDelay = Duration.ofSeconds(1);

        scheduler.accept(mainThreadExecutor, expectedDelay);

        taskCompletedFuture.get();
        mainThreadExecutor.close();
    }

    private static void testScheduleAfterClose(
            BiFunction<RpcEndpoint.MainThreadExecutor, Duration, ScheduledFuture<?>> scheduler) {
        final CompletableFuture<Void> taskCompletedFuture = new CompletableFuture<>();
        final String endpointId = "foobar";

        final MainThreadExecutable mainThreadExecutable =
                new TestMainThreadExecutable((runnable) -> taskCompletedFuture.complete(null));

        final RpcEndpoint.MainThreadExecutor mainThreadExecutor =
                new RpcEndpoint.MainThreadExecutor(mainThreadExecutable, () -> {}, endpointId);

        mainThreadExecutor.close();

        final Duration expectedDelay = Duration.ofSeconds(0);
        ScheduledFuture<?> future = scheduler.apply(mainThreadExecutor, expectedDelay);
        assertFalse(taskCompletedFuture.isDone());
        assertFalse(future.isDone());
    }

    private static void testCancelScheduledTask(
            BiFunction<RpcEndpoint.MainThreadExecutor, CompletableFuture<Void>, ScheduledFuture<?>>
                    scheduler) {
        final MainThreadExecutable mainThreadExecutable =
                new TestMainThreadExecutable(Runnable::run);

        final ManuallyTriggeredScheduledExecutorService manuallyTriggeredScheduledExecutorService =
                new ManuallyTriggeredScheduledExecutorService();

        final RpcEndpoint.MainThreadExecutor mainThreadExecutor =
                new RpcEndpoint.MainThreadExecutor(
                        mainThreadExecutable, () -> {}, manuallyTriggeredScheduledExecutorService);
        final CompletableFuture<Void> actionFuture = new CompletableFuture<>();

        ScheduledFuture<?> scheduledFuture = scheduler.apply(mainThreadExecutor, actionFuture);
        scheduledFuture.cancel(true);
        manuallyTriggeredScheduledExecutorService.triggerAllNonPeriodicTasks();

        assertTrue(scheduledFuture.isCancelled());
        assertFalse(actionFuture.isDone());
        mainThreadExecutor.close();
    }

    /**
     * Tests executing the callable in the main thread of the underlying RPC service, returning a
     * future for the result of the callable. If the callable is not completed within the given
     * timeout, then the future will be failed with a TimeoutException. This schedule method is
     * called directly from RpcEndpoint, MainThreadExecutor do not support this method.
     */
    @Test
    public void testCallAsync() throws InterruptedException, ExecutionException, TimeoutException {
        final RpcEndpoint endpoint = new BaseEndpoint(rpcService);
        final Integer expectedInteger = 12345;
        try {
            endpoint.start();
            final CompletableFuture<Integer> integerFuture =
                    endpoint.callAsync(
                            () -> {
                                endpoint.validateRunsInMainThread();
                                return expectedInteger;
                            },
                            Duration.ofSeconds(10L));
            assertEquals(expectedInteger, integerFuture.get());
        } finally {
            RpcUtils.terminateRpcEndpoint(endpoint);
            endpoint.validateResourceClosed();
        }
    }

    /**
     * Make the callable sleep some time more than specified timeout, so TimeoutException is
     * expected.
     */
    @Test
    public void testCallAsyncTimeout()
            throws InterruptedException, ExecutionException, TimeoutException {
        final RpcEndpoint endpoint = new BaseEndpoint(rpcService);
        final Duration timeout = Duration.ofMillis(100);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            endpoint.start();
            final CompletableFuture<Throwable> throwableFuture =
                    endpoint.callAsync(
                                    () -> {
                                        endpoint.validateRunsInMainThread();
                                        latch.await();
                                        return 12345;
                                    },
                                    timeout)
                            .handle((ignore, throwable) -> throwable);
            final Throwable throwable = throwableFuture.get();

            assertNotNull(throwable);
            assertTrue(throwable instanceof TimeoutException);
        } finally {
            latch.countDown();
            RpcUtils.terminateRpcEndpoint(endpoint);
            endpoint.validateResourceClosed();
        }
    }

    private static class TestMainThreadExecutable implements MainThreadExecutable {

        private final Consumer<Runnable> scheduleRunAsyncConsumer;

        private TestMainThreadExecutable(Consumer<Runnable> scheduleRunAsyncConsumer) {
            this.scheduleRunAsyncConsumer = scheduleRunAsyncConsumer;
        }

        @Override
        public void runAsync(Runnable runnable) {
            scheduleRunAsyncConsumer.accept(runnable);
        }

        @Override
        public <V> CompletableFuture<V> callAsync(Callable<V> callable, Duration callTimeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void scheduleRunAsync(Runnable runnable, long delay) {
            scheduleRunAsyncConsumer.accept(runnable);
        }
    }
}
