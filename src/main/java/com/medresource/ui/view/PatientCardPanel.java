package com.medresource.ui.view;

import com.medresource.model.Symptom;
import com.medresource.model.TriageLevel;
import com.medresource.ui.controller.EventLog;
import com.medresource.ui.controller.PatientCard;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ficha del paciente seleccionado, para la columna derecha de la ventana de la simulación, con el tema oscuro.
 *
 * <p>Arriba va el código del paciente en una placa del color de su triage, su edad, sexo y sangre, y la equis
 * para cerrar. Debajo: el triage y lo que está haciendo, el diagnóstico (pendiente hasta que lo clasifican), los
 * síntomas con su nivel (con una estrella el más grave, que es el que define el triage), un aviso si no puede
 * caminar, dónde está, quién lo atiende, cuánto lleva en el hospital, su salud y el avance de su tratamiento, los
 * últimos eventos de su recorrido y los botones «Seguir con la cámara» y «Cerrar». Sin paciente, dice cómo
 * elegir uno.</p>
 *
 * <p>Se vuelve a armar solo cuando cambia algo de lo que se ve, aunque la ventana le pase la ficha en cada
 * cuadro. Quien la usa decide qué hacer al seguir o cerrar ({@link #onFollow}, {@link #onClose}).</p>
 */
public final class PatientCardPanel extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Ancho de la ficha en píxeles. */
    public static final int WIDTH = 300;
    /** Texto que se ve cuando no hay paciente seleccionado. */
    public static final String HINT = "Haz clic en un paciente del plano para ver su ficha.";
    /** Cuántos eventos del recorrido se muestran como mucho. */
    public static final int TRAIL_SIZE = 5;

    private static final int CONTENT_WIDTH = WIDTH - 28;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private final transient Map<String, String> values = new LinkedHashMap<>();
    private final List<String> chips = new ArrayList<>();
    private final List<String> trail = new ArrayList<>();
    private transient Consumer<Boolean> followAction = following -> { };
    private transient Runnable closeAction = () -> { };
    private String title = "";
    private String hint = HINT;
    private String shownKey = "";
    private boolean showingCard;
    private boolean following;
    private boolean walkingWarning;
    private HomeButton followButton;

    /** Crea la ficha vacía, con el texto que dice cómo elegir un paciente. */
    public PatientCardPanel() {
        super(new BorderLayout(), 14);
        clear();
    }

    /**
     * Cambia lo que se hace al pulsar «Seguir con la cámara» o «Dejar de seguir».
     *
     * @param action recibe {@code true} si ahora hay que seguir al paciente
     * @throws IllegalArgumentException si es nula
     */
    public void onFollow(Consumer<Boolean> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de seguir es obligatoria.");
        }
        this.followAction = action;
    }

    /**
     * Cambia lo que se hace al cerrar la ficha (con la equis o con «Cerrar»).
     *
     * @param action acción
     * @throws IllegalArgumentException si es nula
     */
    public void onClose(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de cerrar es obligatoria.");
        }
        this.closeAction = action;
    }

    /**
     * Muestra la ficha de un paciente.
     *
     * @param card       ficha del paciente tal como se ve ahora
     * @param trailItems eventos de su recorrido, del más viejo al más reciente; se muestran los últimos
     *                   {@value #TRAIL_SIZE}
     * @throws IllegalArgumentException si alguno es nulo
     */
    public void showCard(PatientCard card, List<EventLog.Entry> trailItems) {
        if (card == null || trailItems == null) {
            throw new IllegalArgumentException("La ficha y su recorrido son obligatorios.");
        }
        List<EventLog.Entry> last = trailItems.subList(Math.max(0, trailItems.size() - TRAIL_SIZE), trailItems.size());
        String key = card + "|" + last + "|" + following;
        showingCard = true;
        setHighlighted(true);
        if (key.equals(shownKey)) {
            return;
        }
        shownKey = key;
        rebuild(buildCard(card, last));
    }

    /**
     * Avisa que el paciente seleccionado ya no está en el hospital.
     *
     * @param patientCode código del paciente
     */
    public void showGone(String patientCode) {
        showingCard = false;
        following = false;
        setHighlighted(true);
        shownKey = "gone:" + patientCode;
        title = "Paciente " + patientCode;
        hint = "Ya salió del hospital.";
        JPanel content = column();
        content.add(header(patientCode, HomeTheme.MUTED, hint));
        rebuild(content);
    }

    /** Vuelve a la ficha vacía, con el texto que dice cómo elegir un paciente. */
    public void clear() {
        showingCard = false;
        following = false;
        setHighlighted(false);
        shownKey = "";
        title = "Ficha del paciente";
        hint = HINT;
        rebuild(buildEmpty());
    }

    public boolean isShowingCard() {
        return showingCard;
    }

    public boolean isFollowing() {
        return following;
    }

    /**
     * Marca si la cámara sigue al paciente; cambia el botón entre «Seguir con la cámara» y «Dejar de seguir».
     *
     * @param following {@code true} si la cámara lo sigue
     */
    public void setFollowing(boolean following) {
        if (this.following != following) {
            this.following = following;
            shownKey = "";
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(WIDTH, getPreferredSize().height);
    }

    // ---------- para las pruebas ----------

    /** Título que se ve arriba: «Paciente P003» o «Ficha del paciente». */
    String titleText() {
        return title;
    }

    /** Texto de ayuda o aviso: cómo elegir un paciente o que ya salió. */
    String hintText() {
        return hint;
    }

    /**
     * Valor de una fila de la ficha, por ejemplo «Ubicación» o «Salud».
     *
     * @param field nombre de la fila
     * @return su valor, o vacío si no se ve
     */
    String valueOf(String field) {
        return values.getOrDefault(field, "");
    }

    /** Textos de las etiquetas: el triage, lo que está haciendo y los síntomas, en orden. */
    List<String> chipTexts() {
        return List.copyOf(chips);
    }

    /** Líneas del recorrido, por ejemplo «00:20:16 Llegada de paciente». */
    List<String> trailTexts() {
        return List.copyOf(trail);
    }

    /** Indica si se ve el aviso de que no puede caminar. */
    boolean hasWalkingWarning() {
        return walkingWarning;
    }

    /** Botón de seguir con la cámara, o nulo si no se ve. */
    HomeButton followButton() {
        return followButton;
    }

    // ---------- armado ----------

    private void rebuild(JComponent content) {
        removeAll();
        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JComponent buildEmpty() {
        values.clear();
        chips.clear();
        trail.clear();
        walkingWarning = false;
        followButton = null;
        JPanel content = column();
        content.setBorder(BorderFactory.createEmptyBorder(22, 0, 22, 0));
        JComponent badge = new CircleIcon(HomeIcon.CURSOR);
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(badge);
        content.add(Box.createVerticalStrut(10));
        content.add(centered(label(title, Font.BOLD, 14f, HomeTheme.TEXT)));
        content.add(Box.createVerticalStrut(4));
        content.add(centered(label("<html><div style='text-align:center;width:" + (CONTENT_WIDTH - 70) + "px'>"
                + HINT + "</div></html>", Font.PLAIN, 12.5f, HomeTheme.MUTED)));
        return content;
    }

    private JComponent buildCard(PatientCard card, List<EventLog.Entry> last) {
        values.clear();
        chips.clear();
        trail.clear();
        TriageLevel triage = card.triage();
        Color color = HomeTheme.triageColor(triage);
        title = "Paciente " + card.code();
        hint = "";
        JPanel content = column();
        content.add(header(card.code(), color, card.ageAndSex() + " · " + card.bloodType().getLabel()));
        content.add(Box.createVerticalStrut(10));
        String triageText = triage == null ? "Sin clasificar" : triage.getDisplayName().replace(" — ", " · ");
        content.add(chip(triage == null ? new Chip(triageText, HomeTheme.MUTED, MeterBar.TRACK, null)
                : Chip.tinted(triageText, color)));
        content.add(Box.createVerticalStrut(5));
        content.add(chip(new Chip(card.activity(), HomeTheme.ACCENT, HomeTheme.SELECTED, null)));
        content.add(Box.createVerticalStrut(8));
        content.add(row("Diagnóstico", triage == null ? "Pendiente" : card.disease()));
        content.add(section("Síntomas", card.symptoms().size() + (card.symptoms().size() == 1 ? " síntoma"
                : " síntomas")));
        addSymptoms(content, card);
        if (card.cannotWalk()) {
            content.add(Box.createVerticalStrut(8));
            content.add(new Warning("No puede caminar: se traslada en camilla"));
            walkingWarning = true;
        } else {
            walkingWarning = false;
        }
        content.add(Box.createVerticalStrut(6));
        content.add(row("Ubicación", card.place() + (card.walking() ? " (va en camino)" : "")));
        content.add(row("Atienden", card.caregivers().isEmpty() ? "Nadie por ahora"
                : String.join("<br>", card.caregivers())));
        content.add(row("En el hospital", card.minutesInHospital() + " min"));
        content.add(Box.createVerticalStrut(8));
        Color health = HomeTheme.healthColor(card.health());
        content.add(meter("Salud", card.healthPercent() + " / 100", health, card.health() / 100.0));
        if (card.inTreatment()) {
            content.add(Box.createVerticalStrut(6));
            content.add(meter("Tratamiento", card.progressPercent() + " %", HomeTheme.BLUE, card.progress()));
        } else {
            values.put("Tratamiento", "");
        }
        content.add(section("Recorrido", ""));
        addTrail(content, last);
        content.add(Box.createVerticalStrut(12));
        content.add(buttons());
        return content;
    }

    private void addSymptoms(JPanel content, PatientCard card) {
        List<Symptom> sorted = new ArrayList<>(card.symptoms());
        sorted.sort(Comparator.comparingInt(symptom -> symptom.getTriageLevel().getLevel()));
        TriageLevel worst = card.worstSymptomLevel().orElse(null);
        for (Symptom symptom : sorted) {
            TriageLevel level = symptom.getTriageLevel();
            Color color = HomeTheme.triageColor(level);
            boolean star = card.triage() != null && level == worst;
            String text = (star ? "★ " : "") + symptom.getDisplayName() + " · " + ROMAN[level.getLevel() - 1];
            content.add(chip(new Chip(text, color, Chip.tint(color), star ? color : null)));
            content.add(Box.createVerticalStrut(4));
        }
        if (card.triage() != null && !sorted.isEmpty()) {
            content.add(left(label("★ El síntoma más grave define el triage", Font.PLAIN, 11f, HomeTheme.MUTED)));
        }
    }

    private void addTrail(JPanel content, List<EventLog.Entry> last) {
        if (last.isEmpty()) {
            content.add(left(label("Sin eventos todavía.", Font.PLAIN, 12.5f, HomeTheme.MUTED)));
            return;
        }
        for (EventLog.Entry entry : last) {
            String type = entry.event().type().getDisplayName();
            trail.add(entry.time() + " " + type);
            JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
            line.setOpaque(false);
            line.add(new Dot(HomeTheme.eventColor(entry.event().type())));
            JLabel time = new JLabel(entry.time());
            time.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            time.setForeground(HomeTheme.MUTED);
            line.add(time);
            line.add(label(type, Font.PLAIN, 12.5f, HomeTheme.TEXT));
            content.add(left(line));
        }
    }

    private JComponent buttons() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        followButton = following
                ? new HomeButton(HomeButton.Style.OUTLINE, "Dejar de seguir", HomeIcon.TARGET)
                : new HomeButton(HomeButton.Style.COMPACT, "Seguir con la cámara", HomeIcon.TARGET);
        followButton.addActionListener(event -> {
            following = !following;
            shownKey = "";
            followAction.accept(following);
        });
        HomeButton close = new HomeButton(HomeButton.Style.OUTLINE, "Cerrar", null);
        close.addActionListener(event -> closeAction.run());
        row.add(followButton, BorderLayout.CENTER);
        row.add(close, BorderLayout.EAST);
        return left(row);
    }

    private JComponent header(String code, Color color, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(new CodeBadge(code, color), BorderLayout.WEST);
        JPanel names = column();
        names.add(left(label(title, Font.BOLD, 15f, HomeTheme.TEXT)));
        names.add(left(label(subtitle, Font.PLAIN, 12.5f, HomeTheme.MUTED)));
        header.add(names, BorderLayout.CENTER);
        IconButton close = new IconButton(HomeIcon.CLOSE, "Cerrar ficha", 28);
        close.setOutlined(true);
        close.addActionListener(event -> closeAction.run());
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        corner.setOpaque(false);
        corner.add(close);
        header.add(corner, BorderLayout.EAST);
        return left(header);
    }

    private JComponent chip(Chip chip) {
        chips.add(chip.getText());
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.setOpaque(false);
        holder.add(chip);
        return left(holder);
    }

    private JComponent row(String name, String value) {
        values.put(name, value.replace("<br>", "\n"));
        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(HomeTheme.DIVIDER);
                graphics.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        row.add(label(name, Font.PLAIN, 12.5f, HomeTheme.MUTED), BorderLayout.WEST);
        JLabel shown = label("<html><div style='text-align:right'>" + value + "</div></html>", Font.PLAIN, 12.5f,
                HomeTheme.TEXT);
        shown.setHorizontalAlignment(JLabel.RIGHT);
        row.add(shown, BorderLayout.EAST);
        return left(row);
    }

    private JComponent section(String name, String detail) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
        row.add(label(name, Font.BOLD, 12.5f, HomeTheme.TEXT), BorderLayout.WEST);
        if (!detail.isEmpty()) {
            row.add(label(detail, Font.PLAIN, 12f, HomeTheme.MUTED), BorderLayout.EAST);
        }
        return left(row);
    }

    private JComponent meter(String name, String value, Color color, double fraction) {
        values.put(name, value);
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 3));
        box.setOpaque(false);
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        labels.add(label(name, Font.PLAIN, 12.5f, HomeTheme.MUTED), BorderLayout.WEST);
        JLabel shown = label(value, Font.BOLD, 12.5f, name.equals("Salud") ? color : HomeTheme.TEXT);
        shown.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 3));
        labels.add(shown, BorderLayout.EAST);
        MeterBar bar = new MeterBar(color);
        bar.setFraction(fraction);
        box.add(labels);
        box.add(bar);
        return left(box);
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(style, size));
        label.setForeground(color);
        return label;
    }

    /** Pone un componente a todo el ancho de la ficha, alineado a la izquierda, sin estirarlo hacia abajo. */
    private static JComponent left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(CONTENT_WIDTH, component.getPreferredSize().height));
        return component;
    }

    private static JComponent centered(JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        return component;
    }

    /** Placa cuadrada con el código del paciente, del color de su triage. */
    private static final class CodeBadge extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int SIZE = 44;

        private final String code;
        private final Color color;

        CodeBadge(String code, Color color) {
            this.code = code;
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE, SIZE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            surface.setColor(Chip.tint(color));
            surface.fill(new RoundRectangle2D.Double(0, 0, SIZE, SIZE, 10, 10));
            surface.setFont(HomeTheme.font(Font.BOLD, 13f));
            FontMetrics metrics = surface.getFontMetrics();
            surface.setColor(color);
            surface.drawString(code, (SIZE - metrics.stringWidth(code)) / 2,
                    (SIZE + metrics.getAscent() - metrics.getDescent()) / 2);
            surface.dispose();
        }
    }

    /** Círculo verde oscuro con un icono, para la ficha vacía. */
    private static final class CircleIcon extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int SIZE = 44;

        private final HomeIcon icon;

        CircleIcon(HomeIcon icon) {
            this.icon = icon;
            setMaximumSize(new Dimension(SIZE, SIZE));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE, SIZE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            surface.setColor(HomeTheme.SELECTED);
            double x = (getWidth() - SIZE) / 2.0;
            surface.fill(new Ellipse2D.Double(x, 0, SIZE, SIZE));
            icon.paint(surface, x + 12, 12, 20, HomeTheme.ACCENT);
            surface.dispose();
        }
    }

    /** Aviso rojo de una línea, como «No puede caminar: se traslada en camilla». */
    private static final class Warning extends JLabel {

        private static final long serialVersionUID = 1L;

        Warning(String text) {
            super(text);
            setFont(HomeTheme.font(Font.PLAIN, 12f));
            setForeground(new Color(0xFC, 0xA5, 0xA5));
            setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(CONTENT_WIDTH, getPreferredSize().height));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 8, 8);
            surface.setColor(new Color(0xE5, 0x48, 0x4D, 26));
            surface.fill(box);
            surface.setColor(new Color(0xE5, 0x48, 0x4D, 102));
            surface.draw(box);
            surface.dispose();
            super.paintComponent(graphics);
        }
    }
}
