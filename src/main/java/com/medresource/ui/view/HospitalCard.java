package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.SavedReportSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tarjeta de la pantalla de inicio con el hospital estándar y la última simulación guardada.
 *
 * <p>Arriba muestra un cuadro por cada recurso del hospital (camas, UCI, quirófanos, médicos y enfermeros), con su
 * icono, la cantidad grande y el nombre. Abajo, «Última simulación» dice cuál fue la última simulación guardada en
 * el historial, o «Sin simulaciones recientes»; el botón de las flechas abre «Comparar simulaciones». La tarjeta
 * no consulta la base de datos: quien la usa le pasa la última simulación con
 * {@link #showLastRun(SavedReportSummary)}.</p>
 */
public final class HospitalCard extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Ancho de la tarjeta. */
    public static final int WIDTH = 280;
    /** Texto cuando no hay simulaciones guardadas. */
    public static final String NO_RUNS_TEXT = "Sin simulaciones recientes";

    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm");

    private final List<String> stats = new ArrayList<>();
    private final JLabel lastTitle = new JLabel(NO_RUNS_TEXT, SwingConstants.CENTER);
    private final JLabel lastDetail = new JLabel(" ", SwingConstants.CENTER);
    private final IconButton compare = new IconButton(HomeIcon.SWAP, "Comparar simulaciones", 30);

    /**
     * Arma la tarjeta.
     *
     * @param title     título de la tarjeta, por ejemplo «Hospital estándar»
     * @param hospital  hospital del que se muestran los recursos
     * @param onCompare qué hacer al pulsar el botón de comparar
     * @throws IllegalArgumentException si falta algún dato
     */
    public HospitalCard(String title, HospitalConfig hospital, Runnable onCompare) {
        super(new BorderLayout(), 16);
        if (title == null || title.isBlank() || hospital == null || onCompare == null) {
            throw new IllegalArgumentException("El título, el hospital y la acción de comparar son obligatorios.");
        }
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(left(ThemedCard.heading(HomeIcon.HOSPITAL, title, HomeTheme.ACCENT)));
        body.add(Box.createVerticalStrut(14));
        body.add(left(tiles(hospital)));
        body.add(Box.createVerticalStrut(16));
        body.add(left(divider()));
        body.add(Box.createVerticalStrut(14));
        body.add(left(lastRunHeader()));
        body.add(Box.createVerticalStrut(12));
        lastTitle.setFont(HomeTheme.font(Font.PLAIN, 13f));
        lastDetail.setFont(HomeTheme.font(Font.PLAIN, 11.5f));
        lastDetail.setForeground(HomeTheme.MUTED);
        body.add(left(fullWidth(lastTitle)));
        body.add(Box.createVerticalStrut(3));
        body.add(left(fullWidth(lastDetail)));
        add(body, BorderLayout.CENTER);
        compare.setOutlined(true);
        compare.addActionListener(event -> onCompare.run());
        showLastRun(null);
    }

    /** Cuadros de los recursos: dos por fila y el último, solo, a todo lo ancho. */
    private JPanel tiles(HospitalConfig hospital) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
        grid.setOpaque(false);
        grid.add(tile(HomeIcon.BED, hospital.hospitalizationBeds(), "Camas"));
        grid.add(tile(HomeIcon.ICU, hospital.icuBeds(), "UCI"));
        grid.add(tile(HomeIcon.SURGEON, hospital.operatingRooms(), "Quirófanos"));
        grid.add(tile(HomeIcon.DOCTOR, hospital.doctors(), "Médicos"));
        JPanel all = new JPanel(new BorderLayout(0, 10));
        all.setOpaque(false);
        all.add(grid, BorderLayout.CENTER);
        all.add(tile(HomeIcon.NURSE, hospital.nurses(), "Enfermeros"), BorderLayout.SOUTH);
        return all;
    }

    private StatTile tile(HomeIcon icon, int amount, String name) {
        stats.add(amount + " " + name);
        return new StatTile(icon, String.valueOf(amount), name);
    }

    private static JComponent divider() {
        JComponent line = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(HomeTheme.DIVIDER);
                graphics.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setPreferredSize(new Dimension(WIDTH - 32, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return line;
    }

    private JPanel lastRunHeader() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(ThemedCard.heading(HomeIcon.CLOCK, "Última simulación", HomeTheme.ACCENT), BorderLayout.WEST);
        row.add(compare, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent fullWidth(JLabel label) {
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    /**
     * Muestra la última simulación guardada en el historial.
     *
     * @param last resumen de su reporte; nulo si no hay ninguna
     */
    public void showLastRun(SavedReportSummary last) {
        if (last == null) {
            lastTitle.setText(NO_RUNS_TEXT);
            lastTitle.setForeground(HomeTheme.MUTED);
            lastDetail.setText(" ");
        } else {
            lastTitle.setText(lastRunTitle(last));
            lastTitle.setForeground(HomeTheme.TEXT);
            lastDetail.setText(lastRunDetail(last));
        }
        lastTitle.setToolTipText(last == null ? null : lastRunTitle(last) + " · " + lastRunDetail(last));
        revalidate();
        repaint();
    }

    /**
     * Busca la simulación guardada más reciente.
     *
     * @param summaries resúmenes de los reportes guardados, en cualquier orden
     * @return la de fecha más reciente (y, a igual fecha, la de número más alto); vacía si no hay ninguna
     * @throws IllegalArgumentException si la lista es nula
     */
    public static Optional<SavedReportSummary> latest(List<SavedReportSummary> summaries) {
        if (summaries == null) {
            throw new IllegalArgumentException("La lista de reportes es obligatoria.");
        }
        return summaries.stream().max(Comparator.comparing(SavedReportSummary::savedAt)
                .thenComparingInt(SavedReportSummary::id));
    }

    /** Primera línea de la última simulación: «Ejecución #11 · Catástrofe». */
    static String lastRunTitle(SavedReportSummary last) {
        return "Ejecución #" + last.resultId() + " · " + last.config().scenario().getDisplayName();
    }

    /** Segunda línea de la última simulación: «5 fallecidos · 16/11/2026 16:26». */
    static String lastRunDetail(SavedReportSummary last) {
        return last.deaths() + (last.deaths() == 1 ? " fallecido" : " fallecidos") + " · "
                + SAVED_AT.format(last.savedAt());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    // ---------- consultas ----------

    /**
     * Textos de los cuadros de los recursos, en orden.
     *
     * @return por ejemplo «30 Camas», «6 UCI»…
     */
    public List<String> statTexts() {
        return List.copyOf(stats);
    }

    /**
     * Textos de la última simulación.
     *
     * @return la primera línea y, si la hay, la segunda
     */
    public List<String> lastRunTexts() {
        return lastDetail.getText().isBlank() ? List.of(lastTitle.getText())
                : List.of(lastTitle.getText(), lastDetail.getText());
    }

    public IconButton compareButton() {
        return compare;
    }

    /** Cuadro de un recurso: su icono a la izquierda, la cantidad grande y el nombre debajo. */
    private static final class StatTile extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int HEIGHT = 52;
        private static final int ICON = 26;

        private final HomeIcon icon;
        private final String amount;
        private final String name;

        StatTile(HomeIcon icon, String amount, String name) {
            this.icon = icon;
            this.amount = amount;
            this.name = name;
            setName(amount + " " + name);
            setToolTipText(amount + " " + name);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension((WIDTH - 32 - 10) / 2, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 10,
                    10);
            surface.setColor(HomeTheme.CHIP);
            surface.fill(box);
            surface.setColor(HomeTheme.CARD_BORDER);
            surface.draw(box);
            icon.paint(surface, 10, (getHeight() - ICON) / 2.0, ICON, HomeTheme.ACCENT);
            int textX = 10 + ICON + 10;
            surface.setFont(HomeTheme.font(Font.BOLD, 18f));
            FontMetrics big = surface.getFontMetrics();
            surface.setColor(HomeTheme.TEXT);
            surface.drawString(amount, textX, 8 + big.getAscent() - 2);
            surface.setFont(HomeTheme.font(Font.PLAIN, 11f));
            surface.setColor(HomeTheme.MUTED);
            surface.drawString(name, textX, getHeight() - 9);
            surface.dispose();
        }
    }

    /** Color del texto de la última simulación, para las pruebas. */
    Color lastRunColor() {
        return lastTitle.getForeground();
    }
}
