package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del catálogo inicial de enfermedades {@link DiseaseCatalog}.
 */
class DiseaseCatalogTest {

    private final List<Disease> diseases = DiseaseCatalog.defaultDiseases();

    @Test
    void catalogHasElevenDiseases() {
        assertEquals(11, diseases.size());
    }

    @Test
    void idsAreUnique() {
        long uniqueIds = diseases.stream().map(Disease::getId).distinct().count();
        assertEquals(diseases.size(), uniqueIds);
    }

    @Test
    void catalogCannotBeModified() {
        assertThrows(UnsupportedOperationException.class, () -> diseases.add(diseases.get(0)));
    }

    @Test
    void findByIdReturnsTheRightDisease() {
        assertEquals("Infarto", DiseaseCatalog.findById(9).orElseThrow().getName());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertTrue(DiseaseCatalog.findById(999).isEmpty());
    }

    @Test
    void typicalTriageMatchesTheDesign() {
        assertEquals(TriageLevel.IV, triageOf(1));   // Gripa fuerte
        assertEquals(TriageLevel.III, triageOf(2));  // Dengue
        assertEquals(TriageLevel.II, triageOf(6));   // Trombosis venosa profunda
        assertEquals(TriageLevel.II, triageOf(7));   // Fractura expuesta
        assertEquals(TriageLevel.I, triageOf(9));    // Infarto
        assertEquals(TriageLevel.I, triageOf(10));   // Trauma craneal grave
    }

    @Test
    void onlyInfarctionAndHeadTraumaRequireIcu() {
        List<Integer> icuIds = diseases.stream()
                .filter(Disease::requiresIcu)
                .map(Disease::getId)
                .toList();
        assertEquals(List.of(9, 10), icuIds);
    }

    @Test
    void advancedCancerIsTheOnlyIncurableDisease() {
        List<String> incurable = diseases.stream()
                .filter(disease -> !disease.isCurable())
                .map(Disease::getName)
                .toList();
        assertEquals(List.of("Cáncer avanzado"), incurable);
    }

    @Test
    void bronchiolitisOnlyAffectsBabies() {
        Disease bronchiolitis = DiseaseCatalog.findById(3).orElseThrow();
        assertTrue(bronchiolitis.affectsAge(1));
        assertFalse(bronchiolitis.affectsAge(10));
    }

    private TriageLevel triageOf(int id) {
        return DiseaseCatalog.findById(id).orElseThrow().typicalTriage();
    }
}