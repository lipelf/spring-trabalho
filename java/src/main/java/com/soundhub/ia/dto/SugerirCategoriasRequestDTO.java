package com.soundhub.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido de categorias para uma musica que o artista esta cadastrando. */
public record SugerirCategoriasRequestDTO(

        @NotBlank(message = "Informe o titulo da musica para a IA se basear")
        @Size(max = 150, message = "O titulo deve ter no maximo 150 caracteres")
        String titulo
) {
}
