package com.medresource.services.management;

import com.medresource.model.GeneratedReport;
import com.medresource.model.HospitalConfig;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportProgress;
import com.medresource.model.ReportStage;
import com.medresource.persistence.AiClient;
import com.medresource.services.simulation.WhatIfAnalyzer;
import com.medresource.services.simulation.WhatIfProgress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del generador del reporte por etapas, con las plantillas y dos casos por alternativa para que sea
 * rápido.
 */
class ReportGeneratorTest {

    private static final int CASES = 2;

    /** IA que nunca se llama en estas pruebas: solo sirve para que el generador sepa que hay IA. */
    private static final AiClient UNUSED_AI = new AiClient() {
        @Override
        public String generate(String prompt) {
            throw new AssertionError("No debía llamarse a la IA.");
        }

        @Override
        public String modelName() {
            return "modelo-falso";
        }
    };

    private static ReportGenerator withTemplates() {
        return new ReportGenerator(new WhatIfAnalyzer(), new ReportNarrator(null, null), CASES);
    }

    @Test
    void generatesTheReportOfTheSimulationThatWasSeen() {
        ReportRequest request = ReportRequestTest.request();

        GeneratedReport generated = withTemplates().generate(request, progress -> { });

        assertEquals(request.result().deaths(), generated.report().metrics().deaths());
        assertEquals(ReportFactsTest.SHORT_CASE, generated.report().config());
        assertEquals(CASES, generated.whatIf().cases());
        assertEquals(NarrativeSource.TEMPLATE, generated.narrative().source());
    }

    @Test
    void theProgressGoesThroughTheStagesInOrder() {
        List<ReportProgress> steps = new ArrayList<>();

        withTemplates().generate(ReportRequestTest.request(), steps::add);

        assertEquals(ReportProgress.start(), steps.getFirst());
        assertEquals(ReportStage.WRITING, steps.getLast().stage());
        for (int i = 1; i < steps.size(); i++) {
            assertTrue(steps.get(i).stage().compareTo(steps.get(i - 1).stage()) >= 0, "etapa " + i);
            assertTrue(steps.get(i).elapsedMillis() >= steps.get(i - 1).elapsedMillis(), "tiempo " + i);
        }
        long whatIfSteps = steps.stream().filter(step -> step.stage() == ReportStage.WHAT_IF).count();
        assertEquals(WhatIfAnalyzer.runsNeeded(HospitalConfig.small(), 1, CASES) + 1, whatIfSteps);
    }

    @Test
    void withTheTemplatesTheWritingTakesNoTime() {
        List<ReportProgress> steps = new ArrayList<>();

        withTemplates().generate(ReportRequestTest.request(), steps::add);

        assertEquals(0, steps.getLast().remainingMillis());
        assertTrue(steps.stream().filter(step -> step.stage() == ReportStage.WHAT_IF)
                .anyMatch(ReportProgress::isEstimated));
    }

    @Test
    void duringTheWhatIfItAddsWhatTheAiUsuallyTakes() {
        assertEquals(23000, ReportGenerator.whatIfProgress(new WhatIfProgress(1, 4, 1000), 1500, 20000)
                .remainingMillis());
        assertEquals(1500, ReportGenerator.whatIfProgress(new WhatIfProgress(1, 4, 1000), 1500, 20000)
                .elapsedMillis());
        assertFalse(ReportGenerator.whatIfProgress(new WhatIfProgress(0, 4, 0), 300, 20000).isEstimated());
    }

    @Test
    void knowsIfTheTextWillBeWrittenByTheAi() {
        assertFalse(withTemplates().usesAi());
        assertTrue(new ReportGenerator(new WhatIfAnalyzer(), new ReportNarrator(UNUSED_AI, null), CASES).usesAi());
    }

    @Test
    void canBeCancelledDuringTheWhatIf() {
        AtomicInteger steps = new AtomicInteger();

        assertThrows(CancellationException.class, () -> withTemplates().generate(ReportRequestTest.request(),
                progress -> steps.incrementAndGet(), () -> steps.get() > 3));
    }

    @Test
    void canBeCancelledBeforeTheWhatIfStarts() {
        List<ReportProgress> seen = new ArrayList<>();

        assertThrows(CancellationException.class, () -> withTemplates().generate(ReportRequestTest.request(),
                seen::add, () -> true));
        assertEquals(List.of(ReportProgress.start()), seen);
    }

    @Test
    void wrongDataIsRejected() {
        ReportGenerator generator = withTemplates();
        ReportRequest request = ReportRequestTest.request();
        ReportNarrator narrator = new ReportNarrator(null, null);
        WhatIfAnalyzer analyzer = new WhatIfAnalyzer();

        assertThrows(IllegalArgumentException.class, () -> generator.generate(null, progress -> { }));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(request, null));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(request, progress -> { }, null));
        assertThrows(IllegalArgumentException.class, () -> new ReportGenerator(null, narrator, CASES));
        assertThrows(IllegalArgumentException.class, () -> new ReportGenerator(analyzer, null, CASES));
        assertThrows(IllegalArgumentException.class, () -> new ReportGenerator(analyzer, narrator, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportGenerator(analyzer, narrator, WhatIfAnalyzer.MAX_CASES + 1));
    }
}
