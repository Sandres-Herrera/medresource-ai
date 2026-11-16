package com.medresource.services.management;

import com.medresource.model.CaseFixtures;
import com.medresource.model.ComparisonSaveResult;
import com.medresource.model.ComparisonType;
import com.medresource.model.ResultFixtures;
import com.medresource.model.RunComparison;
import com.medresource.model.RunRecord;
import com.medresource.model.SavedComparison;
import com.medresource.model.Specialty;
import com.medresource.persistence.ComparisonDao;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.ResultDao;
import com.medresource.services.simulation.CaseGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del archivo de comparaciones, con ejecuciones y comparaciones en memoria.
 *
 * <p>Las ejecuciones 1 y 2 son del caso 1 y solo cambia el hospital (la 2 tiene un cirujano extra).</p>
 */
class ComparisonArchiveTest {

    private final InMemoryResults results = new InMemoryResults();
    private final InMemoryComparisons comparisons = new InMemoryComparisons();
    private final ComparisonArchive archive = new ComparisonArchive(new SimulationService(
            new CaseService(new InMemoryCaseDao(), new CaseGenerator(CaseFixtures.diseases())), results),
            comparisons);

    ComparisonArchiveTest() {
        results.stored.put(1, ResultFixtures.runOf(1, "FIFO", 77));
        results.stored.put(2, new RunRecord(1, ResultFixtures.HOSPITAL.withExtraDoctors(List.of(Specialty.SURGERY)),
                ResultFixtures.resultOf("FIFO", 77), 1));
    }

    @Test
    void comparingDoesNotSaveAnything() {
        RunComparison comparison = archive.compare(1, 2);

        assertEquals(ComparisonType.RESOURCES, comparison.type());
        assertTrue(comparisons.stored.isEmpty());
    }

    @Test
    void savingKeepsTheComparisonAndItCanBeOpenedAgain() {
        RunComparison comparison = archive.compare(1, 2);

        ComparisonSaveResult result = archive.save(comparison);

        assertFalse(result.alreadySaved());
        assertEquals(comparison, result.saved().comparison());
        assertEquals(Optional.of(result.saved()), archive.find(result.saved().id()));
        assertEquals(List.of(result.saved()), archive.saved());
    }

    @Test
    void savingTheSamePairAgainReturnsTheOneThatWasAlreadySaved() {
        SavedComparison first = archive.save(archive.compare(1, 2)).saved();

        ComparisonSaveResult again = archive.save(archive.compare(2, 1));

        assertTrue(again.alreadySaved());
        assertEquals(first, again.saved());
        assertEquals(1, comparisons.stored.size());
    }

    @Test
    void deletingAComparisonKeepsTheRuns() {
        SavedComparison saved = archive.save(archive.compare(1, 2)).saved();

        archive.delete(saved.id());

        assertTrue(archive.saved().isEmpty());
        assertEquals(2, results.stored.size());
    }

    @Test
    void aComparisonThatIsNotValidIsExplained() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> archive.compare(1, 1));

        assertTrue(error.getMessage().startsWith("Elige dos ejecuciones distintas"));
    }

    @Test
    void nothingCanBeMissing() {
        SimulationService runs = new SimulationService(
                new CaseService(new InMemoryCaseDao(), new CaseGenerator(CaseFixtures.diseases())), results);

        assertThrows(IllegalArgumentException.class, () -> new ComparisonArchive(null, comparisons));
        assertThrows(IllegalArgumentException.class, () -> new ComparisonArchive(runs, null));
        assertThrows(IllegalArgumentException.class, () -> archive.save(null));
    }

    // ---------- apoyos ----------

    /** Comparaciones guardadas en memoria, como las guarda el DAO: un par se guarda una sola vez. */
    private static final class InMemoryComparisons implements ComparisonDao {

        final Map<Integer, SavedComparison> stored = new TreeMap<>();
        private LocalDateTime clock = LocalDateTime.of(2026, 11, 16, 12, 0);

        @Override
        public SavedComparison save(RunComparison comparison) {
            Optional<SavedComparison> existing = findByRuns(comparison.runA(), comparison.runB());
            if (existing.isPresent()) {
                return existing.get();
            }
            SavedComparison saved = new SavedComparison(stored.size() + 1, clock, comparison);
            clock = clock.plusMinutes(1);
            stored.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<SavedComparison> findById(int id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<SavedComparison> findByRuns(int runA, int runB) {
            return stored.values().stream().filter(saved -> Math.min(saved.comparison().runA(),
                    saved.comparison().runB()) == Math.min(runA, runB) && Math.max(saved.comparison().runA(),
                    saved.comparison().runB()) == Math.max(runA, runB)).findFirst();
        }

        @Override
        public List<SavedComparison> findAll() {
            List<SavedComparison> all = new ArrayList<>(stored.values());
            all.sort(Comparator.comparing(SavedComparison::savedAt).reversed());
            return all;
        }

        @Override
        public void deleteById(int id) {
            if (stored.remove(id) == null) {
                throw new DataAccessException("No existe la comparación " + id + ".");
            }
        }
    }

    /** Ejecuciones guardadas en memoria; solo hace falta buscarlas. */
    private static final class InMemoryResults implements ResultDao {

        final Map<Integer, RunRecord> stored = new TreeMap<>();

        @Override
        public Integer save(RunRecord run) {
            int id = stored.size() + 1;
            stored.put(id, run);
            return id;
        }

        @Override
        public List<Integer> findIdsByCase(int caseId) {
            return stored.entrySet().stream().filter(entry -> entry.getValue().caseId() == caseId)
                    .map(Map.Entry::getKey).toList();
        }

        @Override
        public Optional<RunRecord> findById(Integer id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<RunRecord> findAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public void update(RunRecord run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            stored.remove(id);
        }
    }
}
