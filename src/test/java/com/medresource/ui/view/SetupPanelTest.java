package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.ui.controller.HospitalField;
import com.medresource.ui.controller.HospitalPreset;
import com.medresource.ui.controller.SetupDraft;
import com.medresource.ui.controller.SimulationClock;
import com.medresource.ui.controller.SimulationRequest;
import com.medresource.ui.controller.StrategyOption;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del formulario de una simulación nueva, sin ventana: se pulsan sus piezas y se mira lo que pide.
 */
class SetupPanelTest {

    private final SetupDraft draft = new SetupDraft();
    private final SetupPanel panel = new SetupPanel(draft, new Random(3));
    private final List<SimulationRequest> started = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();

    SetupPanelTest() {
        panel.onStart(started::add);
        panel.onCancel(() -> cancelled.add("cancelar"));
    }

    @Test
    void startsWithTheStandardHospitalNormalScenarioAndClinicalPriority() {
        assertTrue(panel.presetPill(HospitalPreset.STANDARD).isSelected());
        assertTrue(panel.scenarioTile(Scenario.NORMAL).isSelected());
        assertTrue(panel.strategyTile(StrategyOption.CLINICAL_PRIORITY).isSelected());
        assertTrue(panel.speedPill(SimulationClock.DEFAULT_SPEED).isSelected());
        assertEquals(30, panel.resourceStepper(HospitalField.BEDS).getValue());
        assertEquals(draft.summary(), panel.summaryText());
    }

    @Test
    void aSizeFillsEveryResourceAndChangingOneMakesItCustom() {
        panel.presetPill(HospitalPreset.LARGE).doClick();

        assertEquals(60, panel.resourceStepper(HospitalField.BEDS).getValue());
        assertEquals(32, panel.resourceStepper(HospitalField.DOCTORS).getValue());
        assertTrue(panel.presetPill(HospitalPreset.LARGE).isSelected());
        assertFalse(panel.presetPill(HospitalPreset.STANDARD).isSelected());

        panel.resourceStepper(HospitalField.DOCTORS).plusButton().doClick();

        assertFalse(panel.presetPill(HospitalPreset.LARGE).isSelected());
        assertTrue(panel.summaryText().contains("hospital personalizado"), panel.summaryText());
    }

    @Test
    void onlyOneScenarioOneStrategyAndOneSpeedAreChosen() {
        panel.scenarioTile(Scenario.CATASTROPHE).doClick();
        panel.strategyTile(StrategyOption.FIFO).doClick();
        panel.speedPill(10).doClick();

        for (Scenario scenario : Scenario.values()) {
            assertEquals(scenario == Scenario.CATASTROPHE, panel.scenarioTile(scenario).isSelected(), scenario.name());
        }
        for (StrategyOption option : StrategyOption.values()) {
            assertEquals(option == StrategyOption.FIFO, panel.strategyTile(option).isSelected(), option.name());
        }
        for (int speed : SimulationClock.SPEEDS) {
            assertEquals(speed == 10, panel.speedPill(speed).isSelected());
        }
    }

    @Test
    void theScenariosSayHowThePatientsArriveWithTheChosenTime() {
        assertEquals("70 % en 48 min", panel.scenarioTile(Scenario.CATASTROPHE).getDetail());

        panel.durationStepper().setValue(120);

        assertEquals("70 % en 12 min", panel.scenarioTile(Scenario.CATASTROPHE).getDetail());
        assertTrue(panel.summaryText().startsWith("1 paciente cada 1,2 min"), panel.summaryText());
    }

    @Test
    void theDiceDrawsAnotherSeed() {
        panel.diceButton().doClick();

        long expected = new SetupDraft().rollSeed(new Random(3));
        assertEquals(expected, panel.seedStepper().getValue());
        assertEquals(expected, draft.getSeed());
    }

    @Test
    void startingSendsEverythingThatWasChosen() {
        panel.presetPill(HospitalPreset.SMALL).doClick();
        panel.scenarioTile(Scenario.HIGH_DEMAND).doClick();
        panel.patientsStepper().setValue(60);
        panel.durationStepper().setValue(120);
        panel.seedStepper().setValue(7);
        panel.strategyTile(StrategyOption.RANDOM).doClick();
        panel.speedPill(5).doClick();

        panel.startButton().doClick();

        assertEquals(1, started.size());
        SimulationRequest request = started.get(0);
        assertEquals(HospitalConfig.small(), request.setup().hospital());
        assertEquals(new CaseConfig(7L, Scenario.HIGH_DEMAND, 60, 120), request.setup().caseConfig());
        assertEquals(StrategyOption.RANDOM, request.setup().strategy());
        assertEquals(5, request.speed());
    }

    @Test
    void startingTakesWhatWasTypedButNotConfirmed() {
        panel.patientsStepper().field().setText("250");

        panel.startButton().doClick();

        assertEquals(250, started.get(0).setup().caseConfig().patientCount());
    }

    @Test
    void theCrossAndCancelCancel() {
        panel.closeButton().doClick();
        panel.cancelButton().doClick();

        assertEquals(2, cancelled.size());
        assertTrue(started.isEmpty());
    }

    @Test
    void paintsWithTheDarkTheme() {
        panel.setSize(panel.getPreferredSize());
        panel.doLayout();
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        panel.paint(graphics);
        graphics.dispose();

        assertEquals(HomeTheme.BACKGROUND.getRGB(), image.getRGB(6, panel.getHeight() / 2));
    }

    @Test
    void fitsInASmallLaptopScreen() {
        assertTrue(panel.getPreferredSize().width <= 980, "ancho " + panel.getPreferredSize().width);
        assertTrue(panel.getPreferredSize().height <= 680, "alto " + panel.getPreferredSize().height);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new SetupPanel(null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new SetupPanel(new SetupDraft(), null));
        assertThrows(IllegalArgumentException.class, () -> panel.onStart(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onCancel(null));
    }
}
