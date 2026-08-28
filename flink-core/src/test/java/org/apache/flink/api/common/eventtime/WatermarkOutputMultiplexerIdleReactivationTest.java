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
 * Tests (FINDING N1-core) that the {@link WatermarkOutputMultiplexer} re-activates its underlying
 * output after a period where all multiplexed outputs were idle.
 */
class WatermarkOutputMultiplexerIdleReactivationTest {

    /**
     * Drives the multiplexer into the all-idle state: s1 at watermark 100, s2 at 50, then both
     * idle. Returns the recording underlying output with its event log cleared, ready for the
     * resume phase.
     */
    private static RecordingWatermarkOutput setupAllIdleAtMax100(
            WatermarkOutputMultiplexer[] multiplexerHolder, WatermarkOutput[] outputsHolder) {
        RecordingWatermarkOutput underlying = new RecordingWatermarkOutput();
        WatermarkOutputMultiplexer multiplexer = new WatermarkOutputMultiplexer(underlying);
        multiplexer.registerNewOutput("s1");
        multiplexer.registerNewOutput("s2");
        WatermarkOutput s1 = multiplexer.getImmediateOutput("s1");
        WatermarkOutput s2 = multiplexer.getImmediateOutput("s2");

        s1.emitWatermark(new Watermark(100));
        s2.emitWatermark(new Watermark(50));
        assertThat(underlying.events).containsExactly("watermark(50)");

        s2.markIdle();
        // s1 (active, 100) now solely determines the combined watermark
        assertThat(underlying.events).containsExactly("watermark(50)", "watermark(100)");

        s1.markIdle();
        // all idle: max (=100) already flushed, underlying is told to go idle
        assertThat(underlying.events)
                .containsExactly("watermark(50)", "watermark(100)", "markIdle");

        underlying.events.clear();
        multiplexerHolder[0] = multiplexer;
        outputsHolder[0] = s1;
        outputsHolder[1] = s2;
        return underlying;
    }

    /**
     * The realistic backlog-resume case: after all outputs went idle (flushed max = 100), an output
     * resumes with a watermark BELOW the flushed max. Every sibling code path
     * (StatusWatermarkValve, legacy source context, SQL watermark assigner) eagerly re-activates
     * downstream on resume, so the underlying output must receive an ACTIVE signal — either a
     * markActive() call or a watermark emission (which WatermarkToDataOutput in flink-runtime
     * translates to ACTIVE).
     */
    @Test
    void resumeBelowFlushedMaxMustReactivateUnderlyingOutput() {
        WatermarkOutputMultiplexer[] mux = new WatermarkOutputMultiplexer[1];
        WatermarkOutput[] outs = new WatermarkOutput[2];
        RecordingWatermarkOutput underlying = setupAllIdleAtMax100(mux, outs);
        WatermarkOutput s2 = outs[1];

        // resume: s2 becomes active again with watermark 80 (<= flushed max of 100)
        s2.emitWatermark(new Watermark(80));
        mux[0].onPeriodicEmit();

        assertThat(underlying.events)
                .as(
                        "after an output resumes from all-idle, the underlying output must "
                                + "receive an ACTIVE signal (markActive or a watermark), but "
                                + "got nothing at all")
                .isNotEmpty();
        assertThat(underlying.events).contains("markActive");
    }

    /**
     * Characterization contrast case: resuming with a watermark ABOVE the flushed max does emit a
     * watermark (implicit reactivation via WatermarkToDataOutput downstream), but markActive() is
     * never called on the underlying output at this API level.
     */
    @Test
    void resumeAboveFlushedMaxEmitsWatermarkButNeverMarkActive() {
        WatermarkOutputMultiplexer[] mux = new WatermarkOutputMultiplexer[1];
        WatermarkOutput[] outs = new WatermarkOutput[2];
        RecordingWatermarkOutput underlying = setupAllIdleAtMax100(mux, outs);
        WatermarkOutput s2 = outs[1];

        // resume: s2 becomes active again with watermark 150 (> flushed max of 100)
        s2.emitWatermark(new Watermark(150));
        mux[0].onPeriodicEmit();

        // the only signal is the watermark itself; no markActive exists on this path
        assertThat(underlying.events).containsExactly("watermark(150)");
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
