# ANYTHING

Indice principal de documentacao do projeto.  
Este arquivo centraliza os modulos, os caminhos de documentacao e um resumo dos sistemas principais.

## Visao Geral

`ANYTHING` e um RPG textual modular em Kotlin, orientado a dados (`data/*.json`) e com foco em expansao de sistemas (combate, progressao, quests, craft, coleta e geracao procedural).

## Modulos do Projeto

| Modulo | Caminho | Descricao |
|---|---|---|
| CLI | `src/main/kotlin/rpg/cli` | Interface de terminal, HUD, menus e fluxo do jogador. |
| Engine Core | `src/main/kotlin/rpg/engine` | Regras base de jogo, stats, progressao de run e calculos centrais. |
| Combate | `src/main/kotlin/rpg/combat` | Loop de combate, estados de acao, cooldowns e resolucao de acoes. |
| Status | `src/main/kotlin/rpg/status` | Efeitos de status (DOT, bloqueios, modificadores e expiracao). |
| Talentos e Classes | `src/main/kotlin/rpg/classsystem` | Arvores de talentos, classes/subclasses e aplicacao de bonus. |
| Progressao | `src/main/kotlin/rpg/progression` | XP, niveis, pontos de atributo e desbloqueios. |
| Skills (Profissoes) | `src/main/kotlin/rpg/skills` | XP de profissao, eficiencia e progressao de coleta/craft. |
| Economia | `src/main/kotlin/rpg/economy` | Gold, venda/compra, drop e balanceamento economico. |
| Inventario | `src/main/kotlin/rpg/inventory` | Limite de slots, empilhamento e insercao com capacidade. |
| Itens | `src/main/kotlin/rpg/item` | Resolucao, geracao e nomenclatura de itens. |
| Crafting | `src/main/kotlin/rpg/crafting` | Receitas, consumo de ingredientes e entrega de outputs. |
| Gathering | `src/main/kotlin/rpg/gathering` | Coleta de recursos (mining, herbalism, woodcutting, fishing). |
| Quests | `src/main/kotlin/rpg/quest` | Board, progresso, entrega e recompensas. |
| Monstros | `src/main/kotlin/rpg/monster` | Geracao, raridade, comportamento e escalonamento. |
| Procedural Texto/Eventos | `src/main/kotlin/rpg/procedural` e `src/main/kotlin/rpg/events` | Variacao textual e eventos de dungeon. |
| Mundo/Dungeon | `src/main/kotlin/rpg/world` | Fluxo da dungeon, salas e dificuldade. |
| Dados e IO | `src/main/kotlin/rpg/io`, `src/main/kotlin/rpg/model`, `src/main/kotlin/rpg/registry` | Carregamento JSON, modelos e registries. |
| Escalonamento | `src/main/kotlin/rpg/scaling` | Soft caps e ajuste de crescimento de atributos. |
| Entrada da Aplicacao | `src/main/kotlin/rpg/Main.kt` | Bootstrap da aplicacao. |

## Sistemas Principais

### Combate
Sistema em tempo com estados de acao (`IDLE`, `READY`, `CASTING`, `STUNNED`, `DEAD`), cooldowns, uso de itens e integracao com status.  
Base: `rpg/combat`, `rpg/engine/Combat.kt`, `rpg/status`.

### Talentos
Arvores por classe/subclasse com prerequisitos, custo e bonus acumulativos para atributos e derivados.  
Base: `rpg/classsystem`, dados em `data/talents`, `data/classes`, `data/subclasses`.

### Progressao
Gerencia XP/nivel, pontos de atributo, curva de crescimento e desbloqueios de poder.  
Base: `rpg/progression`, `rpg/engine/Progression.kt`.

### Economia
Controla valor de itens, compra/venda, drops e fluxo de moedas para evitar inflacao no jogo.  
Base: `rpg/economy`, `data/shop`, `data/drop_tables`, `data/cash_packs`.

### Procedural
Componente data-driven para texto, quests, monstros e eventos, aumentando variacao de runs sem hardcode fixo.  
Base: `rpg/procedural`, `rpg/events`, `rpg/monster`, `data/text_pools`, `data/quest_templates`.

## Indice de READMEs

### Raiz
- [`README.md`](README.md) - Este indice principal.

### Data Modules (`read.me`)

- [`data/affixes/read.me`](data/affixes/read.me) - Affixes de itens.
- [`data/biomes/read.me`](data/biomes/read.me) - Configuracao de biomas.
- [`data/cash_packs/read.me`](data/cash_packs/read.me) - Pacotes de cash.
- [`data/character/read.me`](data/character/read.me) - Estrutura base de personagem.
- [`data/classes/read.me`](data/classes/read.me) - Definicoes de classes.
- [`data/crafting/read.me`](data/crafting/read.me) - Receitas de crafting.
- [`data/drop_tables/read.me`](data/drop_tables/read.me) - Tabelas de drop.
- [`data/events/read.me`](data/events/read.me) - Eventos de dungeon.
- [`data/gathering/read.me`](data/gathering/read.me) - Nos de coleta.
- [`data/items/read.me`](data/items/read.me) - Itens fixos.
- [`data/item_templates/read.me`](data/item_templates/read.me) - Templates de itens procedurais.
- [`data/maps/read.me`](data/maps/read.me) - Definicoes de mapas.
- [`data/map_tiers/read.me`](data/map_tiers/read.me) - Tiers de mapa/dungeon.
- [`data/monster_archetypes/read.me`](data/monster_archetypes/read.me) - Arquitetipos de monstros.
- [`data/monster_behaviors/read.me`](data/monster_behaviors/read.me) - Comportamentos de monstros.
- [`data/monster_modifiers/read.me`](data/monster_modifiers/read.me) - Modificadores de monstros.
- [`data/quest_templates/read.me`](data/quest_templates/read.me) - Templates de quest procedural.
- [`data/races/read.me`](data/races/read.me) - Definicoes de racas.
- [`data/shop/read.me`](data/shop/read.me) - Entradas de loja.
- [`data/skills/read.me`](data/skills/read.me) - Definicoes de skills/profissoes.
- [`data/subclasses/read.me`](data/subclasses/read.me) - Definicoes de subclasses.
- [`data/talents/read.me`](data/talents/read.me) - Arvores de talentos.
- [`data/talent_trees/read.me`](data/talent_trees/read.me) - Arvores de talentos V2 (ranks, exclusividade, requisitos).
- [`data/text_pools/read.me`](data/text_pools/read.me) - Pools textuais procedurais.

## Navegacao Recomendada

1. Ler este indice (`README.md`).
2. Explorar `src/main/kotlin/rpg/*` por modulo de interesse.
3. Consultar `data/*/read.me` para formato e regras dos JSONs.
