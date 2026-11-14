package com.medresource.ui.controller;

import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import com.medresource.ui.render.PatientMarker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la grabación de los últimos segundos del plano.
 */
class ReplayBufferTest {

    private final ReplayBuffer buffer = new ReplayBuffer();

    private void record(double elapsed, int minute) {
        PatientMarker marker = new PatientMarker("P001", TriageLevel.III, Sex.MALE, false, false, minute, 0, 80,
                -1);
        buffer.record(elapsed, minute, 0, List.of(marker), List.of(), List.of(), 0, List.of());
    }

    @Test
    void thereIsNothingToReplayBeforeTwoFrames() {
        assertFalse(buffer.canReplay());
        assertThrows(IllegalStateException.class, buffer::start);
        record(0, 0);
        assertFalse(buffer.canReplay());
        record(1, 1);
        assertTrue(buffer.canReplay());
    }

    @Test
    void keepsOnlyTheLastSecondsOfPlayback() {
        for (int frame = 0; frame <= 100; frame++) {
            record(1, frame);
        }

        assertEquals(ReplayBuffer.KEEP_SECONDS, buffer.recordedSeconds(), 1e-9);
    }

    @Test
    void theReplayShowsTheLastThirtySecondsAtTheSamePace() {
        for (int frame = 0; frame <= 60; frame++) {
            record(1, frame);
        }

        ReplayBuffer.Replay replay = buffer.start();

        assertEquals(ReplayBuffer.REPLAY_SECONDS, replay.duration(), 1e-9);
        assertEquals(30, replay.frameAt(0).minute());
        assertEquals(40, replay.frameAt(10.5).minute());
        assertEquals(60, replay.frameAt(30).minute());
        assertFalse(replay.isOver(29.9));
        assertTrue(replay.isOver(30));
    }

    @Test
    void withLessRecordedItReplaysWhatThereIs() {
        record(0, 0);
        record(2, 1);
        record(2, 2);

        ReplayBuffer.Replay replay = buffer.start();

        assertEquals(4, replay.duration(), 1e-9);
        assertEquals(0, replay.frameAt(-1).minute());
    }

    @Test
    void clearingForgetsEverything() {
        record(0, 0);
        record(1, 1);

        buffer.clear();

        assertFalse(buffer.canReplay());
        assertEquals(0, buffer.recordedSeconds(), 0.0);
    }

    @Test
    void rejectsNegativeTimesAndMissingLists() {
        assertThrows(IllegalArgumentException.class,
                () -> buffer.record(-1, 0, 0, List.of(), List.of(), List.of(), 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.record(1, 0, 0, null, List.of(), List.of(), 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.record(1, 0, 0, List.of(), List.of(), List.of(), -1, List.of()));
    }
}
