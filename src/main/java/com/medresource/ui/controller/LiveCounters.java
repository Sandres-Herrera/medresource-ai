package com.medresource.ui.controller;

/**
 * Contadores que la ventana de simulación muestra en vivo.
 *
 * @param minute              minuto simulado
 * @param totalPatients       pacientes del caso
 * @param arrived             pacientes que ya llegaron
 * @param waiting             pacientes en la sala de espera
 * @param inTreatment         pacientes en tratamiento fuera de la UCI
 * @param inIcu               pacientes en la UCI
 * @param discharged          pacientes dados de alta o estabilizados
 * @param referred            pacientes remitidos
 * @param deceased            pacientes fallecidos
 * @param unattended          pacientes que se fueron sin atención
 * @param freeBeds            camas de hospitalización libres
 * @param totalBeds           camas de hospitalización
 * @param freeIcuBeds         camas de UCI libres
 * @param totalIcuBeds        camas de UCI
 * @param freeOperatingRooms  quirófanos libres
 * @param totalOperatingRooms quirófanos
 * @param freeDoctors         médicos libres
 * @param totalDoctors        médicos
 * @param freeNurses          enfermeros libres
 * @param totalNurses         enfermeros
 * @param bloodUnits          unidades de sangre que quedan
 * @param morgueOccupancy     cuerpos en la morgue
 * @param morgueCapacity      cupos de la morgue
 */
public record LiveCounters(int minute, int totalPatients, int arrived, int waiting, int inTreatment, int inIcu,
                           int discharged, int referred, int deceased, int unattended,
                           int freeBeds, int totalBeds, int freeIcuBeds, int totalIcuBeds,
                           int freeOperatingRooms, int totalOperatingRooms, int freeDoctors, int totalDoctors,
                           int freeNurses, int totalNurses, int bloodUnits, int morgueOccupancy,
                           int morgueCapacity) {

    /** Pacientes que ya terminaron su caso (alta, remisión, muerte o sin atención). */
    public int finished() {
        return discharged + referred + deceased + unattended;
    }

    /** Avance de la simulación en porcentaje, según los pacientes que ya terminaron. */
    public int progressPercent() {
        return totalPatients == 0 ? 100 : (int) Math.round(100.0 * finished() / totalPatients);
    }
}

