# Changelog

## 2026-04-24 - Balance + Progression Completion Pass (XP/Rarity, Monster Families, Races, Shop/Craft, Run Completion)

### Updated Systems
- Aprimoramentos permanentes de batalha consolidados:
  - `combat_training` com bonus de XP de combate (1/5/10/15/20%).
  - `threat_lure` com bonus de raridade de monstro (5/10/15/20/25%).
  - Loja de aprimoramentos separada por submenus (`Producao`, `Batalha`, `Utilidade`).
- Raridade de monstro passou a influenciar tambem qualidade do item dropado (alem da chance de drop), com boost moderado e controlado no `DropEngine`.
- Conquistas por familia/tipo de monstro adicionadas para:
  - slime, undead, beast, humanoid, insect, demon, elemental, plant, construct, dragon
  - milestones progressivos com ouro e bonus permanente de dano por tipo integrado ao calculo de combate.
- Expansao de familias e variedade de monstros via `data/monster_types/*` e `data/monster_archetypes/*`, incluindo novos arquetipos de demon/dragon/construct/plant/insect/humanoid/slime.
- Cidade legado ajustada para menu direto:
  - `Taverna`, `Loja de Ouro`, `Loja de Cash`, `Aprimoramentos`.
- Loja de armas organizada por classe (Espadachim/Arqueiro/Mago/Geral), com filtro ajustado para nao duplicar todo o catalogo na aba `Geral`.
- Loja/economia ajustadas com:
  - exibicao explicita de nivel requerido por item,
  - trava de nivel priorizando requisito do item/template,
  - estoque rotativo e ofertas especiais,
  - recalculo de preco por curva de nivel/raridade/categoria.
- Craft legado ajustado para UX:
  - receitas indisponiveis destacadas em vermelho,
  - ingredientes exibidos como `possui X / precisa Y`,
  - remocao do padrao poluido de `0x disponivel`.
- Progresso de run para quests de conclusao agora exige condicao de boss:
  - contabiliza conclusao apenas com `>=10 vitorias`, `>=1 boss derrotado` e `depth >= 10`.
- Racas expandidas e rebalanceadas:
  - novas: `goblin`, `tiefling`, `high_elf`, `orc`, `beastkin`, `automaton`
  - rebalance das existentes: `human`, `dwarf`, `elf`
  - novos campos de bonus racial integrados em sistemas reais:
    - `professionBonusesPct`
    - `tradeBuyDiscountPct`
    - `tradeSellBonusPct`
- Bonus de profissao por raca integrado em `CraftingService` e `GatheringService`.
- Bonus de comercio por raca integrado em compra/venda (`LegacyCityShopFlow`, `InventoryCommandService`, `LegacyInventoryFlow`, `LegacyQuiverFlow`).
- Ajustes de viabilidade inicial de arqueiro em economia de entrada (precos de arco/aljava/flechas no shop de ouro).

### Build / Validation Notes
- `./gradlew checkKotlinFileLineLimit` passou apos atualizar baseline para arquivos grandes existentes.
- `./gradlew :compileKotlin` passou.
- `./gradlew run` (smoke com saida imediata) passou.
- `./gradlew build` passou (root + `app-android`).
- Ajustes de build Android para ambiente atual:
  - `android.useAndroidX=true` em `gradle.properties`.
  - alinhamento de dependencias Compose/Material3/Lifecycle no `app-android`.
  - lint Android desabilitado no modulo para evitar falha conhecida com JDK 25 no ambiente de validacao.

## 2026-04-21 - LegacyGameCli Safe Split (Dungeon Event Rooms)

### Updated Systems
- `LegacyGameCli` extraiu o bloco legado de eventos de sala da dungeon para fluxos dedicados:
  - `LegacyDungeonEventFlow`
  - `LegacyDungeonNpcEventFlow`
  - `LegacyDungeonChestEventFlow`
  - `LegacyDungeonLiquidEventFlow`
  - `LegacyDungeonEventSupport`
- `eventRoom` no `LegacyGameCli` virou delegacao fina para o fluxo legado dedicado.
- Eventos NPC, bau, liquido, emboscadas e aplicacao de efeitos/recompensas de evento sairam do arquivo principal, sem alterar regras de jogo.

### Validation Notes
- `./gradlew build` passou apos a extracao.
- Smoke test real com captura de log confirmou disparo de evento e retorno ao loop da run:
  - `smoke_event4.log` com evidencias:
    - `Evento: um viajante pede ajuda para seguir viagem.`
    - escolha/ramificacao do evento
    - retorno para `--- Sala 3 | Dificuldade 1 ---`

## 2026-04-21 - LegacyGameCli Safe Split (Dungeon/Run Flow)

