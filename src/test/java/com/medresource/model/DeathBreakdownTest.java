package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los fallecidos según cuándo murieron.
 */
class DeathBreakdownTest {

    @Test
    void separatesTheAvoidableDeathsFromTheUnavoidable() {
        DeathBreakdown deaths = new DeathBreakdown(4, 2, 3, Map.of(CauseOfDeath.LACK_OF_TIMELY_CARE, 6,
                CauseOfDeath.IRREDUCIBLE_MORTALITY, 3));

        assertEquals(9, deaths.total());
        assertEquals(6, deaths.avoidable());
        assertEquals(3, deaths.unavoidable());
        assertEquals(6, deaths.byCause().get(CauseOfDeath.LACK_OF_TIMELY_CARE).intValue());
    }

    @Test
    void withoutDeathsEverythingIsZero() {
        DeathBreakdown deaths = new DeathBreakdown(0, 0, 0, Map.of());

        assertEquals(0, deaths.total());
        assertEquals(0, deaths.avoidable());
    }

    @Test
    void theCausesMustAddUpToTheDeaths() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeathBreakdown(1, 0, 0, Map.of(CauseOfDeath.NO_ICU_BED, 2)));
        assertThrows(IllegalArgumentException.class, () -> new DeathBreakdown(-1, 0, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new DeathBreakdown(0, 0, 0, null));
    }
}
