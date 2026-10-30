# SmartFiles

Gerenciador de arquivos inteligente para Android (grátis). Kotlin + Jetpack Compose, Android 11+.

## Como rodar

1. Abra a pasta `SmartFiles` no Android Studio (Ladybug ou mais novo).
2. Espere o Gradle sincronizar (na primeira vez ele baixa as dependências).
3. Rode num celular ou emulador com Android 11+.
4. Na primeira abertura, toque em **Conceder permissão** e ative "Permitir acesso para gerenciar todos os arquivos".

## O que já funciona (etapa 1 do MVP)

- Tela de permissão com rechecagem automática ao voltar das configurações
- Navegação por pastas, com barra de caminho clicável e botão voltar
- Pastas primeiro, depois arquivos em ordem alfabética
- Ícone e cor por tipo (imagem, vídeo, áudio, PDF, documento, compactado, APK)
- Tamanho, data e quantidade de itens por pasta
- Abrir arquivo com outro app, compartilhar, renomear, excluir (com confirmação)
- Criar pasta e mostrar/ocultar arquivos ocultos
- Tema Material You (cores do papel de parede no Android 12+) e modo escuro

## Etapa 2: indexação e busca

- **Índice local (Room)**: cada arquivo e pasta vira uma linha na tabela `files`, com o nome
  normalizado (sem acento, minúsculo). Buscar "relatorio" acha "Relatório.pdf".
- **Varredura em segundo plano (WorkManager)**: ao abrir o app, varre se a última varredura
  tiver mais de 1h; e agenda uma varredura a cada 12h, só com o celular carregando e bateria ok.
  Pula ocultos (`.thumbnails` etc.) e `Android/data` / `Android/obb`.
- **Índice sempre em dia**: criar, renomear e excluir pelo app atualiza o índice na hora.
- **Tela de busca** (lupa no topo): resultados enquanto digita, filtros por tipo
  (imagens, vídeos, PDFs...) e por data (hoje, 7 dias, 30 dias, este ano), status da indexação,
  botão de reindexar e "mostrar na pasta". Sem nada digitado, mostra os arquivos mais recentes.

## Etapa 3: armazenamento e duplicados

- **Painel de armazenamento** (ícone de gráfico no topo): espaço usado e livre, barra colorida
  por tipo (imagens, vídeos, áudios...) + "Apps e sistema" (o que fica fora do índice),
  e a lista dos 30 maiores arquivos com exclusão direta.
- **Detector de duplicados** em três filtros, do mais barato pro mais caro:
  1. mesmo tamanho (consulta no índice, instantâneo);
  2. hash rápido (tamanho + primeiros e últimos 64 KB);
  3. SHA-256 completo só pra quem empatou, com cache na tabela `file_hashes`
     (a segunda busca é bem mais rápida).
- **Seleção segura**: já vem marcando tudo menos a cópia mais antiga (provável original),
  e o app não deixa marcar todas as cópias de um grupo.
- **Miniaturas** de imagens no navegador, na busca e nos duplicados (Coil).
- Banco na versão 2, com migração manual (o índice existente não se perde).

## Etapa 4: limpeza do WhatsApp

- Abre pelo painel de armazenamento ("Limpeza do WhatsApp").
- Lê direto as pastas de mídia do **WhatsApp e do WhatsApp Business**
  (`Android/media/com.whatsapp...` no Android 11+, e a pasta antiga na raiz, se existir).
- Agrupa por tipo: fotos, vídeos, áudios de voz, músicas, documentos, figurinhas, GIFs,
  recados de vídeo e fotos de perfil, com quantidade e tamanho de cada um.
- Filtros por idade (+30 dias, +3 meses, +1 ano) e "Incluir enviados" (as cópias da pasta `Sent`).
- Fotos, vídeos e figurinhas em **grade com miniaturas**: toque marca, toque longo abre.
  Áudios e documentos em lista: toque abre/toca, caixa marca.
- Ordenação (maiores, mais antigos, mais recentes), "selecionar todos" e exclusão em lote
  mostrando quanto vai liberar.
- **Miniaturas de vídeo** (Coil + VideoFrameDecoder, configurado em `SmartFilesApp`),
  que também passaram a aparecer no navegador, na busca e nos duplicados.

Observação: os arquivos de mídia não dizem de qual conversa vieram, então não dá pra agrupar
por contato sem ler o banco do WhatsApp (que é criptografado e inacessível).

## Etapa 5: OCR (texto dentro de imagens e PDFs)

- **ML Kit Text Recognition** com o modelo latino embutido: funciona offline, nada sai do celular.
  Lê português, inglês, espanhol e outras línguas com alfabeto latino.
- **OcrWorker** roda em segundo plano depois de cada indexação (e retoma ao abrir o app),
  só com bateria ok. Processa dos arquivos mais recentes pros mais antigos, em lotes de 25;
  se o Android interromper, continua de onde parou na próxima vez.
- Lê imagens (reduzidas pra no máximo 2048 px, respeitando a rotação EXIF) e as
  **3 primeiras páginas** de cada PDF. Ignora arquivos menores que 20 KB, maiores que 25 MB
  e figurinhas.
- O texto vai pra tabela `file_text` (banco v3, migração sem perder nada). Arquivos sem texto
  também são marcados, pra não serem lidos de novo.
