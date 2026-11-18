package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.SavedReportSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Contenido de la pantalla de inicio, en dos columnas: a la izquierda el logo que late, el nombre de la aplicación,
 * la acción principal (nueva simulación) y las dos opciones rápidas como tarjetas; a la derecha la tarjeta del
 * hospital estándar con la última simulación guardada ({@link HospitalCard}). Debajo, los accesos rápidos
 * (reportes guardados, comparar simulaciones y salir) y, al pie, la universidad y la versión.
 *
 * <p>El contenido queda centrado: si la ventana crece (por ejemplo, maximizada), el espacio sobrante se reparte
 * arriba y abajo y a los lados, el pie queda abajo y el fondo llena toda la ventana.</p>
 *
 * <p>El fondo es azul noche con un resplandor verde agua detrás del logo, una línea fina de acento en el borde de
 * arriba y ondas suaves abajo. Los datos de las tarjetas (pacientes, horas, velocidad) salen de los casos que recibe,
 * así que nunca dicen algo distinto de lo que se simula. No abre ventanas ni simula nada: cada opción solo avisa a
 * su {@link Actions acción}.</p>
 */
public final class HomePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Título de la sección de los accesos rápidos. */
    public static final String QUICK_ACCESS = "Accesos rápidos";
    /** Texto del pie, a la izquierda. */
    public static final String UNIVERSITY = "Universidad Popular del Cesar · Ingeniería de Sistemas";
    /** Texto del pie, a la derecha. */
    public static final String VERSION = "MedResource AI v1.0";

    private static final int COLUMN_GAP = 32;

    /** Lo que hace cada opción de la pantalla de inicio. */
    public record Actions(Runnable newSimulation, Runnable quickSimulation, Runnable demo, Runnable savedReports,
                          Runnable compareRuns, Runnable exit) {

        /**
         * Valida las acciones.
         *
         * @throws IllegalArgumentException si falta alguna
         */
        public Actions {
            if (newSimulation == null || quickSimulation == null || demo == null || savedReports == null
                    || compareRuns == null || exit == null) {
                throw new IllegalArgumentException("Todas las acciones de la pantalla de inicio son obligatorias.");
            }
        }
    }

    private final LogoBadge logo = new LogoBadge();
    private final HomeButton newSimulation;
    private final ActionCard quick;
    private final ActionCard demo;
    private final HospitalCard hospitalCard;
    private final QuickAccessButton savedReports;
    private final QuickAccessButton compareRuns;
    private final QuickAccessButton exit;
    private final JPanel columns;

    /**
     * Arma la pantalla de inicio.
     *
     * @param hospital  hospital con el que se simulan las opciones rápidas, del que se muestran sus datos
     * @param quickCase caso de la simulación rápida
     * @param demoCase  caso de la demostración
     * @param speed     velocidad con la que empiezan las dos opciones rápidas
     * @param actions   lo que hace cada opción
     * @throws IllegalArgumentException si falta algún dato o la velocidad no es positiva
     */
    public HomePanel(HospitalConfig hospital, CaseConfig quickCase, CaseConfig demoCase, int speed, Actions actions) {
        if (hospital == null || quickCase == null || demoCase == null || actions == null) {
            throw new IllegalArgumentException("El hospital, los casos y las acciones son obligatorios.");
        }
        if (speed < 1) {
            throw new IllegalArgumentException("La velocidad debe ser positiva.");
        }
        setLayout(new BorderLayout());
        setBackground(HomeTheme.BACKGROUND);

        newSimulation = new HomeButton(HomeButton.Style.PRIMARY, "Nueva simulación", HomeIcon.PLUS);
        newSimulation.setChevron(true);
        newSimulation.addActionListener(event -> actions.newSimulation().run());
        quick = new ActionCard(HomeIcon.BOLT, HomeTheme.QUICK, HomeTheme.QUICK_TILE, "Simulación rápida",
                describe(quickCase, speed), null);
        quick.setDetailIcons(HomeIcon.USERS, HomeIcon.CLOCK, HomeIcon.GAUGE);
        quick.addActionListener(event -> actions.quickSimulation().run());
        demo = new ActionCard(HomeIcon.WARNING, HomeTheme.DANGER, HomeTheme.DANGER_TILE,
                "Demostración: " + demoCase.scenario().getDisplayName().toLowerCase(), describe(demoCase, speed),
                "DEMO");
        demo.setDetailIcons(HomeIcon.USERS, HomeIcon.CLOCK, HomeIcon.GAUGE);
        demo.addActionListener(event -> actions.demo().run());
        hospitalCard = new HospitalCard("Hospital estándar", hospital, actions.compareRuns());
        savedReports = new QuickAccessButton(HomeIcon.CHART, "Reportes guardados");
        savedReports.addActionListener(event -> actions.savedReports().run());
        compareRuns = new QuickAccessButton(HomeIcon.SWAP, "Comparar simulaciones");
        compareRuns.addActionListener(event -> actions.compareRuns().run());
        exit = new QuickAccessButton(HomeIcon.EXIT, "Salir");
        exit.addActionListener(event -> actions.exit().run());

        columns = columns();
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(36, 40, 24, 40));
        center.add(Box.createVerticalGlue());
        center.add(centered(columns));
        center.add(Box.createVerticalStrut(34));
        center.add(centered(new SectionTitle(QUICK_ACCESS, columns.getPreferredSize().width)));
        center.add(Box.createVerticalStrut(16));
        center.add(centered(quickAccess()));
        center.add(Box.createVerticalGlue());
        add(center, BorderLayout.CENTER);
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createEmptyBorder(0, 24, 18, 24));
        foot.add(new Footer(), BorderLayout.CENTER);
        add(foot, BorderLayout.SOUTH);
    }

    /** Las dos columnas: la de las acciones y la de la tarjeta del hospital, alineadas arriba. */
    private JPanel columns() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(centered(logo));
        left.add(Box.createVerticalStrut(18));
        left.add(centered(title()));
        left.add(Box.createVerticalStrut(8));
        left.add(centered(label("Simulador de asignación de recursos hospitalarios con IA", Font.PLAIN, 14f,
                HomeTheme.MUTED)));
        left.add(Box.createVerticalStrut(28));
        left.add(centered(newSimulation));
        left.add(Box.createVerticalStrut(14));
        left.add(centered(quick));
        left.add(Box.createVerticalStrut(10));
        left.add(centered(demo));
        left.setAlignmentY(Component.TOP_ALIGNMENT);
        left.setMaximumSize(left.getPreferredSize());

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);
        right.add(Box.createVerticalStrut(Math.max(0, left.getPreferredSize().height
                - hospitalCard.getPreferredSize().height)));
        hospitalCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(hospitalCard);
        right.setAlignmentY(Component.TOP_ALIGNMENT);
        right.setMaximumSize(right.getPreferredSize());

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.add(left);
        row.add(Box.createHorizontalStrut(COLUMN_GAP));
        row.add(right);
        row.setMaximumSize(row.getPreferredSize());
        return row;
    }

    /** Fila de los tres accesos rápidos. */
    private JPanel quickAccess() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        row.setOpaque(false);
        row.add(savedReports);
        row.add(compareRuns);
        row.add(exit);
        row.setMaximumSize(row.getPreferredSize());
        return row;
    }

    /**
     * Describe un caso en una línea: pacientes, duración de las llegadas y velocidad.
     *
     * @param caseConfig caso
     * @param speed      velocidad con la que empieza
     * @return por ejemplo «60 pacientes · 2 horas · velocidad ×10»
     * @throws IllegalArgumentException si falta el caso
     */
    public static String describe(CaseConfig caseConfig, int speed) {
        if (caseConfig == null) {
            throw new IllegalArgumentException("El caso es obligatorio.");
        }
        int minutes = caseConfig.durationMinutes();
        String duration = minutes % 60 != 0 ? minutes + " min"
                : minutes / 60 + (minutes == 60 ? " hora" : " horas");
        return caseConfig.patientCount() + " pacientes · " + duration + " · velocidad ×" + speed;
    }

    /**
     * Muestra en la tarjeta del hospital la última simulación guardada.
     *
     * @param last resumen de su reporte; nulo si no hay ninguna
     */
    public void showLastRun(SavedReportSummary last) {
        hospitalCard.showLastRun(last);
    }

    /** Botón de nueva simulación. */
    HomeButton newSimulationButton() {
        return newSimulation;
    }

    /** Tarjeta de la simulación rápida. */
    ActionCard quickCard() {
        return quick;
    }

    /** Tarjeta de la demostración. */
    ActionCard demoCard() {
        return demo;
    }

    /** Tarjeta del hospital estándar y de la última simulación. */
    HospitalCard hospitalCard() {
        return hospitalCard;
    }

    /** Acceso rápido a los reportes guardados. */
    QuickAccessButton savedReportsLink() {
        return savedReports;
    }

    /** Acceso rápido para comparar dos simulaciones guardadas. */
    QuickAccessButton compareRunsLink() {
        return compareRuns;
    }

    /** Acceso rápido para salir. */
    QuickAccessButton exitLink() {
        return exit;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        surface.setColor(HomeTheme.BACKGROUND);
        surface.fillRect(0, 0, width, height);
        Point center = logoCenter();
        float radius = Math.max(1f, Math.min(width, 640) * 0.5f);
        Color clear = new Color(HomeTheme.GLOW.getRed(), HomeTheme.GLOW.getGreen(), HomeTheme.GLOW.getBlue(), 0);
        surface.setPaint(new RadialGradientPaint(new Point2D.Double(center.x, center.y), radius,
                new float[]{0f, 1f}, new Color[]{HomeTheme.GLOW, clear}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        surface.fillRect(0, 0, width, height);
        paintWaves(surface, width, height);
        surface.setPaint(new GradientPaint(0, 0, clear, width / 2f, 0, HomeTheme.ACCENT, true));
        surface.fillRect(0, 0, width, 2);
        surface.dispose();
    }

    /** Centro del logo en este panel; antes de acomodarlo, arriba al medio. */
    private Point logoCenter() {
        if (logo.getParent() == null || logo.getWidth() == 0) {
            return new Point(getWidth() / 2, 76);
        }
        return SwingUtilities.convertPoint(logo, logo.getWidth() / 2, logo.getHeight() / 2, this);
    }

    /** Tres ondas suaves de acento en la parte de abajo, detrás de los accesos rápidos y el pie. */
    private static void paintWaves(Graphics2D surface, int width, int height) {
        double[][] waves = {{0.80, 0.70, 0.86, 10}, {0.86, 0.92, 0.78, 16}, {0.92, 0.84, 0.95, 22}};
        for (double[] wave : waves) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(0, height * wave[0]);
            path.curveTo(width * 0.3, height * wave[1], width * 0.6, height * wave[2], width, height * wave[1]);
            path.lineTo(width, height);
            path.lineTo(0, height);
            path.closePath();
            surface.setColor(new Color(HomeTheme.ACCENT.getRed(), HomeTheme.ACCENT.getGreen(),
                    HomeTheme.ACCENT.getBlue(), (int) wave[3] / 2));
            surface.fill(path);
            Path2D.Double edge = new Path2D.Double();
            edge.moveTo(0, height * wave[0]);
            edge.curveTo(width * 0.3, height * wave[1], width * 0.6, height * wave[2], width, height * wave[1]);
            surface.setStroke(new BasicStroke(1f));
            surface.setColor(new Color(HomeTheme.ACCENT.getRed(), HomeTheme.ACCENT.getGreen(),
                    HomeTheme.ACCENT.getBlue(), (int) wave[3] * 2));
            surface.draw(edge);
        }
    }

    private static JLabel title() {
        String accent = String.format("#%06X", HomeTheme.ACCENT.getRGB() & 0xFFFFFF);
        JLabel title = label("<html>MedResource <span style='color:" + accent + "'>AI</span></html>", Font.BOLD,
                38f, HomeTheme.TEXT);
        title.getAccessibleContext().setAccessibleName("MedResource AI");
        return title;
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(HomeTheme.font(style, size));
        label.setForeground(color);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private static <T extends JComponent> T centered(T component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        return component;
    }

    /** Título de una sección, centrado entre dos líneas de acento que se desvanecen hacia los lados. */
    private static final class SectionTitle extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int HEIGHT = 24;

        private final String text;
        private final int width;

        SectionTitle(String text, int width) {
            this.text = text;
            this.width = width;
            setName(text);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            surface.setFont(HomeTheme.font(Font.BOLD, 15f));
            FontMetrics metrics = surface.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int x = (getWidth() - textWidth) / 2;
            int middle = getHeight() / 2;
            Color clear = new Color(HomeTheme.ACCENT.getRed(), HomeTheme.ACCENT.getGreen(),
                    HomeTheme.ACCENT.getBlue(), 0);
            int line = Math.max(0, x - 18);
            surface.setPaint(new GradientPaint(line / 3f, 0, clear, line, 0, HomeTheme.ACCENT));
            surface.fillRect(line / 3, middle, line - line / 3, 2);
            int start = x + textWidth + 18;
            int end = getWidth() - line / 3;
            surface.setPaint(new GradientPaint(start, 0, HomeTheme.ACCENT, end, 0, clear));
            surface.fillRect(start, middle, Math.max(0, end - start), 2);
            surface.setColor(HomeTheme.TEXT);
            surface.drawString(text, x, (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
            surface.dispose();
        }
    }

    /** Pie: la universidad a la izquierda y la versión a la derecha, en una barra redondeada. */
    private static final class Footer extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int HEIGHT = 44;
        private static final int ICON = 18;

        Footer() {
            setName(UNIVERSITY);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(640, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            RoundRectangle2D bar = new RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, 14, 14);
            surface.setColor(new Color(HomeTheme.CARD.getRed(), HomeTheme.CARD.getGreen(), HomeTheme.CARD.getBlue(),
                    200));
            surface.fill(bar);
            surface.setColor(HomeTheme.CARD_BORDER);
            surface.draw(bar);
            surface.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
            FontMetrics metrics = surface.getFontMetrics();
            int baseline = (height + metrics.getAscent() - metrics.getDescent()) / 2;
            HomeIcon.GRADUATION.paint(surface, 16, (height - ICON) / 2.0, ICON, HomeTheme.ACCENT);
            surface.setColor(HomeTheme.MUTED);
            surface.drawString(UNIVERSITY, 16 + ICON + 10, baseline);
            int versionX = width - 18 - metrics.stringWidth(VERSION);
            int iconX = versionX - ICON - 8;
            HomeIcon.PULSE.paint(surface, iconX, (height - ICON) / 2.0, ICON, HomeTheme.ACCENT);
            surface.setColor(HomeTheme.MUTED);
            surface.drawString(VERSION, versionX, baseline);
            surface.setColor(HomeTheme.DIVIDER);
            surface.fillRect(iconX - 16, 10, 1, height - 20);
            surface.dispose();
        }
    }
}
