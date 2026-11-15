package com.medresource.model;

import java.util.List;
import java.util.Map;

/**
 * Atención que recibió un paciente: quién lo trató, con qué recursos, cuánta sangre se usó y cómo llegó.
 *
 * <p>El motor la registra al empezar el tratamiento. El reporte la usa para medir la carga de cada médico,
 * el consumo de sangre y cuánta salud perdió el paciente mientras esperaba.</p>
 *
 * @param patientCode       código del paciente, por ejemplo {@code P001}
 * @param recipientType     tipo de sangre del paciente
 * @param startMinute       minuto en que empezó el tratamiento
 * @param staffEndMinute    minuto en que el médico y los enfermeros quedaron libres
 * @param endMinute         minuto en que termina el tratamiento completo
 * @param doctorCode        código del médico, por ejemplo {@code D03}
 * @param doctorSpecialty   especialidad del médico
 * @param nurseCodes        códigos de los enfermeros que acompañaron la atención (puede no haber ninguno)
 * @param operatingRoomCode código del quirófano; {@code null} si no fue una cirugía
 * @param bedCode           código de la cama; {@code null} si no ocupó cama
 * @param icuBed            {@code true} si la cama es de UCI
 * @param bloodTaken        unidades retiradas del banco por tipo de donante; vacío si no hubo transfusión
 * @param healthAtStart     salud del paciente al empezar el tratamiento
 */
public record CareRecord(String patientCode, BloodType recipientType, int startMinute, int staffEndMinute,
                         int endMinute, String doctorCode, Specialty doctorSpecialty, List<String> nurseCodes,
                         String operatingRoomCode, String bedCode, boolean icuBed,
                         Map<BloodType, Integer> bloodTaken, double healthAtStart) {

    /**
     * Valida los datos y guarda copias inmutables de la lista y del mapa.
     *
     * @throws IllegalArgumentException si falta algún dato obligatorio, si los minutos no van en orden, si
     *                                  la cama de UCI no tiene código, si alguna cantidad de sangre no es
     *                                  positiva o si la salud está fuera de 0 a {@link Patient#MAX_HEALTH}
     */
    public CareRecord {
        if (patientCode == null || patientCode.isBlank() || doctorCode == null || doctorCode.isBlank()) {
            throw new IllegalArgumentException("El paciente y el médico son obligatorios.");
        }
        if (recipientType == null || doctorSpecialty == null || nurseCodes == null || bloodTaken == null) {
            throw new IllegalArgumentException("Faltan datos de la atención.");
        }
        if (startMinute < 0 || staffEndMinute < startMinute || endMinute < staffEndMinute) {
            throw new IllegalArgumentException("Los minutos de la atención deben ir en orden.");
        }
        if (icuBed && bedCode == null) {
            throw new IllegalArgumentException("Una cama de UCI debe tener código.");
        }
        if (bloodTaken.values().stream().anyMatch(units -> units == null || units <= 0)) {
            throw new IllegalArgumentException("Las unidades de sangre retiradas deben ser positivas.");
        }
        if (healthAtStart < 0 || healthAtStart > Patient.MAX_HEALTH) {
            throw new IllegalArgumentException("La salud al empezar la atención no es válida.");
        }
        nurseCodes = List.copyOf(nurseCodes);
        bloodTaken = Map.copyOf(bloodTaken);
    }

    /**
     * Suma las unidades de sangre que se usaron en la atención.
     *
     * @return unidades retiradas del banco; 0 si no hubo transfusión
     */
    public int bloodUnits() {
        return bloodTaken.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Indica si se usó sangre O negativo, el donante universal.
     *
     * @return {@code true} si se retiró al menos una unidad de O negativo
     */
    public boolean usedUniversalDonor() {
        return bloodTaken.containsKey(BloodType.O_NEGATIVE);
    }

    /**
     * Calcula cuántos minutos estuvo ocupado el personal con esta atención.
     *
     * @return minutos entre el inicio y la liberación del médico
     */
    public int staffMinutes() {
        return staffEndMinute - startMinute;
    }
}
