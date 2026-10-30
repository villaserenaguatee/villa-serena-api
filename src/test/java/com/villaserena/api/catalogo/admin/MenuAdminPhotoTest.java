package com.villaserena.api.catalogo.admin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.catalogo.dto.ItemMenuAdmin;
import com.villaserena.api.config.PropiedadesVillaSerena;
class MenuAdminPhotoTest {
    @Test
    @SuppressWarnings("unchecked")
    void detalleConservaClaveYResuelveSoloUrlPublicaSinModificarDatos() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var props=mock(PropiedadesVillaSerena.class);
        when(props.archivos()).thenReturn(new PropiedadesVillaSerena.Archivos("https://assets.example.invalid/",null,null,null,null,null,null,15,false));
        var rs=mock(ResultSet.class);when(rs.getLong("id")).thenReturn(7L);when(rs.getString("fotoClave")).thenReturn("menu/foto.png");when(rs.getBigDecimal("precio")).thenReturn(BigDecimal.TEN);
        when(jdbc.queryForObject(anyString(),any(RowMapper.class),eq(7L))).thenAnswer(invocation->((RowMapper<ItemMenuAdmin>)invocation.getArgument(1)).mapRow(rs,0));
        var service=new MenuAdminService(jdbc,mock(AlmacenArchivos.class),props);
        assertThat(service.obtenerItem(7L).fotoUrl()).isEqualTo("https://assets.example.invalid/menu/foto.png");assertThat(service.obtenerItem(7L).fotoClave()).isEqualTo("menu/foto.png");
        when(rs.getString("fotoClave")).thenReturn(null);assertThat(service.obtenerItem(7L).fotoUrl()).isNull();
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
