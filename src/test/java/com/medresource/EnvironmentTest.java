package com.medresource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de humo: comprueba que el entorno de desarrollo cumple los requisitos
 * mínimos del proyecto.
 */
class EnvironmentTest {

    private static final int REQUIRED_JAVA_VERSION = 25;

    @Test
    void runsOnRequiredJavaVersion() {
        assertTrue(Runtime.version().feature() >= REQUIRED_JAVA_VERSION,
                "El proyecto requiere JDK " + REQUIRED_JAVA_VERSION + " o superior");
    }

    @Test
    void mainRunsWithoutErrors() {
        assertDoesNotThrow(() -> Main.main(new String[0]));
    }
}