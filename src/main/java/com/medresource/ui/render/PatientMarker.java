package com.medresource.ui.render;

import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;

/**
 * Lo que hace falta para dibujar un paciente en un instante: quién es, cómo se ve, dónde está, cuánta salud le
 * queda, cuánto lleva de su tratamiento y si va acostado en una camilla.
 *
 * @param code     código del paciente
 * @param triage   nivel de triage, que da el color; puede ser nulo si todavía no tiene
 * @param sex      sexo, que decide la figura
 * @param minor    {@code true} si es menor de edad, que se dibuja más pequeño
 * @param deceased {@code true} si falleció, que se dibuja en gris
 * @param x        posición horizontal en el mundo, en píxeles
 * @param y        posición vertical en el mundo, en píxeles
 * @param health   salud del paciente, de 0 a {@value Patient#MAX_HEALTH}
 * @param progress parte de su tratamiento que ya pasó, de 0 a 1; {@value #NO_PROGRESS} si no está en tratamiento
 * @param onStretcher {@code true} si está acostado en una camilla: falleció o no puede caminar
 * @param moving      {@code true} si va de un lugar a otro; en camilla, un camillero la empuja
 */
public record PatientMarker(String code, TriageLevel triage, Sex sex, boolean minor, boolean deceased,
                            double x, double y, double health, double progress, boolean onStretcher,
                            boolean moving) {

    /** Valor de {@link #progress()} para un paciente que no está en tratamiento. */
    public static final double NO_PROGRESS = -1.0;

    /**
     * Valida el paciente.
     *
     * @throws IllegalArgumentException si falta el código o el sexo, si la salud no está entre 0 y
     *                                  {@value Patient#MAX_HEALTH}, o si el avance no está entre 0 y 1 ni es
     *                                  {@value #NO_PROGRESS}
     */
    public PatientMarker {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código del paciente es obligatorio.");
        }
        if (sex == null) {
            throw new IllegalArgumentException("El sexo del paciente es obligatorio.");
        }
        if (!(health >= 0.0 && health <= Patient.MAX_HEALTH)) {
            throw new IllegalArgumentException("La salud debe estar entre 0 y " + Patient.MAX_HEALTH + ".");
        }
        if (progress != NO_PROGRESS && !(progress >= 0.0 && progress <= 1.0)) {
            throw new IllegalArgumentException("El avance del tratamiento debe estar entre 0 y 1.");
        }
    }

    /**
     * Crea un paciente con la salud completa y sin tratamiento en curso.
     *
     * @param code     código del paciente
     * @param triage   nivel de triage; puede ser nulo
     * @param sex      sexo
     * @param minor    {@code true} si es menor de edad
     * @param deceased {@code true} si falleció
     * @param x        posición horizontal en el mundo, en píxeles
     * @param y        posición vertical en el mundo, en píxeles
     * @throws IllegalArgumentException si falta el código o el sexo
     */
    public PatientMarker(String code, TriageLevel triage, Sex sex, boolean minor, boolean deceased, double x,
                         double y) {
        this(code, triage, sex, minor, deceased, x, y, Patient.MAX_HEALTH, NO_PROGRESS);
    }

    /**
     * Crea un paciente de pie y quieto, con su salud y el avance de su tratamiento.
     *
     * @param code     código del paciente
     * @param triage   nivel de triage; puede ser nulo
     * @param sex      sexo
     * @param minor    {@code true} si es menor de edad
     * @param deceased {@code true} si falleció
     * @param x        posición horizontal en el mundo, en píxeles
     * @param y        posición vertical en el mundo, en píxeles
     * @param health   salud, de 0 a {@value Patient#MAX_HEALTH}
     * @param progress avance del tratamiento, de 0 a 1, o {@value #NO_PROGRESS}
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public PatientMarker(String code, TriageLevel triage, Sex sex, boolean minor, boolean deceased, double x,
                         double y, double health, double progress) {
        this(code, triage, sex, minor, deceased, x, y, health, progress, false, false);
    }

    /** Indica si un camillero lo está llevando: va en camilla y se está moviendo. */
    public boolean isCarried() {
        return onStretcher && moving;
    }

    /** Indica si el paciente está en tratamiento, así que tiene un avance que mostrar. */
    public boolean inTreatment() {
        return progress != NO_PROGRESS;
    }
}
