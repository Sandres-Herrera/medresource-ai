-- ============================================================================
-- MedResource AI - funciones, procedimientos y triggers PL/SQL
--
-- Se ejecuta después de schema.sql, con el mismo usuario. En SQL Developer:
-- abrir el archivo y ejecutarlo como script (F5). Cada unidad termina en una
-- línea con solo «/». Se puede volver a ejecutar: todo es CREATE OR REPLACE.
--
-- Las funciones calculan los indicadores de una ejecución con las mismas
-- definiciones que la aplicación (clase RunIndicators):
--   - atendido: minuto_inicio_atencion distinto de -1;
--   - sin atención: estado = 'UNATTENDED';
--   - espera: desde la llegada hasta el inicio de la atención, o hasta el
--     desenlace si nunca lo atendieron.
-- Si la ejecución no existe, lanzan el error ORA-20002.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Comprueba que una ejecución existe (ORA-20002 si no).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_exigir_ejecucion (p_id_ejecucion IN NUMBER) IS
    v_cantidad NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cantidad FROM ejecucion WHERE id = p_id_ejecucion;
    IF v_cantidad = 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'No existe la ejecución #' || p_id_ejecucion || '.');
    END IF;
END pr_exigir_ejecucion;
/

-- ---------------------------------------------------------------------------
-- Espera promedio de una ejecución, en minutos (0 si no tiene pacientes).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_espera_promedio (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_promedio NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT AVG(CASE WHEN minuto_inicio_atencion >= 0 THEN minuto_inicio_atencion - minuto_llegada
                    ELSE minuto_desenlace - minuto_llegada END)
      INTO v_promedio
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    RETURN NVL(v_promedio, 0);
END fn_espera_promedio;
/

-- ---------------------------------------------------------------------------
-- Espera más larga de una ejecución, en minutos (0 si no tiene pacientes).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_espera_maxima (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_maxima NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT MAX(CASE WHEN minuto_inicio_atencion >= 0 THEN minuto_inicio_atencion - minuto_llegada
                    ELSE minuto_desenlace - minuto_llegada END)
      INTO v_maxima
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    RETURN NVL(v_maxima, 0);
END fn_espera_maxima;
/

-- ---------------------------------------------------------------------------
-- Porcentaje de pacientes que empezaron a recibir atención (0 a 100).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_porcentaje_atendidos (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_total     NUMBER;
    v_atendidos NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT COUNT(*), NVL(SUM(CASE WHEN minuto_inicio_atencion >= 0 THEN 1 ELSE 0 END), 0)
      INTO v_total, v_atendidos
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    IF v_total = 0 THEN
        RETURN 0;
    END IF;
    RETURN 100 * v_atendidos / v_total;
END fn_porcentaje_atendidos;
/

-- ---------------------------------------------------------------------------
-- Mortalidad de una ejecución: fallecidos sobre pacientes (0 a 100).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_tasa_mortalidad (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_total      NUMBER;
    v_fallecidos NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT COUNT(*), NVL(SUM(CASE WHEN estado = 'DECEASED' THEN 1 ELSE 0 END), 0)
      INTO v_total, v_fallecidos
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    IF v_total = 0 THEN
        RETURN 0;
    END IF;
    RETURN 100 * v_fallecidos / v_total;
END fn_tasa_mortalidad;
/

-- ---------------------------------------------------------------------------
-- Pacientes que se quedaron sin atención (estado UNATTENDED).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_pacientes_sin_atencion (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_cantidad NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT COUNT(*) INTO v_cantidad
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion AND estado = 'UNATTENDED';
    RETURN v_cantidad;
END fn_pacientes_sin_atencion;
/

-- ---------------------------------------------------------------------------
-- Médicos extra de una ejecución, en orden y separados por comas
-- ('-' si no tiene). Sirve para comparar los recursos de dos ejecuciones.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_medicos_extra (p_id_ejecucion IN NUMBER) RETURN VARCHAR2 IS
    v_lista VARCHAR2(1000);
BEGIN
    SELECT LISTAGG(especialidad, ',') WITHIN GROUP (ORDER BY orden_medico)
      INTO v_lista
      FROM ejecucion_medico_extra
     WHERE id_ejecucion = p_id_ejecucion;
    RETURN NVL(v_lista, '-');
END fn_medicos_extra;
/
