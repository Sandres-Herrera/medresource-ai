package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.NarrativeSource;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la tarjeta del hospital estándar de la pantalla de inicio.
 */
class HospitalCardTest {

    private final AtomicInteger compares = new AtomicInteger();
    private final HospitalCard card = new HospitalCard("Hospital estándar", HospitalConfig.standard(),
            compares::incrementAndGet);

    private static SavedReportSummary run(int deaths) {
        return new SavedReportSummary(4, 11, LocalDateTime.of(2026, 11, 16, 16, 26),
                new CaseConfig(7L, Scenario.CATASTROPHE, 100, 120), "Prioridad clínica (triage)", deaths,
                NarrativeSource.AI, 9, 10);
    }

    @Test
    void showsATileForEachResourceOfTheHospital() {
        HospitalConfig standard = HospitalConfig.standard();

        assertEquals(List.of(standard.hospitalizationBeds() + " Camas", standard.icuBeds() + " UCI",
                standard.operatingRooms() + " Quirófanos", standard.doctors() + " Médicos",
                standard.nurses() + " Enfermeros"), card.statTexts());
    }

    @Test
    void withoutSavedRunsItSaysSo() {
        assertEquals(List.of(HospitalCard.NO_RUNS_TEXT), card.lastRunTexts());
        assertEquals(HomeTheme.MUTED, card.lastRunColor());
    }

    @Test
    void showsTheLastSavedRunAndCanGoBackToNone() {
        card.showLastRun(run(5));

        assertEquals(List.of("Ejecución #11 · " + Scenario.CATASTROPHE.getDisplayName(),
                "5 fallecidos · 16/11/2026 16:26"), card.lastRunTexts());
        assertEquals(HomeTheme.TEXT, card.lastRunColor());

        card.showLastRun(null);

        assertEquals(List.of(HospitalCard.NO_RUNS_TEXT), card.lastRunTexts());
    }

    @Test
    void oneDeathIsSingular() {
        assertEquals("1 fallecido · 16/11/2026 16:26", HospitalCard.lastRunDetail(run(1)));
    }

    @Test
    void theArrowsOpenTheComparison() {
        card.compareButton().doClick();

        assertEquals(1, compares.get());
        assertEquals(HomeIcon.SWAP, card.compareButton().getIcon());
    }

    @Test
    void hasAFixedWidth() {
        assertEquals(HospitalCard.WIDTH, card.getPreferredSize().width);
    }

    @Test
    void needsItsData() {
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalCard(" ", HospitalConfig.standard(), () -> { }));
        assertThrows(IllegalArgumentException.class, () -> new HospitalCard("Hospital", null, () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalCard("Hospital", HospitalConfig.standard(), null));
    }
}
