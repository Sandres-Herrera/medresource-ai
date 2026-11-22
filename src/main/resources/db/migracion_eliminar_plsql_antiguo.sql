-- ============================================================================
-- MedResource AI - migración: retirar los objetos PL/SQL antiguos
--
-- Ejecutar UNA vez, con el usuario de la aplicación, en una base que ya tenga
-- los objetos antiguos. plsql.sql ya no los crea, pero CREATE OR REPLACE no los
-- borra, por eso existe este archivo. Si un objeto ya no existe (ORA-04043), se
-- omite: el script se puede repetir sin error.
--
-- Orden: primero el procedimiento que depende de las funciones, luego las
-- funciones y al final PR_EXIGIR_EJECUCION, del que dependían las funciones.
-- No es un bloque CREATE OR REPLACE, así que SchemaInstaller no lo ejecuta.
-- ============================================================================

SET SERVEROUTPUT ON

DECLARE
    PROCEDURE retirar (p_tipo IN VARCHAR2, p_nombre IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP ' || p_tipo || ' ' || p_nombre;
        DBMS_OUTPUT.PUT_LINE('Eliminado ' || p_tipo || ' ' || p_nombre);
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -4043 THEN
                DBMS_OUTPUT.PUT_LINE('No existía ' || p_tipo || ' ' || p_nombre || ': se omite.');
            ELSE
                RAISE;
            END IF;
    END retirar;
BEGIN
    retirar('PROCEDURE', 'PR_RESUMEN_SIMULACION');
    retirar('FUNCTION', 'FN_ESPERA_PROMEDIO');
    retirar('FUNCTION', 'FN_ESPERA_MAXIMA');
    retirar('FUNCTION', 'FN_PORCENTAJE_ATENDIDOS');
    retirar('FUNCTION', 'FN_TASA_MORTALIDAD');
    retirar('FUNCTION', 'FN_PACIENTES_SIN_ATENCION');
    retirar('PROCEDURE', 'PR_EXIGIR_EJECUCION');
END;
/
