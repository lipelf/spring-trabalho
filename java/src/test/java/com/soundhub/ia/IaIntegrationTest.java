package com.soundhub.ia;

import com.soundhub.artista.entity.Artista;
import com.soundhub.common.exception.IaIndisponivelException;
import com.soundhub.common.exception.RecursoNaoEncontradoException;
import com.soundhub.common.exception.RegraNegocioException;
import com.soundhub.ia.dto.DescricaoPlaylistRequestDTO;
import com.soundhub.ia.dto.SugerirCategoriasRequestDTO;
import com.soundhub.ia.service.IaService;
import com.soundhub.musica.dto.CategoriaResumoDTO;
import com.soundhub.musica.dto.MusicaResumoDTO;
import com.soundhub.musica.entity.Categoria;
import com.soundhub.musica.entity.Musica;
import com.soundhub.musica.repository.CategoriaRepository;
import com.soundhub.playlist.dto.AdicionarMusicaDTO;
import com.soundhub.playlist.dto.PlaylistDetalheDTO;
import com.soundhub.playlist.dto.PlaylistRequestDTO;
import com.soundhub.playlist.service.PlaylistService;
import com.soundhub.usuario.entity.TipoUsuario;
import com.soundhub.usuario.entity.Usuario;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modulo de IA com o modelo simulado: nao chama o Gemini, so confere o que vai no
 * prompt, as permissoes e como a resposta (as vezes torta) do modelo e tratada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IaIntegrationTest {

    @MockitoBean private ChatModel chatModel;

    @Autowired private IaService iaService;
    @Autowired private PlaylistService playlistService;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private EntityManager em;

    private Usuario ouvinte;
    private Usuario outro;
    private Usuario dono;
    private Musica rock;
    private Musica samba;
    private Musica forro;

    @BeforeEach
    void preparar() {
        ouvinte = novoUsuario("ouvinte@teste.com", TipoUsuario.OUVINTE);
        outro = novoUsuario("outro@teste.com", TipoUsuario.OUVINTE);
        dono = novoUsuario("artista@teste.com", TipoUsuario.ARTISTA);

        Artista artista = Artista.builder().nomeArtistico("Banda Teste").usuario(dono).build();
        em.persist(artista);

        rock = novaMusica("Estrada de Pedra", artista);
        samba = novaMusica("Roda no Quintal", artista);
        forro = novaMusica("Sanfona Acesa", artista);
        em.flush();
        em.clear();
    }

    // ===== Descricao =====

    @Test
    @DisplayName("descricao usa o nome e as musicas da playlist no prompt")
    void descricaoComMusicas() {
        PlaylistDetalheDTO playlist = playlistComMusicas("Viagem", rock);
        when(chatModel.chat(anyString())).thenReturn("  \"Trilha para pegar a estrada.\"  ");

        String texto = iaService.gerarDescricaoPlaylist(
                new DescricaoPlaylistRequestDTO("Viagem", playlist.id()), ouvinte).texto();

        assertThat(texto).isEqualTo("Trilha para pegar a estrada.");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(prompt.capture());
        assertThat(prompt.getValue()).contains("Viagem", "Estrada de Pedra - Banda Teste");
    }

    @Test
    @DisplayName("descricao funciona sem playlist (tela de criacao) e respeita 300 caracteres")
    void descricaoSemPlaylistCortada() {
        when(chatModel.chat(anyString())).thenReturn("palavra ".repeat(100));

        String texto = iaService.gerarDescricaoPlaylist(
                new DescricaoPlaylistRequestDTO("Treino", null), ouvinte).texto();

        assertThat(texto).hasSizeLessThanOrEqualTo(300).endsWith("...");
    }

    @Test
    @DisplayName("descricao de playlist de outro usuario da 403 e de playlist inexistente da 404")
    void descricaoPermissao() {
        PlaylistDetalheDTO playlist = playlistComMusicas("Minha", rock);

        assertThatThrownBy(() -> iaService.gerarDescricaoPlaylist(
                new DescricaoPlaylistRequestDTO("Minha", playlist.id()), outro))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> iaService.gerarDescricaoPlaylist(
                new DescricaoPlaylistRequestDTO("X", 999_999L), ouvinte))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(chatModel, never()).chat(anyString());
    }

    @Test
    @DisplayName("falha do Gemini (limite, rede) vira IaIndisponivelException")
    void falhaDaIa() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("429 quota"));

        assertThatThrownBy(() -> iaService.gerarDescricaoPlaylist(
                new DescricaoPlaylistRequestDTO("Treino", null), ouvinte))
                .isInstanceOf(IaIndisponivelException.class);
    }

    // ===== Categorias =====

    @Test
    @DisplayName("categorias: descarta ids inventados e limita a 3")
    void categoriasSoExistentes() {
        List<Categoria> todas = categoriaRepository.findAllByOrderByNomeAsc();
        Long a = todas.get(0).getId();
        Long b = todas.get(1).getId();
        Long c = todas.get(2).getId();
        Long d = todas.get(3).getId();
        when(chatModel.chat(anyString()))
                .thenReturn("```json\n[" + a + ", 999999, " + b + ", " + a + ", " + c + ", " + d + "]\n```");

        List<CategoriaResumoDTO> sugeridas = iaService.sugerirCategorias(
                new SugerirCategoriasRequestDTO("Roda no Quintal"), dono);

        assertThat(sugeridas).extracting(CategoriaResumoDTO::id).containsExactly(a, b, c);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(prompt.capture());
        assertThat(prompt.getValue()).contains("Roda no Quintal", "Banda Teste", "Samba");
    }

    // ===== Recomendacoes =====

    @Test
    @DisplayName("recomendacoes: so musicas fora da playlist, na ordem da IA")
    void recomendacoes() {
        PlaylistDetalheDTO playlist = playlistComMusicas("Brasil", rock);
        // a IA "recomenda" uma que ja esta na playlist e um id inexistente: os dois saem
        when(chatModel.chat(anyString()))
                .thenReturn("[" + forro.getId() + ", " + rock.getId() + ", 424242, " + samba.getId() + "]");

        List<MusicaResumoDTO> recomendadas = iaService.recomendarMusicas(playlist.id(), ouvinte);

        assertThat(recomendadas).extracting(MusicaResumoDTO::titulo)
                .containsExactly("Sanfona Acesa", "Roda no Quintal");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(prompt.capture());
        assertThat(prompt.getValue()).doesNotContain(rock.getId() + ": Estrada de Pedra");
    }

    @Test
    @DisplayName("recomendacoes: playlist vazia da 400 e de outro usuario da 403")
    void recomendacoesRegras() {
        PlaylistDetalheDTO vazia = playlistService.criar(
                new PlaylistRequestDTO("Vazia", null, true, null), ouvinte);
        PlaylistDetalheDTO cheia = playlistComMusicas("Cheia", rock);

        assertThatThrownBy(() -> iaService.recomendarMusicas(vazia.id(), ouvinte))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> iaService.recomendarMusicas(cheia.id(), outro))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatModel, never()).chat(anyString());
    }

    @Test
    @DisplayName("recomendacoes: com o catalogo todo na playlist, nem chama a IA")
    void recomendacoesSemCandidatas() {
        PlaylistDetalheDTO playlist = playlistComMusicas("Tudo", rock, samba, forro);

        assertThat(iaService.recomendarMusicas(playlist.id(), ouvinte)).isEmpty();
        verify(chatModel, never()).chat(anyString());
    }

    // ===== Apoio =====

    private PlaylistDetalheDTO playlistComMusicas(String nome, Musica... musicas) {
        PlaylistDetalheDTO playlist = playlistService.criar(
                new PlaylistRequestDTO(nome, null, true, null), ouvinte);
        for (Musica musica : musicas) {
            playlistService.adicionarMusica(playlist.id(), new AdicionarMusicaDTO(musica.getId()), ouvinte);
        }
        em.flush();
        em.clear();
        return playlist;
    }

    private Usuario novoUsuario(String email, TipoUsuario tipo) {
        Usuario usuario = Usuario.builder()
                .nome(email.substring(0, email.indexOf('@')))
                .email(email)
                .senha("$2a$10$hashdementira")
                .tipo(tipo)
                .build();
        em.persist(usuario);
        return usuario;
    }

    private Musica novaMusica(String titulo, Artista artista) {
        Musica musica = Musica.builder().titulo(titulo).duracao(200).artista(artista).build();
        em.persist(musica);
        return musica;
    }
}
