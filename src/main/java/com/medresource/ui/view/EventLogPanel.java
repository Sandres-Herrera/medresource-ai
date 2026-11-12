package com.medresource.ui.view;

import com.medresource.ui.controller.EventCategory;
import com.medresource.ui.controller.EventFilter;
import com.medresource.ui.controller.EventLog;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tarjeta «Registro de eventos» de la ventana de la simulación, con el tema oscuro.
 *
 * <p>Arriba van el título, una pastilla por grupo de eventos y la casilla «Solo el paciente seleccionado».
 * Debajo, una fila por evento: la hora, un punto del color del evento ({@link HomeTheme#eventColor}), el tipo y la
 * descripción. Las filas del paciente seleccionado se ven resaltadas, y al hacer clic en una fila se avisa el
 * código de su paciente ({@link #onPatientClicked}), para seleccionarlo.</p>
 *
 * <p>El panel no guarda la historia: muestra las filas que le pasan ({@link #showEntries}, {@link #appendEntries})
 * y dice qué filtro eligió el usuario ({@link #filter(String)}); guarda como mucho {@value #MAX_ROWS} filas.</p>
 */
public final class EventLogPanel extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Filas que se muestran como mucho; las más viejas se quitan. */
    public static final int MAX_ROWS = 400;
    /** Alto de cada fila. */
    public static final int ROW_HEIGHT = 24;
    /** Texto cuando no hay eventos que mostrar. */
    public static final String EMPTY = "Todavía no hay eventos para mostrar.";
    /** Texto cuando se filtran los fallecimientos y no hay ninguno. */
    public static final String NO_DEATHS = "Sin fallecimientos por ahora.";

    private static final int VISIBLE_ROWS = 5;

    private final Map<EventCategory, Pill> pills = new EnumMap<>(EventCategory.class);
    private final JCheckBox onlySelected = new JCheckBox("Solo el paciente seleccionado");
    private final EventList list = new EventList();
    private final JScrollPane scroll;
    private transient Runnable filterAction = () -> { };
    private transient Consumer<String> patientAction = code -> { };
    private EventCategory category = EventCategory.ALL;

    /** Crea el registro vacío, mostrando todos los eventos. */
    public EventLogPanel() {
        super(new BorderLayout(0, 8), 12);
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        JLabel title = new JLabel("Registro de eventos");
        title.setFont(HomeTheme.font(Font.BOLD, 14f));
        title.setForeground(HomeTheme.TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        left.add(title);
        for (EventCategory each : EventCategory.values()) {
            Pill pill = new Pill(shortName(each));
            pill.addActionListener(event -> choose(each));
            pills.put(each, pill);
            left.add(pill);
        }
        onlySelected.setOpaque(false);
        onlySelected.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        onlySelected.setForeground(HomeTheme.MUTED);
        onlySelected.setFocusPainted(false);
        onlySelected.setEnabled(false);
        onlySelected.setToolTipText("Muestra solo la historia del paciente cuya ficha se ve.");
        onlySelected.addActionListener(event -> filterAction.run());
        header.add(left, BorderLayout.CENTER);
        header.add(onlySelected, BorderLayout.EAST);
        scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        DarkScrollBarUI.apply(scroll);
        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        pills.get(category).setSelected(true);
    }

    /**
     * Nombre corto de un grupo para su pastilla, por ejemplo «Atención».
     *
     * @param category grupo de eventos
     * @return su nombre corto
     * @throws IllegalArgumentException si es nulo
     */
    public static String shortName(EventCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("El grupo de eventos es obligatorio.");
        }
        return switch (category) {
            case ALL -> "Todos";
            case ARRIVALS -> "Llegadas y triage";
            case CARE -> "Atención";
            case OUTCOMES -> "Altas y remisiones";
            case DEATHS -> "Fallecimientos";
        };
    }

    /**
     * Cambia lo que se hace cuando el usuario cambia el filtro.
     *
     * @param action acción; normalmente vuelve a pedir las filas con {@link #filter(String)}
     * @throws IllegalArgumentException si es nula
     */
    public void onFilterChanged(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del filtro es obligatoria.");
        }
        this.filterAction = action;
    }

    /**
     * Cambia lo que se hace al hacer clic en una fila.
     *
     * @param action recibe el código del paciente de esa fila
     * @throws IllegalArgumentException si es nula
     */
    public void onPatientClicked(Consumer<String> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del clic es obligatoria.");
        }
        this.patientAction = action;
    }

    /**
     * Filtro que eligió el usuario.
     *
     * @param selectedPatient código del paciente seleccionado, o nulo si no hay
     * @return el grupo elegido y, si marcó la casilla y hay paciente, solo ese paciente
     */
    public EventFilter filter(String selectedPatient) {
        String patient = onlySelected.isSelected() && selectedPatient != null ? selectedPatient : null;
        return new EventFilter(category, patient);
    }

    public EventCategory getCategory() {
        return category;
    }

    /**
     * Elige un grupo de eventos, como si el usuario hubiera pulsado su pastilla.
     *
     * @param chosen grupo
     * @throws IllegalArgumentException si es nulo
     */
    public void choose(EventCategory chosen) {
        if (chosen == null) {
            throw new IllegalArgumentException("El grupo de eventos es obligatorio.");
        }
        category = chosen;
        pills.forEach((each, pill) -> pill.setSelected(each == chosen));
        list.setEmptyText(chosen == EventCategory.DEATHS ? NO_DEATHS : EMPTY);
        filterAction.run();
    }

    /**
     * Habilita o no la casilla «Solo el paciente seleccionado»; sin paciente, además la desmarca.
     *
     * @param enabled {@code true} si hay un paciente seleccionado
     */
    public void setOnlySelectedEnabled(boolean enabled) {
        onlySelected.setEnabled(enabled);
        if (!enabled) {
            onlySelected.setSelected(false);
        }
    }

    /**
     * Cambia las filas por otras, por ejemplo al cambiar el filtro; baja hasta la última.
     *
     * @param entries filas, de la más vieja a la más reciente
     * @throws IllegalArgumentException si es nula
     */
    public void showEntries(List<EventLog.Entry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("Las filas son obligatorias.");
        }
        list.set(entries);
        scrollToEnd();
    }

    /**
     * Agrega filas al final; si pasan de {@value #MAX_ROWS}, quita las más viejas. Baja hasta la última.
     *
     * @param entries filas nuevas
     * @throws IllegalArgumentException si es nula
     */
    public void appendEntries(List<EventLog.Entry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("Las filas son obligatorias.");
        }
        if (!entries.isEmpty()) {
            list.add(entries);
            scrollToEnd();
        }
    }

    /**
     * Resalta las filas de un paciente.
     *
     * @param patientCode código del paciente seleccionado, o nulo para no resaltar ninguna
     */
    public void setSelectedPatient(String patientCode) {
        list.setHighlighted(patientCode);
    }

    private void scrollToEnd() {
        list.revalidate();
        list.scrollRectToVisible(new Rectangle(0, Math.max(0, list.rowCount() * ROW_HEIGHT - 1), 1, 1));
    }

    // ---------- para las pruebas ----------

    /** Cantidad de filas que se ven. */
    int rowCount() {
        return list.rowCount();
    }

    /**
     * Texto de una fila: hora, tipo y descripción.
     *
     * @param index número de fila, desde 0
     * @return el texto
     */
    String rowText(int index) {
        return list.text(index);
    }

    /** Texto que se ve cuando no hay filas. */
    String emptyText() {
        return list.emptyText;
    }

    /**
     * Hace clic en una fila, como si fuera el usuario.
     *
     * @param index número de fila, desde 0
     */
    void clickRow(int index) {
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0L, 0, 20, index * ROW_HEIGHT + 5, 1,
                false, MouseEvent.BUTTON1));
    }

    /** Indica si una fila se ve resaltada. */
    boolean isHighlighted(int index) {
        return list.isHighlighted(index);
    }

    Pill pill(EventCategory each) {
        return pills.get(each);
    }

    JCheckBox onlySelectedBox() {
        return onlySelected;
    }

    /** Lista pintada a mano: una fila por evento. */
    private final class EventList extends JComponent implements Scrollable {

        private static final long serialVersionUID = 1L;
        private static final int TIME_WIDTH = 70;
        private static final int TYPE_WIDTH = 160;

        private final List<EventLog.Entry> rows = new ArrayList<>();
        private String highlighted;
        private int hover = -1;
        private String emptyText = EMPTY;

        EventList() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    int index = event.getY() / ROW_HEIGHT;
                    if (index >= 0 && index < rows.size()) {
                        patientAction.accept(rows.get(index).event().patientCode());
                    }
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    int index = event.getY() / ROW_HEIGHT;
                    if (index != hover) {
                        hover = index;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = -1;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void set(List<EventLog.Entry> entries) {
            rows.clear();
            rows.addAll(entries.subList(Math.max(0, entries.size() - MAX_ROWS), entries.size()));
            revalidate();
            repaint();
        }

        void add(List<EventLog.Entry> entries) {
            rows.addAll(entries);
            if (rows.size() > MAX_ROWS) {
                rows.subList(0, rows.size() - MAX_ROWS).clear();
            }
            revalidate();
            repaint();
        }

        void setHighlighted(String patientCode) {
            highlighted = patientCode;
            repaint();
        }

        void setEmptyText(String text) {
            emptyText = text;
            repaint();
        }

        int rowCount() {
            return rows.size();
        }

        boolean isHighlighted(int index) {
            return highlighted != null && highlighted.equals(rows.get(index).event().patientCode());
        }

        String text(int index) {
            EventLog.Entry entry = rows.get(index);
            return entry.time() + " " + entry.event().type().getDisplayName() + " " + entry.event().description();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, Math.max(ROW_HEIGHT * 2, rows.size() * ROW_HEIGHT));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(400, VISIBLE_ROWS * ROW_HEIGHT);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(ROW_HEIGHT, visible.height - ROW_HEIGHT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (rows.isEmpty()) {
                surface.setFont(HomeTheme.font(Font.PLAIN, 13f));
                surface.setColor(HomeTheme.MUTED);
                surface.drawString(emptyText, 6, ROW_HEIGHT);
                surface.dispose();
                return;
            }
            Rectangle clip = surface.getClipBounds();
            int first = clip == null ? 0 : Math.max(0, clip.y / ROW_HEIGHT);
            int last = clip == null ? rows.size() - 1 : Math.min(rows.size() - 1, (clip.y + clip.height) / ROW_HEIGHT);
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 11);
            Font text = HomeTheme.font(Font.PLAIN, 12.5f);
            for (int index = first; index <= last; index++) {
                paintRow(surface, index, mono, text);
            }
            surface.dispose();
        }

        private void paintRow(Graphics2D surface, int index, Font mono, Font text) {
            EventLog.Entry entry = rows.get(index);
            int top = index * ROW_HEIGHT;
            Color background = isHighlighted(index) ? HomeTheme.SELECTED
                    : index == hover ? HomeTheme.CARD_HOVER : null;
            if (background != null) {
                surface.setColor(background);
                surface.fill(new RoundRectangle2D.Double(0, top + 1, getWidth(), ROW_HEIGHT - 2, 8, 8));
            }
            int baseline = top + ROW_HEIGHT / 2 + 5;
            surface.setFont(mono);
            surface.setColor(HomeTheme.MUTED);
            surface.drawString(entry.time(), 6, baseline);
            surface.setColor(HomeTheme.eventColor(entry.event().type()));
            surface.fill(new Ellipse2D.Double(6 + TIME_WIDTH, top + ROW_HEIGHT / 2.0 - Dot.SIZE / 2.0, Dot.SIZE,
                    Dot.SIZE));
            surface.setFont(text);
            surface.setColor(HomeTheme.MUTED);
            int typeX = 6 + TIME_WIDTH + Dot.SIZE + 10;
            surface.drawString(entry.event().type().getDisplayName(), typeX, baseline);
            surface.setColor(HomeTheme.TEXT);
            int descriptionX = typeX + TYPE_WIDTH;
            surface.drawString(fit(surface.getFontMetrics(), entry.event().description(),
                    getWidth() - descriptionX - 6), descriptionX, baseline);
        }

        /** Corta un texto con «…» para que quepa en el ancho dado. */
        private String fit(FontMetrics metrics, String value, int width) {
            if (metrics.stringWidth(value) <= width) {
                return value;
            }
            String cut = value;
            while (!cut.isEmpty() && metrics.stringWidth(cut + "…") > width) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return cut + "…";
        }
    }
}
