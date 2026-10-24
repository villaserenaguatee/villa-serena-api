package com.villaserena.api.turnos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.piso.dto.ResponsableEmpleado;

/** Gestión de turnos y asignaciones semanales de empleados (HU-ADM-12). */
@Service
public class TurnosService {

    private static final long LOCK_ID = 2948175L;

    private final JdbcTemplate jdbc;

    public TurnosService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TurnoAdmin> listarTurnos() {
        return jdbc.query("""
                SELECT id, name, start_time, end_time, status
                FROM work_shifts
                ORDER BY start_time ASC, id ASC
                """, (rs, i) -> new TurnoAdmin(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("start_time"),
                rs.getString("end_time"),
                rs.getString("status")
        ));
    }

    @Transactional(readOnly = true)
    public TurnoAdmin obtenerTurno(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id, name, start_time, end_time, status
                    FROM work_shifts
                    WHERE id = ?
                    """, (rs, i) -> new TurnoAdmin(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("start_time"),
                    rs.getString("end_time"),
                    rs.getString("status")
            ), id);
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.noEncontrado();
        }
    }

    @Transactional
    public TurnoAdmin crearTurno(TurnoPeticion peticion) {
        validarHoras(peticion.horaInicio(), peticion.horaFin());
        validarNombreUnico(peticion.nombre().trim(), null);

        Long id = jdbc.queryForObject("""
                INSERT INTO work_shifts (name, start_time, end_time, status)
                VALUES (?, ?, ?, 'ACTIVO')
                RETURNING id
                """, Long.class,
                peticion.nombre().trim(),
                peticion.horaInicio().trim(),
                peticion.horaFin().trim());

        return obtenerTurno(id);
    }

    @Transactional
    public TurnoAdmin editarTurno(long id, EditarTurnoPeticion peticion) {
        TurnoAdmin actual = obtenerTurno(id);

        String nuevoNombre = peticion.nombre() != null ? peticion.nombre().trim() : actual.nombre();
        if (peticion.nombre() != null && nuevoNombre.isBlank()) {
            throw ApiException.datosInvalidos("El nombre no puede estar vacío.",
                    List.of(new DetalleError("nombre", "El nombre no puede estar vacío.")));
        }

        String nuevoInicio = peticion.horaInicio() != null ? peticion.horaInicio().trim() : actual.horaInicio();
        String nuevoFin = peticion.horaFin() != null ? peticion.horaFin().trim() : actual.horaFin();

        validarHoras(nuevoInicio, nuevoFin);
        validarNombreUnico(nuevoNombre, id);

        jdbc.update("""
                UPDATE work_shifts
                SET name = ?, start_time = ?, end_time = ?
                WHERE id = ?
                """, nuevoNombre, nuevoInicio, nuevoFin, id);

        return obtenerTurno(id);
    }

    @Transactional
    public TurnoAdmin desactivarTurno(long id) {
        obtenerTurno(id);
        jdbc.update("UPDATE work_shifts SET status = 'INACTIVO' WHERE id = ?", id);
        return obtenerTurno(id);
    }

    @Transactional(readOnly = true)
    public List<AsignacionTurno> listarAsignaciones(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw ApiException.datosInvalidos("Las fechas de inicio y fin son obligatorias.",
                    List.of(new DetalleError("fechas", "Las fechas de inicio y fin son obligatorias.")));
        }
        if (fin.isBefore(inicio)) {
            throw ApiException.datosInvalidos("La fecha final debe ser igual o posterior a la inicial.",
                    List.of(new DetalleError("fin", "La fecha final debe ser igual o posterior a la inicial.")));
        }

        return jdbc.query("""
                SELECT a.id, a.work_date,
                       e.id AS emp_id, e.full_name AS emp_nombre,
                       s.id AS shift_id, s.name AS shift_nombre, s.start_time AS shift_inicio, s.end_time AS shift_fin, s.status AS shift_estado
                FROM shift_assignments a
                JOIN employees e ON e.id = a.employee_id
                JOIN work_shifts s ON s.id = a.shift_id
                WHERE a.work_date BETWEEN ? AND ?
                ORDER BY a.work_date ASC, s.start_time ASC, a.id ASC
                """, (rs, i) -> new AsignacionTurno(
                rs.getLong("id"),
                new ResponsableEmpleado(rs.getLong("emp_id"), rs.getString("emp_nombre")),
                new TurnoAdmin(rs.getLong("shift_id"), rs.getString("shift_nombre"), rs.getString("shift_inicio"), rs.getString("shift_fin"), rs.getString("shift_estado")),
                rs.getObject("work_date", LocalDate.class)
        ), inicio, fin);
    }

    @Transactional
    public List<AsignacionTurno> asignarTurnos(AsignarTurnosPeticion peticion) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, LOCK_ID);

        // Validar empleado
        Map<String, Object> empleado;
        try {
            empleado = jdbc.queryForMap("""
                    SELECT id, full_name, role, status
                    FROM employees
                    WHERE id = ?
                    """, peticion.empleadoId());
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.datosInvalidos("Empleado inexistente.",
                    List.of(new DetalleError("empleadoId", "Empleado inexistente.")));
        }

        boolean activo = "ACTIVO".equalsIgnoreCase(String.valueOf(empleado.get("status")));
        String rol = String.valueOf(empleado.get("role"));
        if (!activo || "ADMIN".equalsIgnoreCase(rol)) {
            throw ApiException.datosInvalidos("Solo se pueden asignar turnos a empleados activos que no sean administradores.",
                    List.of(new DetalleError("empleadoId", "El empleado debe estar activo y no ser administrador.")));
        }

        // Validar turno
        TurnoAdmin turno = obtenerTurno(peticion.turnoId());
        if (!"ACTIVO".equals(turno.estado())) {
            throw ApiException.datosInvalidos("El turno seleccionado está inactivo.",
                    List.of(new DetalleError("turnoId", "El turno seleccionado está inactivo.")));
        }

        // Validar fechas
        List<LocalDate> fechas = peticion.fechas().stream().distinct().sorted().toList();
        if (fechas.isEmpty()) {
            throw ApiException.datosInvalidos("Debe especificar al menos una fecha.",
                    List.of(new DetalleError("fechas", "Debe especificar al menos una fecha.")));
        }

        LocalTime horaInicio = LocalTime.parse(turno.horaInicio());
        LocalTime horaFin = LocalTime.parse(turno.horaFin());

        List<AsignacionTurno> resultado = new ArrayList<>();

        for (LocalDate fecha : fechas) {
            LocalDateTime inicioNueva = LocalDateTime.of(fecha, horaInicio);
            LocalDateTime finNueva = horaFin.isAfter(horaInicio)
                    ? LocalDateTime.of(fecha, horaFin)
                    : LocalDateTime.of(fecha.plusDays(1), horaFin);

            // Consultar asignaciones existentes del empleado en fecha - 1, fecha, fecha + 1
            List<Map<String, Object>> existentes = jdbc.queryForList("""
                    SELECT a.id, a.work_date, s.start_time, s.end_time
                    FROM shift_assignments a
                    JOIN work_shifts s ON s.id = a.shift_id
                    WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ?
                    """, peticion.empleadoId(), fecha.minusDays(1), fecha.plusDays(1));

            for (Map<String, Object> existente : existentes) {
                LocalDate f = ((java.sql.Date) existente.get("work_date")).toLocalDate();
                LocalTime hIni = LocalTime.parse(String.valueOf(existente.get("start_time")));
                LocalTime hFin = LocalTime.parse(String.valueOf(existente.get("end_time")));

                LocalDateTime iniEx = LocalDateTime.of(f, hIni);
                LocalDateTime finEx = hFin.isAfter(hIni)
                        ? LocalDateTime.of(f, hFin)
                        : LocalDateTime.of(f.plusDays(1), hFin);

                if (inicioNueva.isBefore(finEx) && iniEx.isBefore(finNueva)) {
                    throw ApiException.conflicto("TURNO_SOLAPADO",
                            "El empleado ya tiene un turno asignado que se solapa con el horario solicitado en la fecha " + fecha);
                }
            }

            Long idAsignacion = jdbc.queryForObject("""
                    INSERT INTO shift_assignments (employee_id, shift_id, work_date)
                    VALUES (?, ?, ?)
                    RETURNING id
                    """, Long.class, peticion.empleadoId(), peticion.turnoId(), fecha);

            resultado.add(new AsignacionTurno(
                    idAsignacion,
                    new ResponsableEmpleado(((Number) empleado.get("id")).longValue(), String.valueOf(empleado.get("full_name"))),
                    turno,
                    fecha
            ));
        }

        return resultado;
    }

    @Transactional
    public void eliminarAsignacion(long id) {
        Integer existe = jdbc.queryForObject("SELECT count(*) FROM shift_assignments WHERE id = ?", Integer.class, id);
        if (existe == null || existe == 0) {
            throw ApiException.noEncontrado();
        }
        jdbc.update("DELETE FROM shift_assignments WHERE id = ?", id);
    }

    private void validarHoras(String inicio, String fin) {
        if (inicio.equals(fin)) {
            throw ApiException.datosInvalidos("La hora de inicio y de fin no pueden ser iguales.",
                    List.of(new DetalleError("horaFin", "La hora de inicio y de fin no pueden ser iguales.")));
        }
    }

    private void validarNombreUnico(String nombre, Long idExcluido) {
        String sql = "SELECT count(*) FROM work_shifts WHERE lower(name) = lower(?)"
                + (idExcluido != null ? " AND id <> ?" : "");
        Object[] params = idExcluido != null ? new Object[]{nombre, idExcluido} : new Object[]{nombre};
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        if (count != null && count > 0) {
            throw ApiException.conflicto("TURNO_EXISTENTE", "Ya existe un turno con ese nombre.");
        }
    }
}
