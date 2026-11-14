package com.medresource.ui.view;

import com.medresource.ui.controller.PlaceInfo;
import com.medresource.ui.render.PlanAlert;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que el panel del plano dibuja encima de la escena, en píxeles de la pantalla (no cambia de tamaño con el
 * aumento): los avisos con su ícono y su texto, la brújula de la vista 2.5D, el cartel de la repetición y el panel
 * flotante con lo que hay en una cama, un quirófano o un consultorio.
 */
final class MapOverlay {

    /** Radio del círculo de un aviso, en píxeles. */
    static final double ALERT_RADIUS = 11.0;
    /** Distancia al centro de un aviso, en píxeles, a la que un clic todavía le atina. */
    static final double ALERT_HIT = 15.0;
    /** Radio de la brújula, en píxeles. */
    static final double COMPASS_RADIUS = 19.0;
    /** Distancia del centro de la brújula a los bordes de arriba y de la derecha del panel, en píxeles. */
    static final double COMPASS_INSET = 30.0;
    /** Ancho del panel flotante de un lugar, en píxeles. */
    static final int CARD_WIDTH = 250;

    private static final Color CARD_BACKGROUND = new Color(0x0F, 0x17, 0x2A, 235);
    private static final Color CARD_BORDER = new Color(0x38, 0xBD, 0xF8);
    private static final Color CARD_TEXT = new Color(0xE2, 0xE8, 0xF0);
    private static final Color CARD_MUTED = new Color(0x94, 0xA3, 0xB8);
    private static final Color LABEL_BACKGROUND = new Color(0x0F, 0x17, 0x2A, 215);
    private static final Color BANNER = new Color(0xDC, 0x26, 0x26, 230);
    private static final Color COMPASS_FACE = new Color(0x0F, 0x17, 0x2A, 200);
    private static final Color NORTH = new Color(0xF8, 0x71, 0x71);

    private MapOverlay() {
    }

    /**
     * Ícono de un aviso: un círculo de su color con un borde que late, su símbolo y, al lado, su texto.
     *
     * @param graphics superficie de dibujo, en píxeles de la pantalla
     * @param alert    aviso
     * @param at       centro del ícono en la pantalla
     * @param pulse    momento de la animación, en segundos: hace latir el borde
     */
    static void paintAlert(Graphics2D graphics, PlanAlert alert, Point2D at, double pulse) {
        smooth(graphics);
        double x = at.getX();
        double y = at.getY();
        double ring = ALERT_RADIUS + 3 + 3 * (0.5 + 0.5 * Math.sin(pulse * 2 * Math.PI));
        Color color = alert.kind().getColor();
        graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
        graphics.fill(new Ellipse2D.Double(x - ring, y - ring, 2 * ring, 2 * ring));
        graphics.setColor(color);
        graphics.fill(new Ellipse2D.Double(x - ALERT_RADIUS, y - ALERT_RADIUS, 2 * ALERT_RADIUS, 2 * ALERT_RADIUS));
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(Color.WHITE);
        graphics.draw(new Ellipse2D.Double(x - ALERT_RADIUS, y - ALERT_RADIUS, 2 * ALERT_RADIUS, 2 * ALERT_RADIUS));
        symbol(graphics, alert.kind(), x, y);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics metrics = graphics.getFontMetrics();
        double width = metrics.stringWidth(alert.text()) + 12;
        double left = x + ALERT_RADIUS + 5;
        graphics.setColor(LABEL_BACKGROUND);
        graphics.fill(new RoundRectangle2D.Double(left, y - 9, width, 18, 9, 9));
        graphics.setColor(Color.WHITE);
        graphics.drawString(alert.text(), (float) (left + 6), (float) (y + 4));
    }

