package com.soundhub.ia;

import com.soundhub.common.exception.IaIndisponivelException;
import com.soundhub.ia.dto.DescricaoPlaylistRequestDTO;
import com.soundhub.ia.service.IaService;
import com.soundhub.usuario.entity.TipoUsuario;
import com.soundhub.usuario.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sem GEMINI_API_KEY a API sobe e as rotas de IA respondem 503, sem derrubar nada. */
@SpringBootTest(properties = "ia.gemini.api-key=")
@ActiveProfiles("test")
class IaSemChaveTest {

    @Autowired private IaService iaService;

    @Test
    @DisplayName("sem chave configurada, a IA responde indisponivel")
    void semChave() {
        Usuario usuario = Usuario.builder().id(1L).tipo(TipoUsuario.OUVINTE).build();

        assertThatThrownBy(() -> iaService.gerarDescricaoPlaylist(
                new DescricaoPlaylistRequestDTO("Treino", null), usuario))
                .isInstanceOf(IaIndisponivelException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }
}
