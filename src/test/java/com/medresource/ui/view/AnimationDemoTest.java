package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la lectura de los argumentos de la demostración animada. La ventana no se prueba porque
 * necesita pantalla.
 */
class AnimationDemoTest {

    @Test
    void withoutArgumentsItRunsANormalCaseOfSixtyPatients() {
        CaseConfig config = AnimationDemo.caseFrom(new String[0]);

        assertEquals(Scenario.NORMAL, config.scenario());
        assertEquals(60, config.patientCount());
        assertEquals(1L, config.seed());
        assertEquals(CaseConfig.DEFAULT_DURATION_MINUTES, config.durationMinutes());
    }

    @Test
    void readsTheScenarioWithoutCaringAboutCaseOrSpaces() {
        assertEquals(Scenario.CATASTROPHE, AnimationDemo.caseFrom(new String[]{"catastrophe"}).scenario());
        assertEquals(Scenario.HIGH_DEMAND, AnimationDemo.caseFrom(new String[]{" HIGH_DEMAND "}).scenario());
        assertEquals(Scenario.UNEXPECTED_EMERGENCIES,
                AnimationDemo.caseFrom(new String[]{"Unexpected_Emergencies"}).scenario());
    }

    @Test
    void readsThePatientsAndTheSeed() {
        CaseConfig config = AnimationDemo.caseFrom(new String[]{"CATASTROPHE", "100", "7"});

        assertEquals(Scenario.CATASTROPHE, config.scenario());
        assertEquals(100, config.patientCount());
        assertEquals(7L, config.seed());
    }

    @Test
    void theSeedIsOptional() {
        CaseConfig config = AnimationDemo.caseFrom(new String[]{"NORMAL", "20"});

        assertEquals(20, config.patientCount());
        assertEquals(1L, config.seed());
    }

    @Test
    void rejectsMoreThanThreeArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> AnimationDemo.caseFrom(new String[]{"NORMAL", "20", "1", "extra"}));
    }

    @Test
    void rejectsAScenarioThatDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> AnimationDemo.caseFrom(new String[]{"CALM"}));
    }

    @Test
    void rejectsNumbersThatAreNotIntegers() {
        assertThrows(IllegalArgumentException.class, () -> AnimationDemo.caseFrom(new String[]{"NORMAL", "muchos"}));
        assertThrows(IllegalArgumentException.class, () -> AnimationDemo.caseFrom(new String[]{"NORMAL", "20", "x"}));
    }

    @Test
    void rejectsAmountsOfPatientsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> AnimationDemo.caseFrom(new String[]{"NORMAL", "0"}));
        assertThrows(IllegalArgumentException.class, () -> AnimationDemo.caseFrom(new String[]{"NORMAL", "1001"}));
        assertEquals(1, AnimationDemo.caseFrom(new String[]{"NORMAL", "1"}).patientCount());
        assertEquals(1000, AnimationDemo.caseFrom(new String[]{"NORMAL", "1000"}).patientCount());
    }
}
