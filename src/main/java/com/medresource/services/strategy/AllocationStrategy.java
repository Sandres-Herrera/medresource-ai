package com.medresource.services.strategy;

import com.medresource.model.Hospital;

import java.util.List;
import java.util.Optional;

/**
 * Forma de decidir qué paciente recibe qué recursos del hospital.
 *
 * <p>Todas las estrategias (FIFO, prioridad clínica e inteligencia artificial)
 * se usan igual y se pueden intercambiar. El motor de simulación le pide una
 * decisión cada vez, aplica los recursos y vuelve a preguntar hasta que ya no
 * haya más decisiones posibles en ese minuto.</p>
 */
public interface AllocationStrategy {

    /**
     * Devuelve el nombre que se muestra al usuario.
     *
     * @return nombre en español
     */
    String getName();

    /**
     * Decide a qué paciente atender ahora y con qué recursos.
     *
     * <p>Solo debe mirar los recursos libres del hospital y no modificarlo.
     * Como el motor ocupa los recursos después de cada decisión, la siguiente
     * llamada ya no los ve libres.</p>
     *
     * @param minute   minuto actual de la simulación
     * @param waiting  pacientes en espera con su diagnóstico
     * @param hospital hospital con sus recursos
     * @return la decisión, o vacío si ningún paciente puede ser atendido ahora
     * @throws IllegalArgumentException si la lista de espera o el hospital son nulos
     */
    Optional<Assignment> nextAssignment(int minute, List<WaitingPatient> waiting, Hospital hospital);
}