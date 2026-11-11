package com.medresource.ui.render;

/**
 * Movimiento de reposo: quien está quieto en la escena respira, así que su figura sube y baja un poco.
 *
 * <p>Sirve para que el hospital nunca se vea congelado aunque en un rato nadie cambie de lugar: los que esperan
 * sentados, los que están en su cama y el personal en su puesto se mueven apenas, cada uno a su ritmo (el desfase
 * sale de su código, así que dos personas vecinas no se mueven igual). Quien camina no respira así: ya se mueve.
 * El movimiento es solo del dibujo; no cambia el punto donde está la persona ni el resultado.</p>
 */
public final class IdleMotion {

    /** Cuánto sube y baja la figura, en píxeles. */
    public static final double AMPLITUDE = 1.2;
    /** Segundos simulados de una respiración completa: a ×1 es lenta y a ×10 se ve más ágil. */
    public static final double PERIOD_SECONDS = 6.0;

    private IdleMotion() {
    }

    /**
     * Desplazamiento vertical de la figura de una persona en un momento.
     *
     * @param code   código de la persona, que da su ritmo
     * @param time   momento, en segundos simulados
     * @param moving {@code true} si va caminando o en camilla; entonces no se desplaza
     * @return desplazamiento en píxeles, entre {@code -AMPLITUDE} y {@code AMPLITUDE}; 0 si se mueve
     * @throws IllegalArgumentException si falta el código
     */
    public static double bob(String code, double time, boolean moving) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de la persona es obligatorio.");
        }
        if (moving) {
            return 0.0;
        }
        double phase = Math.floorMod(code.hashCode(), 360) * Math.PI / 180.0;
        return AMPLITUDE * Math.sin(2 * Math.PI * time / PERIOD_SECONDS + phase);
    }
}
