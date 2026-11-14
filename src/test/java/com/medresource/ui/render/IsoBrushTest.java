package com.medresource.ui.render;

import com.medresource.model.Sex;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del pincel de la vista 2.5D: dibuja en imágenes en memoria.
 */
class IsoBrushTest {

    private static final Color BACKGROUND = Color.BLACK;

    private final IsoProjection projection = new IsoProjection(4, 4, 84, 48);
    private final BufferedImage image = new BufferedImage(projection.size().width, projection.size().height,
            BufferedImage.TYPE_INT_RGB);
    private final Graphics2D graphics = image.createGraphics();

    IsoBrushTest() {
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    }

    private int pixel(Point2D point) {
        return image.getRGB((int) Math.round(point.getX()), (int) Math.round(point.getY()));
    }

    /** Indica si alguno de los píxeles alrededor de un punto tiene ese color. */
    private boolean near(double x, double y, Color color) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (image.getRGB((int) Math.round(x) + dx, (int) Math.round(y) + dy) == color.getRGB()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theFloorQuadGoesThroughItsFourCorners() {
        Path2D quad = IsoBrush.quad(projection, 1, 1, 3, 2, 0);
        Rectangle2D bounds = quad.getBounds2D();

        assertEquals(projection.at(1, 2, 0).getX(), bounds.getMinX(), 1e-9);
        assertEquals(projection.at(3, 1, 0).getX(), bounds.getMaxX(), 1e-9);
        assertEquals(projection.at(1, 1, 0).getY(), bounds.getMinY(), 1e-9);
        assertEquals(projection.at(3, 2, 0).getY(), bounds.getMaxY(), 1e-9);
    }

    @Test
    void aBoxShowsItsTopLighterAndItsRightSideDarker() {
        Color color = new Color(100, 120, 140);

        IsoBrush.box(graphics, projection, 1, 1, 0, 1, 1, 1, color);

        assertEquals(IsoPalette.lighter(color, 0.18).getRGB(), pixel(projection.at(1.5, 1.5, 1)));
        assertEquals(color.getRGB(), pixel(projection.at(1.5, 2, 0.5)));
        assertEquals(IsoPalette.darker(color, 0.76).getRGB(), pixel(projection.at(2, 1.5, 0.5)));
    }

    @Test
    void seenFromBehindABoxShowsTheSidesThatWereHidden() {
        IsoProjection back = projection.withView(180, IsoProjection.DEFAULT_ELEVATION);
        BufferedImage picture = new BufferedImage(back.size().width, back.size().height, BufferedImage.TYPE_INT_RGB);
        Graphics2D surface = picture.createGraphics();
        Color left = new Color(200, 40, 40);
        Color right = new Color(40, 40, 200);

        IsoBrush.box(surface, back, 1, 1, 0, 1, 1, 1, Color.WHITE, left, right);

        Point2D front = back.at(1.5, 1, 0.5);
        Point2D side = back.at(1, 1.5, 0.5);
        assertEquals(left.getRGB(), picture.getRGB((int) Math.round(front.getX()), (int) Math.round(front.getY())));
        assertEquals(right.getRGB(), picture.getRGB((int) Math.round(side.getX()), (int) Math.round(side.getY())));
        surface.dispose();
    }

    @Test
    void aSideThatLooksStraightDownMixesBothColors() {
        Color mixed = IsoBrush.mix(Color.BLACK, Color.WHITE, 0.5);

        assertEquals(new Color(128, 128, 128), mixed);
        assertEquals(Color.RED, IsoBrush.mix(Color.RED, Color.BLUE, 0));
        assertEquals(Color.BLUE, IsoBrush.mix(Color.RED, Color.BLUE, 1));
    }

    @Test
    void seenFromAboveOnlyTheTopOfABoxIsDrawn() {
        IsoProjection above = projection.withView(0, IsoProjection.MAX_ELEVATION);
        BufferedImage picture = new BufferedImage(above.size().width, above.size().height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D surface = picture.createGraphics();

        IsoBrush.box(surface, above, 1, 1, 0, 1, 1, 1, Color.WHITE, Color.RED, Color.BLUE);

        Point2D center = above.at(1.5, 1.5, 1);
        assertEquals(Color.WHITE.getRGB(), picture.getRGB((int) Math.round(center.getX()),
                (int) Math.round(center.getY())));
        surface.dispose();
    }

    @Test
    void theGlassLetsWhatIsBehindItBeSeen() {
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        IsoBrush.pane(graphics, projection, 1, 1, 0, 1, 0.1, 1, IsoTheme.LIGHT.color(IsoTheme.Part.WINDOW),
                IsoTheme.LIGHT.color(IsoTheme.Part.WINDOW_FRAME));

        Color seen = new Color(pixel(projection.at(1.5, 1.1, 0.5)));
        assertTrue(seen.getRed() > 180, "el rojo de atrás se sigue viendo: " + seen);
        assertNotEquals(Color.RED.getRGB(), seen.getRGB());
    }

    @Test
    void aPersonStandsOnItsFeetWithTheHeadAbove() {
        IsoBrush.Person person = new IsoBrush.Person(Palette.NURSE_UNIFORM, null, Sex.MALE, 1.0, false);

        Point2D top = IsoBrush.person(graphics, projection, 2, 2, 0, 0, person);

        Point2D feet = projection.at(2, 2, 0);
        assertEquals(feet.getX(), top.getX(), 1e-9);
        double height = IsoBrush.BODY_HEIGHT * IsoProjection.Z_SCALE;
        assertTrue(top.getY() < feet.getY() - height, "la cabeza va encima del cuerpo");
        Color body = new Color(pixel(projection.at(2, 2, IsoBrush.BODY_HEIGHT / 2)));
        assertTrue(body.getGreen() > body.getRed(), "el cuerpo lleva el verde agua del enfermero: " + body);
    }

    @Test
    void aSeatedPersonIsShorterAndASmallerOneToo() {
        Point2D standing = IsoBrush.person(graphics, projection, 2, 2, 0, 0,
                new IsoBrush.Person(Palette.DOCTOR_COAT, IsoPalette.DOCTOR_CAP, Sex.FEMALE, 1.0, false));
        Point2D seated = IsoBrush.person(graphics, projection, 2, 2, 0, 0,
                new IsoBrush.Person(Palette.DOCTOR_COAT, IsoPalette.DOCTOR_CAP, Sex.FEMALE, 1.0, true));
        Point2D minor = IsoBrush.person(graphics, projection, 2, 2, 0, 0,
                new IsoBrush.Person(Palette.DOCTOR_COAT, null, Sex.FEMALE, 0.8, false));

        assertTrue(seated.getY() > standing.getY());
        assertTrue(minor.getY() > standing.getY());
    }

    @Test
    void theStepAdvancesHalfARoundEveryStepLength() {
        assertEquals(0.0, IsoBrush.stride(0, 0), 1e-12);
        assertEquals(Math.PI, IsoBrush.stride(IsoBrush.STEP_LENGTH, 0), 1e-9);
        assertEquals(IsoBrush.stride(1, 2), IsoBrush.stride(2, 1), 1e-12);
    }

    @Test
    void whoeverWalksOpensTheLegsAndRisesWhenTheyCross() {
        IsoBrush.Person person = new IsoBrush.Person(Palette.NURSE_UNIFORM, null, Sex.MALE, 1.0, false);
        Point2D still = IsoBrush.person(graphics, projection, 2, 2, 0, 0, person);
        Point2D crossing = IsoBrush.person(graphics, projection, 2, 2, 0, 0, person, 0.0);
        Point2D open = IsoBrush.person(graphics, projection, 2, 2, 0, 0, person, Math.PI / 2);
        Point2D notWalking = IsoBrush.person(graphics, projection, 2, 2, 0, 0, person, Double.NaN);

        assertEquals(still.getY() - IsoBrush.WALK_BOUNCE, crossing.getY(), 1e-9);
        assertEquals(still.getY(), open.getY(), 1e-9);
        assertEquals(still, notWalking);
    }

    @Test
    void aSeatedPersonDoesNotWalk() {
        IsoBrush.Person seated = new IsoBrush.Person(Palette.DOCTOR_COAT, null, Sex.FEMALE, 1.0, true);

        assertEquals(IsoBrush.person(graphics, projection, 2, 2, 0, 0, seated),
                IsoBrush.person(graphics, projection, 2, 2, 0, 0, seated, 0.0));
    }

    @Test
    void aPersonHasHairUnlessItWearsACapAndTheChestIsOnTheTorso() {
        IsoBrush.Person patient = new IsoBrush.Person(Palette.UNCLASSIFIED, null, Sex.MALE, 1.0, false);
        Color red = new Color(0xA0, 0x52, 0x2D);

        Point2D top = IsoBrush.person(graphics, projection, 2, 2, 0, 0,
                new IsoBrush.Person(Palette.UNCLASSIFIED, null, Sex.MALE, 1.0, false, red));

        assertEquals(IsoBrush.Person.DEFAULT_HAIR, patient.hair());
        Color hair = new Color(pixel(new Point2D.Double(top.getX(), top.getY() + 2)));
        assertEquals(red.getRGB(), hair.getRGB(), "el pelo va arriba de la cabeza");
        assertTrue(IsoBrush.chestHeight(false) > IsoBrush.LEG_HEIGHT);
        assertTrue(IsoBrush.chestHeight(false) < IsoBrush.BODY_HEIGHT);
    }

    @Test
    void theBarFillsItsShareFromTheLeft() {
        IsoBrush.bar(graphics, 100, 50, 20, 0.5, Color.GREEN);

        assertEquals(Color.GREEN.getRGB(), image.getRGB(93, 51));
        assertNotEquals(Color.GREEN.getRGB(), image.getRGB(107, 51));
    }

    @Test
    void theShadowAndTheRingAreOnTheFloor() {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight() / 2);
        IsoBrush.ring(graphics, projection, 2, 2, 0.3, Palette.SELECTION);
        IsoBrush.shadow(graphics, projection, 1, 1, 0.2);

        double rx = IsoBrush.screenRadius(0.3);
        Point2D center = projection.at(2, 2, 0);
        assertTrue(near(center.getX() + rx, center.getY(), Palette.SELECTION), "el anillo pasa por su borde");
        assertTrue(projection.at(1, 1, 0).getY() < image.getHeight() / 2.0);
        assertNotEquals(Color.WHITE.getRGB(), pixel(projection.at(1, 1, 0)), "la sombra oscurece el piso");
    }

    @Test
    void rejectsAPersonWithoutColorSexOrSize() {
        assertThrows(IllegalArgumentException.class, () -> new IsoBrush.Person(null, null, Sex.MALE, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new IsoBrush.Person(Color.RED, null, null, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new IsoBrush.Person(Color.RED, null, Sex.MALE, 0, false));
    }
}