- **A busca agora procura no nome e no texto.** Quando o termo aparece só no texto,
  o resultado mostra o trecho com a palavra destacada: buscar "boleto" encontra o print do boleto
  mesmo que ele se chame `Screenshot_20261029.png`.
- A tela de busca mostra o progresso da leitura ("Lendo texto das imagens… 120 de 3.400").

## Etapa 6: busca em linguagem natural e categorias automáticas

### Busca em linguagem natural (100% offline)
O `QueryParser` interpreta frases em português por regras e o `SearchQueryBuilder` monta o SQL.
A tela mostra o que foi entendido ("Entendi: Imagens · Mês passado · Comprovantes").

| Exemplo | Entende |
|---|---|
| comprovantes de pix do mês passado | categoria Comprovantes + mês anterior |
| prints de hoje | categoria Prints + hoje |
| fotos do gato | imagens com rótulo `cat` |
| boletos de luz | categoria Boletos + palavra "luz" (no nome ou no texto) |
| vídeos grandes do whatsapp | vídeos ≥ 5 MB, ordenados por tamanho, da pasta do WhatsApp |
| nota fiscal da amazon em março | Notas fiscais + "amazon" + março (do ano atual ou do anterior) |
| fotos da praia de janeiro de 2025 | imagens com rótulo `beach` + jan/2025 |
| arquivos maiores que 100 mb | tamanho mínimo |

Entende datas (hoje, ontem, esta semana, semana passada, mês passado, últimos N dias, nome do mês,
"de 2024"...), tipos, categorias, origem (WhatsApp, Telegram, Instagram, Downloads, câmera),
tamanho e rótulos de imagem em português (gato, cachorro, praia, flor, carro...).
As palavras que sobram precisam aparecer **todas**, em qualquer ordem, no nome ou no texto do arquivo
(isso também resolveu o limite de "frase exata" da etapa 5).

### Categorias automáticas
O `OcrWorker` agora faz a análise completa de cada imagem/PDF numa passada só:
OCR (reaproveitado se já foi lido na etapa 5) + **rótulos de imagem do ML Kit** (offline) +
regras do `Categorizer`:

- **Prints**: pasta Screenshots ou nome "Screenshot"/"Captura"
- **Comprovantes**: texto com "comprovante", "pix", "pagamento realizado", "valor pago"...
- **Boletos e contas**: "boleto", "linha digitável", "código de barras", "beneficiário"...
- **Notas fiscais**: "nota fiscal", "NF-e", "DANFE", "chave de acesso"...
- **Documentos**: PDFs com texto, fotos de papel com bastante texto, e arquivos do Office
- **Pessoas / Animais / Comida / Paisagens**: pelos rótulos do ML Kit
- **Memes** (palpite): imagens do WhatsApp com pouco texto por cima, que não são prints nem financeiras

As categorias aparecem como chips com contagem na busca e embaixo de cada resultado.
Ficam na tabela `file_analysis` (banco v4). Pra reprocessar tudo depois de melhorar as regras,
é só subir `FileAnalysis.CURRENT_VERSION`.

## Estrutura

```
app/src/main/java/com/smartfiles/app/
├── MainActivity.kt              # decide entre tela de permissão e navegador
├── data/
│   ├── FileItem.kt              # modelo do arquivo + classificação por tipo
│   ├── FileRepository.kt        # operações de disco (sempre em Dispatchers.IO)
│   ├── index/                   # Room (IndexedFile, FileHash, FileDao, AppDatabase), FileIndexer, IndexWorker
│   ├── duplicates/              # Hashing (rápido + completo) e DuplicateFinder
│   ├── whatsapp/                # leitura das pastas de mídia do WhatsApp
│   ├── ocr/                     # OcrEngine (texto + rótulos, PdfRenderer) e OcrWorker (análise)
│   └── smart/                   # SmartTag + Categorizer, QueryParser, SearchQueryBuilder
├── ui/
│   ├── browser/                 # tela principal (ViewModel + Compose)
│   ├── search/                  # busca com filtros
│   ├── storage/                 # painel de armazenamento
│   ├── duplicates/              # revisão e exclusão de duplicados
│   ├── whatsapp/                # limpeza do WhatsApp (visão geral + grade/lista por tipo)
│   ├── components/              # ícones, rótulos e miniaturas por tipo de arquivo
│   ├── permission/              # pedido de "acesso a todos os arquivos"
│   └── theme/                   # cores e tema
└── util/FileUtils.kt            # abrir/compartilhar, formatação de tamanho e data
```

## Próximas etapas

2. ~~Indexação em segundo plano (Room + WorkManager) e busca por nome/tipo/data~~ ✅
3. ~~Painel de armazenamento + detector de duplicados por hash~~ ✅
4. ~~Limpeza do WhatsApp (`Android/media/com.whatsapp`)~~ ✅
5. ~~OCR com ML Kit entrando na busca~~ ✅
6. ~~Busca em linguagem natural e categorias automáticas~~ ✅

## Antes de publicar na Play Store

A permissão `MANAGE_EXTERNAL_STORAGE` exige o formulário de declaração na Play Console
(categoria "Gerenciador de arquivos"). Troque também o `applicationId` (`com.smartfiles.app`)
por um definitivo, pois ele não pode mudar depois da publicação.