    /** Símbolo blanco de cada aviso: una cruz, una gota, una cama o tres personas. */
    private static void symbol(Graphics2D graphics, PlanAlert.Kind kind, double x, double y) {
        graphics.setColor(Color.WHITE);
        switch (kind) {
            case DEATH -> {
                graphics.fill(new Rectangle2D.Double(x - 1.6, y - 7, 3.2, 14));
                graphics.fill(new Rectangle2D.Double(x - 5, y - 3.5, 10, 3.2));
            }
            case BLOOD_SHORTAGE -> {
                Path2D.Double drop = new Path2D.Double();
                drop.moveTo(x, y - 7);
                drop.curveTo(x + 6, y, x + 5, y + 6, x, y + 6);
                drop.curveTo(x - 5, y + 6, x - 6, y, x, y - 7);
                graphics.fill(drop);
            }
            case ICU_FULL -> {
                graphics.fill(new Rectangle2D.Double(x - 7, y + 1, 14, 3));
                graphics.fill(new Rectangle2D.Double(x - 7, y - 4, 2.4, 8));
                graphics.fill(new Rectangle2D.Double(x - 3, y - 2, 9, 3));
                graphics.fill(new Ellipse2D.Double(x - 4.5, y - 5.5, 4, 4));
            }
            case MASS_ARRIVAL -> {
                for (double dx : new double[] {-5, 0, 5}) {
                    graphics.fill(new Ellipse2D.Double(x + dx - 1.8, y - 5, 3.6, 3.6));
                    graphics.fill(new RoundRectangle2D.Double(x + dx - 2.2, y - 0.8, 4.4, 6, 2, 2));
                }
            }
        }
    }

    /**
     * Indica si un punto de la pantalla le atina al ícono de un aviso.
     *
     * @param at    centro del ícono en la pantalla
     * @param x     coordenada x del punto
     * @param y     coordenada y del punto
     * @return {@code true} si está a menos de {@value #ALERT_HIT} píxeles
     */
    static boolean hitsAlert(Point2D at, double x, double y) {
        return at.distance(x, y) <= ALERT_HIT;
    }

    /**
     * Brújula de la vista 2.5D, arriba a la derecha: la aguja roja apunta hacia donde queda la parte de arriba del
     * plano 2D, así que gira con el hospital. Un clic sobre ella devuelve la vista inicial.
     *
     * @param graphics  superficie de dibujo, en píxeles de la pantalla
     * @param center    centro de la brújula
     * @param direction hacia dónde apunta la parte de arriba del plano, en la pantalla
     */
    static void paintCompass(Graphics2D graphics, Point2D center, Point2D direction) {
        smooth(graphics);
        double x = center.getX();
        double y = center.getY();
        double r = COMPASS_RADIUS;
        graphics.setColor(COMPASS_FACE);
        graphics.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.setColor(CARD_BORDER);
        graphics.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        double length = Math.hypot(direction.getX(), direction.getY());
        double ux = length == 0 ? 0 : direction.getX() / length;
        double uy = length == 0 ? -1 : direction.getY() / length;
        double tip = r - 5;
        Path2D.Double north = new Path2D.Double();
        north.moveTo(x + ux * tip, y + uy * tip);
        north.lineTo(x - uy * 4, y + ux * 4);
        north.lineTo(x + uy * 4, y - ux * 4);
        north.closePath();
        graphics.setColor(NORTH);
        graphics.fill(north);
        Path2D.Double south = new Path2D.Double();
        south.moveTo(x - ux * tip, y - uy * tip);
        south.lineTo(x - uy * 4, y + ux * 4);
        south.lineTo(x + uy * 4, y - ux * 4);
        south.closePath();
        graphics.setColor(CARD_MUTED);
        graphics.fill(south);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        graphics.setColor(Color.WHITE);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString("N", (float) (x + ux * (r + 8) - metrics.stringWidth("N") / 2.0),
                (float) (y + uy * (r + 8) + 3));
    }

    /**
     * Cartel arriba en el centro del panel, por ejemplo el de la repetición.
     *
     * @param graphics superficie de dibujo, en píxeles de la pantalla
     * @param width    ancho del panel
     * @param text     texto del cartel
     */
    static void paintBanner(Graphics2D graphics, int width, String text) {
        smooth(graphics);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        FontMetrics metrics = graphics.getFontMetrics();
        double w = metrics.stringWidth(text) + 40;
        double left = (width - w) / 2;
        graphics.setColor(BANNER);
        graphics.fill(new RoundRectangle2D.Double(left, 10, w, 28, 14, 14));
        graphics.setColor(Color.WHITE);
        graphics.fill(new Ellipse2D.Double(left + 12, 20, 8, 8));
        graphics.drawString(text, (float) (left + 28), 29f);
    }

