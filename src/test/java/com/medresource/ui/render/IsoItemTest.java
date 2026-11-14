package com.medresource.ui.render;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del orden en que se dibuja la vista 2.5D.
 */
class IsoItemTest {

    @Test
    void whatIsAtTheBackIsDrawnFirstAndTheFurnitureBeforeThePeople() {
        List<String> drawn = new ArrayList<>();
        List<IsoItem> items = new ArrayList<>(List.of(
                new IsoItem(5, IsoItem.PERSON, graphics -> drawn.add("persona cerca")),
                new IsoItem(3, IsoItem.PERSON, graphics -> drawn.add("persona al fondo")),
                new IsoItem(3, IsoItem.FURNITURE, graphics -> drawn.add("cama al fondo")),
                new IsoItem(1, IsoItem.WALL, graphics -> drawn.add("muro"))));
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();

        items.sort(IsoItem.BACK_TO_FRONT);
        items.forEach(item -> item.paint(graphics));

        assertEquals(List.of("muro", "cama al fondo", "persona al fondo", "persona cerca"), drawn);
    }

    @Test
    void rejectsADepthThatIsNotANumberOrNothingToDraw() {
        assertThrows(IllegalArgumentException.class, () -> new IsoItem(Double.NaN, IsoItem.WALL, graphics -> { }));
        assertThrows(IllegalArgumentException.class, () -> new IsoItem(1, IsoItem.WALL, null));
    }
}
