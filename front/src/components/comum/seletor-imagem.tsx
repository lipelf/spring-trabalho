"use client";

import { useRef, useState } from "react";
import { ImagePlus, Music2, X, type LucideIcon } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ErroImagem, prepararImagem } from "@/lib/utils/imagem";


/**
 * Escolhe uma imagem e devolve um data URI base64 num input hidden, para o
 * formulario enviar junto com os outros campos. Usado na capa da playlist, na
 * capa do album e na foto do artista.
 */
export function SeletorImagem({
  name,
  valorInicial,
  rotulo = "Imagem",
  descricao = "PNG ou JPG, até 10MB. Opcional.",
  Icone = Music2,
  /** `circulo` para foto de perfil, `quadrado` para capa. */
  formato = "quadrado",
}: {
  name: string;
  valorInicial?: string | null;
  rotulo?: string;
  descricao?: string;
  Icone?: LucideIcon;
  formato?: "quadrado" | "circulo";
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [imagem, setImagem] = useState<string>(valorInicial ?? "");

  async function escolher(arquivo: File) {
    try {
      // valida tipo e tamanho e reduz para 600px (ver lib/utils/imagem.ts)
      setImagem(await prepararImagem(arquivo));
    } catch (erro) {
      toast.error(erro instanceof ErroImagem ? erro.message : "Não foi possível ler a imagem.");
    }
  }

  const arredondamento = formato === "circulo" ? "rounded-full" : "rounded-md";

  return (
    <div className="flex items-center gap-4">
      {/* o valor vai para o FormData junto com os outros campos */}
      <input type="hidden" name={name} value={imagem} />

      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        aria-label={imagem ? `Trocar ${rotulo.toLowerCase()}` : `Escolher ${rotulo.toLowerCase()}`}
        className={cn(
          "group relative size-24 shrink-0 cursor-pointer overflow-hidden border border-border bg-superficie-alta transition-colors hover:border-texto-fraco",
          arredondamento,
        )}
      >
        {imagem ? (
          // a imagem vem em base64, entao <img> comum (o next/image nao otimiza data URI)
          // eslint-disable-next-line @next/next/no-img-element
          <img src={imagem} alt="" className="size-full object-cover" />
        ) : (
          <span className="flex size-full items-center justify-center">
            <Icone className="size-8 text-texto-fraco" aria-hidden="true" />
          </span>
        )}

        <span
          className={cn(
            "absolute inset-0 flex items-center justify-center bg-black/60 opacity-0 transition-opacity group-hover:opacity-100",
            arredondamento,
          )}
        >
          <ImagePlus className="size-5 text-white" aria-hidden="true" />
        </span>
      </button>

      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg"
        className="sr-only"
        onChange={(e) => {
          const arquivo = e.target.files?.[0];
          if (arquivo) escolher(arquivo);
          e.target.value = ""; // permite reenviar o mesmo arquivo
        }}
      />

      <div className="space-y-2">
        <div>
          <p className="text-sm font-bold">{rotulo}</p>
          <p className="text-xs text-texto-suave">{descricao}</p>
        </div>

        <div className="flex gap-2">
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={() => inputRef.current?.click()}
          >
            {imagem ? "Trocar" : "Escolher"}
          </Button>

          {imagem && (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setImagem("")}
              className="text-texto-suave hover:text-destructive"
            >
              <X className="size-4" />
              Remover
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
