package com.medresource.ui.view;

import com.medresource.persistence.DataAccessException;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las operaciones con el archivo de reportes en segundo plano.
 */
class ArchiveWorkerTest {

    private static final long WAIT_SECONDS = 30;

    @Test
    void deliversTheResultInTheSwingThread() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Boolean> inSwing = new AtomicReference<>();
        AtomicReference<Boolean> workedInSwing = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        new ArchiveWorker<>(() -> {
            workedInSwing.set(SwingUtilities.isEventDispatchThread());
            return "guardado";
        }, value -> {
            inSwing.set(SwingUtilities.isEventDispatchThread());
            result.set(value);
            finished.countDown();
        }, error -> finished.countDown()).execute();

        assertTrue(finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("guardado", result.get());
        assertTrue(inSwing.get());
        assertFalse(workedInSwing.get());
    }

    @Test
    void anErrorIsDeliveredInsteadOfTheResult() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        new ArchiveWorker<String>(() -> {
            throw new DataAccessException("Oracle apagado");
        }, value -> {
            result.set(value);
            finished.countDown();
        }, failure -> {
            error.set(failure);
            finished.countDown();
        }).execute();

        assertTrue(finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertNull(result.get());
        assertInstanceOf(DataAccessException.class, error.get());
        assertEquals("Oracle apagado", error.get().getMessage());
    }

    @Test
    void nothingCanBeMissing() {
        assertThrows(IllegalArgumentException.class, () -> new ArchiveWorker<>(null, value -> { }, error -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ArchiveWorker<>(() -> 1, null, error -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ArchiveWorker<>(() -> 1, value -> { }, null));
    }
}
