package com.medresource.model;

import java.util.List;
import java.util.Optional;

/**
 * Reporte de fin de simulación con todos los números calculados a partir del resultado y de la traza.
 *
 * <p>Reúne las secciones del reporte: resumen, resultados por nivel de triage (esperas, metas y deterioro),
 * fallecidos evitables e inevitables, pacientes sin terminar, fallas de la atención, cuellos de botella,
 * periodos de colapso, ocupación de los recursos, carga del personal y de cada especialidad, banco de sangre,
 * casos para revisar y los supuestos del simulador. Todos los números los calcula el código; el texto que
 * los explica se arma después.</p>
 *
 * @param config         configuración del caso simulado
 * @param totalMinutes   minutos que duró la simulación completa
 * @param metrics        métricas generales de la ejecución
 * @param treated        pacientes que recibieron tratamiento
 * @param maxWaitMinutes espera más larga de todos los pacientes
 * @param triage         resultados de cada nivel de triage, del I al V
 * @param deaths         fallecidos según cuándo murieron
 * @param unfinished     pacientes que seguían en el hospital al cerrar el periodo de llegadas
 * @param failures       fallas de la atención que no son muertes
 * @param bottlenecks    recursos que hicieron esperar, del que más espera causó al que menos
 * @param collapses      periodos de colapso, en orden
 * @param resources      ocupación de cada grupo de recursos
 * @param staff          carga de cada médico y enfermero
 * @param specialties    carga de cada especialidad que tiene el hospital
 * @param blood          resumen del banco de sangre
 * @param cases          pacientes para revisar
 * @param assumptions    supuestos y límites del simulador
 */
public record SimulationReport(CaseConfig config, int totalMinutes, RunMetrics metrics, int treated,
                               int maxWaitMinutes, List<TriageLevelStats> triage, DeathBreakdown deaths,
                               UnfinishedPatients unfinished, CareFailures failures,
                               List<BottleneckStats> bottlenecks, List<CollapsePeriod> collapses,
                               List<ResourceUsage> resources, List<StaffLoad> staff,
                               List<SpecialtyLoad> specialties, BloodReport blood, List<ReviewCase> cases,
                               List<String> assumptions) {

    /**
     * Valida que el reporte esté completo y sea coherente, y guarda copias inmutables de las listas.
     *
     * @throws IllegalArgumentException si falta alguna sección, si hay números negativos, si los atendidos son
     *                                  más que los pacientes o si los fallecidos no coinciden con las métricas
     */
    public SimulationReport {
        if (config == null || metrics == null || triage == null || deaths == null || unfinished == null
                || failures == null || bottlenecks == null || collapses == null || resources == null
                || staff == null || specialties == null || blood == null || cases == null || assumptions == null) {
            throw new IllegalArgumentException("Faltan secciones del reporte.");
        }
        if (totalMinutes < 0 || treated < 0 || maxWaitMinutes < 0) {
            throw new IllegalArgumentException("Los números del reporte no pueden ser negativos.");
        }
        if (treated > metrics.patients()) {
            throw new IllegalArgumentException("No puede haber más atendidos que pacientes.");
        }
        if (deaths.total() != metrics.deaths()) {
            throw new IllegalArgumentException("Los fallecidos del reporte deben coincidir con las métricas.");
        }
        triage = List.copyOf(triage);
        bottlenecks = List.copyOf(bottlenecks);
        collapses = List.copyOf(collapses);
        resources = List.copyOf(resources);
        staff = List.copyOf(staff);
        specialties = List.copyOf(specialties);
        cases = List.copyOf(cases);
        assumptions = List.copyOf(assumptions);
    }

    /**
     * Devuelve el recurso que más espera causó.
     *
     * @return el principal cuello de botella; vacío si nadie esperó por un recurso
     */
    public Optional<BottleneckStats> mainBottleneck() {
        return bottlenecks.stream().findFirst();
    }

    /**
     * Devuelve los resultados de un nivel de triage.
     *
     * @param level nivel
     * @return resultados del nivel; vacío si el reporte no lo trae
     */
    public Optional<TriageLevelStats> triageOf(TriageLevel level) {
        return triage.stream().filter(stats -> stats.level() == level).findFirst();
    }

    /**
     * Devuelve los grupos de recursos subutilizados.
     *
     * @return grupos con ocupación promedio por debajo del límite
     */
    public List<ResourceUsage> underusedResources() {
        return resources.stream().filter(ResourceUsage::isUnderused).toList();
    }

    /**
     * Devuelve el personal saturado.
     *
     * @return médicos y enfermeros saturados
     */
    public List<StaffLoad> saturatedStaff() {
        return staff.stream().filter(StaffLoad::isSaturated).toList();
    }

    /**
     * Devuelve el personal que estuvo libre la mayor parte del tiempo.
     *
     * @return médicos y enfermeros poco ocupados
     */
    public List<StaffLoad> idleStaff() {
        return staff.stream().filter(StaffLoad::isIdle).toList();
    }
}
