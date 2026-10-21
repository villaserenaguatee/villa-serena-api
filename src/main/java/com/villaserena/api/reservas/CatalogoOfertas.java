package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.dto.*;

/** Tarifas comerciales separadas del inventario y de las reservas históricas. */
@Service
public class CatalogoOfertas {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DisponibilidadService disponibilidad;
    private final ReservaService reservas;
    public CatalogoOfertas(JdbcTemplate jdbc,ObjectMapper json,DisponibilidadService disponibilidad,ReservaService reservas) {
        this.jdbc=jdbc;this.json=json;this.disponibilidad=disponibilidad;this.reservas=reservas;
    }
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> catalogo() {
        return jdbc.query("SELECT o.slug,o.room_type_id AS \"tipo_habitacion_id\",o.base_price AS \"precio_base\",o.content AS \"contenido\",o.sort_order AS \"orden\",o.requirements AS \"requisitos\",t.status AS \"estado\" FROM catalog_offers o JOIN room_types t ON t.id=o.room_type_id ORDER BY o.sort_order",(r,i)->{
            Map<String,Object> data=new LinkedHashMap<>(json.readValue(r.getString("contenido"),Map.class));
            // Editorial review notes are internal, never amenities promised to a guest.
            data.remove("pendingFeatures");
            data.put("apiTypeId",r.getLong("tipo_habitacion_id"));data.put("price",r.getBigDecimal("precio_base"));
            boolean enabled="ACTIVO".equals(r.getString("estado"));
            data.put("bookable",enabled && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM catalog_offer_rooms e JOIN rooms h ON h.id=e.room_id JOIN room_features c ON c.room_id=h.id JOIN catalog_offers f ON f.slug=e.slug WHERE e.slug=? AND h.room_type_id=? AND h.status='ACTIVO' AND h.condition<>'FUERA_DE_SERVICIO' AND c.content @> f.requirements)",Boolean.class,r.getString("slug"),r.getLong("tipo_habitacion_id"))));
            return data;
        });
    }
    private Map<String,Object> oferta(String slug) {
        return catalogo().stream().filter(o->slug.equals(o.get("slug"))).findFirst().orElseThrow(()->ApiException.conflicto("OFERTA_NO_ENCONTRADA","No existe esta oferta."));
    }
    private List<Long> libres(Map<String,Object> o,LocalDate entrada,LocalDate salida,boolean bloquear) {
        return jdbc.query("""
            SELECT h.id  FROM catalog_offer_rooms e JOIN rooms h ON h.id=e.room_id
            JOIN room_features c ON c.room_id=h.id JOIN catalog_offers f ON f.slug=e.slug
            WHERE e.slug=? AND h.room_type_id=? AND h.status='ACTIVO' AND h.condition<>'FUERA_DE_SERVICIO'
            AND c.content @> f.requirements
            AND NOT EXISTS(SELECT 1 FROM reservations r WHERE r.room_id=h.id
              AND r.status IN ('PENDIENTE_PAGO','CONFIRMADA','EN_ESTADIA') AND r.checkin_date<? AND r.checkout_date>?)
            ORDER BY h.id
            """+(bloquear?" FOR UPDATE OF h":""),(r,i)->r.getLong(1),o.get("slug"),o.get("apiTypeId"),salida,entrada);
    }
    private Cotizacion cotizar(Map<String,Object> o,LocalDate entrada,LocalDate salida) {
        BigDecimal precio=(BigDecimal)o.get("price");
        List<PrecioNoche> noches=entrada.datesUntil(salida).map(d->new PrecioNoche(d,precio,null,false)).toList();
        return new Cotizacion(noches.size(),precio.multiply(BigDecimal.valueOf(noches.size())),noches);
    }
    public List<Map<String,Object>> disponibles(LocalDate entrada,LocalDate salida,int huespedes) {
        disponibilidad.validarEstadia(entrada,salida,huespedes);
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> o:catalogo()) {
            long id=((Number)o.get("apiTypeId")).longValue();
            if(!Boolean.TRUE.equals(o.get("bookable")) || ((Number)o.get("capacity")).intValue()<huespedes
                || disponibilidad.cupo(id,entrada,salida)<=0 || libres(o,entrada,salida,false).isEmpty())continue;
            Cotizacion c=cotizar(o,entrada,salida);
            result.add(Map.of("ofertaSlug",o.get("slug"),"tipoHabitacion",new TipoHabitacionPublico(id,(String)o.get("name"),(String)o.get("description"),((Number)o.get("capacity")).intValue(),(BigDecimal)o.get("price"),List.of((String)o.get("image"))),"noches",c.noches(),"total",c.total(),"desglose",c.desglose(),"inventarioCompartido","tipo-"+id));
        }
        return result;
    }
    @Transactional
    public Reserva crear(String slug,ReservaWebPeticion p) {
        Map<String,Object> o=oferta(slug);long id=((Number)o.get("apiTypeId")).longValue();
        disponibilidad.validarEstadia(p.entrada(),p.salida(),p.numeroHuespedes());
        if(p.tipoHabitacionId()!=id || ((Number)o.get("capacity")).intValue()<p.numeroHuespedes())throw ApiException.conflicto("OFERTA_INCOMPATIBLE","La oferta y el tipo o capacidad no coinciden.");
        disponibilidad.bloquearTipo(id);
        List<Long> elegibles=libres(o,p.entrada(),p.salida(),true);
        if(!Boolean.TRUE.equals(o.get("bookable")) || elegibles.isEmpty() || disponibilidad.cupo(id,p.entrada(),p.salida())<=0)throw ApiException.conflicto("SIN_DISPONIBILIDAD","No hay una habitación acreditada disponible para esta oferta.");
        SolicitudReserva sol=new SolicitudReserva(id,p.entrada(),p.salida(),p.numeroHuespedes(),null,p.huesped(),elegibles.getFirst(),CanalReserva.DIRECTO_WEB,null,null,com.villaserena.api.comun.Responsable.CLIENTE,p.horaEstimadaLlegadaLocal());
        Reserva reserva=reservas.crearOferta(sol,cotizar(o,p.entrada(),p.salida()),(String)o.get("name"));
        jdbc.update("INSERT INTO reservation_offers(reservation_id,slug,content) VALUES (?,?,?::jsonb)",reserva.getId(),slug,json.writeValueAsString(o));
        return reserva;
    }
}
