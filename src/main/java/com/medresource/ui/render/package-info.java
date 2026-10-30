/**
 * Dibujo del hospital: colores, cámara y renderizadores que pintan el plano sobre un
 * {@link java.awt.Graphics2D}.
 *
 * <p>Los renderizadores solo leen el plano y no conocen la simulación; como dibujan sobre un
 * {@code Graphics2D}, sirven igual para un panel de Swing que para una imagen en memoria (capturas para
 * los documentos).</p>
 */
package com.medresource.ui.render;