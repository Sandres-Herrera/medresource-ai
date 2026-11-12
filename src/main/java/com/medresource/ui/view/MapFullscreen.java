package com.medresource.ui.view;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana sin bordes que muestra el plano del hospital a pantalla completa, con la {@link FullscreenBar} arriba.
 *
 * <p>Ocupa toda la pantalla en la que está la ventana de la simulación. El plano es el mismo panel de la ventana
 * (con su selección y su cámara): se pasa aquí al abrirla y quien la abrió lo devuelve a su marco al
 * cerrarla. Se cierra con el botón de salir de la barra, con Esc o al cerrar la ventana; en cualquiera de los tres
 * casos se avisa una sola vez a quien escucha ({@link #onClose}).</p>
 */
public final class MapFullscreen extends JDialog {

    private static final long serialVersionUID = 1L;

    private transient Runnable closeAction = () -> { };
    private boolean closed;

    /**
     * Crea la ventana, sin mostrarla.
     *
     * @param owner ventana de la simulación; la pantalla completa se abre en su misma pantalla
     * @param bar   barra con los controles de la simulación
     * @param map   panel del plano que se muestra
     * @throws IllegalArgumentException si falta alguno
     */
    public MapFullscreen(Window owner, FullscreenBar bar, HospitalMapPanel map) {
        super(owner, MapFrame.TITLE, ModalityType.MODELESS);
        if (owner == null || bar == null || map == null) {
            throw new IllegalArgumentException("La ventana, la barra y el plano son obligatorios.");
        }
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setContentPane(content(bar, map));
        Rectangle screen = owner.getGraphicsConfiguration().getBounds();
        setBounds(screen);
        bar.exitButton().addActionListener(event -> close());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "salir");
        getRootPane().getActionMap().put("salir", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                close();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                close();
            }
        });
    }

    /**
     * Arma el contenido: la barra arriba y el plano en el resto.
     *
     * @param bar barra con los controles
     * @param map panel del plano
     * @return el panel con los dos
     */
    static JPanel content(FullscreenBar bar, HospitalMapPanel map) {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(HomeTheme.BACKGROUND);
        content.add(bar, BorderLayout.NORTH);
        content.add(map, BorderLayout.CENTER);
        return content;
    }

    /**
     * Cambia lo que se hace al cerrar la pantalla completa.
     *
     * @param action acción; por ejemplo, devolver el plano a su marco
     * @throws IllegalArgumentException si es nula
     */
    public void onClose(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de cerrar es obligatoria.");
        }
        this.closeAction = action;
    }

    /** Cierra la pantalla completa y avisa a quien escucha; si ya estaba cerrada, no hace nada. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        setVisible(false);
        dispose();
        closeAction.run();
    }

    public boolean isClosed() {
        return closed;
    }
}
