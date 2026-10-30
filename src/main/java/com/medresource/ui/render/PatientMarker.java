package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;

/**
 * Lo que hace falta para dibujar un paciente en un instante: quién es, cómo se ve y dónde está.
 *
 * @param code     código del paciente
 * @param triage   nivel de triage, que da el color; puede ser nulo si todavía no tiene
 * @param sex      sexo, que decide la figura
 * @param minor    {@code true} si es menor de edad, que se dibuja más pequeño
 * @param deceased {@code true} si falleció, que se dibuja en gris
 * @param x        posición horizontal en el mundo, en píxeles
 * @param y        posición vertical en el mundo, en píxeles
 */
public record PatientMarker(String code, TriageLevel triage, Sex sex, boolean minor, boolean deceased,
                            double x, double y) {

    /**
     * Valida el paciente.
     *
     * @throws IllegalArgumentException si falta el código o el sexo
     */
    public PatientMarker {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código del paciente es obligatorio.");
        }
        if (sex == null) {
            throw new IllegalArgumentException("El sexo del paciente es obligatorio.");
        }
    }
}