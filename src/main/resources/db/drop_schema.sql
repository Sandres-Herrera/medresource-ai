-- ============================================================================
-- MedResource AI - borra todas las tablas del esquema (y sus datos).
--
-- Se borran primero las tablas hijas y al final las padres. Si alguna tabla no
-- existe, Oracle muestra ORA-00942: es normal y se puede ignorar.
-- ============================================================================

DROP TABLE auditoria_reporte CASCADE CONSTRAINTS PURGE;
DROP TABLE comparacion CASCADE CONSTRAINTS PURGE;
DROP TABLE reporte CASCADE CONSTRAINTS PURGE;
DROP TABLE evento CASCADE CONSTRAINTS PURGE;
DROP TABLE resultado_paciente CASCADE CONSTRAINTS PURGE;
DROP TABLE ejecucion_medico_extra CASCADE CONSTRAINTS PURGE;
DROP TABLE ejecucion CASCADE CONSTRAINTS PURGE;
DROP TABLE caso_paciente_sintoma CASCADE CONSTRAINTS PURGE;
DROP TABLE caso_paciente CASCADE CONSTRAINTS PURGE;
DROP TABLE caso CASCADE CONSTRAINTS PURGE;
DROP TABLE enfermedad_sintoma CASCADE CONSTRAINTS PURGE;
DROP TABLE enfermedad_especialidad_sec CASCADE CONSTRAINTS PURGE;
DROP TABLE enfermedad CASCADE CONSTRAINTS PURGE;