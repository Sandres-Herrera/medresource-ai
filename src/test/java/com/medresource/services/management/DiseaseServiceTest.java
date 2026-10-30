package com.medresource.services.management;

import com.medresource.model.Disease;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.GenericDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.medresource.model.DiseaseFixtures.assertSameDisease;
import static com.medresource.model.DiseaseFixtures.full;
import static com.medresource.model.DiseaseFixtures.simple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiseaseServiceTest {

    // ---------- consultar ----------

    @Test
    void rejectsAMissingDao() {
        assertThrows(IllegalArgumentException.class, () -> new DiseaseService(null));
    }

    @Test
    void listsAnEmptyCatalog() {
        assertTrue(new DiseaseService(new InMemoryDao()).listAll().isEmpty());
    }

    @Test
    void listsTheDiseasesOrderedById() {
        InMemoryDao dao = new InMemoryDao(simple(5, "Gripa"), full(2, "Neumonía"));

        List<Disease> all = new DiseaseService(dao).listAll();

        assertEquals(2, all.get(0).getId());
        assertEquals(5, all.get(1).getId());
    }

    @Test
    void findsADiseaseById() {
        DiseaseService service = new DiseaseService(new InMemoryDao(full(2, "Neumonía")));

        assertSameDisease(full(2, "Neumonía"), service.find(2).orElseThrow());
        assertEquals(Optional.empty(), service.find(3));
    }

    @Test
    void nextIdIsOneForAnEmptyCatalog() {
        assertEquals(1, new DiseaseService(new InMemoryDao()).nextId());
    }

    @Test
    void nextIdIsTheLargestIdPlusOne() {
        DiseaseService service = new DiseaseService(new InMemoryDao(simple(3, "A"), simple(9, "B"), simple(4, "C")));

        assertEquals(10, service.nextId());
    }

    /** DAO en memoria: guarda las enfermedades en una lista y cuenta las modificaciones. */
    private static final class InMemoryDao implements GenericDao<Disease, Integer> {

        final List<Disease> stored = new ArrayList<>();
        int updates;
        boolean failOnDelete;

        InMemoryDao(Disease... initial) {
            stored.addAll(List.of(initial));
        }

        Disease byId(int id) {
            return stored.stream().filter(disease -> disease.getId() == id).findFirst().orElseThrow();
        }

        @Override
        public Integer save(Disease disease) {
            stored.add(disease);
            return disease.getId();
        }

        @Override
        public Optional<Disease> findById(Integer id) {
            return stored.stream().filter(disease -> disease.getId() == id).findFirst();
        }

        @Override
        public List<Disease> findAll() {
            return stored.stream().sorted(Comparator.comparingInt(Disease::getId)).toList();
        }

        @Override
        public void update(Disease disease) {
            updates++;
            stored.replaceAll(old -> old.getId() == disease.getId() ? disease : old);
        }

        @Override
        public void deleteById(Integer id) {
            if (failOnDelete) {
                throw new DataAccessException("No se pudo borrar la enfermedad: restricción de llave foránea.");
            }
            stored.removeIf(disease -> disease.getId() == id);
        }
    }
}