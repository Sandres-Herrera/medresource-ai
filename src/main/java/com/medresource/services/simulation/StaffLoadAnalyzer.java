package com.medresource.services.simulation;

import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.MissingResource;
import com.medresource.model.Nurse;
import com.medresource.model.PatientOutcome;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.Specialty;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.StaffLoad;

import java.util.ArrayList;
import java.util.List;

/**
 * Mide la carga de trabajo de cada médico y enfermero, y la de cada especialidad.
 *
 * <p>La ocupación de una persona es la parte del tiempo de la simulación que estuvo ocupada (tratamientos y
 * primeros auxilios). Sus pacientes son las atenciones en que participó. La espera por médico de una
 * especialidad suma los minutos que esperaron por un médico los pacientes cuya enfermedad tiene esa
 * especialidad como principal: así se ve qué especialistas faltaron.</p>
 */
public class StaffLoadAnalyzer {

    /**
     * Calcula la carga de cada médico y de cada enfermero.
     *
     * @param trace    traza de la simulación
     * @param hospital hospital simulado
     * @return carga de los médicos (en su orden) y después la de los enfermeros
     * @throws IllegalArgumentException si falta la traza o el hospital
     */
    public List<StaffLoad> staff(SimulationTrace trace, Hospital hospital) {
        requireData(trace, hospital);
        int minutes = Math.max(1, trace.samples().size());
        List<StaffLoad> loads = new ArrayList<>();
        for (Doctor doctor : hospital.getDoctors()) {
            String code = doctor.getCode();
            int patients = (int) trace.cares().stream().filter(care -> care.doctorCode().equals(code)).count();
            loads.add(loadOf(code, doctor.getSpecialty(), patients, trace, minutes));
        }
        for (Nurse nurse : hospital.getNurses()) {
            String code = nurse.getCode();
            int patients = (int) trace.cares().stream().filter(care -> care.nurseCodes().contains(code)).count();
            loads.add(loadOf(code, null, patients, trace, minutes));
        }
        return loads;
    }

    /**
     * Calcula la carga de cada especialidad: las que tiene el hospital y las que a algún paciente le faltaron.
     *
     * @param result   resultado de la simulación
     * @param trace    traza de la misma simulación
     * @param hospital hospital simulado
     * @return carga de cada especialidad, en el orden de {@link Specialty}
     * @throws IllegalArgumentException si falta el resultado, la traza o el hospital
     */
    public List<SpecialtyLoad> specialties(SimulationResult result, SimulationTrace trace, Hospital hospital) {
        if (result == null) {
            throw new IllegalArgumentException("El resultado de la simulación es obligatorio.");
        }
        requireData(trace, hospital);
        List<StaffLoad> doctors = staff(trace, hospital).stream().filter(StaffLoad::isDoctor).toList();
        List<SpecialtyLoad> loads = new ArrayList<>();
        for (Specialty specialty : Specialty.values()) {
            List<StaffLoad> ofSpecialty = doctors.stream().filter(load -> load.specialty() == specialty).toList();
            int patients = (int) trace.cares().stream()
                    .filter(care -> care.doctorSpecialty() == specialty)
                    .count();
            double occupancy = ofSpecialty.stream().mapToDouble(StaffLoad::occupancy).average().orElse(0.0);
            int waitMinutes = result.outcomes().stream()
                    .filter(outcome -> outcome.disease().getMainSpecialty() == specialty)
                    .mapToInt(outcome -> doctorWaitOf(outcome, trace))
                    .sum();
            if (!ofSpecialty.isEmpty() || waitMinutes > 0) {
                loads.add(new SpecialtyLoad(specialty, ofSpecialty.size(), patients, occupancy, waitMinutes));
            }
        }
        return loads;
    }

    private static StaffLoad loadOf(String code, Specialty specialty, int patients, SimulationTrace trace,
                                    int minutes) {
        int busy = trace.busyMinutesOf(code);
        return new StaffLoad(code, specialty, patients, busy, Math.min(1.0, busy / (double) minutes));
    }

    private static int doctorWaitOf(PatientOutcome outcome, SimulationTrace trace) {
        return trace.blockedMinutesOf(outcome.patientCode()).getOrDefault(MissingResource.DOCTOR, 0);
    }

    private static void requireData(SimulationTrace trace, Hospital hospital) {
        if (trace == null || hospital == null) {
            throw new IllegalArgumentException("La traza y el hospital son obligatorios.");
        }
    }
}
