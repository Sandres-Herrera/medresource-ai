package com.medresource.ui.view;

import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import com.medresource.ui.view.DownloadPanel.Choice;

import javax.swing.JDialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

/**
 * Diálogo donde el usuario elige el formato del reporte (Word o PDF) y completa los datos de la portada.
 *
 * <p>Muestra el {@link DownloadPanel} con el tema oscuro, sin el marco de Windows: el título y la equis de cerrar
 * son parte del formulario y el diálogo se mueve arrastrando su parte de arriba, igual que el diálogo de una
 * simulación nueva.</p>
 */
public final class DownloadDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Alto de la franja de arriba que sirve para arrastrar el diálogo. */
    private static final int DRAG_HEIGHT = 70;
    /** Ancho del formulario. */
    private static final int WIDTH = 640;

    private transient Choice result;
    private Point dragStart;

    private DownloadDialog(Window owner, DocumentInfo draft, ExportFormat format) {
        super(owner, DownloadPanel.TITLE, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        DownloadPanel panel = new DownloadPanel(draft, format);
        panel.onDownload(choice -> {
            result = choice;
            dispose();
        });
        panel.onCancel(this::dispose);
        setContentPane(panel);
        enableDragging(panel);
        pack();
        setSize(new Dimension(WIDTH, getHeight()));
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (owner != null && owner.isShowing()) {
            setLocationRelativeTo(owner);
        } else {
            setBounds(ScreenFit.centered(getSize(), usable));
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                panel.downloadButton().requestFocusInWindow();
            }
        });
    }

    /**
     * Muestra el diálogo y espera a que el usuario termine.
     *
     * @param owner  ventana dueña del diálogo (puede ser nula)
     * @param draft  datos de la portada que se muestran al abrir
     * @param format formato elegido al abrir
     * @return el formato y la portada, o vacío si el usuario canceló
     * @throws IllegalArgumentException si faltan los datos propuestos o el formato
     */
    public static Optional<Choice> ask(Window owner, DocumentInfo draft, ExportFormat format) {
        DownloadDialog dialog = new DownloadDialog(owner, draft, format);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /** Permite mover el diálogo arrastrando la franja de arriba del formulario. */
    private void enableDragging(DownloadPanel panel) {
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragStart = event.getY() <= DRAG_HEIGHT ? event.getPoint() : null;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragStart != null) {
                    Point screen = event.getLocationOnScreen();
                    setLocation(screen.x - dragStart.x, screen.y - dragStart.y);
                }
            }
        };
        panel.addMouseListener(drag);
        panel.addMouseMotionListener(drag);
    }
}
