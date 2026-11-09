package com.medresource.ui.render;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.StaffSeat;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

/**
 * Dibuja el plano del hospital sobre un {@link Graphics2D}.
 *
 * <p>Cada vista (desde arriba, isométrica) es una implementación distinta del mismo contrato. Los
 * renderizadores dibujan en coordenadas del <em>mundo</em>, sin saber nada de la cámara: quien los usa
 * prepara la superficie con {@link Camera#applyTo(Graphics2D)}. Solo leen el plano; nunca cambian la
 * simulación.</p>
 */
public interface SceneRenderer {

    /**
     * Tamaño del dibujo completo del plano, en píxeles del mundo.
     *
     * @param layout plano del hospital
     * @return ancho y alto del dibujo
     * @throws IllegalArgumentException si el plano es nulo
     */
    Dimension worldSize(HospitalLayout layout);

    /**
     * Punto del mundo donde queda el centro de una casilla; ahí se ubican los personajes.
     *
     * @param position casilla del plano
     * @return centro de la casilla
     * @throws IllegalArgumentException si la casilla es nula
     */
    Point2D centerOf(Position position);

    /**
     * Punto del mundo donde se sienta un médico o enfermero en su mesa de la sala del personal.
     *
     * @param seat puesto en una mesa
     * @return punto del puesto, a la izquierda o a la derecha del centro de la mesa
     * @throws IllegalArgumentException si el puesto es nulo
     */
    Point2D seatOf(StaffSeat seat);

    /**
     * Dibuja el plano completo.
     *
     * @param graphics superficie de dibujo, ya preparada con la cámara
     * @param layout   plano del hospital
     * @throws IllegalArgumentException si la superficie o el plano son nulos
     */
    void render(Graphics2D graphics, HospitalLayout layout);
}