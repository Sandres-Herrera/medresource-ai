package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.SavedReport;
import com.medresource.services.management.ReportRequest;
import com.medresource.ui.view.ReportSaving.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la decisión de guardar una simulación en el historial, sin base de datos ni hilos.
 */
class ReportSavingTest {

    private static final GeneratedReport REPORT = GeneratedReportFixtures.small();
    private static final SavedReport SAVED = new SavedReport(4, 12, LocalDateTime.of(2026, 11, 17, 17, 30), REPORT);

    /** Cuántas veces se intentó guardar. */
    private final AtomicInteger saves = new AtomicInteger();
    /** Guardados que empezaron y todavía no terminan, para terminarlos cuando la prueba quiera. */
    private final List<Runnable> pending = new ArrayList<>();

    /** Guarda con éxito, pero solo termina cuando la prueba ejecuta lo pendiente. */
    private ReportSaving deferred() {
        return new ReportSaving(report -> {
            saves.incrementAndGet();
            return Optional.of(SAVED);
        }, (task, done, failed) -> pending.add(() -> done.accept(task.get())));
    }

    /** Termina al momento con lo que devuelva el guardado, o con su error. */
    private ReportSaving immediate(Function<GeneratedReport, Optional<SavedReport>> saver) {
        return new ReportSaving(report -> {
            saves.incrementAndGet();
            return saver.apply(report);
        }, (task, done, failed) -> {
            try {
                done.accept(task.get());
            } catch (RuntimeException e) {
                failed.accept(e);
            }
        });
    }

    @Test
    void nothingIsSavedUntilTheReportIsReadyAndSomeoneDecides() {
        ReportSaving saving = deferred();

        assertEquals(Status.WAITING_REPORT, saving.status());
        assertFalse(saving.save());

        saving.reportReady(REPORT);

        assertEquals(Status.UNDECIDED, saving.status());
        assertEquals(0, saves.get());
        assertTrue(saving.canSave());
    }

    @Test
    void savingRegistersTheSimulationOnce() {
        ReportSaving saving = deferred();
        saving.reportReady(REPORT);

        assertTrue(saving.save());
        assertEquals(Status.SAVING, saving.status());
        assertFalse(saving.save());
        assertFalse(saving.save());
        pending.forEach(Runnable::run);

        assertEquals(1, saves.get());
        assertEquals(Status.SAVED, saving.status());
        assertEquals(Optional.of(SAVED), saving.saved());
        assertFalse(saving.save());
    }

    @Test
    void continuingWithoutSavingStillLetsSaveLater() {
        ReportSaving saving = immediate(report -> Optional.of(SAVED));
        saving.reportReady(REPORT);

        saving.skip();
        assertEquals(Status.SKIPPED, saving.status());
        assertEquals(0, saves.get());

        assertTrue(saving.save());
        assertEquals(Status.SAVED, saving.status());
        saving.skip();
        assertEquals(Status.SAVED, saving.status());
    }

    @Test
    void aFailedSaveSaysWhyAndCanBeTriedAgain() {
        List<Boolean> works = new ArrayList<>(List.of(false, true));
        ReportSaving saving = immediate(report -> {
            if (!works.remove(0)) {
                throw new IllegalStateException("Sin conexión con Oracle.");
            }
            return Optional.of(SAVED);
        });
        saving.reportReady(REPORT);

        saving.save();
        assertEquals(Status.FAILED, saving.status());
        assertEquals(Optional.of("Sin conexión con Oracle."), saving.error());
        assertTrue(saving.canSave());

        saving.save();
        assertEquals(Status.SAVED, saving.status());
        assertEquals(Optional.empty(), saving.error());
    }

    @Test
    void withoutADatabaseItCannotBeSaved() {
        ReportSaving saving = immediate(report -> Optional.empty());
        saving.reportReady(REPORT);

        saving.save();

        assertEquals(Status.NO_DATABASE, saving.status());
        assertFalse(saving.canSave());
        assertFalse(saving.save());
        assertEquals(1, saves.get());
    }

    @Test
    void tellsEachChangeToWhoListensUntilTheyStop() {
        ReportSaving saving = deferred();
        List<Status> heard = new ArrayList<>();
        Runnable listener = () -> heard.add(saving.status());
        saving.addListener(listener);

        saving.reportReady(REPORT);
        saving.save();
        saving.removeListener(listener);
        pending.forEach(Runnable::run);

        assertEquals(List.of(Status.UNDECIDED, Status.SAVING), heard);
        assertEquals(Status.SAVED, saving.status());
    }

    @Test
    void onlyTheFirstReportCounts() {
        ReportSaving saving = deferred();
        saving.reportReady(REPORT);
        saving.skip();

        saving.reportReady(GeneratedReportFixtures.small());

        assertEquals(Status.SKIPPED, saving.status());
        assertTrue(saving.report().isPresent());
    }

    @Test
    void needsItsData() {
        assertThrows(IllegalArgumentException.class, () -> new ReportSaving(null, (task, done, failed) -> { }));
        assertThrows(IllegalArgumentException.class, () -> new ReportSaving(report -> Optional.empty(), null));
        assertThrows(IllegalArgumentException.class, () -> new ReportSaving(Optional::empty, (ReportRequest) null));
        assertThrows(IllegalArgumentException.class, () -> deferred().reportReady(null));
        assertThrows(IllegalArgumentException.class, () -> deferred().addListener(null));
    }
}
