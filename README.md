# ANYTHING RPG

RPG textual em Kotlin com gameplay orientada a dados.

## Fluxo ativo

Entrada da aplicacao CLI:

`app-cli/src/main/kotlin/rpg/Main.kt` -> `GameCli` -> `CliFlowController`

Esse e o fluxo suportado para execucao local.

## Estrutura principal

- `core/`: regras de dominio, sistemas de combate, inventario, quests, crafting, encantamento, caca e apresentacao de telas.
- `app-cli/`: interface textual (render, input, controle de fluxo CLI).
- `app-android/`: app Android integrado ao core.
- `data/`: conteudo JSON (itens, drops, receitas, classes, quest templates, saves).
- `docs/context/`: memoria tecnica e changelog de evolucao.

## Build e execucao

Build completo:

```bash
./gradlew build
```

Executar CLI:

```bash
./gradlew run
```

Executar testes:

```bash
./gradlew test
```

Pacote Windows portatil:

```bash
./gradlew packageWindowsPortable
```

## Dados e saves

- Catalogos sao carregados de `data/` de forma recursiva.
- Saves ficam em `data/saves/`.

## Diretrizes de manutencao

- Priorizar conteudo de gameplay data-driven em JSON.
- Evitar colocar regra de dominio na camada de CLI.
- Manter compatibilidade com o fluxo ativo da CLI.
