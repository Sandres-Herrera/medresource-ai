package com.medresource.services.strategy;

import com.medresource.model.Bed;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;
import com.medresource.model.TreatmentType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Base de las estrategias que ordenan a los pacientes por prioridad.
 *
 * <p>Recorre la lista de espera en el orden que define cada estrategia y atiende
 * al primer paciente para el que haya recursos. Si un paciente no se puede
 * atender (por ejemplo, falta un quirófano), pasa al siguiente en lugar de dejar
 * recursos libres sin usar. Lo único que cambia entre estrategias es el orden:
 * la elección de recursos es igual para todas.</p>
 *
 * <p>Los recursos se eligen según el diagnóstico, nunca según la enfermedad real:
 * el mejor médico libre (especialidad principal antes que secundaria), y la
 * primera cama, quirófano y enfermeros libres que el tratamiento necesite.</p>
 */
public abstract class PriorityBasedStrategy implements AllocationStrategy {

    /** Unidades de sangre que consume un tratamiento que la requiere (supuesto). */
    public static final int BLOOD_UNITS_PER_TREATMENT = 2;

    /**
     * Define el orden de atención.
     *
     * @param minute minuto actual de la simulación
     * @return comparador que pone primero al paciente que se debe atender antes
     */
    protected abstract Comparator<WaitingPatient> priorityOrder(int minute);

    @Override
    public Optional<Assignment> nextAssignment(int minute, List<WaitingPatient> waiting, Hospital hospital) {
        if (waiting == null) {
            throw new IllegalArgumentException("La lista de espera es obligatoria.");
        }
        if (hospital == null) {
            throw new IllegalArgumentException("El hospital es obligatorio.");
        }
        return waiting.stream()
                .sorted(priorityOrder(minute))
                .map(candidate -> assignmentFor(candidate, hospital))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Busca los recursos que necesita un paciente.
     *
     * @param candidate paciente con su diagnóstico
     * @param hospital  hospital con sus recursos
     * @return la asignación, o vacío si falta algún recurso
     */
    protected Optional<Assignment> assignmentFor(WaitingPatient candidate, Hospital hospital) {
        Patient patient = candidate.patient();
        Disease diagnosis = candidate.diagnosis();
        TreatmentType treatment = diagnosis.getTreatment();

        Optional<Doctor> doctor = bestDoctor(hospital.freeDoctors(), diagnosis, patient.getAge());
        List<Nurse> freeNurses = hospital.freeNurses();
        if (doctor.isEmpty() || freeNurses.size() < treatment.getRequiredNurses()) {
            return Optional.empty();
        }

        Bed bed = null;
        if (diagnosis.requiresIcu() || treatment.requiresBed()) {
            List<Bed> beds = diagnosis.requiresIcu() ? hospital.freeIcuBeds() : hospital.freeBeds();
            if (beds.isEmpty()) {
                return Optional.empty();
            }
            bed = beds.get(0);
        }

        OperatingRoom operatingRoom = null;
        if (treatment.requiresOperatingRoom()) {
            List<OperatingRoom> rooms = hospital.freeOperatingRooms();
            if (rooms.isEmpty()) {
                return Optional.empty();
            }
            operatingRoom = rooms.get(0);
        }

        int bloodUnits = 0;
        if (diagnosis.requiresBlood()) {
            bloodUnits = BLOOD_UNITS_PER_TREATMENT;
            if (!hospital.getBloodBank().hasCompatibleUnits(patient.getBloodType(), bloodUnits)) {
                return Optional.empty();
            }
        }

        return Optional.of(new Assignment(patient, doctor.get(),
                freeNurses.subList(0, treatment.getRequiredNurses()), bed, operatingRoom, bloodUnits));
    }

    /** Elige el médico más efectivo; a igual efectividad, el primero de la lista. */
    private Optional<Doctor> bestDoctor(List<Doctor> freeDoctors, Disease diagnosis, int patientAge) {
        Doctor best = null;
        double bestEffectiveness = 0.0;
        for (Doctor doctor : freeDoctors) {
            CompatibilityLevel level = doctor.compatibilityWith(diagnosis, patientAge);
            if (level.isSuitable() && level.getEffectiveness() > bestEffectiveness) {
                best = doctor;
                bestEffectiveness = level.getEffectiveness();
            }
        }
        return Optional.ofNullable(best);
    }
}