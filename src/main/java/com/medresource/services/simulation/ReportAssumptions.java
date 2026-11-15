package com.medresource.services.simulation;

import com.medresource.model.Hospital;
import com.medresource.model.Morgue;
import com.medresource.model.ResourceUsage;
import com.medresource.model.StaffLoad;
import com.medresource.services.strategy.PriorityBasedStrategy;

import java.util.List;

/**
 * Supuestos y límites del simulador que se explican en el reporte, para que quien lo lea sepa qué se modela y
 * qué no.
 *
 * <p>Los números salen de las constantes del simulador: si una cambia, el texto del reporte cambia con
 * ella.</p>
 */
public final class ReportAssumptions {

    private ReportAssumptions() {
    }

    /**
     * Devuelve los supuestos y límites, en el orden en que se muestran.
     *
     * @return textos en español
     */
    public static List<String> all() {
        return List.of(
                "No se simulan turnos: el personal trabaja toda la simulación, sin relevos ni descansos.",
                "El diagnóstico siempre es correcto: no se simulan diagnósticos equivocados.",
                "Las metas de espera de los niveles I y II siguen la Resolución 5596 de 2015; las de los niveles"
                        + " III a V son un supuesto.",
                "Hay " + Hospital.TRIAGE_STATIONS + " estaciones de triage, cada una con su propia enfermera, que"
                        + " no cuenta entre los enfermeros del hospital.",
                "Cada enfermero vigila hasta " + Hospital.WARD_PATIENTS_PER_NURSE + " pacientes en cama o "
                        + Hospital.ICU_PATIENTS_PER_NURSE + " en UCI.",
                "Cada transfusión usa " + PriorityBasedStrategy.BLOOD_UNITS_PER_TREATMENT + " unidades de sangre.",
                "Los cuerpos permanecen " + Morgue.RETENTION_MINUTES + " minutos en la morgue; si no hay cupo, el"
                        + " fallecido sigue ocupando su cama.",
                "Un paciente que espera es crítico si es de triage I o si su salud bajó a "
                        + (int) SimulationEngine.CRITICAL_HEALTH + " o menos; un crítico sin primeros auxilios por "
                        + OutcomeAnalyzer.FIRST_AID_TOLERANCE_MINUTES + " minutos o más cuenta como falla.",
                "Los pacientes que siguen esperando " + SimulationEngine.EXTRA_MINUTES + " minutos después de la"
                        + " última llegada se registran como sin atender.",
                "Un grupo de recursos está subutilizado si en promedio se ocupó menos del "
                        + ResourceUsage.UNDERUSED_PERCENT + " %; una persona está saturada si estuvo ocupada el "
                        + StaffLoad.SATURATED_PERCENT + " % del tiempo o más.",
                "Dos colapsos separados por menos de " + BottleneckAnalyzer.COLLAPSE_GAP_MINUTES + " minutos se"
                        + " cuentan como uno solo.",
                "El recorrido de las personas en el plano es solo visual: no cambia quién vive o muere.");
    }
}
