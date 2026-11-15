package com.medresource.services.simulation;

import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.ReviewCase;
import com.medresource.model.ReviewReason;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Elige hasta {@value #MAX_CASES} pacientes representativos de lo que pasó en la simulación para revisarlos en
 * el reporte.
 *
 * <p>Busca, en este orden: el que más esperó antes de morir sin ser atendido; el que murió después de la
 * atención más tardía; el paciente urgente (triage I o II) que más esperó; el que más salud perdió esperando;
 * el que más se pasó de la meta de su nivel; y el que más esperó antes de irse sin atención. Un paciente
 * aparece una sola vez: si ya se eligió por un motivo, el siguiente motivo busca a otro. A igual valor gana el
 * que llegó primero. Los remitidos no se eligen porque el hospital no podía atenderlos.</p>
 */
public class CaseSelector {

    /** Cantidad máxima de casos para revisar. */
    public static final int MAX_CASES = 5;

    /**
     * Elige los casos para revisar.
     *
     * @param result resultado de la simulación
     * @param trace  traza de la misma simulación
     * @return hasta {@value #MAX_CASES} casos, en el orden de los motivos
     * @throws IllegalArgumentException si falta el resultado o la traza
     */
    public List<ReviewCase> select(SimulationResult result, SimulationTrace trace) {
        if (result == null || trace == null) {
            throw new IllegalArgumentException("El resultado y la traza son obligatorios.");
        }
        List<ReviewCase> cases = new ArrayList<>();
        Set<String> chosen = new HashSet<>();
        List<PatientOutcome> candidates = result.outcomes().stream()
                .filter(outcome -> outcome.status() != PatientStatus.REFERRED)
                .toList();
        pick(ReviewReason.LONGEST_WAIT_BEFORE_DEATH, candidates,
                outcome -> outcome.status() == PatientStatus.DECEASED && !outcome.wasTreated(),
                PatientOutcome::waitingMinutes, trace, chosen, cases);
        pick(ReviewReason.DIED_AFTER_LATE_CARE, candidates,
                outcome -> outcome.status() == PatientStatus.DECEASED && outcome.wasTreated()
                        && !TriageAnalyzer.treatedWithinTarget(outcome),
                PatientOutcome::waitingMinutes, trace, chosen, cases);
        pick(ReviewReason.LONGEST_URGENT_WAIT, candidates,
                outcome -> outcome.triage().getLevel() <= 2 && outcome.waitingMinutes() > 0,
                PatientOutcome::waitingMinutes, trace, chosen, cases);
        pick(ReviewReason.LARGEST_HEALTH_LOSS, candidates,
                outcome -> TriageAnalyzer.healthLost(outcome, trace) > 0,
                outcome -> TriageAnalyzer.healthLost(outcome, trace), trace, chosen, cases);
        pick(ReviewReason.FURTHEST_OVER_TARGET, candidates, outcome -> overTarget(outcome) > 0,
                CaseSelector::overTarget, trace, chosen, cases);
        pick(ReviewReason.LEFT_UNATTENDED, candidates, outcome -> outcome.status() == PatientStatus.UNATTENDED,
                PatientOutcome::waitingMinutes, trace, chosen, cases);
        return cases;
    }

    private static void pick(ReviewReason reason, List<PatientOutcome> candidates, Predicate<PatientOutcome> applies,
                             ToDoubleFunction<PatientOutcome> value, SimulationTrace trace, Set<String> chosen,
                             List<ReviewCase> cases) {
        if (cases.size() >= MAX_CASES) {
            return;
        }
        Optional<PatientOutcome> best = Optional.empty();
        for (PatientOutcome outcome : candidates) {
            if (chosen.contains(outcome.patientCode()) || !applies.test(outcome)) {
                continue;
            }
            if (best.isEmpty() || value.applyAsDouble(outcome) > value.applyAsDouble(best.get())) {
                best = Optional.of(outcome);
            }
        }
        best.ifPresent(outcome -> {
            chosen.add(outcome.patientCode());
            cases.add(new ReviewCase(outcome.patientCode(), reason, outcome.triage(), outcome.waitingMinutes(),
                    TriageAnalyzer.healthLost(outcome, trace), outcome.status(), focusMinute(reason, outcome)));
        });
    }

    /** Minutos que el paciente pasó de la meta de su nivel; negativo si no la pasó. */
    private static double overTarget(PatientOutcome outcome) {
        return outcome.waitingMinutes() - outcome.triage().getTargetMinutes();
    }

    /**
     * Minuto que conviene ver en el plano: cuando pasó la meta, si se eligió por eso; si no, cuando empezó su
     * tratamiento, y si nunca lo empezó, cuando terminó su caso.
     */
    private static int focusMinute(ReviewReason reason, PatientOutcome outcome) {
        if (reason == ReviewReason.FURTHEST_OVER_TARGET) {
            return outcome.arrivalMinute() + outcome.triage().getTargetMinutes();
        }
        return outcome.wasTreated() ? outcome.treatmentStartMinute() : outcome.outcomeMinute();
    }
}
