package com.medresource.services.strategy;

import com.medresource.model.Hospital;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static com.medresource.services.strategy.StrategyFixtures.waiting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la estrategia aleatoria.
 */
class RandomStrategyTest {

    private static List<WaitingPatient> twentyPatients() {
        List<WaitingPatient> patients = new ArrayList<>();
        for (int id = 1; id <= 20; id++) {
            patients.add(waiting(id, 0, GENERAL_FLU, 30));
        }
        return patients;
    }

    private static int chosen(RandomStrategy strategy, int minute, List<WaitingPatient> patients) {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        return strategy.nextAssignment(minute, patients, hospital).orElseThrow().patient().getId();
    }

    @Test
    void hasAName() {
        assertFalse(new RandomStrategy(1).getName().isBlank());
    }

    @Test
    void theSameSeedAndMinuteAlwaysChooseTheSamePatient() {
        List<WaitingPatient> patients = twentyPatients();

        for (int minute = 0; minute < 10; minute++) {
            assertEquals(chosen(new RandomStrategy(7), minute, patients),
                    chosen(new RandomStrategy(7), minute, patients));
        }
    }

    @Test
    void differentSeedsChooseDifferentPatients() {
        List<WaitingPatient> patients = twentyPatients();
        Set<Integer> chosen = new HashSet<>();

        for (long seed = 0; seed < 30; seed++) {
            chosen.add(chosen(new RandomStrategy(seed), 10, patients));
        }

        assertTrue(chosen.size() >= 5, "eligió solo " + chosen.size() + " pacientes distintos");
    }

    @Test
    void differentMinutesChooseDifferentPatients() {
        List<WaitingPatient> patients = twentyPatients();
        RandomStrategy strategy = new RandomStrategy(3);
        Set<Integer> chosen = new HashSet<>();

        for (int minute = 0; minute < 30; minute++) {
            chosen.add(chosen(strategy, minute, patients));
        }

        assertTrue(chosen.size() >= 5, "eligió solo " + chosen.size() + " pacientes distintos");
    }

    @Test
    void theOrderOfTheListDoesNotMatter() {
        List<WaitingPatient> patients = twentyPatients();
        List<WaitingPatient> reversed = new ArrayList<>(patients);
        Collections.reverse(reversed);

        for (int minute = 0; minute < 10; minute++) {
            assertEquals(chosen(new RandomStrategy(5), minute, patients),
                    chosen(new RandomStrategy(5), minute, reversed));
        }
    }

    @Test
    void ignoresTheArrivalOrder() {
        List<WaitingPatient> patients = new ArrayList<>();
        for (int id = 1; id <= 20; id++) {
            patients.add(waiting(id, id * 10, GENERAL_FLU, 30));
        }
        Set<Integer> chosen = new HashSet<>();

        for (long seed = 0; seed < 30; seed++) {
            chosen.add(chosen(new RandomStrategy(seed), 300, patients));
        }

        assertTrue(chosen.size() >= 5);
    }

    @Test
    void givesNothingWhenNobodyCanBeTreated() {
        Hospital noGeneralDoctor = hospital(Specialty.PEDIATRICS);

        assertTrue(new RandomStrategy(1).nextAssignment(10, twentyPatients(), noGeneralDoctor).isEmpty());
    }
}