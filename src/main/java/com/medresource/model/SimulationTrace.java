package com.medresource.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro detallado de una ejecución que el motor guarda mientras simula, además del
 * {@link SimulationResult}: con qué salud llegó cada paciente, cada atención, una foto del hospital por
 * minuto y por qué esperó cada paciente.
 *
 * <p>Con estos datos se arma el reporte de fin de simulación. Registrarlos no cambia ninguna decisión del
 * motor: con o sin traza, el resultado es el mismo.</p>
 *
 * @param arrivalHealth              salud de cada paciente al llegar, por código
 * @param cares                      atenciones, en el orden en que empezaron
 * @param samples                    una foto del hospital por minuto, en orden
 * @param blockedMinutes             minutos que cada paciente esperó por cada recurso que le faltaba, por
 *                                   código del paciente
 * @param busyMinutes                minutos que estuvo ocupado cada recurso o integrante del personal, por
 *                                   código (camas, quirófanos, médicos, enfermeros y estaciones de triage)
 * @param criticalWithoutAidMinutes  minutos que cada paciente crítico esperó sin primeros auxilios, por
 *                                   código; solo aparecen los que pasaron por eso
 * @param initialBlood               unidades de sangre de cada tipo al empezar
 */
public record SimulationTrace(Map<String, Double> arrivalHealth, List<CareRecord> cares,
                              List<MinuteSample> samples, Map<String, Map<MissingResource, Integer>> blockedMinutes,
                              Map<String, Integer> busyMinutes, Map<String, Integer> criticalWithoutAidMinutes,
                              Map<BloodType, Integer> initialBlood) {

    /**
     * Valida los datos y guarda copias inmutables.
     *
     * @throws IllegalArgumentException si falta algún dato, si las fotos no van una por minuto desde el 0
     *                                  o si hay dos atenciones del mismo paciente
     */
    public SimulationTrace {
        if (arrivalHealth == null || cares == null || samples == null || blockedMinutes == null
                || busyMinutes == null || criticalWithoutAidMinutes == null || initialBlood == null) {
            throw new IllegalArgumentException("Faltan datos de la traza de la simulación.");
        }
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i).minute() != i) {
                throw new IllegalArgumentException("Debe haber una foto del hospital por minuto, desde el 0.");
            }
        }
        if (cares.stream().map(CareRecord::patientCode).distinct().count() != cares.size()) {
            throw new IllegalArgumentException("Un paciente no puede tener dos atenciones.");
        }
        Map<String, Map<MissingResource, Integer>> blockedCopy = new HashMap<>();
        blockedMinutes.forEach((code, minutes) -> blockedCopy.put(code, Map.copyOf(minutes)));
        arrivalHealth = Map.copyOf(arrivalHealth);
        cares = List.copyOf(cares);
        samples = List.copyOf(samples);
        blockedMinutes = Map.copyOf(blockedCopy);
        busyMinutes = Map.copyOf(busyMinutes);
        criticalWithoutAidMinutes = Map.copyOf(criticalWithoutAidMinutes);
        initialBlood = Map.copyOf(initialBlood);
    }

    /**
     * Busca la atención de un paciente.
     *
     * @param patientCode código del paciente
     * @return su atención; vacío si nunca recibió tratamiento
     */
    public Optional<CareRecord> careOf(String patientCode) {
        return cares.stream().filter(care -> care.patientCode().equals(patientCode)).findFirst();
    }

    /**
     * Devuelve la foto del hospital en un minuto.
     *
     * @param minute minuto de la simulación
     * @return la foto; vacío si el minuto no se simuló
     */
    public Optional<MinuteSample> sampleAt(int minute) {
        return minute >= 0 && minute < samples.size() ? Optional.of(samples.get(minute)) : Optional.empty();
    }

    /**
     * Devuelve los minutos que un paciente esperó por cada recurso.
     *
     * @param patientCode código del paciente
     * @return minutos por recurso; vacío si nunca le faltó nada
     */
    public Map<MissingResource, Integer> blockedMinutesOf(String patientCode) {
        return blockedMinutes.getOrDefault(patientCode, Map.of());
    }

    /**
     * Devuelve los minutos que estuvo ocupado un recurso o un integrante del personal.
     *
     * @param code código, por ejemplo {@code D03}, {@code Q1} o {@code H1-C2}
     * @return minutos ocupado; 0 si nunca se ocupó
     */
    public int busyMinutesOf(String code) {
        return busyMinutes.getOrDefault(code, 0);
    }

    /**
     * Devuelve la salud con la que llegó un paciente.
     *
     * @param patientCode código del paciente
     * @return salud al llegar
     * @throws IllegalArgumentException si el paciente no llegó al hospital
     */
    public double arrivalHealthOf(String patientCode) {
        Double health = arrivalHealth.get(patientCode);
        if (health == null) {
            throw new IllegalArgumentException(patientCode + " no llegó al hospital.");
        }
        return health;
    }
}
