package com.medresource.ui.controller;

import java.util.OptionalInt;

/**
 * Rondas de enfermería: a qué paciente hospitalizado va a ver cada enfermero libre en cada minuto.
 *
 * <p>El motor no le asigna un enfermero a cada cama: después del ingreso, el paciente queda a cargo de la
 * enfermería en general. Para que eso se vea, los enfermeros libres hacen rondas: el tiempo se parte en turnos
 * de {@value #VISIT_MINUTES} minutos y, en cada turno, la mitad de los enfermeros libres visita a un paciente
 * mientras la otra mitad descansa en la sala del personal; en el turno siguiente se cambian. Cada vez que un
 * enfermero vuelve a salir de ronda va al paciente siguiente, y dos enfermeros que salen al mismo tiempo van a
 * pacientes distintos; si hay más enfermeros que pacientes, los que sobran se quedan descansando.</p>
 *
 * <p>Es solo de la vista: el enfermero sigue libre para el motor, que lo puede llamar en cualquier momento.</p>
 */
public final class NursingRounds {

    /** Minutos que dura cada turno de visita o de descanso (supuesto visual, ajustable). */
    public static final int VISIT_MINUTES = 3;

    private NursingRounds() {
    }

    /**
     * Paciente que visita un enfermero libre en un minuto.
     *
     * @param nurseIndex posición del enfermero entre los enfermeros libres, desde 0
     * @param minute     minuto que se ve
     * @param patients   cantidad de pacientes hospitalizados que se pueden visitar
     * @return posición del paciente que visita, desde 0; vacío si en ese turno descansa
     * @throws IllegalArgumentException si algún dato es negativo
     */
    public static OptionalInt visit(int nurseIndex, int minute, int patients) {
        if (nurseIndex < 0 || minute < 0 || patients < 0) {
            throw new IllegalArgumentException("La posición, el minuto y los pacientes no pueden ser negativos.");
        }
        int turn = minute / VISIT_MINUTES;
        if ((turn + nurseIndex) % 2 != 0) {
            return OptionalInt.empty();
        }
        int partner = nurseIndex / 2;
        if (partner >= patients) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((partner + turn / 2) % patients);
    }
}
