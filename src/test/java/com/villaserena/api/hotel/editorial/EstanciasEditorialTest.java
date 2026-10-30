package com.villaserena.api.hotel.editorial;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import tools.jackson.databind.ObjectMapper;
class EstanciasEditorialTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    EditorialService service(){var props=mock(PropiedadesVillaSerena.class);when(props.archivos()).thenReturn(new PropiedadesVillaSerena.Archivos("https://assets.example.invalid",null,null,null,null,null,null,15,false));return new EditorialService(jdbc,new ObjectMapper(),mock(AlmacenArchivos.class),props);}
    void validate(ElementoEditorial... elements){ReflectionTestUtils.invokeMethod(service(),"validar",List.of(elements));}
    ElementoEditorial stay(String id,String nombre,List<String> slugs){return new ElementoEditorial(id,"Descripción existente",null,null,null,nombre,slugs);}
    @Test void nombresNoCambianIdsYOfertasPuedenReutilizarseEntreEstancias(){when(jdbc.queryForObject(anyString(),eq(Boolean.class),anyString())).thenReturn(true);validate(stay("estancias.descanso","Aventura",List.of("a","b","c")),stay("estancias.familiar","Familia",List.of("a")));verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void rechazaDuplicadosExcesoOfertasNoPublicasYCamposFueraDeEstancias(){assertThatThrownBy(()->validate(stay("estancias.descanso","Aventura",List.of("a","a")))).isInstanceOf(ApiException.class);assertThatThrownBy(()->validate(stay("estancias.descanso","Aventura",List.of("a","b","c","d")))).isInstanceOf(ApiException.class);assertThatThrownBy(()->validate(stay("estancias.descanso","Aventura",List.of("no-publica")))).isInstanceOf(ApiException.class);assertThatThrownBy(()->validate(stay("inicio.titulo","Aventura",List.of()))).isInstanceOf(ApiException.class);assertThatThrownBy(()->validate(stay("estancias.descanso","<script>",List.of()))).isInstanceOf(ApiException.class);}
    @Test void documentosAnterioresYListaVaciaConservanSuSemantica(){validate(new ElementoEditorial("estancias.descanso","Descripción anterior",null,null),stay("estancias.familiar","Familia",List.of()));verifyNoInteractions(jdbc);}
}
