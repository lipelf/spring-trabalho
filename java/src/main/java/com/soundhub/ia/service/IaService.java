package com.soundhub.ia.service;

import com.soundhub.artista.entity.Artista;
import com.soundhub.artista.repository.ArtistaRepository;
import com.soundhub.common.exception.IaIndisponivelException;
import com.soundhub.common.exception.RecursoNaoEncontradoException;
import com.soundhub.common.exception.RegraNegocioException;
import com.soundhub.ia.dto.DescricaoPlaylistRequestDTO;
import com.soundhub.ia.dto.SugerirCategoriasRequestDTO;
import com.soundhub.ia.dto.TextoIaDTO;
import com.soundhub.musica.dto.CategoriaResumoDTO;
import com.soundhub.musica.dto.MusicaResumoDTO;
import com.soundhub.musica.entity.Categoria;
import com.soundhub.musica.entity.Musica;
import com.soundhub.musica.repository.CategoriaRepository;
import com.soundhub.musica.repository.MusicaRepository;
import com.soundhub.playlist.entity.Playlist;
import com.soundhub.playlist.entity.PlaylistMusica;
import com.soundhub.playlist.repository.PlaylistRepository;
import com.soundhub.usuario.entity.Usuario;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recursos de IA do SoundHub, com os prompts em {@code resources/prompts/*.st}.
 *
 * Sem {@code @Transactional} de proposito: a chamada a IA leva alguns segundos e nao
 * deve segurar uma conexao do banco. Cada consulta abaixo ja traz (JOIN FETCH) tudo
 * o que o prompt usa, entao nada e carregado de forma lazy depois.
 *
 * A resposta da IA nunca e confiada direto: ids que nao existem na lista enviada
 * sao descartados, e a descricao e cortada no limite da coluna.
 */
@Slf4j
@Service
public class IaService {

    /** Mesmo limite de {@code Playlist.descricao}. */
    private static final int MAX_DESCRICAO = 300;
    private static final int MAX_CATEGORIAS = 3;
    private static final int MAX_RECOMENDACOES = 5;
    /** Teto de musicas mandadas no prompt, para nao estourar o limite do plano gratuito. */
    private static final int MAX_MUSICAS_NO_PROMPT = 200;

    private static final Pattern NUMERO = Pattern.compile("\\d+");

    private final ObjectProvider<ChatModel> chatModel;
    private final PlaylistRepository playlistRepository;
    private final MusicaRepository musicaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ArtistaRepository artistaRepository;

    private final PromptTemplate promptDescricao;
    private final PromptTemplate promptCategorias;
    private final PromptTemplate promptRecomendacao;

    public IaService(ObjectProvider<ChatModel> chatModel,
                     PlaylistRepository playlistRepository,
                     MusicaRepository musicaRepository,
                     CategoriaRepository categoriaRepository,
                     ArtistaRepository artistaRepository) {
        this.chatModel = chatModel;
        this.playlistRepository = playlistRepository;
        this.musicaRepository = musicaRepository;
        this.categoriaRepository = categoriaRepository;
        this.artistaRepository = artistaRepository;
        this.promptDescricao = carregarPrompt("descricao-playlist.st");
        this.promptCategorias = carregarPrompt("sugerir-categorias.st");
        this.promptRecomendacao = carregarPrompt("recomendar-musicas.st");
    }

    // ===== Descricao de playlist =====

    public TextoIaDTO gerarDescricaoPlaylist(DescricaoPlaylistRequestDTO dto, Usuario logado) {
        String musicas = "(a playlist ainda nao tem musicas)";
        if (dto.playlistId() != null) {
            Playlist playlist = buscarPlaylistDoDono(dto.playlistId(), logado);
            if (!playlist.getMusicas().isEmpty()) {
                musicas = listarMusicasDaPlaylist(playlist);
            }
        }

        String resposta = perguntar(promptDescricao, Map.of(
                "nome", dto.nome().trim(),
                "musicas", musicas));

        return new TextoIaDTO(limparDescricao(resposta));
    }

    // ===== Categorias de musica =====

    public List<CategoriaResumoDTO> sugerirCategorias(SugerirCategoriasRequestDTO dto, Usuario logado) {
        List<Categoria> categorias = categoriaRepository.findAllByOrderByNomeAsc();
        if (categorias.isEmpty()) {
            return List.of();
        }

        String artista = artistaRepository.findByUsuarioId(logado.getId())
                .map(Artista::getNomeArtistico)
                .orElse("desconhecido");
        String lista = categorias.stream()
                .map(c -> c.getId() + ": " + c.getNome())
                .collect(Collectors.joining("\n"));

        String resposta = perguntar(promptCategorias, Map.of(
                "titulo", dto.titulo().trim(),
                "artista", artista,
                "categorias", lista));

        Map<Long, Categoria> porId = categorias.stream()
                .collect(Collectors.toMap(Categoria::getId, Function.identity()));
        return extrairIds(resposta, porId.keySet(), MAX_CATEGORIAS).stream()
                .map(porId::get)
                .map(CategoriaResumoDTO::from)
                .toList();
    }

    // ===== Recomendacao de musicas =====

    public List<MusicaResumoDTO> recomendarMusicas(Long playlistId, Usuario logado) {
        Playlist playlist = buscarPlaylistDoDono(playlistId, logado);
        if (playlist.getMusicas().isEmpty()) {
            throw new RegraNegocioException(
                    "Adicione pelo menos uma musica para a IA entender o estilo da playlist");
        }

        Set<Long> naPlaylist = playlist.getMusicas().stream()
                .map(pm -> pm.getMusica().getId())
                .collect(Collectors.toSet());
        List<Musica> candidatas = musicaRepository.findAllCompleto().stream()
                .filter(m -> !naPlaylist.contains(m.getId()))
                .limit(MAX_MUSICAS_NO_PROMPT)
                .toList();
        if (candidatas.isEmpty()) {
            return List.of();   // todo o catalogo ja esta na playlist: nem chama a IA
        }

        String catalogo = candidatas.stream()
                .map(m -> m.getId() + ": " + m.getTitulo() + " - " + m.getArtista().getNomeArtistico()
                        + " [" + m.getCategorias().stream().map(Categoria::getNome)
                        .collect(Collectors.joining(", ")) + "]")
                .collect(Collectors.joining("\n"));

        String resposta = perguntar(promptRecomendacao, Map.of(
                "nome", playlist.getNome(),
                "musicas", listarMusicasDaPlaylist(playlist),
                "catalogo", catalogo,
                "quantidade", MAX_RECOMENDACOES));

        Map<Long, Musica> porId = candidatas.stream()
                .collect(Collectors.toMap(Musica::getId, Function.identity()));
        return extrairIds(resposta, porId.keySet(), MAX_RECOMENDACOES).stream()
                .map(porId::get)
                .map(MusicaResumoDTO::from)
                .toList();
    }

    // ===== Apoio =====

    private String perguntar(PromptTemplate template, Map<String, Object> variaveis) {
        ChatModel modelo = chatModel.getIfAvailable();
        if (modelo == null) {
            throw new IaIndisponivelException(
                    "IA nao configurada no servidor. Defina a variavel GEMINI_API_KEY");
        }
        try {
            return modelo.chat(template.apply(variaveis).text());
        } catch (RuntimeException e) {
            // 429 do plano gratuito, timeout, rede... o detalhe fica no log, nao na resposta
            log.warn("Falha ao chamar a IA: {}", e.getMessage());
            throw new IaIndisponivelException("A IA nao respondeu agora. Tente de novo em instantes");
        }
    }

    private Playlist buscarPlaylistDoDono(Long id, Usuario logado) {
        Playlist playlist = playlistRepository.findByIdComMusicas(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Playlist", id));
        if (!playlist.getUsuario().getId().equals(logado.getId())) {
            throw new AccessDeniedException("Esta playlist pertence a outro usuario");
        }
        return playlist;
    }

    private static String listarMusicasDaPlaylist(Playlist playlist) {
        return playlist.getMusicas().stream()
                .limit(MAX_MUSICAS_NO_PROMPT)
                .map(PlaylistMusica::getMusica)
                .map(m -> "- " + m.getTitulo() + " - " + m.getArtista().getNomeArtistico())
                .collect(Collectors.joining("\n"));
    }

    /** Pega os numeros da resposta, na ordem, e fica so com os que estavam na lista enviada. */
    static List<Long> extrairIds(String resposta, Set<Long> validos, int maximo) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = NUMERO.matcher(resposta == null ? "" : resposta);
        while (matcher.find() && ids.size() < maximo) {
            try {
                long id = Long.parseLong(matcher.group());
                if (validos.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignorado) {
                // numero grande demais para ser um id
            }
        }
        return List.copyOf(ids);
    }

    /** Tira aspas e espacos que o modelo as vezes devolve e respeita o limite da coluna. */
    static String limparDescricao(String resposta) {
        String texto = resposta == null ? "" : resposta.strip();
        if (texto.length() >= 2 && texto.startsWith("\"") && texto.endsWith("\"")) {
            texto = texto.substring(1, texto.length() - 1).strip();
        }
        texto = texto.replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            throw new IaIndisponivelException("A IA devolveu uma resposta vazia. Tente de novo");
        }
        if (texto.length() > MAX_DESCRICAO) {
            int corte = texto.lastIndexOf(' ', MAX_DESCRICAO - 3);
            texto = texto.substring(0, corte > 0 ? corte : MAX_DESCRICAO - 3) + "...";
        }
        return texto;
    }

    private static PromptTemplate carregarPrompt(String arquivo) {
        try {
            return PromptTemplate.from(new ClassPathResource("prompts/" + arquivo)
                    .getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt nao encontrado: " + arquivo, e);
        }
    }
}