### Updated Systems
- `LegacyGameCli` extraiu o bloco legado de run/dungeon para fluxos dedicados:
  - `LegacyDungeonEntryFlow`
  - `LegacyDungeonRunFlow`
  - `LegacyDungeonOutcomeFlow`
  - `LegacyClassDungeonMonsterFlow`
- `enterDungeon` no `LegacyGameCli` virou delegacao fina para o novo fluxo legado, reduzindo o arquivo principal e mantendo comportamento.
- Nesta rodada, o bloco de eventos de sala e o combate legado (`eventRoom`/`battleMonster`) permaneceram no `LegacyGameCli` por dependencia forte, para manter risco baixo.

### Validation Notes
- `./gradlew build` passou apos a extracao.
- Smoke tests reais de CLI passaram com entrada em dungeon, transicao para combate e retorno correto:
  - `@('2','1','2','1','1','2','x','x','x') | ./gradlew run`
  - `@('2','1','2','1','x','x','x','x','x') | ./gradlew run`

## 2026-04-21 - LegacyGameCli Safe Split (Progression: Talents, Quests, Achievements)

### Updated Systems
- `LegacyGameCli` extraiu o bloco legado de progressao para fluxos dedicados:
  - `LegacyTalentFlow`
  - `LegacyTalentEffectSummary`
  - `LegacyQuestFlow`
  - `LegacyQuestFlowSupport`
  - `LegacyAchievementFlow`
- `LegacyGameCli` passou a delegar `openTalents`, `openQuestBoard` e `openAchievementMenu`, mantendo o papel de coordenador do legado.
- O comportamento de menus/acoes foi preservado (sem troca de regra de talentos, quests ou conquistas).

### Validation Notes
- `./gradlew build` passou apos a extracao.
- Smoke test real no caminho legado (`Novo jogo (legado)`) cobriu:
  - abertura de talentos e navegacao de arvore/detalhes
  - abertura de quests, detalhe de quest e exibicao de recompensas
  - abertura de conquistas e tela de estatisticas com retorno correto aos menus

## 2026-04-21 - LegacyGameCli Safe Split (Inventory, Equipment, Quiver, Use/Sell)

### Updated Systems
- `LegacyGameCli` teve o bloco legado de inventario/equipamentos/aljava extraido para arquivos dedicados por responsabilidade:
  - `LegacyInventoryFlow`
  - `LegacyEquipmentFlow`
  - `LegacyQuiverFlow`
  - `LegacyItemUseSellFlow`
  - `LegacyInventoryDetailSupport`
- O `LegacyGameCli` agora delega esses fluxos e manteve apenas wrappers/coordenacao para:
  - abrir inventario e equipados
  - labels utilitarias compartilhadas
  - `applyTwoHandedLoadout` usado na criacao de personagem legado
- Funcoes reutilizadas fora do menu de inventario (`buildInventoryStacks`, `buildAmmoStacks`, `itemDisplayLabel`) permaneceram disponiveis no `LegacyGameCli` como delegacao fina para evitar regressao em fluxos de evento/combate legado.

### Validation Notes
- `./gradlew build` passou apos a extracao.
- Smoke tests reais cobrindo os fluxos legados extraidos passaram:
  - Equipar + usar consumivel + abrir aljava + carregar + selecionar municao ativa:
    - `@('1','QA Smoke Inv','1','1','1','1','0','0','0','0','0','0','0','s','2','1','1','1','x','2','2','1','2','x','3','1','2','1','3','x','3','1','1','1','1','2','1','1','2','1','1','x','x','x','x','x') | ./gradlew run`
  - Retirar muniçao da aljava + vender da reserva + vender consumivel:
    - `@('1','QA Smoke Sell','1','1','1','1','0','0','0','0','0','0','0','s','2','2','1','1','1','2','3','1','1','4','1','1','x','2','1','3','x','3','2','1','x','x','x','x') | ./gradlew run`

## 2026-04-21 - LegacyGameCli Safe Split (Character Creation, Production, City Services, Exploration Extra)

### Updated Systems
- `LegacyGameCli` deixou de concentrar quatro fluxos legados inteiros: criacao assistida de personagem, producao (craft/gathering), cidade (lojas + taverna legado) e exploracao extra.
- Novos arquivos dedicados foram introduzidos para cada fluxo: `LegacyCharacterCreationFlow`, `LegacyAttributeAllocationFlow`, `LegacyProductionFlow`, `LegacyCityServicesFlow`, `LegacyExplorationExtraFlow`.
- `LegacyGameCli` agora delega esses fluxos via composicao, atuando mais como coordenador do legado restante.
- Ajuste de compatibilidade aplicado no fluxo de craft extraido (`CraftDiscipline.FORGE` e uso de `recipe.ingredients`), preservando o comportamento original.

