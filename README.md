# ANYTHING RPG

RPG em Kotlin com gameplay orientada a dados.

## Fluxo ativo

Nota: o CLI foi removido.

Caminho ativo do projeto:

`app-android` -> `core`

## Estrutura principal

- `core/`: regras de dominio, sistemas de combate, inventario, quests, crafting, encantamento, caca e apresentacao de telas.
- `app-android/`: app Android integrado ao core.
- `data/`: conteudo JSON (itens, drops, receitas, classes, quest templates, saves).
- `docs/context/`: memoria tecnica e changelog de evolucao.

## Build e execucao

Build completo:

```bash
./gradlew build
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
- Evitar acoplamento de regra de dominio com camada de interface.
- Manter o fluxo ativo `app-android -> core`.
