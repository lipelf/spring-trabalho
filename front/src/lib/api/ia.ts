import "server-only";

import { apiFetch } from "./client";
import type { Categoria, MusicaResumo, TextoIa } from "@/types/api";

/**
 * Rotas de IA. Nada aqui grava dado: a API so devolve sugestoes, e o formulario
 * decide se usa. Todas podem responder 503 quando a IA esta fora do ar.
 */

export const gerarDescricaoPlaylist = (nome: string, playlistId?: number) =>
  apiFetch<TextoIa>("/ia/playlists/descricao", {
    method: "POST",
    body: JSON.stringify({ nome, playlistId }),
  });

export const sugerirCategorias = (titulo: string) =>
  apiFetch<Categoria[]>("/ia/musicas/categorias", {
    method: "POST",
    body: JSON.stringify({ titulo }),
  });

export const recomendarMusicas = (playlistId: number) =>
  apiFetch<MusicaResumo[]>(`/ia/playlists/${playlistId}/recomendacoes`, {
    cache: "no-store",
  });