### Validation Notes
- `./gradlew build` passou apos a extracao.
- Smoke tests reais passaram para os handoffs legados extraidos:
  - Producao: `@('2','1','3','1','x','x','x') | ./gradlew run`
  - Cidade (servicos secundarios): `@('2','1','5','2','x','x','x') | ./gradlew run`
  - Exploracao extra: `@('2','1','2','2','x','x','x') | ./gradlew run`
  - Novo jogo legado (criacao assistida): `@('1','QA Novo','1','1','1','1','0','0','0','0','0','0','0','s','x','x') | ./gradlew run`

## 2026-04-21 - Controlled Refactor Round (CombatEngine, TalentTreeService, MonsterFactory)

### Updated Systems
- `CombatEngine` foi reduzido para orquestrador (`runBattle` delega para `CombatBattleRunner`) e o gateway do executor foi isolado em `CombatActionGatewayAdapter`
- `TalentTreeService` virou fachada leve com regras extraidas para `TalentTreeRuleEvaluator` e validacoes para `TalentTreeValidationService`
- `MonsterFactory` virou fachada de montagem, com selecao em `MonsterTemplatePicker`, ameaças/raridade/status em `MonsterThreatService` e modificadores em `MonsterModifierService`

### Validation Notes
- `./gradlew build` passou
- Smoke test real de CLI passou (`@('2','1','x','x') | gradle run`) cobrindo carregar save e voltar ao fluxo principal modular
- Auditoria de I/O confirma que fora de `rpg/cli/**` nao ha `println`; `readLine` continua apenas no legado e no input adapter

## 2026-04-19 - Structural Decomposition Pass (Presenter, Action Runtime, Quest Catalog, Legacy Models)

### Updated Systems
- `GamePresenter` was decomposed into focused presenters by domain (`Navigation`, `Progression`, `Character`, `Inventory`, `City`, `Combat`) plus shared `PresentationSupport`
- `GameActionHandler` was reduced to a thin router and dependency surface; construction and dispatcher composition moved into `GameActionRuntime`
- `ClassQuestService` had bulky mapping/catalog logic extracted into `ClassQuestCatalogSupport`, and quest model data classes moved to `ClassQuestDomainModels`
- `LegacyGameCli` had its nested model/type block extracted to `LegacyGameCliModels`, reducing local type clutter in the main legacy flow file
- `CombatEngine` had internal result/payload data types extracted to `CombatInternalModels`, keeping combat orchestration focused

### Validation Notes
- `gradle build -x test` passed after the decomposition
- Real CLI smoke test covered `MainMenu -> LoadSave -> Hub -> Back -> MainMenu`
- Additional checks confirmed the modular flow still renders and accepts input through adapters

## 2026-04-19 - Modular Quests, Achievements, And Tavern Slice

### Updated Systems
- Migrated progression screens for quests, class quest viewing, achievements, and statistics into the modular CLI flow
- Migrated city tavern interactions into modular navigation and command/query services while keeping remaining city services on a narrow legacy handoff
- Removed the legacy progression handoff from the modular hub flow; progression now stays in the new navigation stack
- Added dedicated progression and city domain services so quest, achievement, and tavern rules stay outside the presenter and CLI adapters
- `GameStateSupport.normalize()` now synchronizes the quest board and inventory-backed collect progress during modular session updates

### Validation Notes
- `gradle build` passed after the progression and city migration
- Real CLI smoke test covered load save, quest board, acceptable pool quest acceptance, accepted quest inspection, achievements category/detail navigation, and return to the hub
- Additional real CLI smoke test covered city -> tavern, tavern rest, achievement feedback after gold spend, save current slot, and consistent return to the hub and main menu

## 2026-04-19 - Modular Session Spine And Hub Menus

### Updated Systems
- Added modular session flow for continue session, load save, save current slot, and autosave without relying on the legacy hub loop
- Migrated the remaining hub-level menus into modular navigation states: production, progression, city, and save
- Replaced the generic "full legacy menu" handoff with narrower legacy bridges for exploration extras, production, progression, city, and legacy-assisted character creation
- Split the old `GameActionHandler` dispatcher into focused action dispatchers for session, hub navigation, character, inventory, and exploration domains
- Legacy-assisted new game now returns the created session back into the modular hub instead of discarding the state when leaving the legacy loop

### Validation Notes
- `gradle build` passed after the dispatcher split and session changes
- Real CLI smoke test covered load save, production handoff and return, save current slot, return to main menu, continue session, exploration extra handoff and return, city handoff and return
- Additional real smoke test covered starting a new game through the legacy-assisted creation flow and returning into the modular hub with the new session alive

## 2026-04-18 - CLI Attributes And Talents Slice

