-- V15: Incident work orders. Schema only.
CREATE UNIQUE INDEX uq_active_incident_order ON operation_records((data->>'incidenciaId')) WHERE type='ordenes' AND data->>'incidenciaId' IS NOT NULL AND data->>'estado' NOT IN ('cerrada','cancelada');
