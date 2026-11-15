package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reporte del análisis «¿y si…?».
 */
class WhatIfReportTest {

    private static final Statistic BASE = Statistic.of(0.10, 0.12, 0.11);
    private static final Statistic NONE = Statistic.of(0.0, 0.0, 0.0);

    private static WhatIfResult resource(ResourceChange change, double mortalityChange, double waitChange) {
        return new WhatIfResult(change.describe(), change, "Prioridad clínica (triage)", BASE,
                Statistic.of(mortalityChange - 0.001, mortalityChange, mortalityChange + 0.001),
                Statistic.of(waitChange - 0.1, waitChange, waitChange + 0.1));
    }

    private static WhatIfResult policy(double mortalityChange) {
        return new WhatIfResult("Usar FIFO (orden de llegada)", null, "FIFO (orden de llegada)", BASE,
                Statistic.of(mortalityChange - 0.001, mortalityChange, mortalityChange + 0.001), NONE);
    }

    private static WhatIfReport report(List<WhatIfResult> resources, List<WhatIfResult> policies) {
        return new WhatIfReport(3, "Prioridad clínica (triage)", BASE, Statistic.of(40, 45, 50), resources,
                policies);
    }

    private static final WhatIfResult ROOM = resource(ResourceChange.of(ExtraResource.OPERATING_ROOMS), -0.03, -20);
    private static final WhatIfResult SURGEON = resource(ResourceChange.doctor(Specialty.SURGERY), -0.04, -30);
    private static final WhatIfResult BEDS = resource(ResourceChange.of(ExtraResource.BEDS), 0.0, -5);
    private static final WhatIfResult MORGUE = resource(ResourceChange.of(ExtraResource.MORGUE), 0.0, 0.0);

    @Test
    void theMissingResourcesAreTheOnesThatLowerTheMortality() {
        WhatIfReport report = report(List.of(ROOM, SURGEON, BEDS, MORGUE), List.of());

        assertEquals(List.of(SURGEON, ROOM), report.missingResources());
    }

    @Test
    void recommendsFromTheOneThatSavesMoreLives() {
        WhatIfReport report = report(List.of(ROOM, SURGEON, BEDS, MORGUE), List.of(policy(-0.035)));

        List<WhatIfResult> recommendations = report.recommendations();
        assertEquals(4, recommendations.size());
        assertEquals(SURGEON, recommendations.get(0));
        assertTrue(recommendations.get(1).isPolicy());
        assertEquals(ROOM, recommendations.get(2));
        assertEquals(BEDS, recommendations.get(3));
    }

    @Test
    void recommendsAtMostFive() {
        List<WhatIfResult> resources = new ArrayList<>();
        for (Specialty specialty : Specialty.values()) {
            resources.add(resource(ResourceChange.doctor(specialty), -0.02, -10));
        }

        assertEquals(WhatIfReport.MAX_RECOMMENDATIONS, report(resources, List.of()).recommendations().size());
    }

    @Test
    void anotherStrategyIsBetterOnlyIfItImproves() {
        assertTrue(report(List.of(), List.of(policy(0.02))).betterPolicy().isEmpty());
        assertTrue(report(List.of(), List.of(policy(-0.02))).betterPolicy().isPresent());
    }

    @Test
    void resourcesAndPoliciesGoInSeparateLists() {
        assertThrows(IllegalArgumentException.class, () -> report(List.of(policy(0.0)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> report(List.of(), List.of(ROOM)));
    }

    @Test
    void everythingMustComeFromTheSameCases() {
        WhatIfResult twoCases = new WhatIfResult("Usar FIFO", null, "FIFO", Statistic.of(0.1, 0.1),
                Statistic.of(0, 0), Statistic.of(0, 0));

        assertThrows(IllegalArgumentException.class, () -> report(List.of(), List.of(twoCases)));
        assertThrows(IllegalArgumentException.class, () -> new WhatIfReport(1, "FIFO", Statistic.of(0.1),
                Statistic.of(1), List.of(), List.of()));
    }
}
