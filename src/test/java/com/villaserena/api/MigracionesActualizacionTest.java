package com.villaserena.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class MigracionesActualizacionTest {

    @Test
    void actualizarConCatalogoPropioNoCargaDatosDeDemo() {
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            var origen = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(origen).locations("classpath:db/migration")
                    .target("16").load().migrate();
            var jdbc = new JdbcTemplate(origen);
            jdbc.update("""
                    INSERT INTO room_types(name,description,capacity,base_price)
                    VALUES ('Tipo propio','Configuración del hotel',2,100)
                    """);
            jdbc.update("""
                    INSERT INTO rooms(number,floor,room_type_id)
                    SELECT '106',1,id FROM room_types WHERE name='Tipo propio'
                    """);

            Flyway.configure().dataSource(origen).locations("classpath:db/migration")
                    .target("21").load().migrate();
            jdbc.update("""
                    INSERT INTO catalog_offers(slug,room_type_id,base_price,content,sort_order)
                    SELECT 'habitacion-estandar',id,100,'{"description":"Contenido propio"}'::jsonb,0
                    FROM room_types WHERE name='Tipo propio'
                    """);

            Flyway.configure().dataSource(origen).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForList("SELECT name AS \"nombre\" FROM room_types", String.class))
                    .containsExactly("Tipo propio");
            assertThat(jdbc.queryForList("SELECT number AS \"numero\" FROM rooms", String.class)).containsExactly("106");
            assertThat(jdbc.queryForList("SELECT content->>'description'  FROM catalog_offers", String.class))
                    .containsExactly("Contenido propio");
            assertThat(jdbc.queryForObject("SELECT count(*)  FROM room_features", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*)  FROM catalog_internal_revision", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*)  FROM catalog_inventory_before_v24", Long.class)).isZero();
        }
    }
}
