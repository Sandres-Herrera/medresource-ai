package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los síntomas que le impiden caminar a un paciente.
 */
class SymptomTest {

    @Test
    void anUnconsciousPatientOrOneWithABrokenLimbCannotWalk() {
        assertTrue(Symptom.UNCONSCIOUS.preventsWalking());
        assertTrue(Symptom.UNABLE_TO_WALK.preventsWalking());
        assertTrue(Symptom.EXPOSED_BONE.preventsWalking());
        assertTrue(Symptom.LIMB_DEFORMITY.preventsWalking());
    }

    @Test
    void theOtherSymptomsLetThePatientWalk() {
        Set<Symptom> walking = EnumSet.complementOf(EnumSet.of(Symptom.UNCONSCIOUS, Symptom.UNABLE_TO_WALK,
                Symptom.EXPOSED_BONE, Symptom.LIMB_DEFORMITY));

        assertEquals(12, walking.size());
        walking.forEach(symptom -> assertFalse(symptom.preventsWalking(), symptom.name()));
    }
}
