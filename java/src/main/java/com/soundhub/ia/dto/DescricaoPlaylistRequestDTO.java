package com.soundhub.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de descricao para uma playlist.
 *
 * Funciona na criacao (so o nome, a playlist ainda nao existe) e na edicao
 * (com o playlistId, as musicas dela entram no prompt).
 */
public record DescricaoPlaylistRequestDTO(

        @NotBlank(message = "Informe o nome da playlist para a IA se basear")
        @Size(max = 100, message = "O nome deve ter no maximo 100 caracteres")
        String nome,

        /** Opcional. Quando informado, so o dono da playlist pode pedir. */
        Long playlistId
) {
}
