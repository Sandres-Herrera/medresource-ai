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
 */
public record StaffMarker(String code, Specialty specialty, Sex sex, boolean seated, double x, double y) {

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

    /** Indica si es un médico (tiene especialidad) y no un enfermero. */
    public boolean isDoctor() {
        return specialty != null;
    }
}
