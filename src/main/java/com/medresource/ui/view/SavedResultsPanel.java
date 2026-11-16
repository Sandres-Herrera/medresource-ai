package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Contenido de la ventana de reportes guardados: dos pestañas, «Simulaciones» con los reportes de cada simulación
 * ({@link SavedReportsPanel}) y «Comparaciones» con las comparaciones guardadas ({@link SavedComparisonsPanel}).
 *
 * <p>Las pestañas son pastillas arriba a la izquierda; empieza en «Simulaciones».</p>
 */
public final class SavedResultsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Las pestañas de la ventana. */
    public enum Tab {
        /** Reportes de las simulaciones. */
        SIMULATIONS("Simulaciones"),
        /** Comparaciones de dos simulaciones. */
        COMPARISONS("Comparaciones");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final SavedReportsPanel reports = new SavedReportsPanel();
    private final SavedComparisonsPanel comparisons = new SavedComparisonsPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Tab, Pill> pills = new EnumMap<>(Tab.class);
    private transient Consumer<Tab> tabAction = tab -> { };
    private Tab selected;

    /** Crea el panel con las dos pestañas, mostrando «Simulaciones». */
    public SavedResultsPanel() {
        super(new BorderLayout());
        setBackground(HomeTheme.BACKGROUND);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(16, 24, 0, 24));
        bar.add(segment());
        add(bar, BorderLayout.NORTH);
        content.setOpaque(false);
        content.add(reports, Tab.SIMULATIONS.name());
        content.add(comparisons, Tab.COMPARISONS.name());
        add(content, BorderLayout.CENTER);
        select(Tab.SIMULATIONS);
    }

    private JPanel segment() {
        JPanel segment = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D surface = (Graphics2D) graphics.create();
                surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0,
                        10, 10);
                surface.setColor(HomeTheme.CARD);
                surface.fill(box);
                surface.setColor(HomeTheme.CARD_BORDER);
                surface.draw(box);
                surface.dispose();
            }
        };
        segment.setOpaque(false);
        segment.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        for (Tab tab : Tab.values()) {
            Pill pill = new Pill(tab.getLabel());
            pill.addActionListener(event -> {
                select(tab);
                tabAction.accept(tab);
            });
            pills.put(tab, pill);
            segment.add(pill);
        }
        return segment;
    }

    /**
     * Muestra una pestaña.
     *
     * @param tab pestaña
     * @throws IllegalArgumentException si es nula
     */
    public void select(Tab tab) {
        if (tab == null) {
            throw new IllegalArgumentException("La pestaña es obligatoria.");
        }
        selected = tab;
        pills.forEach((each, pill) -> pill.setSelected(each == tab));
        cards.show(content, tab.name());
    }

    /**
     * Cambia lo que se hace cuando el usuario elige una pestaña.
     *
     * @param action recibe la pestaña elegida
     * @throws IllegalArgumentException si es nula
     */
    public void onTab(Consumer<Tab> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de las pestañas es obligatoria.");
        }
        this.tabAction = action;
    }

    // ---------- consultas ----------

    public Tab selectedTab() {
        return selected;
    }

    /**
     * Pastilla de una pestaña.
     *
     * @param tab pestaña
     * @return su pastilla
     */
    public Pill pill(Tab tab) {
        return pills.get(tab);
    }

    public SavedReportsPanel reports() {
        return reports;
    }

    public SavedComparisonsPanel comparisons() {
        return comparisons;
    }

    /**
     * Dice si se ve el contenido de una pestaña.
     *
     * @param tab pestaña
     * @return {@code true} si es la que se ve
     */
    public boolean isShowing(Tab tab) {
        return (tab == Tab.SIMULATIONS ? reports : comparisons).isVisible();
    }
}