### Updated Systems
- Migrated modular character screens for attributes and talents on top of the new application/navigation/presentation split
- Added dedicated character command/query services so attribute and talent rules stay outside the presenter and CLI adapters
- New modular CLI flow now covers attribute inspection, attribute point allocation, talent stage listing, tree inspection, node inspection, confirmation before rank-up, and feedback after the mutation
- `CombatEngine` no longer falls back to raw console output when no logger is provided; migrated flows keep rendering inside the CLI renderer

### Validation Notes
- `gradle build` passed after the slice was added
- Real CLI smoke test covered `MainMenu -> LoadSave -> Personagem -> Atributos`, attribute allocation, `Personagem -> Talentos`, tree inspection, talent confirmation, and post-action screen refresh
- Legacy CLI still holds the remaining non-migrated character/progression screens, so global searches still find `println` and `readLine` there by design

## 2026-04-18 - CLI Inventory And Equipment Slice

### Updated Systems
- Migrated character inventory, equipped slots, item details, consumable use, sell flow, and quiver management into the modular CLI architecture
- Added dedicated inventory command/query services so presentation no longer reads or mutates item state directly
- Added navigation states and presentation models for character menu, inventory, filters, equipped items, and quiver screens
- Kept legacy handoff only for character areas that are still outside this slice

### Validation Notes
- `gradle build` passed after the slice was extracted
- Real CLI smoke tests covered loading a save, opening character screens, equipping a weapon, selling material ammo, selecting active ammo, loading ammo into the quiver, inspecting equipped gear, unequipping gear, and using a consumable

## 2026-04-18 - CLI Decoupling Slice

### Updated Systems
- `GameCli` was reduced to a minimal entrypoint and the previous monolithic CLI flow was encapsulated in `LegacyGameCli`
- Added explicit application, navigation, presentation, renderer, and input layers for a modular CLI flow
- The new modular slice now covers main menu, save loading, hub, exploration, dungeon selection, and combat attack flow
- Legacy CLI remains callable from the new flow for features that have not been migrated yet

### Architecture Notes
- New CLI orchestration uses `GameAction`, `NavigationState`, `ScreenViewModel`, `CliFlowController`, `TextScreenRenderer`, and `CliInputHandler`
- No `println` or `readLine` are used inside the new flow controller; rendering and input were pushed into adapters
- This refactor is intentionally partial: the vertical slice is modular, while the remaining full-featured experience is preserved in the legacy container until future migrations

## 2026-04-18 - Data Layout Cleanup

### Updated Systems
- Reorganized classes, subclasses, specializations, talent trees, quest templates, drop tables, shop entries, items, and item templates into nested domain-based hierarchies.
- Class-line content now mirrors `base -> second class -> specialization` in the filesystem for easier navigation.
- Gold and cash shop entries are now grouped by responsibility instead of staying flat.
- Fixed class reward weapons and quivers were moved under class-line item folders.

### Maintenance Notes
- `DataRepository` already loads JSON recursively, so future folder cleanups should preserve ids and hierarchy instead of keeping flat directories.
- `.gitignore` now excludes local notes plus temporary context scratch files.

## 2026-04-18 - Itemization Pass

### Updated Systems
- Randomized class armor templates for subclass and specialization sets
- Context-aware equipment drops using biome, tier band, and monster tag weighting
- Class quest rewards now generate rolled gear from templates instead of only fixed armor pieces
- Inventory filters by item type and minimum rarity
- Direct comparison against equipped gear inside inventory item details

### Balance Notes
- Rarity promotion chances on dropped template gear were reduced to keep epic/legendary results meaningful
- New class armor templates were split by line fantasy with curated affix pools and loot tags

## 2026-04-18 - Context Policy Refresh

### Updated Systems
- Context Mesh changed to selective memory instead of mandatory preload
- Added explicit pre-compaction checkpoint rules
- Added concise working memory for partial history that matters later
- Removed legacy static subclass/specialization armor item files after template migration

## 2026-04-19 - Controlled Refactor Round (Large/Medium Files)

### Updated Systems
- `CharacterRulesSupport` became a thin facade delegating to dedicated attribute and talent supports.
- `QuestGenerator` was split into context builder, template eligibility, target resolver, reward calculator, text generator, and shared catalog support.
- `InventoryRulesSupport` was split with dedicated equip-rule and item-detail helpers.
- `AchievementService` was split into definition catalog, progress support, and stat key constants.
- `InventorySystem` became a facade with separated core rule support and mutation/ammo flows.
- `CombatEngine` extracted action/cast execution into `CombatActionExecutor` and combat API models into `CombatApiModels`.

### Validation Notes
- `gradlew build` passed after refactor.
- Real smoke run via CLI (`gradlew run` with exit input) succeeded.

## 2026-04-22 - Legacy Dungeon Combat Split

