"use client";

import { useState, useTransition } from "react";
import { Plus, Sparkles } from "lucide-react";
import { toast } from "sonner";

import {
  adicionarMusicaAction,
  recomendarMusicasAction,
} from "@/app/(app)/playlists/actions";
import { Button } from "@/components/ui/button";
import { formatarDuracao } from "@/lib/utils/formato";
import type { MusicaResumo } from "@/types/api";

/**
 * Pede à IA músicas do catálogo que combinem com a playlist. Só aparece para o
 * dono, e só quando a playlist já tem música (a API recusa playlist vazia).
 */
export function RecomendacoesIa({ playlistId }: { playlistId: number }) {
  const [recomendadas, setRecomendadas] = useState<MusicaResumo[] | null>(null);
  const [buscando, startBuscar] = useTransition();
  const [adicionando, startAdicionar] = useTransition();

  function recomendar() {
    startBuscar(async () => {
      const resultado = await recomendarMusicasAction(playlistId);
      if (resultado.ok) {
        setRecomendadas(resultado.musicas);
      } else {
        toast.error(resultado.erro);
      }
    });
  }

  function adicionar(musica: MusicaResumo) {
    startAdicionar(async () => {
      const resultado = await adicionarMusicaAction(playlistId, musica.id);
      if (resultado.ok) {
        toast.success(`"${musica.titulo}" adicionada`);
        setRecomendadas((atuais) => atuais?.filter((m) => m.id !== musica.id) ?? null);
      } else {
        toast.error(resultado.erro);
      }
    });
  }

  return (
    <section className="mt-8 rounded-lg border border-marca/30 bg-marca/5 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 font-bold">
            <Sparkles className="size-4 text-marca dark:text-marca-clara" />
            Recomendações da IA
          </h2>
          <p className="text-sm text-texto-suave">
            Músicas do catálogo que combinam com esta playlist.
          </p>
        </div>
        <Button variant="outline" onClick={recomendar} disabled={buscando}>
          {buscando
            ? "Pensando..."
            : recomendadas
              ? "Recomendar de novo"
              : "Recomendar músicas"}
        </Button>
      </div>

      {recomendadas && (
        <div className="mt-4 space-y-1">
          {recomendadas.length === 0 ? (
            <p className="py-4 text-center text-sm text-texto-suave">
              Nenhuma recomendação agora. Todo o catálogo já pode estar na playlist.
            </p>
          ) : (
            recomendadas.map((musica) => (
              <button
                key={musica.id}
                type="button"
                disabled={adicionando}
                onClick={() => adicionar(musica)}
                className="flex w-full cursor-pointer items-center gap-3 rounded-md px-3 py-2 text-left transition-colors hover:bg-superficie-alta disabled:cursor-not-allowed disabled:opacity-50"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold">{musica.titulo}</p>
                  <p className="truncate text-xs text-texto-suave">
                    {musica.artistaNome}
                    {musica.categorias.length > 0 &&
                      ` · ${musica.categorias.map((c) => c.nome).join(", ")}`}
                  </p>
                </div>
                <span className="shrink-0 text-xs tabular-nums text-texto-fraco">
                  {formatarDuracao(musica.duracao)}
                </span>
                <Plus
                  className="size-4 shrink-0 text-marca dark:text-marca-clara"
                  aria-label="Adicionar"
                />
              </button>
            ))
          )}
        </div>
      )}
    </section>
  );
}
