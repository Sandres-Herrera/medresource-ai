package com.medresource.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Morgue del hospital, con cupos finitos.
 */
public class Morgue {

    /** Minutos que un cuerpo permanece en la morgue (supuesto de la simulación: 4 horas). */
    public static final int RETENTION_MINUTES = 240;

    private final int capacity;
    private final Map<Patient, Integer> releaseMinutes = new LinkedHashMap<>();

    /**
     * @param capacity número máximo de cuerpos, al menos 1
     * @throws IllegalArgumentException si la capacidad es menor que 1
     */
    public Morgue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("La morgue debe tener al menos un cupo.");
        }
        this.capacity = capacity;
    }

    public boolean hasSpace() {
        return releaseMinutes.size() < capacity;
    }

    /**
     * Ingresa un cuerpo y programa su retiro {@link #RETENTION_MINUTES} minutos después.
     *
     * @param patient paciente fallecido
     * @param now     minuto actual de la simulación
     * @throws IllegalArgumentException si el paciente es nulo, no ha fallecido o el minuto es negativo
     * @throws IllegalStateException    si el paciente ya está en la morgue o no hay cupo
     */
    public void admit(Patient patient, int now) {
        if (patient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        if (patient.getStatus() != PatientStatus.DECEASED) {
            throw new IllegalArgumentException(patient.getCode() + " no ha fallecido.");
        }
        if (now < 0) {
            throw new IllegalArgumentException("El minuto actual no puede ser negativo.");
        }
        if (releaseMinutes.containsKey(patient)) {
            throw new IllegalStateException(patient.getCode() + " ya está en la morgue.");
        }
        if (!hasSpace()) {
            throw new IllegalStateException("La morgue está llena.");
        }
        releaseMinutes.put(patient, now + RETENTION_MINUTES);
    }

    /**
     * Retira los cuerpos cuyo tiempo ya se cumplió.
     *
     * @param now minuto actual de la simulación
     * @return cuerpos retirados, en el orden en que ingresaron
     */
    public List<Patient> releaseDue(int now) {
        List<Patient> due = releaseMinutes.entrySet().stream()
                .filter(entry -> entry.getValue() <= now)
                .map(Map.Entry::getKey)
                .toList();
        due.forEach(releaseMinutes::remove);
        return due;
    }

    public int occupancy() {
        return releaseMinutes.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public List<Patient> getBodies() {
        return List.copyOf(releaseMinutes.keySet());
    }
}