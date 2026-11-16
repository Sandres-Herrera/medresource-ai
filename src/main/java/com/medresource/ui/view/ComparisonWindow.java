package com.medresource.ui.view;

import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;
import com.medresource.services.management.ComparisonArchive;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Ventana con la comparación de dos ejecuciones ({@link ComparisonPanel}).
 *
 * <p>Una comparación recién hecha se puede guardar con «Guardar comparación»; se guarda en segundo plano
 * ({@link ArchiveWorker}) y, si esas dos ejecuciones ya se habían comparado, la ventana dice con qué número quedó
 * guardada la primera vez. Una comparación que se abre desde los reportes guardados ya está guardada y no tiene el
 * botón.</p>
 */
public final class ComparisonWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Comienzo del título de la ventana. */
    public static final String TITLE = "MedResource AI — ";

    private static final Dimension SIZE = new Dimension(1000, 760);
    private static final Dimension MIN_SIZE = new Dimension(720, 480);
    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm");

    private final ComparisonPanel panel;

    private ComparisonWindow(String title, ComparisonPanel panel) {
        super(TITLE + title);
        this.panel = panel;
        JScrollPane scroll = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(HomeTheme.BACKGROUND);
        DarkScrollBarUI.apply(scroll);
        setContentPane(scroll);
        setIconImages(List.of(LogoBadge.image(16), LogoBadge.image(32), LogoBadge.image(64)));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(ScreenFit.fit(MIN_SIZE, usable));
        setBounds(ScreenFit.centered(ScreenFit.fit(SIZE, usable), usable));
    }

    /**
     * Muestra una comparación recién hecha, con el botón para guardarla.
     *
     * @param comparison comparación
     * @param archive    donde se guarda si se pide
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta algún dato
     */
    public static ComparisonWindow showing(RunComparison comparison, ComparisonArchive archive) {
        if (comparison == null || archive == null) {
            throw new IllegalArgumentException("Faltan la comparación o dónde guardarla.");
        }
        ComparisonPanel panel = new ComparisonPanel(comparison);
        panel.onSave(() -> {
            panel.showSaving();
            new ArchiveWorker<>(() -> archive.save(comparison), result -> panel.showSaved(
                    (result.alreadySaved() ? "Estas dos ejecuciones ya estaban comparadas: es la comparación #"
                            : "Guardada como comparación #") + result.saved().id() + "."),
                    error -> panel.showSaveError("No se pudo guardar la comparación: " + detail(error))).execute();
        });
        ComparisonWindow window = new ComparisonWindow(comparison.title(), panel);
        window.setVisible(true);
        return window;
    }

    /**
     * Muestra una comparación guardada, sin el botón de guardar.
     *
     * @param saved comparación guardada
     * @return la ventana ya visible
     * @throws IllegalArgumentException si es nula
     */
    public static ComparisonWindow showingSaved(SavedComparison saved) {
        if (saved == null) {
            throw new IllegalArgumentException("La comparación guardada es obligatoria.");
        }
        ComparisonPanel panel = new ComparisonPanel(saved.comparison());
        panel.showAlreadySaved("Comparación #" + saved.id() + ", guardada el " + SAVED_AT.format(saved.savedAt()));
        ComparisonWindow window = new ComparisonWindow(saved.title(), panel);
        window.setVisible(true);
        return window;
    }

    private static String detail(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /**
     * Devuelve el panel que muestra la comparación.
     *
     * @return el panel
     */
    public ComparisonPanel getPanel() {
        return panel;
    }
}