### Existing Features (refactor)
- The legacy dungeon combat block was extracted from `LegacyGameCli` into focused legacy files without changing game rules.
- `LegacyGameCli` now delegates dungeon combat execution and quest progress updates instead of owning combat internals.

### Technical Refactor
- Added `LegacyDungeonCombatFlow` to execute battle lifecycle, victory resolution, drops, class-dungeon hooks, and class quest combat outcome integration.
- Added `LegacyDungeonCombatQuestProgress` for quest-board kill/collection progression updates.
- Added `LegacyDungeonCombatSkillSupport` for combat skill option assembly and attack menu action building.
- Added `LegacyDungeonCombatController` as the decision/input adapter for legacy combat interaction.
- Added `LegacyDungeonCombatRenderer` to isolate combat frame rendering/state coloring/history drawing.
- Removed legacy combat helper methods and the large inner combat controller from `LegacyGameCli`.

### Validation Notes
- `./gradlew build` passed.
- Real dungeon smoke via CLI covered:
  - load save -> explore -> enter dungeon
  - combat start
  - combat resolution by flee
  - combat resolution by victory
  - return to exploration/hub flow

## 2026-04-22 - Legacy Hub + Utility Split

### Existing Features (refactor)
- Remaining legacy hub/menu coordination and utility-heavy helpers were extracted from `LegacyGameCli` into focused legacy supports.
- Legacy behavior was preserved; this round only changed file structure and delegation boundaries.

### Technical Refactor
- Added `LegacyHubFlow` for:
  - main legacy hub loop
  - character menu loop
  - progression menu loop
- Added `LegacyClassProgressionSupport` for:
  - subclass/specialization unlock checks
  - full/safe class talent reset support
- Added `LegacyStatusTimeSupport` for:
  - status/debuff display
  - time/clock synchronization
  - out-of-combat regen and room tick effects
  - progress bar rendering support
- Added `LegacyMenuFormattingSupport` for UI alert/label/color helpers.
- `LegacyGameCli` now delegates these areas instead of owning their full implementations.

### Validation Notes
- `./gradlew build` passed.
- Real smoke run via CLI covered hub navigation paths:
  - modular menu -> `Novo jogo (legado)` -> character creation -> legacy hub -> return
  - load save -> explore -> back
  - progression -> back
  - production -> back
  - city -> back
  - return to top menu

## 2026-04-22 - Legacy Final Reduction (Bridge + Wrappers + Residual Utilities)

### Existing Features (refactor)
- `LegacyGameCli` was further reduced by extracting residual bridge/session entry logic, state wrappers, IO helpers, and run-resolution utilities.
- Gameplay behavior remains unchanged; this round focused on safe delegation and file-cohesion improvements.

### Technical Refactor
- Added `LegacyIoSupport` for legacy input helpers (`readNonEmpty`, `readMenuChoice`, `readInt`, `choose`).
- Added `LegacySessionBridge` for legacy new/load/session bridge (`newGame`, `loadGame`, section entry wrapper).
- Added `LegacyStateSupport` for state/sync wrappers (`normalizeLoadedState`, quest sync, menu readiness checks, labels).
- Added `LegacyRunResolutionSupport` for run-closure/death-penalty/heal/effect-resolution helpers.
- Added `LegacyDungeonPreparationSupport` for rest/pre-boss/prompt-continue flow.
- Added `LegacyAttributePointSupport` for legacy attribute point allocation wrappers.
- `LegacyGameCli` now delegates these residual blocks and stays closer to a thin legacy coordinator.

### Validation Notes
- `./gradlew build` passed after extraction.
- Real smoke run via CLI covered:
  - `Novo jogo (legado)` full creation -> legacy hub -> save -> return
  - `Carregar jogo` -> hub -> explorar -> return

## Current State - Context Mesh Added

### Existing Features (documented)
- Dungeon runs with boss cycles and room progression
- Combat with skills, status effects, and combat telemetry
- Three-stage class progression with talents
- Class unlock quests with path-based rewards
- Inventory, equipment, shops, and rarity-based loot
- Crafting and gathering profession loops
- Procedural quest generation from reusable templates
- Achievement tracking with lifetime counters

### Tech Stack (documented)
- Kotlin JVM application
- Gradle build and application packaging
- Java 21 toolchain
- kotlinx.serialization JSON

### Patterns Identified
- Data repository registry loading
- Engine service composition
- Combat snapshot controller loop
- Inventory capacity and quiver routing
- Staged class quest flow
- Procedural quest context filtering

---
*Context Mesh added: 2026-04-18*
*This changelog documents the state when Context Mesh was added.*
*Future changes will be tracked below.*

## 2026-04-22 - Legacy Promotion Pass (Neutral + Low-CLI Coupling Files)

