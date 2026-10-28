package com.medresource.services.strategy;

import com.medresource.model.Assignable;
import com.medresource.model.Bed;
import com.medresource.model.Doctor;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;

import java.util.ArrayList;
import java.util.List;

/**
 * Decisión de una estrategia: qué recursos recibe un paciente para empezar su
 * tratamiento.
 *
 * <p>Solo describe la decisión. Quien ocupa los recursos, retira la sangre y
 * calcula cuándo termina el tratamiento es el motor de simulación.</p>
 *
 * @param patient       paciente que será atendido
 * @param doctor        médico que lo atiende
 * @param nurses        enfermeros asignados; vacío si el tratamiento no los necesita
 * @param bed           cama de hospitalización o de UCI; {@code null} si no la necesita
 * @param operatingRoom quirófano; {@code null} si no lo necesita
 * @param bloodUnits    unidades de sangre compatible que se retiran; 0 si no necesita
 */
public record Assignment(Patient patient, Doctor doctor, List<Nurse> nurses, Bed bed,
                         OperatingRoom operatingRoom, int bloodUnits) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el paciente, el médico o la lista
     *                                  de enfermeros, o si las unidades de sangre son negativas
     */
    public Assignment {
        if (patient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        if (doctor == null) {
            throw new IllegalArgumentException("El médico es obligatorio.");
        }
        if (nurses == null) {
            throw new IllegalArgumentException("La lista de enfermeros es obligatoria.");
        }
        if (bloodUnits < 0) {
            throw new IllegalArgumentException("Las unidades de sangre no pueden ser negativas.");
        }
        nurses = List.copyOf(nurses);
    }

    /**
     * Lista todos los recursos que ocupa el tratamiento, para que el motor los
     * asigne y los libere juntos.
     *
     * @return médico, enfermeros, cama y quirófano, solo los que se necesitan
     */
    public List<Assignable> resources() {
        List<Assignable> resources = new ArrayList<>();
        resources.add(doctor);
        resources.addAll(nurses);
        if (bed != null) {
            resources.add(bed);
        }
        if (operatingRoom != null) {
            resources.add(operatingRoom);
        }
        return resources;
    }
}