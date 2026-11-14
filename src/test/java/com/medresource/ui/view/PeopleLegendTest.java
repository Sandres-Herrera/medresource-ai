package com.medresource.ui.view;

import com.medresource.model.TriageLevel;
import com.medresource.ui.render.Palette;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la leyenda «Quién es quién», de sus figuras y del punto de color, sin ventana.
 */
class PeopleLegendTest {

    private final PeopleLegend legend = new PeopleLegend();

    @Test
    void namesEachLevelShortly() {
        assertEquals("I · Inmediato", PeopleLegend.shortName(TriageLevel.I));
        assertEquals("IV · Menos urgente", PeopleLegend.shortName(TriageLevel.IV));
        assertThrows(IllegalArgumentException.class, () -> PeopleLegend.shortName(null));
    }

    @Test
    void listsThePatientsByTheirColorAndThenTheStaffByTheirRole() {
        List<String> entries = legend.entries();

        assertEquals(TriageLevel.values().length + 6, entries.size());
        assertEquals("I · Inmediato", entries.get(0));
        assertEquals(PeopleLegend.PENDING, entries.get(TriageLevel.values().length));
        assertEquals(PeopleLegend.DECEASED, entries.get(TriageLevel.values().length + 1));
        assertEquals(List.of(PeopleLegend.DOCTOR, PeopleLegend.NURSE, PeopleLegend.TRIAGE_NURSE, PeopleLegend.PORTER),
                entries.subList(entries.size() - 4, entries.size()));
    }

    @Test
    void eachFigureHasTheColorOfThePeopleOnThePlanAndOnlyTheStaffWearsACap() {
        List<FigureIcon> figures = legend.figures();

        assertEquals(legend.entries().size(), figures.size());
        assertEquals(Palette.triage(TriageLevel.I), figures.get(0).getBody());
        assertNull(figures.get(0).getCap());
        int doctor = legend.entries().indexOf(PeopleLegend.DOCTOR);
        assertEquals(Palette.DOCTOR_COAT, figures.get(doctor).getBody());
        assertEquals(Palette.PORTER_UNIFORM, figures.get(figures.size() - 1).getBody());
        figures.subList(doctor, figures.size()).forEach(figure -> assertNotNull(figure.getCap(),
                "el personal lleva gorro"));
    }

    @Test
    void theFigurePaintsItsBody() {
        FigureIcon figure = new FigureIcon(Palette.NURSE_UNIFORM, Palette.CARD);
        figure.setSize(figure.getPreferredSize());
        BufferedImage image = new BufferedImage(FigureIcon.WIDTH, FigureIcon.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        figure.paint(graphics);

        assertEquals(Palette.NURSE_UNIFORM.getRGB(), image.getRGB(FigureIcon.WIDTH / 2, 14));
        assertThrows(IllegalArgumentException.class, () -> new FigureIcon(null, null));
    }

    @Test
    void theDotKeepsItsColor() {
        Dot dot = new Dot(HomeTheme.RED);

        dot.setColor(HomeTheme.BLUE);

        assertEquals(HomeTheme.BLUE, dot.getColor());
        assertEquals(Dot.SIZE, dot.getPreferredSize().width);
        assertThrows(IllegalArgumentException.class, () -> new Dot(null));
    }
}
