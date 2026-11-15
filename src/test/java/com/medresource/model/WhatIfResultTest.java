package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del resultado de una alternativa del análisis «¿y si…?».
 */
class WhatIfResultTest {

    private static final ResourceChange ROOM = ResourceChange.of(ExtraResource.OPERATING_ROOMS);
    private static final Statistic MORTALITY = Statistic.of(0.08, 0.10, 0.09, 0.11);

    private static WhatIfResult result(Statistic mortalityChange, Statistic waitChange) {
        return new WhatIfResult(ROOM.describe(), ROOM, "Prioridad clínica (triage)", MORTALITY, mortalityChange,
                waitChange);
    }

    @Test
    void aClearDropInMortalityIsAnImprovement() {
        WhatIfResult result = result(Statistic.of(-0.03, -0.02, -0.04, -0.03), Statistic.of(1, -1, 2, 0));

        assertTrue(result.lowersMortality());
        assertFalse(result.raisesMortality());
        assertTrue(result.isImprovement());
        assertEquals(3.0, result.deathsAvoidedPer100(), 1e-9);
    }

    @Test
    void aShorterWaitWithoutMoreDeathsIsAnImprovement() {
        WhatIfResult result = result(Statistic.of(0.01, -0.01, 0.0, -0.002), Statistic.of(-10, -12, -9, -11));

        assertFalse(result.lowersMortality());
        assertTrue(result.lowersWait());
        assertTrue(result.isImprovement());
    }

    @Test
    void aShorterWaitThatMightCostLivesIsNotAnImprovement() {
        WhatIfResult result = result(Statistic.of(0.02, -0.01, 0.03, -0.02), Statistic.of(-10, -12, -9, -11));

        assertFalse(result.raisesMortality());
        assertTrue(result.lowersWait());
        assertFalse(result.isImprovement());
    }

    @Test
    void aChangeThatMakesNoClearDifferenceIsNotRecommended() {
        WhatIfResult result = result(Statistic.of(0.02, -0.02, 0.01, -0.01), Statistic.of(3, -3, 1, -1));

        assertFalse(result.isImprovement());
    }

    @Test
    void aClearButTinyGainIsNotWorthRecommending() {
        WhatIfResult result = result(Statistic.of(-0.0010, -0.0011, -0.0012, -0.0011),
                Statistic.of(-0.2, -0.1, -0.2, -0.1));

        assertTrue(result.mortalityChange().excludesZero());
        assertFalse(result.lowersMortality());
        assertFalse(result.lowersWait());
        assertFalse(result.isImprovement());
    }

    @Test
    void moreDeathsAreNeverAnImprovementEvenWithAShorterWait() {
        WhatIfResult result = result(Statistic.of(0.03, 0.02, 0.04, 0.03), Statistic.of(-10, -12, -9, -11));

        assertTrue(result.raisesMortality());
        assertFalse(result.isImprovement());
    }

    @Test
    void anotherStrategyIsAPolicy() {
        WhatIfResult policy = new WhatIfResult("Usar FIFO", null, "FIFO (orden de llegada)", MORTALITY,
                Statistic.of(0, 0, 0, 0), Statistic.of(0, 0, 0, 0));

        assertTrue(policy.isPolicy());
        assertFalse(result(Statistic.of(0, 0, 0, 0), Statistic.of(0, 0, 0, 0)).isPolicy());
    }

    @Test
    void theResultsMustComeFromTheSameCases() {
        assertThrows(IllegalArgumentException.class, () -> result(Statistic.of(0, 0), Statistic.of(0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new WhatIfResult(" ", ROOM, "FIFO", MORTALITY,
                MORTALITY, MORTALITY));
    }
}
