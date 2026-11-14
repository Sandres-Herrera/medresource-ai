package com.medresource.ui.render;

import com.medresource.model.Position;

import java.awt.Color;

/**
 * Aviso que se dibuja sobre el plano cuando pasa algo importante: un ícono sobre el lugar donde pasó y un texto
 * corto. Al hacer clic en el ícono, la cámara va hasta ese lugar.
 *
 * @param kind     qué pasó
 * @param position casilla del plano sobre la que va el ícono
 * @param text     texto corto, por ejemplo «Falleció P012»
 */
public record PlanAlert(Kind kind, Position position, String text) {

    /** Qué pasó: cada aviso tiene su símbolo y su color. */
    public enum Kind {
        /** Murió un paciente: va sobre su cama o el lugar donde estaba. */
        DEATH("Fallecido", new Color(0x1F, 0x29, 0x37)),
        /** Se acabó la sangre de algún tipo: va sobre el banco de sangre. */
        BLOOD_SHORTAGE("Falta sangre", new Color(0xDC, 0x26, 0x26)),
        /** No quedan camas de UCI libres: va sobre la UCI. */
        ICU_FULL("UCI llena", new Color(0xEA, 0x58, 0x0C)),
        /** Llegó una emergencia con muchos pacientes: va sobre la entrada. */
        MASS_ARRIVAL("Llegada masiva", new Color(0xCA, 0x8A, 0x04));

        private final String displayName;
        private final Color color;

        Kind(String displayName, Color color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** Color del círculo del ícono. */
        public Color getColor() {
            return color;
        }
    }

    /**
     * Valida el aviso.
     *
     * @throws IllegalArgumentException si falta el tipo, la casilla o el texto
     */
    public PlanAlert {
        if (kind == null || position == null || text == null || text.isBlank()) {
            throw new IllegalArgumentException("Faltan datos del aviso.");
        }
    }
}
