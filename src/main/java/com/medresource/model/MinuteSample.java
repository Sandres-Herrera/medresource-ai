package com.medresource.model;

import java.util.Map;

/**
 * Foto del hospital al final de un minuto de la simulación: cuántos pacientes había en cada etapa, qué
 * recursos estaban ocupados y por qué seguían esperando los que esperaban.
 *
 * <p>Con una foto por minuto el reporte sabe cuándo se llenó cada recurso, cuándo colapsó el servicio y
 * cuándo se recuperó.</p>
 *
 * @param minute             minuto de la simulación
 * @param inTriage           pacientes en la fila del triage o en valoración
 * @param waiting            pacientes en la sala de espera (incluidos los que esperan en una cama con
 *                           primeros auxilios)
 * @param inTreatment        pacientes en tratamiento o en recuperación
 * @param overTarget         pacientes sin tratamiento que ya pasaron la meta de espera de su nivel de triage
 * @param busyBeds           camas de hospitalización ocupadas
 * @param busyIcuBeds        camas de UCI ocupadas
 * @param busyOperatingRooms quirófanos ocupados
 * @param busyDoctors        médicos ocupados
 * @param busyNurses         enfermeros ocupados
 * @param morgueBodies       cuerpos en la morgue
 * @param blocked            pacientes a los que les faltaba cada recurso; solo aparecen los recursos que
 *                           le faltaban a alguien
 * @param bloodUnits         unidades de sangre que quedaban de cada tipo
 */
public record MinuteSample(int minute, int inTriage, int waiting, int inTreatment, int overTarget, int busyBeds,
                           int busyIcuBeds, int busyOperatingRooms, int busyDoctors, int busyNurses,
                           int morgueBodies, Map<MissingResource, Integer> blocked,
                           Map<BloodType, Integer> bloodUnits) {

    /**
     * Valida los datos y guarda copias inmutables de los mapas.
     *
     * @throws IllegalArgumentException si algún número es negativo, si faltan los mapas o si los pacientes
     *                                  fuera de su meta son más que los que esperan
     */
    public MinuteSample {
        if (minute < 0 || inTriage < 0 || waiting < 0 || inTreatment < 0 || overTarget < 0 || busyBeds < 0
                || busyIcuBeds < 0 || busyOperatingRooms < 0 || busyDoctors < 0 || busyNurses < 0
                || morgueBodies < 0) {
            throw new IllegalArgumentException("Los números de una foto del hospital no pueden ser negativos.");
        }
        if (blocked == null || bloodUnits == null) {
            throw new IllegalArgumentException("Faltan los datos de la foto del hospital.");
        }
        if (overTarget > inTriage + waiting) {
            throw new IllegalArgumentException("No puede haber más pacientes fuera de su meta que esperando.");
        }
        if (blocked.values().stream().anyMatch(count -> count == null || count < 0)
                || bloodUnits.values().stream().anyMatch(units -> units == null || units < 0)) {
            throw new IllegalArgumentException("Las cantidades de una foto del hospital no pueden ser negativas.");
        }
        blocked = Map.copyOf(blocked);
        bloodUnits = Map.copyOf(bloodUnits);
    }

    /**
     * Cuenta los pacientes a los que les faltaba un recurso.
     *
     * @param resource recurso
     * @return pacientes que esperaban por él; 0 si no le faltaba a nadie
     */
    public int blockedBy(MissingResource resource) {
        return blocked.getOrDefault(resource, 0);
    }

    /**
     * Devuelve las unidades que quedaban de un tipo de sangre.
     *
     * @param type tipo de sangre
     * @return unidades disponibles; 0 si el tipo no aparece
     */
    public int bloodOf(BloodType type) {
        return bloodUnits.getOrDefault(type, 0);
    }

    /**
     * Cuenta todos los pacientes que esperaban, en el triage o en la sala de espera.
     *
     * @return pacientes sin tratamiento
     */
    public int totalWaiting() {
        return inTriage + waiting;
    }
}