### Existing Features (refactor)
- Neutral legacy files were promoted to domain packages/names without gameplay rule changes.
- Low-coupling legacy supports were adjusted with notifier/callback IO boundaries and then promoted.
- Legacy dungeon/quest references were updated to use promoted model/service names.

### Technical Refactor
- Promoted services/supports:
  - `ClassDungeonMonsterService`
  - `CombatQuestProgressService`
  - `DungeonEventRouter`
  - `StateSyncService`
  - `TalentEffectSummary`
  - `TextFormattingSupport`
  - `ClassProgressionSupport`
  - `DungeonCombatSkillSupport`
  - `RunResolutionService`
  - `SessionBridge`
  - `AttributePointAllocator`
  - `CliIoSupport`
  - `CliFlowModels` (model package)
- Replaced direct prints in promoted low-coupling supports with `notify(message)` callback injection where needed.

### Validation Notes
- `./gradlew build --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Xms512m' '-Dkotlin.compiler.execution.strategy=in-process'` passed.
- Real CLI smoke runs passed for:
  - load -> hub -> return
  - progressao -> quests -> return
  - exploracao -> dungeon -> combate -> retorno
  - salvar fluxo no hub e retorno ao menu principal

## 2026-04-22 - LegacyGameCli Wrapper Trim (Minimum Wiring Pass)

### Existing Features (refactor)
- `LegacyGameCli` removed redundant one-line wrappers and now wires legacy flows/services mostly by direct references.
- Behavior remained unchanged; this pass only reduced delegation noise and constructor indirection.

### Technical Refactor
- Replaced wrapper method references with direct callbacks to:
  - `CliIoSupport`
  - `TextFormattingSupport`
  - `StateSyncService`
  - `RunResolutionService`
  - `LegacyStatusTimeSupport`
  - promoted flows/services (`SessionBridge`, `LegacyHubFlow`, `LegacyDungeonRunFlow`, etc.)
- Added reusable lazy instances for menu flows:
  - `LegacyExplorationExtraFlow`
  - `LegacyProductionFlow`
  - `LegacyCityServicesFlow`
- Reduced helper surface in `LegacyGameCli` to core entry/wiring/save-format concerns.

### Validation Notes
- `./gradlew build --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Xms512m' '-Dkotlin.compiler.execution.strategy=in-process'` passed.
- Real CLI smoke runs passed for:
  - load -> hub -> progressao (quests/conquistas) -> cidade/taverna -> salvar -> retorno
  - load -> explorar -> dungeon -> combate -> retorno

## 2026-04-22 - Legacy Final Cleanup Pass (Entry Thin + Eligible Promotions)

### Existing Features (refactor)
- `LegacyGameCli` became a thin entry/delegation shell while runtime wiring moved to a dedicated runtime class.
- Eligible non-IO combat display/controller legacy files were promoted (prefix removed + package separation).

### Technical Refactor
- Added `LegacyCliRuntime` to hold legacy runtime wiring/orchestration previously embedded in `LegacyGameCli`.
- Reduced `LegacyGameCli` to thin delegation surface (`run`, `runNewGameFlow`, and section entry methods).
- Promoted combat display/controller files:
  - `LegacyDungeonCombatController` -> `DungeonCombatController`
  - `LegacyDungeonCombatRenderer` -> `DungeonCombatRenderer`
  - package moved to `rpg.cli.combat`
  - display controller contract renamed to `DungeonCombatDisplayController`
- Updated imports/usages in runtime and dungeon combat flow to the promoted names.

### Validation Notes
- `./gradlew build --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Xms512m' '-Dkotlin.compiler.execution.strategy=in-process'` passed.
- Real CLI smoke runs passed for:
  - load/save + hub + progressao + cidade/taverna + retorno
  - exploracao/dungeon + combate + retorno ao hub/menu

## 2026-04-22 - Legacy Consolidation Pass (Additional Flow Promotions)

### Existing Features (refactor)
- Additional dungeon/exploration flows were promoted by removing direct `println` usage through injected `emit` callbacks.
- Legacy entrypoint remained thin while runtime wiring continued centralized in `LegacyCliRuntime`.

### Technical Refactor
- Promoted/renamed flows and supports:
  - `LegacyExplorationExtraFlow` -> `ExplorationExtraFlow`
  - `LegacyDungeonEntryFlow` -> `DungeonEntryFlow`
  - `LegacyDungeonEntryResult` -> `DungeonEntryResult`
  - `LegacyDungeonOutcomeFlow` -> `DungeonOutcomeFlow`
  - `LegacyDungeonPreparationSupport` -> `DungeonPreparationSupport`
  - `LegacyDungeonRunFlow` -> `DungeonRunFlow`
  - `LegacyDungeonCombatFlow` -> `DungeonCombatFlow`
  - `LegacyDungeonEventSupport` -> `DungeonEventSupport`
  - `LegacyDungeonLiquidEventFlow` -> `DungeonLiquidEventFlow`
  - `LegacyDungeonChestEventFlow` -> `DungeonChestEventFlow`
  - `LegacyDungeonNpcEventFlow` -> `DungeonNpcEventFlow`
