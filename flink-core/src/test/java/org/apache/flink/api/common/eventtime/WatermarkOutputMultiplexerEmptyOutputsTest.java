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

package org.apache.flink.api.common.eventtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests (FLINK-39586) for {@link WatermarkOutputMultiplexer} behavior when the set of registered
 * outputs becomes empty: unregistering the last output must mark the underlying output idle so that
 * the last emitted watermark does not permanently cap downstream progress, and idleness must be
 * announced exactly once per transition.
 */
class WatermarkOutputMultiplexerEmptyOutputsTest {

    /**
     * The only output goes idle (markIdle already sent) and is then unregistered. Idleness is a
     * transition signal: it must not be re-sent on subsequent periodic emits, and a newly
     * registered output recovers the multiplexer with its first watermark.
     */
    @Test
    void unregisteringIdleOutputDoesNotRepeatMarkIdle() {
        RecordingWatermarkOutput underlying = new RecordingWatermarkOutput();
        WatermarkOutputMultiplexer multiplexer = new WatermarkOutputMultiplexer(underlying);
        multiplexer.registerNewOutput("o1");
        WatermarkOutput o1 = multiplexer.getImmediateOutput("o1");

        o1.markIdle();
        assertThat(underlying.events).containsExactly("markIdle");

        assertThat(multiplexer.unregisterOutput("o1")).isTrue();
        underlying.events.clear();

        multiplexer.onPeriodicEmit();
        multiplexer.onPeriodicEmit();
        multiplexer.onPeriodicEmit();
        assertThat(underlying.events)
                .as("the underlying output is already idle, so markIdle must not be repeated")
                .isEmpty();

        // a new output recovers the multiplexer: its first watermark re-activates downstream
        multiplexer.registerNewOutput("o2");
        WatermarkOutput o2 = multiplexer.getImmediateOutput("o2");
        o2.emitWatermark(new Watermark(200));
        assertThat(underlying.events).containsExactly("watermark(200)");
    }

    /**
     * The FLINK-39586 scenario: the only output is still ACTIVE (last watermark 100) when it is
     * unregistered, e.g. during a HybridSource bounded-to-unbounded transition. Once no outputs
     * remain, the subtask must signal idleness exactly once so it does not gate downstream
     * watermark progress forever.
     */
    @Test
    void unregisteringLastActiveOutputSignalsIdleExactlyOnce() {
        RecordingWatermarkOutput underlying = new RecordingWatermarkOutput();
        WatermarkOutputMultiplexer multiplexer = new WatermarkOutputMultiplexer(underlying);
        multiplexer.registerNewOutput("o1");
        WatermarkOutput o1 = multiplexer.getImmediateOutput("o1");

        o1.emitWatermark(new Watermark(100));
        assertThat(underlying.events).containsExactly("watermark(100)");

        assertThat(multiplexer.unregisterOutput("o1")).isTrue();

        multiplexer.onPeriodicEmit();
        multiplexer.onPeriodicEmit();
        multiplexer.onPeriodicEmit();

        assertThat(underlying.events)
                .as(
                        "with zero registered outputs the multiplexer must mark the underlying "
                                + "output idle exactly once, otherwise watermark 100 permanently "
                                + "caps downstream progress")
                .containsExactly("watermark(100)", "markIdle");
    }

    /**
     * Guards the FLINK-23011 behavior: a multiplexer that never had any outputs must not announce
     * idleness, because a FLIP-27 source must not go idle before any split was assigned.
     */
    @Test
    void initiallyEmptyMultiplexerStaysNonIdle() {
        RecordingWatermarkOutput underlying = new RecordingWatermarkOutput();
        WatermarkOutputMultiplexer multiplexer = new WatermarkOutputMultiplexer(underlying);

        multiplexer.onPeriodicEmit();
        multiplexer.onPeriodicEmit();

        assertThat(underlying.events).isEmpty();
    }

    /** Records every call on the underlying {@link WatermarkOutput}, in order. */
    private static final class RecordingWatermarkOutput implements WatermarkOutput {

        final List<String> events = new ArrayList<>();

        @Override
        public void emitWatermark(Watermark watermark) {
            events.add("watermark(" + watermark.getTimestamp() + ")");
        }

        @Override
        public void markIdle() {
            events.add("markIdle");
        }

        @Override
        public void markActive() {
            events.add("markActive");
        }
    }
}
