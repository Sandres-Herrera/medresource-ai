package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.CaseFixtures;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.persistence.DataAccessException;
import com.medresource.services.simulation.CaseGenerator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.medresource.model.CaseFixtures.assertSameCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseServiceTest {

    private static final CaseConfig CONFIG = new CaseConfig(5, Scenario.NORMAL, 10, 120);

    private final InMemoryCaseDao dao = new InMemoryCaseDao();
    private final CaseService service = new CaseService(dao, new CaseGenerator(CaseFixtures.diseases()));

    @Test
    void rejectsMissingDependencies() {
        CaseGenerator generator = new CaseGenerator(CaseFixtures.diseases());

        assertThrows(IllegalArgumentException.class, () -> new CaseService(null, generator));
        assertThrows(IllegalArgumentException.class, () -> new CaseService(dao, null));
    }

    @Test
    void generatesAndSavesTheCaseTheFirstTimeAConfigurationIsAsked() {
        int id = service.idFor(CONFIG);

        assertEquals(1, id);
        assertEquals(1, dao.saved.size());
        assertEquals(CONFIG, dao.saved.get(0).config());
        assertEquals(10, dao.saved.get(0).profiles().size());
    }

    @Test
    void reusesTheSavedCaseTheNextTimes() {
        int first = service.idFor(CONFIG);
        int second = service.idFor(CONFIG);

        assertEquals(first, second);
        assertEquals(1, dao.saved.size());
    }

    @Test
    void savesADifferentCaseForADifferentConfiguration() {
        int first = service.idFor(CONFIG);
        int second = service.idFor(new CaseConfig(6, Scenario.NORMAL, 10, 120));

        assertEquals(1, first);
        assertEquals(2, second);
    }

    @Test
    void theSavedCaseIsTheOneTheGeneratorProducesForTheSeed() {
        int id = service.idFor(CONFIG);

        assertSameCase(new CaseGenerator(CaseFixtures.diseases()).generate(CONFIG), service.find(id).orElseThrow());
    }

    @Test
    void doesNotAskForANullConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> service.idFor(null));
    }

    @Test
    void findsNothingForAMissingCase() {
        assertEquals(Optional.empty(), service.find(3));
    }

    @Test
    void deletesAnExistingCase() {
        int id = service.idFor(CONFIG);

        service.delete(id);

        assertTrue(dao.saved.isEmpty());
        assertEquals(Optional.empty(), service.find(id));
    }

    @Test
    void doesNotDeleteAMissingCase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.delete(4));

        assertTrue(error.getMessage().contains("identificador 4"));
    }

    @Test
    void aDatabaseFailureWhileSavingReachesTheCaller() {
        dao.failOnSave = true;

        assertThrows(DataAccessException.class, () -> service.idFor(CONFIG));
    }
}