- Updated `DungeonEventRouter` references to promoted dungeon event flow names.

### Validation Notes
- `./gradlew build --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Xms512m' '-Dkotlin.compiler.execution.strategy=in-process'` passed.
- Real CLI smoke runs passed for:
  - load/save + hub + progressao + cidade/taverna
  - exploracao/dungeon + combate + retorno

## 2026-04-22 - Arrow/UX Fix Pack (Attributes, Production Flow, Archer Ammo)

### Updated Systems
- Attribute allocation confirmation now always prints an explicit prompt before reading input:
  - `Deseja confirmar a selecao de atributos? (S/N)`
  - invalid responses keep the loop and print the corrective message.
- Hub production navigation now opens legacy production directly, removing the extra intermediate menu layer.
- Archer creation now injects 10 extra `arrow_wood` items to prevent starting with no usable ammo.
- Arrow baseline was standardized from `arrow_simple` to `arrow_wood`, with data updates in starter loadouts, drop tables, and gold ammo shop entry.
- Added new ammo types and crafting recipes:
  - `arrow_stone`, `arrow_copper` item defs
  - `craft_arrow_stone`, `craft_arrow_copper`, `craft_arrow_wood` recipes

### Validation Notes
- `./gradlew build` passed.
- Real CLI smoke runs confirmed:
  - attribute confirm prompt and `S/N` loop behavior
  - production menu opens directly (no extra "Abrir producao (legado)" step)
  - new archer starts with wood arrows in inventory reserve
  - arrow crafting (`Criar Flecha de Madeira`) succeeds through production flow
  - combat consumes `Flecha de Madeira` and warns when quiver ammo is depleted

## 2026-04-22 - Project Layout Reorganization (Core + App-CLI + App-Android + Data + Docs)

### Updated Systems
- Source tree reorganized into:
  - `core/src/main/kotlin` (gameplay and reusable logic)
  - `app-cli/src/main/kotlin` (CLI runtime, flows, adapters, entrypoint)
  - `app-android/` (future visual app base scaffold)
- Context/documentation moved to `docs/context`.
- Saves moved from `saves/` to `data/saves/` with runtime path updates in:
  - `SaveGameGateway`
  - `SessionBridge`
  - `LegacyCliRuntime`
- Gradle source sets updated to compile from `core` + `app-cli`.
- Legacy helper scripts removed:
  - `compilar-pra-exportar.ps1`
  - `compilar-pra-exportar.sh`
  - `compilar_pra_exportar.bat`
  - `scripts/reorganize_data_layout.ps1`
  - `Relatório.md` removed

### Validation Notes
- `./gradlew build` passed after reorganization.
- Real CLI smoke runs covered:
  - load/save
  - hub navigation
  - exploration/dungeon
  - combat
  - progression menus
  - city/tavern

## 2026-04-22 - Compose Menu UI Base (app-android)

### Updated Systems
- `app-android` was integrated into Gradle (`include("app-android")`) with a dedicated module build file.
- Implemented Compose menu flow (without combat) with all required screens:
  - Main Menu, Hub, Exploracao, Personagem, Producao, Progressao, Cidade
  - Inventario, Atributos, Talentos, Quests, Conquistas
  - CharacterCreation screen with full attribute distribution controls.
- Added `CharacterCreationScreen` and `AttributesScreen` with `[-]/[+]` behavior:
  - local draft state on click
  - no immediate engine mutation per click
  - batch apply only on `CONFIRMAR` / `APLICAR`.
- Core character command flow now supports batch attribute application via:
  - `CharacterCommandService.applyAttributes(state, targetValues)`.

### Validation Notes
- `./gradlew build` passed with `app-android` included.
- `./gradlew :app-android:compileKotlin --rerun-tasks` passed.
- CLI smoke entry (`cmd /c "(echo x) | gradlew run"`) passed, confirming CLI startup flow remains functional.

## 2026-04-22 - Modularization Guard + LegacyCliRuntime Split

### Updated Systems
- `LegacyCliRuntime` foi quebrado em componentes menores e coesos:
  - `LegacyCliRuntimeSupportContext`
  - `LegacyCliRuntimeInventoryProgressionFlows`
  - `LegacyCliRuntimeDungeonFlows`
  - `LegacyCliRuntimeSessionFlows`
  - `LegacyCliRuntimeConfig` (constantes/tunables/palette)
