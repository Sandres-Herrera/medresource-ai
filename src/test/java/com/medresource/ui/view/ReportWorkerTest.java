package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportProgress;
import com.medresource.model.ReportStage;
import com.medresource.services.management.ReportGenerator;
import com.medresource.services.management.ReportNarrator;
import com.medresource.services.management.ReportRequest;
import com.medresource.services.simulation.WhatIfAnalyzer;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la generación del reporte en segundo plano.
 */
class ReportWorkerTest {

    private static final long WAIT_SECONDS = 60;

    /** Generador que siempre falla. */
    private static final class FailingGenerator extends ReportGenerator {
        FailingGenerator() {
            super(new WhatIfAnalyzer(), new ReportNarrator(null, null), 2);
        }

        @Override
        public GeneratedReport generate(ReportRequest request, Consumer<ReportProgress> progress,
                                        BooleanSupplier cancelled) {
            throw new IllegalStateException("Falló la generación.");
        }
    }

    @Test
    void deliversTheProgressAndThenTheReportInTheSwingThread() throws InterruptedException {
        List<ReportProgress> seen = new CopyOnWriteArrayList<>();
        AtomicReference<GeneratedReport> result = new AtomicReference<>();
        AtomicReference<Boolean> inSwing = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        new ReportWorker(ReportViewFixtures.generator(), ReportViewFixtures.request(), seen::add, report -> {
            inSwing.set(SwingUtilities.isEventDispatchThread());
            result.set(report);
            finished.countDown();
        }, error -> finished.countDown()).execute();

        assertTrue(finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertTrue(inSwing.get());
        assertFalse(seen.isEmpty());
        assertTrue(seen.stream().allMatch(progress -> progress.stage().compareTo(ReportStage.WRITING) <= 0));
    }

    @Test
    void anErrorIsDeliveredInsteadOfTheReport() throws InterruptedException {
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<GeneratedReport> result = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        new ReportWorker(new FailingGenerator(), ReportViewFixtures.request(), progress -> { }, report -> {
            result.set(report);
            finished.countDown();
        }, failure -> {
            error.set(failure);
            finished.countDown();
        }).execute();

        assertTrue(finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("Falló la generación.", error.get().getMessage());
        assertNull(result.get());
    }

    @Test
    void ifItIsCancelledNothingElseIsDelivered() throws Exception {
        AtomicReference<Object> delivered = new AtomicReference<>();
        ReportWorker worker = new ReportWorker(ReportViewFixtures.generator(), ReportViewFixtures.request(),
                progress -> { }, delivered::set, delivered::set);

        worker.cancel(true);
        worker.execute();
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(worker.isCancelled());
        assertNull(delivered.get());
    }

    @Test
    void nothingCanBeMissing() {
        ReportGenerator generator = ReportViewFixtures.generator();
        ReportRequest request = ReportViewFixtures.request();

        assertThrows(IllegalArgumentException.class, () -> new ReportWorker(null, request, p -> { }, r -> { },
                e -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ReportWorker(generator, null, p -> { }, r -> { },
                e -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ReportWorker(generator, request, null, r -> { },
                e -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ReportWorker(generator, request, p -> { }, null,
                e -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ReportWorker(generator, request, p -> { }, r -> { },
                null));
    }
}
