package com.medresource.ui.controller;

import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PlanAlert;
import com.medresource.ui.render.PorterMarker;
import com.medresource.ui.render.StaffMarker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Lo que se vio del plano en los últimos segundos de reproducción, para volver a verlo.
 *
 * <p>La ventana le pasa cada cuadro que muestra mientras la simulación corre ({@link #record}), con los segundos
 * reales que pasaron desde el anterior. Guarda los últimos {@value #KEEP_SECONDS} segundos de reproducción: el tiempo
 * en pausa no cuenta. Con {@link #start} se arma una repetición de los últimos {@value #REPLAY_SECONDS} segundos
 * (o de lo que haya, si es menos), que muestra cada cuadro en el mismo momento en que se vio. Repetir no cambia la
 * simulación: solo vuelve a mostrar lo que ya se dibujó.</p>
 */
public final class ReplayBuffer {

    /** Segundos de reproducción que se vuelven a ver. */
    public static final double REPLAY_SECONDS = 30.0;
    /** Segundos de reproducción que se guardan: un poco más de los que se repiten. */
    public static final double KEEP_SECONDS = 32.0;

    private final Deque<Frame> frames = new ArrayDeque<>();
    private double played;

    /**
     * Cuadro del plano tal como se vio.
     *
     * @param played   segundos de reproducción hasta este cuadro, contados desde que se empezó a grabar
     * @param minute   minuto simulado que se veía
     * @param second   segundo de ese minuto
     * @param patients pacientes
     * @param staff    médicos y enfermeros
     * @param porters  camilleros
     * @param overflow pacientes esperando que no cabían en las sillas
     * @param alerts   avisos sobre el plano
     */
    public record Frame(double played, int minute, int second, List<PatientMarker> patients,
                        List<StaffMarker> staff, List<PorterMarker> porters, int overflow, List<PlanAlert> alerts) {

        /**
         * Valida el cuadro y copia sus listas.
         *
         * @throws IllegalArgumentException si falta una lista o un número es negativo
         */
        public Frame {
            if (patients == null || staff == null || porters == null || alerts == null) {
                throw new IllegalArgumentException("Las listas del cuadro son obligatorias.");
            }
            if (played < 0 || minute < 0 || second < 0 || overflow < 0) {
                throw new IllegalArgumentException("Los tiempos y el sobrante no pueden ser negativos.");
            }
            patients = List.copyOf(patients);
            staff = List.copyOf(staff);
            porters = List.copyOf(porters);
            alerts = List.copyOf(alerts);
        }
    }

    /**
     * Guarda un cuadro que se acaba de mostrar mientras la simulación corría.
     *
     * @param elapsedSeconds segundos reales desde el cuadro anterior
     * @param minute         minuto simulado que se ve
     * @param second         segundo de ese minuto
     * @param patients       pacientes
     * @param staff          médicos y enfermeros
     * @param porters        camilleros
     * @param overflow       pacientes esperando que no caben en las sillas
     * @param alerts         avisos sobre el plano
     * @throws IllegalArgumentException si los segundos son negativos o falta algún dato
     */
    public void record(double elapsedSeconds, int minute, int second, List<PatientMarker> patients,
                       List<StaffMarker> staff, List<PorterMarker> porters, int overflow, List<PlanAlert> alerts) {
        if (!(elapsedSeconds >= 0)) {
            throw new IllegalArgumentException("Los segundos transcurridos no pueden ser negativos.");
        }
        played += frames.isEmpty() ? 0 : elapsedSeconds;
        frames.addLast(new Frame(played, minute, second, patients, staff, porters, overflow, alerts));
        while (frames.size() > 1 && frames.peekFirst().played() < played - KEEP_SECONDS) {
            frames.removeFirst();
        }
    }

    /** Indica si hay algo que repetir: al menos dos cuadros. */
    public boolean canReplay() {
        return frames.size() >= 2;
    }

    /** Segundos de reproducción guardados. */
    public double recordedSeconds() {
        return frames.isEmpty() ? 0 : frames.peekLast().played() - frames.peekFirst().played();
    }

    /**
     * Arma la repetición de los últimos {@value #REPLAY_SECONDS} segundos guardados.
     *
     * @return la repetición
     * @throws IllegalStateException si no hay nada que repetir
     */
    public Replay start() {
        if (!canReplay()) {
            throw new IllegalStateException("Todavía no hay nada que repetir.");
        }
        double end = frames.peekLast().played();
        double from = Math.max(frames.peekFirst().played(), end - REPLAY_SECONDS);
        List<Frame> chosen = new ArrayList<>();
        for (Frame frame : frames) {
            if (frame.played() >= from) {
                chosen.add(frame);
            }
        }
        return new Replay(chosen);
    }

    /** Borra todo lo guardado, como al volver a empezar la simulación. */
    public void clear() {
        frames.clear();
        played = 0;
    }

    /** Repetición de unos segundos ya vistos: muestra cada cuadro en el mismo momento en que se vio. */
    public static final class Replay {

        private final List<Frame> frames;

        private Replay(List<Frame> frames) {
            this.frames = List.copyOf(frames);
        }

        /** Segundos que dura la repetición. */
        public double duration() {
            return frames.get(frames.size() - 1).played() - frames.get(0).played();
        }

        /**
         * Cuadro que se ve a unos segundos de empezar la repetición: el último que ya se había visto en ese momento.
         *
         * @param seconds segundos desde que empezó la repetición
         * @return el cuadro
         */
        public Frame frameAt(double seconds) {
            double at = frames.get(0).played() + Math.max(0.0, seconds);
            Frame shown = frames.get(0);
            for (Frame frame : frames) {
                if (frame.played() > at) {
                    break;
                }
                shown = frame;
            }
            return shown;
        }

        /**
         * Indica si la repetición ya terminó: se vio hasta el último cuadro guardado.
         *
         * @param seconds segundos desde que empezó la repetición
         * @return {@code true} si terminó
         */
        public boolean isOver(double seconds) {
            return seconds >= duration();
        }
    }
}
