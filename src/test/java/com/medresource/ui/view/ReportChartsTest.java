package com.medresource.ui.view;

import com.medresource.model.BloodType;
import com.medresource.model.BloodUsage;
import com.medresource.model.BottleneckStats;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.DeathBreakdown;
import com.medresource.model.GeneratedReport;
import com.medresource.model.MissingResource;
import com.medresource.model.ReportSection;
import com.medresource.model.ResourceGroup;
import com.medresource.model.ResourceUsage;
import com.medresource.model.Specialty;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.TriageLevelStats;
import com.medresource.model.WhatIfReport;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las gráficas de cada sección del reporte.
 */
class ReportChartsTest {

    @Test
    void everySectionButTheCasesHasAChartInTheSampleReport() {
        GeneratedReport generated = ReportViewFixtures.generated();

        assertTrue(ReportCharts.chartFor(ReportSection.CASES, generated).isEmpty());
        JComponent timeline = ReportCharts.chartFor(ReportSection.TIMELINE, generated).orElseThrow();
        assertInstanceOf(TimelineStrip.class, timeline);
        assertEquals(generated.report().totalMinutes(), ((TimelineStrip) timeline).getTotalMinutes());
        for (ReportSection section : List.of(ReportSection.TRIAGE, ReportSection.DEATHS, ReportSection.BOTTLENECKS,
                ReportSection.RESOURCES, ReportSection.STAFF, ReportSection.POLICY)) {
            assertInstanceOf(BarChart.class, ReportCharts.chartFor(section, generated).orElseThrow(),
                    section.name());
        }
    }

    @Test
    void theTriageShowsOnlyTheLevelsThatHadPatients() {
        GeneratedReport generated = ReportViewFixtures.generated();
        long levels = generated.report().triage().stream().filter(level -> level.patients() > 0).count();

        BarChart chart = ReportCharts.triage(generated.report()).orElseThrow();

        assertEquals(levels, chart.getBars().size());
        TriageLevelStats first = generated.report().triage().stream().filter(level -> level.patients() > 0)
                .findFirst().orElseThrow();
        assertEquals(HomeTheme.triageColor(first.level()), chart.getBars().getFirst().color());
        assertTrue(chart.getBars().getFirst().valueText().endsWith(" en meta"));
    }

    @Test
    void theDeathsAreSplitByWhenTheyDied() {
        BarChart chart = ReportCharts.deaths(new DeathBreakdown(3, 1, 2,
                Map.of(CauseOfDeath.LACK_OF_TIMELY_CARE, 6))).orElseThrow();

        assertEquals(List.of(3.0, 1.0, 2.0), chart.getBars().stream().map(BarChart.Bar::value).toList());
        assertEquals("1 paciente", chart.getBars().get(1).valueText());
        assertTrue(ReportCharts.deaths(new DeathBreakdown(0, 0, 0, Map.of())).isEmpty());
    }

    @Test
    void theMainBottleneckIsRedAndTheOthersAmber() {
        BarChart chart = ReportCharts.bottlenecks(List.of(
                new BottleneckStats(MissingResource.OPERATING_ROOM, 300, 0.75, 4, 30, 200, 3, 90),
                new BottleneckStats(MissingResource.DOCTOR, 100, 0.25, 3, 40, 120, 2, 60))).orElseThrow();

        assertEquals(HomeTheme.RED, chart.getBars().get(0).color());
        assertEquals(HomeTheme.AMBER, chart.getBars().get(1).color());
        assertEquals("75 % de la espera · 4 pacientes", chart.getBars().get(0).valueText());
        assertTrue(ReportCharts.bottlenecks(List.of()).isEmpty());
    }

    @Test
    void anAlmostAlwaysBusyResourceIsRedAndOneAlmostUnusedIsGrey() {
        BarChart chart = ReportCharts.resources(List.of(
                new ResourceUsage(ResourceGroup.OPERATING_ROOMS, 2, 0.9, 2, 120),
                new ResourceUsage(ResourceGroup.BEDS, 10, 0.2, 5, 0),
                new ResourceUsage(ResourceGroup.NURSES, 10, 0.6, 8, 0))).orElseThrow();

        assertEquals(1.0, chart.getScale(), 1e-9);
        assertEquals(HomeTheme.RED, chart.getBars().get(0).color());
        assertEquals(HomeTheme.MUTED, chart.getBars().get(1).color());
        assertEquals(HomeTheme.ACCENT, chart.getBars().get(2).color());
    }