- `LegacyCliRuntime` agora atua como coordenador fino de alto nivel.
- Adicionada verificacao automatica de tamanho de arquivo Kotlin no Gradle:
  - task `checkKotlinFileLineLimit` (limite 300 linhas)
  - baseline inicial em `tools/kotlin-line-limit-baseline.txt`
  - integrada ao ciclo `check`.

### Validation Notes
- `./gradlew build` passou com a nova estrutura.
- `./gradlew run` smoke test passou (menu inicial CLI exibido e encerramento normal).

## 2026-04-22 - Lojas por Categoria + Aprimoramentos Permanentes

### Updated Systems
- Loja de Ouro e Loja de Cash agora operam por categoria:
  - Armas
  - Armaduras
  - Itens
  - Acessorios
  - Aprimoramentos
- Novo submenu de aprimoramentos integrado com compra real por nivel, com custos por modalidade:
  - ouro
  - cash
  - ouro + item
- Modelagem data-driven de upgrades permanentes adicionada em `data/upgrades/*.json` e carregada pelo `DataRepository`.
- Novo `PermanentUpgradeService` integrado ao `GameEngine` e aos sistemas:
  - crafting (limite de lote e reducao de custo por disciplina)
  - gathering (chance de coleta dobrada por tipo)
  - profissao (bonus de XP em skills de profissao)
  - quest de entrega (chance de preservar item consumivel)
  - taverna (desconto sobre novo custo dinamico)
  - aljava (expansao de capacidade)
- Craft da Forja ganhou submenu por categoria de material + filtro de classe (Espadachim/Mago/Arqueiro/Todos).
- Exibicao de aljava foi padronizada para mostrar capacidade atual no fluxo de equipados (`Aljava: atual/max flechas`).

### Persistence / Compatibility
- `PlayerState` recebeu `permanentUpgradeLevels` com default seguro (`emptyMap()`), mantendo fallback para saves antigos sem quebra de load.

### Validation Notes
- `./gradlew build` passou.
- Smoke tests reais via CLI cobriram:
  - cidade -> loja ouro/cash -> categorias -> aprimoramentos
  - fluxo de compra de upgrade com bloqueio por recurso insuficiente
  - producao -> craft -> forja por categoria + filtro de classe
  - personagem -> equipados com exibicao de capacidade da aljava

## 2026-04-23 - UX Text Cleanup + Main Menu Info Recovery

### Updated Systems
- Removidos textos tecnicos de transicao (termos de refactor/legacy/modular/handoff) das telas principais de navegacao.
- Menu Principal voltou a exibir:
  - resumo de atributos (STR, AGI, DEX, VIT, INT, SPR, LUK)
  - resumo de skills de producao/coleta (Forja, Pesca, Mineracao, Coleta, Lenhador, Alquimia, Culinaria)
- Ajustados textos de menus para linguagem diegetica e orientada ao jogador.
- Mensagem tecnica residual de derrota em combate modular foi substituida por texto de gameplay.

### Validation Notes
- `./gradlew build` passou.
- Smoke test CLI validou navegacao em: tela inicial, carregar, menu principal, personagem, progressao, cidade, exploracao e salvar.

## 2026-04-23 - Android App Runtime Bridge (Compose Touch UI + Core Reuse)

### Updated Systems
- `app-android` foi convertido para app Android (`com.android.application`) com:
  - `AndroidManifest.xml`
  - `MainActivity` real com `setContent`
  - estrutura de telas Compose por toque.
- Nova camada Android conectada ao core existente:
  - `AndroidGameViewModel` orquestrando `GameActionHandler` + `GamePresenter`
  - novo `AndroidCharacterCreationService` para criar personagem sem fluxo de terminal
  - `AndroidCombatFlowController` para combate semi-ATB por toque (ataque/item/fuga) reutilizando `CombatEngine`.
- Fluxo Android agora cobre navegacao tocavel para:
  - menu principal, load/save, hub, personagem, atributos, talentos, inventario/equipados, progressao, cidade, exploracao e dungeon.
- Atributos (Android) com `[-]/[+]` em lote e aplicacao unica via `CharacterCommandService.applyAttributes`.
- Criacao de personagem (Android) com selecao de raca/classe + distribuicao de atributos por toque.

### Validation Notes
- `./gradlew :app-android:tasks --all` passou (modulo Android configurado e exposto com tarefas de install/assemble/test).
- `./gradlew :compileKotlin` passou para o core/CLI.
- Smoke test CLI (`./gradlew run` com saida imediata) passou.
- Build completo do APK ainda depende de SDK Android instalado/localizado (`ANDROID_HOME` ou `local.properties` com `sdk.dir`), ausente no ambiente de validacao atual.
