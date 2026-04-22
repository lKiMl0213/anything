# ANYTHING RPG

Projeto RPG textual em Kotlin, orientado a dados, com arquitetura reorganizada para separar:
- `core` (regras e logica reaproveitavel),
- `app-cli` (interface textual atual),
- `app-android` (base preparada para UI visual futura),
- `data` (catalogos JSON + saves),
- `docs` (documentacao e memoria de projeto).

## Estrutura Atual

```text
anything/
|-- app-cli/
|   `-- src/main/kotlin/rpg/
|       |-- Main.kt
|       `-- cli/...
|-- app-android/
|   |-- README.md
|   `-- src/main/kotlin/rpg/android/
|       |-- AndroidAppBootstrap.kt
|       |-- navigation/
|       |-- screens/
|       |-- components/
|       `-- theme/
|-- core/
|   `-- src/main/kotlin/rpg/
|       |-- achievement/
|       |-- application/
|       |-- classquest/
|       |-- classsystem/
|       |-- combat/
|       |-- crafting/
|       |-- creation/
|       |-- dungeon/
|       |-- economy/
|       |-- engine/
|       |-- events/
|       |-- gathering/
|       |-- inventory/
|       |-- io/
|       |-- item/
|       |-- model/
|       |-- monster/
|       |-- navigation/
|       |-- presentation/
|       |-- procedural/
|       |-- progression/
|       |-- quest/
|       |-- registry/
|       |-- scaling/
|       |-- session/
|       |-- skills/
|       |-- state/
|       |-- status/
|       |-- talent/
|       `-- world/
|-- data/
|   |-- ... (classes, itens, drop tables, crafting, etc.)
|   `-- saves/
|-- docs/
|   `-- context/
|-- build.gradle.kts
|-- settings.gradle.kts
|-- gradlew
`-- gradlew.bat
```

## Responsabilidades por Camada

### `core`
- regras de combate, progressao, inventario, economia, quests, talentos, monstros, classes e estado.
- carregamento de dados/registries e servicos de dominio.
- logica que deve ser reaproveitada por CLI e futura UI visual.

### `app-cli`
- fluxo textual atual do jogo.
- menus, render de texto, input de terminal e runtime legado/modular da CLI.

### `app-android`
- base minima para iniciar Jetpack Compose no proximo passo.
- estrutura inicial de navegacao/telas/componentes/theme.

### `data`
- catalogos JSON recursivos do jogo.
- `data/saves` como destino padrao de save/load/autosave.

### `docs`
- contexto, changelog e memoria de projeto em `docs/context`.

## Build e Execucao

### Build
```bash
./gradlew build
```

### Rodar CLI
```bash
./gradlew run
```

### Testes
```bash
./gradlew test
```

## Observacoes Importantes

- A reorganizacao foi estrutural/fisica; regras de gameplay foram preservadas.
- O projeto continua single-module no Gradle por seguranca nesta fase, com `sourceSets` apontando para:
  - `core/src/main/kotlin`
  - `app-cli/src/main/kotlin`
- `app-android` foi preparado como base de trabalho futuro sem acoplar no build agora.

## Dados e Saves

- O jogo continua carregando catalogos a partir de `data/`.
- Save/load/autosave padrao agora usa:
  - `data/saves/*.json`

## Limpeza de Scripts

Foram removidos scripts auxiliares legacy/duplicados de compilacao/exportacao:
- `compilar-pra-exportar.ps1`
- `compilar-pra-exportar.sh`
- `compilar_pra_exportar.bat`
- `scripts/reorganize_data_layout.ps1`
- `Relatório.md` (removido)

Wrappers do Gradle (`gradlew`/`gradlew.bat`) foram mantidos por serem necessarios para build/execucao multiplataforma.
