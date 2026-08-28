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

package org.apache.flink.streaming.api.operators.source;

import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.watermarkstatus.WatermarkStatus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WatermarkToDataOutput} re-activation after idleness (FINDING N1-runtime).
 *
 * <p>The resume-with-backlog case: a source goes idle after having emitted watermark W, then
 * resumes with data whose watermark is not (yet) larger than W. The correct behavior — matching
 * {@code StatusWatermarkValve} (post-FLINK-40475), the legacy {@code
 * StreamSourceContexts.WatermarkContext} and the SQL {@code WatermarkAssignerOperator}, which all
 * eagerly emit ACTIVE on resume — is that an ACTIVE watermark status is emitted downstream.
 */
class WatermarkToDataOutputIdleReactivationTest {

    @Test
    void resumingWithNonAdvancingWatermarkEmitsActiveStatus() {
        final CollectingDataOutput<Object> testingOutput = new CollectingDataOutput<>();
        final WatermarkToDataOutput wmOutput = new WatermarkToDataOutput(testingOutput);

        wmOutput.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(100L));
        wmOutput.markIdle();

        // resume with backlog: the new watermark does not exceed the pre-idle watermark
        wmOutput.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(80L));

        assertThat(testingOutput.getEvents())
                .as(
                        "Resuming after idleness must re-activate the output even if the new "
                                + "watermark does not advance beyond the pre-idle watermark")
                .containsExactly(new Watermark(100L), WatermarkStatus.IDLE, WatermarkStatus.ACTIVE);
    }

    @Test
    void activeIsOnlyReachableViaStrictlyAdvancingWatermark() {
        // characterization of the current (buggy) behavior, contrasting with the test above:
        // ACTIVE is emitted only once a strictly larger watermark arrives
        final CollectingDataOutput<Object> testingOutput = new CollectingDataOutput<>();
        final WatermarkToDataOutput wmOutput = new WatermarkToDataOutput(testingOutput);

        wmOutput.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(100L));
        wmOutput.markIdle();
        wmOutput.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(80L));

        // current behavior: nothing at all was emitted for the non-advancing watermark
        assertThat(testingOutput.getEvents())
                .containsExactly(new Watermark(100L), WatermarkStatus.IDLE);

        // only a strictly advancing watermark re-activates the output
        wmOutput.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(150L));

        assertThat(testingOutput.getEvents())
                .containsExactly(
                        new Watermark(100L),
                        WatermarkStatus.IDLE,
                        WatermarkStatus.ACTIVE,
                        new Watermark(150L));
    }
}
