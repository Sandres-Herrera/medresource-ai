package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.Specialty;

/**
 * Lo que hace falta para dibujar a un médico o enfermero en un instante: quién es, cómo se ve y dónde está.
 *
 * @param code      código del médico ({@code D01}) o del enfermero ({@code E01})
 * @param specialty especialidad del médico, que da el color de su distintivo; nula para un enfermero
 * @param sex       sexo, que decide la figura
 * @param seated    {@code true} si está sentado en su puesto de la sala del personal, donde se escribe su código
 * @param x         posición horizontal en el mundo, en píxeles
 * @param y         posición vertical en el mundo, en píxeles
 * @param bob       cuánto sube o baja la figura por el {@linkplain IdleMotion movimiento de reposo}, en píxeles;
 *                  no cambia el punto donde está
 * @param moving    {@code true} si va caminando de un lugar a otro; la vista 2.5D le mueve las piernas y los brazos
 */
public record StaffMarker(String code, Specialty specialty, Sex sex, boolean seated, double x, double y,
                          double bob, boolean moving) {

    /**
     * Valida el marcador.
     *
     * @throws IllegalArgumentException si falta el código o el sexo
     */
    public StaffMarker {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código del personal es obligatorio.");
        }
        if (sex == null) {
            throw new IllegalArgumentException("El sexo del personal es obligatorio.");
        }
    }

    /**
     * Crea un marcador sin movimiento de reposo.
     *
     * @param code      código del médico o del enfermero
     * @param specialty especialidad del médico; nula para un enfermero
     * @param sex       sexo
     * @param seated    {@code true} si está sentado en su puesto
     * @param x         posición horizontal en el mundo, en píxeles
     * @param y         posición vertical en el mundo, en píxeles
     * @throws IllegalArgumentException si falta el código o el sexo
     */
    public StaffMarker(String code, Specialty specialty, Sex sex, boolean seated, double x, double y) {
        this(code, specialty, sex, seated, x, y, 0.0, false);
    }

    /**
     * Crea un marcador de alguien que no va caminando.
     *
     * @param code      código del médico o del enfermero
     * @param specialty especialidad del médico; nula para un enfermero
     * @param sex       sexo
     * @param seated    {@code true} si está sentado en su puesto
     * @param x         posición horizontal en el mundo, en píxeles
     * @param y         posición vertical en el mundo, en píxeles
     * @param bob       cuánto sube o baja la figura por el movimiento de reposo, en píxeles
     * @throws IllegalArgumentException si falta el código o el sexo
     */
    public StaffMarker(String code, Specialty specialty, Sex sex, boolean seated, double x, double y, double bob) {
        this(code, specialty, sex, seated, x, y, bob, false);
    }

    /** Indica si es un médico (tiene especialidad) y no un enfermero. */
    public boolean isDoctor() {
        return specialty != null;
    }
}
