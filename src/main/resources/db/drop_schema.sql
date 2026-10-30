-- ============================================================================
-- MedResource AI - borra todas las tablas del esquema (y sus datos).
--
-- Se borran primero las tablas hijas y al final las padres. Si alguna tabla no
-- existe, Oracle muestra ORA-00942: es normal y se puede ignorar.
-- ============================================================================

DROP TABLE sim_event CASCADE CONSTRAINTS PURGE;
DROP TABLE patient_result CASCADE CONSTRAINTS PURGE;
DROP TABLE sim_result CASCADE CONSTRAINTS PURGE;
DROP TABLE case_patient_symptom CASCADE CONSTRAINTS PURGE;
DROP TABLE case_patient CASCADE CONSTRAINTS PURGE;
DROP TABLE sim_case CASCADE CONSTRAINTS PURGE;
DROP TABLE disease_symptom CASCADE CONSTRAINTS PURGE;
DROP TABLE disease_secondary_specialty CASCADE CONSTRAINTS PURGE;
DROP TABLE disease CASCADE CONSTRAINTS PURGE;