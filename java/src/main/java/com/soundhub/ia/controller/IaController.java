package com.soundhub.ia.controller;

import com.soundhub.ia.dto.DescricaoPlaylistRequestDTO;
import com.soundhub.ia.dto.SugerirCategoriasRequestDTO;
import com.soundhub.ia.dto.TextoIaDTO;
import com.soundhub.ia.service.IaService;
import com.soundhub.musica.dto.CategoriaResumoDTO;
import com.soundhub.musica.dto.MusicaResumoDTO;
import com.soundhub.usuario.entity.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Recursos de IA (Gemini via LangChain4j). Todas as rotas so sugerem: nada e gravado,
 * quem decide se usa o resultado e o usuario, no formulario.
 */
@Tag(name = "IA")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaService iaService;

    @Operation(summary = "Gera uma descricao para a playlist",
            description = "Com playlistId, usa as musicas da playlist (somente o dono). "
                    + "503 quando a IA nao esta configurada ou nao respondeu")
    @PostMapping("/playlists/descricao")
    public ResponseEntity<TextoIaDTO> descricaoPlaylist(@RequestBody @Valid DescricaoPlaylistRequestDTO dto,
                                                        @AuthenticationPrincipal Usuario logado) {
        return ResponseEntity.ok(iaService.gerarDescricaoPlaylist(dto, logado));
    }

    @Operation(summary = "Sugere categorias existentes para uma musica (somente ARTISTA)")
    @PreAuthorize("hasRole('ARTISTA')")
    @PostMapping("/musicas/categorias")
    public ResponseEntity<List<CategoriaResumoDTO>> sugerirCategorias(
            @RequestBody @Valid SugerirCategoriasRequestDTO dto,
            @AuthenticationPrincipal Usuario logado) {
        return ResponseEntity.ok(iaService.sugerirCategorias(dto, logado));
    }

    @Operation(summary = "Recomenda ate 5 musicas do catalogo para a playlist (somente o dono)",
            description = "400 quando a playlist esta vazia")
    @GetMapping("/playlists/{id}/recomendacoes")
    public ResponseEntity<List<MusicaResumoDTO>> recomendar(@PathVariable Long id,
                                                            @AuthenticationPrincipal Usuario logado) {
        return ResponseEntity.ok(iaService.recomendarMusicas(id, logado));
    }
}