    /**
     * Panel flotante junto al puntero con lo que hay en un lugar: su nombre y, por cada paciente, su código, su
     * triage, qué está haciendo, desde hace cuánto y cuánto le falta; si no hay nadie, «Libre». Se corre para no
     * salirse del panel del plano.
     *
     * @param graphics superficie de dibujo, en píxeles de la pantalla
     * @param mouseX   coordenada x del puntero
     * @param mouseY   coordenada y del puntero
     * @param info     lo que hay en el lugar
     * @param width    ancho del panel del plano
     * @param height   alto del panel del plano
     */
    static void paintPlaceCard(Graphics2D graphics, int mouseX, int mouseY, PlaceInfo info, int width, int height) {
        smooth(graphics);
        List<String[]> rows = placeRows(info);
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        Font body = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        int lineHeight = 16;
        int cardHeight = 30 + rows.size() * lineHeight + 8;
        double left = mouseX + 18;
        double top = mouseY + 18;
        if (left + CARD_WIDTH > width - 4) {
            left = mouseX - 18 - CARD_WIDTH;
        }
        if (top + cardHeight > height - 4) {
            top = mouseY - 18 - cardHeight;
        }
        left = Math.max(4, left);
        top = Math.max(4, top);
        graphics.setColor(CARD_BACKGROUND);
        graphics.fill(new RoundRectangle2D.Double(left, top, CARD_WIDTH, cardHeight, 12, 12));
        graphics.setColor(CARD_BORDER);
        graphics.setStroke(new BasicStroke(1.2f));
        graphics.draw(new RoundRectangle2D.Double(left, top, CARD_WIDTH, cardHeight, 12, 12));
        graphics.setFont(title);
        graphics.setColor(Color.WHITE);
        graphics.drawString(fit(graphics, info.place(), CARD_WIDTH - 20), (float) (left + 10), (float) (top + 20));
        graphics.setColor(new Color(0x33, 0x41, 0x55));
        graphics.draw(new Line2D.Double(left + 10, top + 27, left + CARD_WIDTH - 10, top + 27));
        graphics.setFont(body);
        double y = top + 27 + lineHeight;
        for (String[] row : rows) {
            graphics.setColor(row[0].equals("title") ? CARD_TEXT : CARD_MUTED);
            graphics.setFont(row[0].equals("title") ? body.deriveFont(Font.BOLD) : body);
            graphics.drawString(fit(graphics, row[1], CARD_WIDTH - 20), (float) (left + 10), (float) y);
            y += lineHeight;
        }
    }

    /**
     * Renglones del panel flotante de un lugar: por cada paciente uno con su código y su triage y otro con lo que
     * hace y sus tiempos; si no hay nadie, uno que dice «Libre».
     *
     * @param info lo que hay en el lugar
     * @return pares de tipo de renglón («title» o «detail») y texto
     */
    static List<String[]> placeRows(PlaceInfo info) {
        List<String[]> rows = new ArrayList<>();
        if (info.isFree()) {
            rows.add(new String[] {"detail", "Libre"});
            return rows;
        }
        for (PlaceInfo.Occupant occupant : info.occupants()) {
            String triage = occupant.triage() == null ? "sin triage" : "Triage " + occupant.triage().name();
            rows.add(new String[] {"title", occupant.patientCode() + " · " + triage});
            String detail = occupant.activity();
            if (occupant.hasTimes()) {
                detail += " · lleva " + duration(occupant.secondsThere()) + " · faltan "
                        + duration(occupant.secondsRemaining());
            }
            rows.add(new String[] {"detail", detail});
        }
        return rows;
    }

    /**
     * Duración corta para el panel flotante: en segundos si es menos de un minuto; si no, en minutos (redondeados
     * hacia arriba).
     *
     * @param seconds segundos simulados
     * @return por ejemplo «40 s» o «12 min»
     */
    static String duration(double seconds) {
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%d s", (int) Math.ceil(seconds));
        }
        return String.format(Locale.ROOT, "%d min", (int) Math.ceil(seconds / 60.0));
    }

    /** Recorta un texto con «…» para que quepa en un ancho. */
    private static String fit(Graphics2D graphics, String text, int width) {
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (!shortened.isEmpty() && metrics.stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    private static void smooth(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
