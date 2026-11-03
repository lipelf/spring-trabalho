/**
 * Preparo de imagens no navegador antes de enviar para a API.
 *
 * Por que reduzir: a imagem vai como data URI base64 e aparece em mais de um
 * lugar da pagina (avatar do perfil, menu do header, avatar flutuante). Uma foto
 * de celular de 2MB vira ~2,8MB de texto e, repetida, estoura o limite de ~4,5MB
 * por resposta da Vercel — a pagina passa a dar erro 500. Reduzida para 600px,
 * a mesma foto fica com ~100KB.
 */

export const TIPOS_ACEITOS = ["image/png", "image/jpeg"];

/** Limite do arquivo escolhido, antes de reduzir. */
export const TAMANHO_MAXIMO_ARQUIVO = 10 * 1024 * 1024; // 10MB

/** Mesmo limite do backend (@Size(max = 2_800_000) nos DTOs de imagem). */
export const LIMITE_BASE64 = 2_800_000;

const LADO_MAXIMO = 600;
const QUALIDADE_JPEG = 0.85;

/** Erro com mensagem pronta para mostrar ao usuario. */
export class ErroImagem extends Error {}

/** "1,4MB", "850KB" */
export function formatarTamanho(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))}KB`;
}

function carregar(arquivo: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(arquivo);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new ErroImagem("Não foi possível ler a imagem. O arquivo pode estar corrompido."));
    };
    img.src = url;
  });
}

/**
 * Valida o arquivo, reduz para no maximo 600px no maior lado e devolve o data URI.
 * Lanca `ErroImagem` com uma mensagem clara quando o arquivo nao serve.
 */
export async function prepararImagem(arquivo: File): Promise<string> {
  if (!TIPOS_ACEITOS.includes(arquivo.type)) {
    throw new ErroImagem("Formato inválido. Envie uma imagem PNG ou JPG.");
  }
  if (arquivo.size > TAMANHO_MAXIMO_ARQUIVO) {
    throw new ErroImagem(
      `A imagem tem ${formatarTamanho(arquivo.size)}. O limite é ${formatarTamanho(TAMANHO_MAXIMO_ARQUIVO)}.`,
    );
  }

  const img = await carregar(arquivo);
  const escala = Math.min(1, LADO_MAXIMO / Math.max(img.width, img.height));
  const largura = Math.round(img.width * escala);
  const altura = Math.round(img.height * escala);

  const canvas = document.createElement("canvas");
  canvas.width = largura;
  canvas.height = altura;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new ErroImagem("Seu navegador não conseguiu processar a imagem.");
  ctx.drawImage(img, 0, 0, largura, altura);

  // PNG continua PNG para nao perder a transparencia; o resto vira JPEG
  const dataUri =
    arquivo.type === "image/png"
      ? canvas.toDataURL("image/png")
      : canvas.toDataURL("image/jpeg", QUALIDADE_JPEG);

  if (dataUri.length > LIMITE_BASE64) {
    throw new ErroImagem(
      `Mesmo reduzida, a imagem ficou com ${formatarTamanho(dataUri.length)} e o limite é ` +
        `${formatarTamanho(LIMITE_BASE64)}. Tente um JPG em vez de PNG.`,
    );
  }

  return dataUri;
}