    @Test
    void saysHowLongAResourceWasFullOrElseItsPeak() {
        BarChart chart = ReportCharts.resources(List.of(
                new ResourceUsage(ResourceGroup.OPERATING_ROOMS, 2, 0.9, 2, 120),
                new ResourceUsage(ResourceGroup.NURSES, 10, 0.6, 8, 0))).orElseThrow();

        assertEquals("90 % prom. · lleno 120 min", chart.getBars().get(0).valueText());
        assertEquals("60 % prom. · pico 80 %", chart.getBars().get(1).valueText());
    }

    @Test
    void oneDeathIsSingular() {
        assertEquals("1 muerte", ReportCharts.deaths(1.0));
        assertEquals("1 muerte", ReportCharts.deaths(0.96));
        assertEquals("0,8 muertes", ReportCharts.deaths(0.8));
        assertEquals("2 muertes", ReportCharts.deaths(2));
    }

    @Test
    void saturatedSpecialtiesAreRed() {
        BarChart chart = ReportCharts.staff(List.of(new SpecialtyLoad(Specialty.SURGERY, 2, 9, 0.9, 100),
                new SpecialtyLoad(Specialty.PEDIATRICS, 1, 1, 0.1, 0))).orElseThrow();

        assertEquals("Cirugía (2)", chart.getBars().get(0).label());
        assertEquals(HomeTheme.RED, chart.getBars().get(0).color());
        assertEquals(HomeTheme.MUTED, chart.getBars().get(1).color());
    }

    @Test
    void theBloodShowsOnlyTheTypesThatWereUsed() {
        List<BloodUsage> used = List.of(new BloodUsage(BloodType.O_NEGATIVE, 10, 10, 0, 90, 30),
                new BloodUsage(BloodType.A_POSITIVE, 10, 2, 8, -1, 0),
                new BloodUsage(BloodType.AB_NEGATIVE, 0, 0, 0, 0, 120));

        BarChart chart = ReportCharts.blood(used).orElseThrow();

        assertEquals(2, chart.getBars().size());
        assertEquals(HomeTheme.RED, chart.getBars().get(0).color());
        assertEquals("2 de 10 usadas", chart.getBars().get(1).valueText());
        assertEquals(10, chart.getScale(), 1e-9);
        assertTrue(ReportCharts.blood(List.of(new BloodUsage(BloodType.A_POSITIVE, 10, 0, 10, -1, 0))).isEmpty());
    }

    @Test
    void thePolicyComparesTheCurrentHospitalWithTheOtherStrategies() {
        WhatIfReport whatIf = ReportViewFixtures.generated().whatIf();

        BarChart chart = ReportCharts.policy(whatIf).orElseThrow();

        assertEquals(whatIf.policies().size() + 1, chart.getBars().size());
        assertEquals(whatIf.strategyName() + ReportCharts.CURRENT, chart.getBars().getFirst().label());
        assertEquals(whatIf.mortality().mean(), chart.getBars().getFirst().value(), 1e-9);
        assertTrue(ReportCharts.policy(new WhatIfReport(whatIf.cases(), whatIf.strategyName(), whatIf.mortality(),
                whatIf.waitMinutes(), whatIf.resources(), List.of())).isEmpty());
    }

    @Test
    void theMissingResourcesSayHowManyDeathsTheyWouldAvoid() {
        WhatIfReport whatIf = ReportViewFixtures.generated().whatIf();

        assertEquals(whatIf.missingResources().size(),
                (int) ReportCharts.missingResources(whatIf).map(chart -> chart.getBars().size()).orElse(0));
    }

    @Test
    void needsTheSectionAndTheReport() {
        assertThrows(IllegalArgumentException.class, () -> ReportCharts.chartFor(null,
                ReportViewFixtures.generated()));
        assertThrows(IllegalArgumentException.class, () -> ReportCharts.chartFor(ReportSection.BLOOD, null));
    }
}
