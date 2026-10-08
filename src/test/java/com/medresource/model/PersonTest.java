package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los datos comunes de {@link Person}.
 */
class PersonTest {

    /** Persona mínima para probar la clase abstracta. */
    private static final class TestPerson extends Person {

        TestPerson(int id, int age) {
            super(id, "T" + id, age, Sex.FEMALE);
        }

        @Override
        public String describe() {
            return getCode();
        }
    }

    @Test
    void validPersonKeepsItsData() {
        Person person = new TestPerson(7, 30);
        assertEquals(7, person.getId());
        assertEquals("T7", person.getCode());
        assertEquals(30, person.getAge());
        assertEquals(Sex.FEMALE, person.getSex());
    }

    @Test
    void invalidIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TestPerson(0, 30));
    }

    @Test
    void ageOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TestPerson(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new TestPerson(1, Person.MAX_AGE + 1));
    }

    @Test
    void minorIsUnderFifteen() {
        assertTrue(new TestPerson(1, 14).isMinor());
        assertFalse(new TestPerson(2, 15).isMinor());
    }

    @Test
    void personsWithSameTypeAndIdAreEqual() {
        assertEquals(new TestPerson(5, 20), new TestPerson(5, 60));
        assertNotEquals(new TestPerson(5, 20), new TestPerson(6, 20));
    }
}