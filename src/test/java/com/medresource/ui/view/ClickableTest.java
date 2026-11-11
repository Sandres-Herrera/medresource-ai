package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import javax.accessibility.AccessibleRole;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la base de las piezas que se pueden pulsar, con eventos de ratón y de teclado simulados.
 */
class ClickableTest {

    /** Pieza mínima de prueba, de 100 × 40. */
    private static final class Piece extends Clickable {

        private static final long serialVersionUID = 1L;

        Piece() {
            super("Pieza");
            setSize(100, 40);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            // No dibuja nada.
        }
    }

    private final Piece piece = new Piece();
    private final List<String> clicks = new ArrayList<>();

    ClickableTest() {
        piece.addActionListener(event -> clicks.add(event.getActionCommand()));
    }

    private void mouse(int id, int x, int y) {
        piece.dispatchEvent(new MouseEvent(piece, id, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    /** Pulsa una tecla sobre la pieza, como si tuviera el foco. */
    private void key(int code) {
        KeyEvent event = new KeyEvent(piece, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code,
                KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : piece.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    @Test
    void aClickInsideThePieceNotifiesItsListeners() {
        mouse(MouseEvent.MOUSE_PRESSED, 10, 10);
        assertTrue(piece.isPressed());
        mouse(MouseEvent.MOUSE_RELEASED, 12, 11);

        assertEquals(List.of("Pieza"), clicks);
        assertFalse(piece.isPressed());
    }

    @Test
    void releasingOutsideThePieceCancelsTheClick() {
        mouse(MouseEvent.MOUSE_PRESSED, 10, 10);
        mouse(MouseEvent.MOUSE_RELEASED, 150, 10);

        assertTrue(clicks.isEmpty());
    }

    @Test
    void enterAndSpaceAlsoPressThePiece() {
        key(KeyEvent.VK_ENTER);
        key(KeyEvent.VK_SPACE);
        key(KeyEvent.VK_A);

        assertEquals(2, clicks.size());
    }

    @Test
    void remembersWhenTheMouseIsOverIt() {
        mouse(MouseEvent.MOUSE_ENTERED, 5, 5);
        assertTrue(piece.isHover());

        mouse(MouseEvent.MOUSE_EXITED, 105, 5);
        assertFalse(piece.isHover());
    }

    @Test
    void aDisabledPieceDoesNotNotify() {
        piece.setEnabled(false);
        piece.doClick();
        key(KeyEvent.VK_ENTER);

        assertTrue(clicks.isEmpty());
    }

    @Test
    void showsTheHandAndCanTakeTheFocus() {
        assertEquals(Cursor.HAND_CURSOR, piece.getCursor().getType());
        assertTrue(piece.isFocusable());
        assertEquals("Pieza", piece.getAccessibleContext().getAccessibleName());
        assertEquals(AccessibleRole.PUSH_BUTTON, piece.getAccessibleContext().getAccessibleRole());
    }

    @Test
    void rejectsAPieceWithoutNameAndAMissingListener() {
        assertThrows(IllegalArgumentException.class, () -> piece.addActionListener(null));
        assertThrows(IllegalArgumentException.class, () -> new Clickable(" ") {
            private static final long serialVersionUID = 1L;
        });
    }
}
