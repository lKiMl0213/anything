# Changelog
## 2026-05-12 - Patch notes consolidado para 0.1.3

### Updated Systems
- `data/patchnotes/changelog.json` atualizado para `currentVersion: 0.1.3`.
- Entrada `0.1.3` adicionada como patch note consolidado (mudancas novas + historico relevante anterior) para exibir no popup ao abrir o app no proximo build.
- Entrada anterior `0.1.1` mantida no historico para rastreabilidade.
- Versao Android atualizada em `app-android/build.gradle.kts`:
  - `versionName = "0.1.3"`
  - `versionCode = 4`

### Validation Notes
- `./gradlew --no-daemon :app-android:compileDebugKotlin -x checkKotlinFileLineLimit` passou.

## 2026-05-12 - Premium em 2 colunas + craft com ordem fixa por nivel e lista leve

### Updated Systems
- Menu Premium (Android) reorganizado em duas colunas fixas:
  - coluna `OURO` com planos de 7/15/30 dias por ouro;
  - coluna `CASH` com 7/15/30 dias + permanente por cash;
  - titulos centralizados e botoes compactos, sem mistura entre moedas.
- Craft/Forja (e demais disciplinas de craft) com separacao explicita de responsabilidades:
  - ordem da lista fixa por `minSkillLevel` e nome (cacheada por catalogo);
  - bloqueio continua por nivel de skill;
  - craftavel (verde/vermelho) continua por materiais do inventario.
- Performance no menu de receitas:
  - `warmCraftRecipeCaches` agora aquece apenas a ordem (nao recalcula status pesado);
  - lista de receitas passou a usar caminho leve sem calculo de tempo/lote em massa;
  - tempos e lote continuam no detalhe da receita (quando o usuario abre a receita).
- Inventario nao altera a posicao dos itens na lista; altera apenas estado de craftabilidade.

### Validation Notes
- `./gradlew compileKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew :app-android:compileDebugKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew test -x checkKotlinFileLineLimit` passou.

## 2026-05-12 - Performance no menu de craft (cache de lista por disciplina/estado)

### Updated Systems
- `ProductionQueryService` agora separa:
  - ordem visivel da lista de receitas;
  - status de desbloqueio;
  - status de craftabilidade por materiais.
- Ordem do menu de receitas de craft alterada para:
  - `minSkillLevel` crescente (`nv0`, `nv1`, `nv2`, ...);
  - desempate por nome (e `id` como fallback estavel).
- Cache aplicado no menu de receitas por disciplina:
  - reutiliza lista pronta quando assinatura de estado nao muda;
  - assinatura considera inventario real, niveis de skill relevantes e limite de lote.
- Pre-warm de cache adicionado ao abrir o menu `Craft`, reduzindo latencia ao entrar em `Forja`, `Alquimia` e `Culinaria`.
- `CraftingService` passou a expor catalogo habilitado por disciplina + revisao de catalogo para invalidacao segura de cache quando a base de receitas mudar.

### Validation Notes
- `./gradlew compileKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew :app-android:compileDebugKotlin -x checkKotlinFileLineLimit` passou.
- `graphify update .` executado apos as mudancas.

## 2026-05-12 - Cash/Premium + offline regen + producao persistente + ajustes UX Android

### Updated Systems
- Android (`app-android`) recebeu ajustes de UX solicitados:
  - botoes de exclusao de save em vermelho (lixeira e confirmar excluir);
  - painel da cidade mostra `Ouro` e `Cash` abaixo de HP/MP;
  - feedback de loja sem ouro virou fluxo reutilizavel e repetivel (toast + destaque vermelho + shake sem travar apos 1 uso);
  - detalhe de receita em producao preserva linhas de ingredientes para evitar exibicao vazia.
- Producao/craft:
  - status visual de craftabilidade reforcado com fonte de verdade no core (`craftable` vindo da regra real);
  - otimizado carregamento de preview/menu para reduzir latencia ao abrir Forja/Alquimia/Culinaria;
  - timer de acao de producao passou a persistir no Android ViewModel (retoma apos troca de tela/app e em retorno de sessao quando aplicavel).
- Regen offline de HP/MP:
  - sincronizacao por timestamp (`lastClockSyncEpochMs`) aplicada em load/continue de sessao;
  - app passa a gravar sync de clock ao ir para background para catch-up consistente.
- Sistema de CASH:
  - novo fluxo `Cidade > Loja > Comprar Cash` com packs data-driven entre `R$ 5` e `R$ 200`, com taxa progressiva;
  - primeira compra concede bonus de +10%;
  - bonus `Bem-vindo de volta` (+10%) liberado apos usar bonus inicial e ficar 30 dias sem atividade elegivel.
- Sistema Premium:
  - nova loja Premium na cidade com compra por ouro (7/15/30 dias) e por cash (7/15/30/permanente);
  - ativacao temporal/permanente centralizada em `PremiumSupport`;
  - beneficios aplicados em: limites de quests/rerolls, bonus de ouro, desconto de loja, XP (combat + skills), custo/duracao de producao e runs extras de boss global.
- Missoes:
  - quests aceitaveis agora expiram em 24h;
  - limites base alinhados para 5 aceitaveis e 5 aceitas;
  - novos upgrades adicionados: capacidade de aceitaveis, reducao de refresh por nivel (min 5 min) e capacidade de aceitas (cap total 20).

### Data-driven Content
- Novos arquivos:
  - `data/cash_packs/cash_pack_mega.json`
  - `data/upgrades/upgrade_quest_acceptables_capacity.json`
  - `data/upgrades/upgrade_quest_acceptables_refresh.json`
  - `data/upgrades/upgrade_quest_accepted_capacity.json`
- Ajustes de balanceamento em packs existentes:
  - `cash_pack_small.json`, `cash_pack_medium.json`, `cash_pack_large.json`.

### Validation Notes
- `./gradlew compileKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew :app-android:compileDebugKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew test -x checkKotlinFileLineLimit` passou.
- `graphify update .` executado para atualizar `graphify-out/`.

## 2026-05-12 - Bugfix producao craft (performance + status visual de craftabilidade)

### Updated Systems
- Otimizacao de abertura de listas de craft (`Forja`, `Alquimia`, `Culinaria`):
  - `ProductionQueryService` passou a calcular snapshot de inventario uma unica vez por tela de receitas;
  - cache local de nivel de skill por tipo durante a montagem da lista;
  - remocao de recalculos duplicados de duracao/lote por receita.
- `ProductionActionDurationService` ganhou rota de resolucao por receita ja conhecida:
  - `resolveCraftFromRecipe(...)` evita nova busca de receita e novo recalculo redundante quando os dados ja estao em memoria.
- Reativado feedback visual de craft disponivel no Android:
  - receita desbloqueada e craftavel: botao verde;
  - receita desbloqueada sem materiais suficientes (inclusive parcial): botao vermelho;
  - receita bloqueada por requisito de unlock: mantem visual bloqueado atual.
- Regra de craftabilidade mantida em fonte unica de dominio:
  - `maxCraftable > 0` somente quando todos os ingredientes atendem `qtdJogador >= qtdNecessaria`.

### Validation Notes
- `./gradlew :app-android:compileDebugKotlin -x checkKotlinFileLineLimit` passou com sucesso.

## 2026-05-12 - Android UI consistente (tema/popup) + exclusao de save + bloqueio de producao por skill

### Updated Systems
- Padrao global de popup no Android consolidado:
  - removido botao `X` em `GamePopup`/`GamePopupMenu`;
  - fechamento permanece por clique fora/back;
  - clique interno nao fecha popup sem acao explicita.
- Ajustes de contraste em telas de criacao:
  - campo de nome em criacao migrado para `GameOutlinedTextField` (texto, cursor, borda e placeholder com tokens de tema claro/escuro);
  - linha de selecao atual de raca/classe movida para `GameInfoPanel` para legibilidade nos dois temas.
- Talentos (`Ver detalhes`) no Android:
  - detalhe de node passou a abrir como popup sobreposto em vez de navegar para nova tela;
  - arvore permanece como contexto de fundo.
- Carregar saves no Android:
  - popup sem `X` (padrao global);
  - adicionada exclusao por save com botao `🗑`;
  - exclusao exige confirmacao explicita (`Cancelar` / `Excluir`) e atualiza lista apos remover.
- Feedback de loja para compra sem ouro:
  - compra continua bloqueada pela regra de dominio;
  - Android agora mostra toast `Ouro insuficiente`;
  - linha de ouro no header recebe destaque vermelho temporario + animacao de erro (shake).
- Producao com desbloqueio por skill (nao por nivel do personagem):
  - `CraftingService` e `GatheringService` removeram gating por nivel do personagem para disponibilidade/listagem;
  - `ProductionQueryService` passou a expor `unlocked`/`unlockReason` por receita/no;
  - lista de producao exibe todos os itens, incluindo bloqueados;
  - itens bloqueados ficam desabilitados, com motivo `Desbloqueado no nv X de [skill]`.
- Componente reutilizavel criado:
  - `LockedContent` para encapsular estado bloqueado (estilo + motivo), reaproveitavel em producao e futuros gates.

### Validation Notes
- `./gradlew build` falhou por regra preexistente de limite de linhas em arquivos antigos de tutorial (fora do escopo desta entrega).
- `./gradlew build -x checkKotlinFileLineLimit` passou com sucesso.
- Grafo do projeto atualizado com `python -m graphify update .` (saida em `graphify-out/`).

## 2026-05-06 - Patchnotes automatico por versao no Android

### Updated Systems
- Novo fluxo de patchnotes no Android com exibicao unica por versao e por save:
  - `GameState` agora persiste `lastSeenPatchNotesVersion`.
  - `PatchNotesService` compara versao atual do changelog com a ultima versao vista no save.
  - ao carregar sessao com versao nova, Android abre popup grande/scrollavel de notas e marca como visto.
- Popup segue padrao visual existente:
  - sem botao `X`
  - fecha ao tocar fora
  - texto resumido por secoes (`Novidades`, `Melhorias`, `Correcoes`).
- Bootstrap de dados Android agora sincroniza `patchnotes/changelog.json` dos assets em inicializacoes apos update, para garantir que o changelog mais recente chegue ao dispositivo mesmo fora da primeira instalacao.

### Tooling / Automation
- Adicionada automacao de patchnotes em `tools/patchnotes/update_patchnotes.ps1`:
  - le changelog atual
  - compara mudancas por Git (baseline/tag/ref)
  - gera resumo amigavel por categorias
  - atualiza `data/patchnotes/changelog.json` sem reescrever historico inteiro.
- Novo task Gradle:
  - `./gradlew updatePatchNotes`
  - suporta propriedades opcionais:
    - `-PpatchVersion=...`
    - `-PpatchSinceRef=...`
    - `-PpatchIncludeWorkingTree=true`

## 2026-04-30 - Craft de Encantamento por Tier (Runas, Pergaminhos, Pedras +1..+15)

### Updated Systems
- Encantamento agora suporta consumo de runas por tier sem sistema paralelo:
  - `EnchantConfig` recebeu mapas data-driven de IDs por tier e bonus por tier.
  - `EnchantService` passou a selecionar runas disponiveis por prioridade de bonus e aplicar formula relativa por soma percentual consumida.
  - `EnchantValidator` e `EnchantQueryService` passaram a contar runas por conjunto de IDs (common..legendary), mantendo compatibilidade com IDs legados.
- Extracao/Fusao passaram a mapear pedras por nivel:
  - `ExtractionConfig` e `FusionConfig` agora mapeiam `+0..+15` para templates de pedra.
  - `ExtractionService` gera pedra exatamente do template correspondente ao `+X` extraido.
  - `FusionItemSupport` reconhece e gera pedras tierizadas mantendo a tag `enchant_stone`.
- Integracao de rastreamento foi ampliada para recursos tierizados:
  - `ExtractionCommandService`, `FusionCommandService`, `ProductionCommandService`, `HuntingCommandService` agora rastreiam conjuntos de IDs de runas/pergaminhos/pedras.
- Validacao de dados reforcada:
  - `DataIntegrityValidator` valida todos os IDs de encantamento por tier e garante existencia de pedra para cada nivel `+1..+15`.

### Data-driven Content
- Itens de encantamento expandidos em `data/items/materials/enchant/`:
  - runas de aprimoramento e protecao em tiers `common/uncommon/rare/epic/legendary`
  - pergaminhos de remocao e protecao em tiers `common/uncommon/rare/epic/legendary`
  - pedras `enchant_stone_tier_1` ate `enchant_stone_tier_15` (mantendo `enchant_stone` para `+0`)
- Receitas completas adicionadas em `data/crafting/` para:
  - todas as runas por tier
  - todos os pergaminhos por tier
- Drops de caca anteriormente sem uso agora foram integrados em receitas:
  - `rat_tail`, `wolf_fang`, `wolf_pelt`, `rough_hide`
- Materiais chave receberam campo `tier` para progressao de dificuldade nos crafts de encantamento.
- Configs de encantamento atualizadas:
  - `data/enchanting/system.json`
  - `data/enchanting/extraction.json`
  - `data/enchanting/fusion.json`

### Validation Notes
- Verificacao de cobertura de drops de caca em crafting retornou `ALL_USED`.
- `./gradlew build` passou com sucesso apos ajuste de limite de linhas (split de helpers em arquivos dedicados).

## 2026-04-29 - Caca + Buffs de Culinaria + Integracao de Producao/Encantamento

### Updated Systems
- Novo dominio de caca em `core/src/main/kotlin/rpg/hunting/`:
  - `HuntingConfig`, `HuntingSpot`, `HuntingDropResolver`, `HuntingService`
  - preview + execucao com custo em ouro, ganho de XP de skill, controle de rare drop e anti-farm por gap de nivel
  - duracao e rendimento influenciados por skill, nivel do spot e RNG configuravel
- Fluxo modular de caca integrado ao app/CLI:
  - novos estados de navegacao: `ProductionHuntingSpotList`, `ProductionHuntingDurationList`
  - novas acoes: `OpenHuntingMenu`, `SelectHuntingSpot`, `AttemptHunting`, `ExecuteHunting`
  - apresentacao via `HuntingScreenPresenter`
- Culinaria com buffs leves em `core/src/main/kotlin/rpg/cooking/`:
  - `CookingBuffConfig` e `CookingBuffService`
  - 1 buff ativo por vez (substituicao), duracao decrescente por tempo fora de combate
  - tipos: `HP_REGEN`, `MP_REGEN`, `DAMAGE`, `DEFENSE`, `TASK_EFFICIENCY`
  - escala de poder/duracao por dificuldade da receita e quantidade de ingredientes
- Integracoes de gameplay:
  - buffs culinarios aplicados ao usar consumiveis em combate e fora de combate
  - bonus de `TASK_EFFICIENCY` reduz tempo efetivo em atividades de producao/coleta/caca
  - novos contadores de conquistas para caca/culinaria/encantamento/fusao/extracao e recursos de encantamento
  - validacao central de integridade de dados em `DataIntegrityValidator` (IDs de itens, receitas, nodes, drops de caca e buffs)
- Dados data-driven adicionados:
  - `data/hunting/system.json`
  - spots em `data/hunting/spots/*.json`
  - `data/cooking/buffs.json`
  - receitas combinadas:
    - `data/crafting/cook_hunter_field_ration.json`
    - `data/crafting/cook_predator_broth.json`

### Validation Notes
- Correcoes de compatibilidade aplicadas:
  - import ausente em `ProductionCommandService`
  - split de `AchievementDefinitionCatalog` em arquivo auxiliar para respeitar limite de linhas
  - ajuste de carregamento de spots: `DataRepository` agora carrega `hunting/spots`, evitando parse indevido de `hunting/system.json` como spot
- Novos testes de cobertura:
  - `core/src/test/kotlin/rpg/hunting/HuntingAndCookingSystemsTest.kt`
    - drop valido + consumo de ouro na caca
    - buff culinario unico com substituicao e impacto em eficiencia de caca
    - validacao de integridade de referencias de caca/culinaria
  - `core/src/test/kotlin/rpg/enchant/EnchantSafetyMechanicsTest.kt`
    - runa de aprimoramento com formula relativa (`base * (1 + bonus)`)
    - falha com runa de protecao sem quebra e com consumo da runa
    - falha sem protecao com quebra do item
- Comandos executados com sucesso:
  - `./gradlew :test`
  - `./gradlew build`
  - smoke CLI com fluxo real:
    - `Carregar save -> Producao -> Encantamento (submenu)`
    - `Carregar save -> Producao -> Caca -> Spot -> Duracao -> execucao`

## 2026-04-29 - Fusao + Extracao de Encantamento e validacao de modificadores

### Updated Systems
- Novo fluxo modular de `Fusao` (integrado ao encantamento):
  - estados: `ProductionFusionSlot1`, `ProductionFusionSlot2`, `ProductionFusionPreview`
  - acoes: `OpenFusionMenu`, `SelectFusionSlot1`, `SelectFusionSlot2`, `AttemptFusion`, `ExecuteFusion`
  - regras centrais implementadas:
    - `base = floor((A + B) / 2)`
    - limite superior por `min(15, max(A, B) + 1)`
    - sucesso em `base` ou `base+1`
    - falha gera pedra inferior (sempre gera resultado)
  - suporte para:
    - equipamento + equipamento (mesmo template)
    - pedra + pedra
    - pedra + equipamento
- Novo fluxo modular de `Extracao` (integrado ao encantamento):
  - estados: `ProductionExtractionSlot1`, `ProductionExtractionPreview`
  - acoes: `OpenExtractionMenu`, `SelectExtractionItem`, `AttemptExtraction`, `ExecuteExtraction`
  - chance baseada na mesma base do encantamento com multiplicadores por uso de pergaminho
  - sucesso gera pedra `+X` exata
  - com protecao: item volta para `+0`
  - sem protecao: item e consumido
- Novos servicos/configs data-driven:
  - `core/src/main/kotlin/rpg/enchant/FusionService.kt`
  - `core/src/main/kotlin/rpg/enchant/ExtractionService.kt`
  - `data/enchanting/fusion.json`
  - `data/enchanting/extraction.json`
- Itens e receitas de suporte adicionados:
  - item base de pedra: `enchant_stone`
  - pergaminhos: `extract_scroll_remocao`, `extract_scroll_protecao`
  - receitas em `data/crafting/extract_scroll_*.json`
- Validacao/correcao de modificadores:
  - suite de testes integrada criada em `core/src/test/kotlin/rpg/enchant/FusionExtractionSystemsTest.kt`
  - cobertura de:
    - geracao de modificadores em drop
    - persistencia em save/load
    - exibicao no inventario
    - impacto no calculo de combate
    - presenca em craft
    - preservacao/mescla em fusao
    - extracao com pedra `+X` correta
- Ajuste de stack para materiais encantados:
  - `InventoryRuleSupport.stackKey` agora separa `MATERIAL` com `enchantLevel > 0`, evitando mistura de pedras com niveis diferentes na mesma pilha.

### Validation Notes
- `./gradlew test` passou.
- `./gradlew build` passou.

## 2026-04-29 - Sistema de Encantamento modular (+0 a +15, runas, risco de quebra)

### Updated Systems
- Novo modulo de encantamento em `core/src/main/kotlin/rpg/enchant/`:
  - `EnchantService`
  - `EnchantChanceCalculator`
  - `EnchantResult`
  - `EnchantConfig`
  - `EnchantValidator`
  - suporte interno: `EnchantItemSupport`
- Encantamento agora suporta:
  - upgrade de equipamento de `+0` ate `+15`
  - chance progressiva de sucesso por nivel
  - falha com chance de quebra (protecao opcional)
  - custo em ouro escalando com nivel do item + nivel do encantamento
  - ganho de XP de profissao por tentativa/sucesso com anti-exploit para item lixo
- Formula de runa de aprimoramento aplicada de forma relativa:
  - `newChance = baseChance * (1 + totalRuneBonus)`
- Runa de protecao integrada:
  - maximo de 1 por tentativa
  - impede quebra
  - sempre consumida
- Persistencia de encantamento adicionada em `ItemInstance`:
  - `enchantLevel`
  - `enchantBaseBonuses`
  - `enchantBasePowerScore`
- Novo skill de profissao:
  - `SkillType.ENCHANTING`
  - exibido no resumo de Producao do hub
- Fluxo modular da CLI atualizado (sem tocar no legado):
  - menu `Producao -> Encantamento`
  - lista de equipamentos encantaveis
  - detalhe com opcoes de tentativa (runas + protecao)
  - execucao temporizada via `GameEffect.LaunchProductionTimedAction`
- Novos estados/acoes de navegacao para encantamento:
  - `NavigationState.ProductionEnchantList`
  - `NavigationState.ProductionEnchantDetail`
  - acoes `OpenEnchantMenu`, `InspectEnchantItem`, `AttemptEnchantItem`, `ExecuteEnchantItem`
- Data-driven de encantamento:
  - `data/enchanting/system.json` para tabelas de chance/quebra, custos, runas e curvas
  - novos itens:
    - `enchant_rune_aprimoramento`
    - `enchant_rune_protecao`
  - novas receitas:
    - `data/crafting/enchant_rune_aprimoramento.json`
    - `data/crafting/enchant_rune_protecao.json`

### Validation Notes
- `./gradlew :compileKotlin` passou.
- `./gradlew build` passou.
- Smoke CLI modular passou em fluxo real:
  - `Main Menu -> Carregar -> Hub -> Producao -> Encantamento -> Voltar`
## 2026-04-29 - Autosave + Boss Global balance + milestones resgataveis

### Updated Systems
- Autosave centralizado no fluxo modular (`GameActionHandler` + `AutoSavePolicyService`):
  - ao entrar/sair de dungeon
  - ao entrar/sair de eventos (Global Boss e evento de dungeon)
  - ao alterar atributos
  - ao voltar para o menu principal
  - pos-combate (persistencia de encerramento de run/evento)
- Persistencia passou a manter `currentRun` no save (`SaveGameGateway.save`), evitando perda de progresso de run em autosave.
- Boss Global:
  - alerta `(!)` agora so aparece por estado real: run disponivel (free/paid pronta) ou milestone resgatavel.
  - milestones seguem fluxo manual de resgate com status, timestamp de recebimento e ordem com resgatados no final.
  - scaling de combate em `CombatMode.GLOBAL_BOSS` agora aplica multiplicadores por evento (semanal/mensal) alem da escala base global.
  - balanceamento data-driven por evento em `data/global_boss/events/*.json` (dano base reduzido, scaling aumentado, mensal mais tanque e com recompensa maior).
- `GlobalBossProgressService` foi quebrado em suportes menores para manter limite de tamanho:
  - `GlobalBossProgressCycleSupport`
  - `GlobalBossRewardScaleSupport`

### Validation Notes
- `./gradlew :compileKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew clean build` passou.
- Smokes CLI:
  - alocacao de atributo persistindo em save ativo apos retorno ao menu principal;
  - estado sem conteudo relevante sem `Eventos(!)`;
  - milestone resgatavel exibindo `(!)` e resgate manual com recompensa + timestamp;
  - comparativo de dano semanal/mensal com diferenca real apos rebalance.

## 2026-04-29 - Boss Global (ajustes de run) + Produção temporizada modular + Split de eventos

### Updated Systems
- `DungeonEventFlowCoordinator` foi dividido por responsabilidade em arquivos menores:
  - `DungeonEventPreparationService`
  - `DungeonEventResolutionService`
  - `DungeonEventOutcomeService`
- Boss Global ajustado para fluxo de run isolado de HP/MP persistido:
  - entrada em run com snapshot de combate em HP/MP cheios
  - fim da run (inclusive morte) sem sobrescrever HP/MP persistido no save/menu
  - compra de tentativas extras migrada de ouro para `CASH` (config + regra + UI)
- Produção modular voltou a usar ação temporizada com barra/timer visual no CLI:
  - nova preparação de ação temporizada (`prepareCraft` / `prepareGather`)
  - execução diferida via `GameEffect.LaunchProductionTimedAction`
  - aplicação de recompensa/progresso apenas ao final do timer
  - duração segue escala por nível de skill existente (`actionDurationSeconds`)

### Validation Notes
- `./gradlew :compileKotlin -x checkKotlinFileLineLimit` passou.
- `./gradlew build` passou após marcar `checkKotlinFileLineLimit` como não compatível com configuration cache.
- Smoke CLI validou:
  - renderização de barra/timer de Produção durante coleta
  - acesso a Boss Global com HP persistido em 0 (run inicia com HP/MP cheios)
  - morte no Boss Global sem alterar HP/MP persistido
  - compra de tentativa extra via `CASH`

## 2026-04-29 - Exportacao portatil (configuration cache) estabilizada

### Updated Systems
- `prepareWindowsPortable` deixou de gerar launchers via `doLast` (acao em script) e passou a copiar launchers estaticos de `tools/portable/`.
- Launchers versionados adicionados:
  - `tools/portable/run-anything.cmd`
  - `tools/portable/run-anything.bat`
  - `tools/portable/run-anything.ps1`
- Ajuste de empacotamento para sempre incluir `data/saves` via `from("data/saves")`, evitando condicional de configuracao.

### Validation Notes
- `./gradlew packageWindowsPortable` passou e armazenou configuration cache.
- `./exportar_portatil.bat` passou com sucesso e gerou zip versionado + `latest`.

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






## 2026-05-01 - Remocao Segura do Legacy CLI

### Updated Systems
- Legacy isolado removido de `app-cli` com exclusao de 25 arquivos (`Legacy*` e `SessionBridge`).
- Fluxo ativo preservado sem alteracao funcional: `Main.kt -> GameCli -> CliFlowController`.
- Nenhuma regra de gameplay foi alterada; remocao focada em dead code.

### Validation Notes
- Buscas em codigo-fonte ativo (`app-cli/src`, `core/src`, `app-android/src`) sem referencias restantes para `LegacyGameCli`, `LegacyCliRuntime`, `SessionBridge` e `TODO-REMOVE-LEGACY`.
- `./gradlew clean build` passou.
- Sem breaking change esperado para o CLI atual.

## 2026-05-01 - Class Quest Stage Clarity (Lv 25 / Lv 50)

### Updated Systems
- Tela de quest de classe agora exibe detalhes concretos por etapa quando o caminho esta escolhido:
  - local recomendado da instancia do caminho,
  - mobs alvo por nome (etapas de kill),
  - bosses alvo por nome (etapas de boss),
  - boss final alvo por nome (etapa final),
  - orientacao de coleta para drops da instancia.
- A melhoria usa os catálogos existentes de dungeon/path da class quest (sem sistema paralelo).

### Validation Notes
- `./gradlew build` passou.
- Smoke CLI confirmou exibicao detalhada em `Quest de Classe - Mago` com caminho `Elementalista`.
- Regra aplicada de forma generica para subclasses (nv 25) e especializacoes (nv 50), cobrindo Mago, Arqueiro e Espadachim.

## 2026-05-01 - Exploracao por Areas + Torre Infinita (UX + Progressao)

### Updated Systems
- Menu `Explorar` deixou de exibir `tierX` e passou a mostrar nomes de areas data-driven via `displayName` em `data/map_tiers/*`.
- Pools de monstros por area foram revisados para coerencia tematica e diversidade minima por mapa.
- Nova area adicionada: `Esgotos de Ferro Velho` (`sewer_depths`) para ampliar progressao entre mid/high level.
- `Torre infinita` ganhou nota dedicada no menu com:
  - maior andar historico alcancado,
  - informacao util de ciclo de boss/nota de risco.
- Melhor andar da Torre infinita agora persiste em `lifetimeStats.customCounters` usando chave `dungeon:infinite_highest_floor`.
- Dicas/textos de quest foram alinhados para o novo padrao de "areas" (substituindo referencia direta a "tiers/dungeon infinita" onde aplicavel).
- Dicas de quest de classe reforcam acesso via fluxo de areas/instancia de classe.

### Validation Notes
- `./gradlew build` passou.

## 2026-05-01 - Estabilizacao de Build Android (AGP/Kotlin/Compose)

### Updated Systems
- `app-android` voltou a compilar com plugin explicito:
  - `id("com.android.application")`
  - `kotlin("android")`
  - `id("org.jetbrains.kotlin.plugin.compose")`
- Alinhamento de toolchain/targets no Android:
  - `compileOptions` Java 17 mantido
  - `kotlin.compilerOptions.jvmTarget = JVM_17` no modulo Android
- Compatibilidade Gradle/Android ajustada:
  - AGP `9.0.1`
  - Gradle wrapper `9.3.0`
  - Kotlin plugins `2.2.10` para build scripts
- Correcao de classloader do KGP para Android:
  - adicionado `classpath("com.android.tools.build:gradle:9.0.1")` no `build.gradle.kts` raiz para manter classes AGP visiveis durante aplicacao do plugin Kotlin Android.
- Ajustes de compilacao no app Android:
  - `AndroidGameApp` passou a tratar `AndroidUiState.CharacterCreation` no `when` (sem crash por exaustividade).
  - `AndroidGameViewModel` passou a tratar `GameEffect.LaunchProductionTimedAction`, aplicando a acao de conclusao no fluxo Android.

### Validation Notes
- `./gradlew clean build --no-daemon` passou com `app-android` compilando Kotlin (`compileDebugKotlin`/`compileReleaseKotlin`) e gerando assemble debug/release.

## 2026-05-05 - Android UI Flow Stabilization (Fullscreen + Compose Screens + Timed Production + Autosave)

### Updated Systems
- Android app moved to immersive fullscreen with system bars hidden and safe-area aware layout root:
  - `MainActivity` uses edge-to-edge + hidden status/navigation bars.
  - Shared `GameScreenRoot` applies `WindowInsets.safeDrawing` + `imePadding`.
- New reusable Compose component set introduced for consistent UI structure and actions:
  - `GameScreenRoot`, `GameBackground`, `GamePrimaryButton`, `GameFooterActions`, `GamePopupMenu`, `GameInfoPanel`, `GameStatBar`, `GameTopHud`, `GameSideMenu`, `GameBottomNav`, `AttributeRow`, `EquipmentSlot`, `InventoryPanel`, `StatMiniPanel`.
- New Android screen flow implemented using existing game state/action systems (no duplicated gameplay rules):
  - `StartPage`, `NewGame`, `RaceClass`, `AttributeDistribution`, `MainHub`, `Character`, generic section menus, combat touch screen integration.
- Character creation Android flow now supports live name input, race/class selection using real repository data, and reusable attribute distribution screen with info popup.
- Production timing behavior fixed in Android flow:
  - `GameEffect.LaunchProductionTimedAction` now runs with coroutine timer/progress overlay and only applies completion action after duration.
- Android autosave behavior reinforced:
  - save requested after game-state mutations,
  - immediate save on timed production start/end,
  - lifecycle background save on `ON_STOP`.
- Placeholder background resources added for future swap without code changes:
  - `bg_menu`, `bg_new_game`, `bg_attribute_distribution`, `bg_main_hub`, `bg_character`, `bg_inventory`, `bg_production`, `bg_progression`, `bg_city`, `bg_combat`, `bg_loading`.

### Validation Notes
- `./gradlew clean build --no-daemon` passed.
- `./gradlew :app-android:assembleDebug --no-daemon` passed.
- CLI module remains buildable in the same root build (no CLI-specific rule duplication introduced).

## 2026-05-06 - Android UI/UX Polish Pass (Inventory, Shop, Talents, Combat)

### Updated Systems
- Inventory popup flow on Android now supports selling by quantity using existing core `SellInventoryItem` action:
  - quantity stepper (+/-),
  - unit value + total value shown,
  - multi-sell execution in Android ViewModel without changing core sell rules.
- Generic menu flow gained contextual action previews (Android-only) for:
  - shop buys (`BuyShopEntry`) with usage/equip slot, attributes, class tag and value,
  - permanent upgrades (`BuyUpgrade`) with description/level/cost confirmation,
  - talent nodes (`InspectTalentNode`) with quick rank-up confirmation when available.
- Production menu bug fix:
  - `ConfigureCraftRecipeQuantity` is now handled in `ProductionActionDispatcher` (no fallback message "Acao ainda nao suportada no fluxo modular").
- Combat UI readability improvements:
  - separated player/enemy resource visuals,
  - status lines rendered with compact effect icons,
  - combat log sanitization strips ANSI/control remnants (`[31m`, `[0m`, etc.) before display.
- Character/inventory visual compacting on Android:
  - reduced panel/button density tokens,
  - compact slot labels with icons,
  - item emoji labels where applicable.
- Popup behavior polish:
  - centered popup text rendering,
  - timed production overlay popup hides close `X` and keeps explicit cancel action.

### Validation Notes
- `./gradlew clean build --no-daemon` passed.
- `./gradlew :app-android:assembleDebug --no-daemon` passed.
- `./gradlew :app-android:installDebug --no-daemon` passed (installed on emulator).

## 2026-05-06 - Android Final UI/UX Stabilization (Theme/Tree/Production/Tavern)

### Updated Systems
- Build stability:
  - split `AndroidUiModelBuilders` into smaller files to satisfy `checkKotlinFileLineLimit` without baseline expansion.
- Theme and readability:
  - unified component colors with `MaterialTheme` for dark/light contrast in buttons, panels, popup, bottom nav and slots.
  - rounded corners standardized via shared UI tokens.
- Talent tree:
  - Android now consumes real prerequisite metadata from core character query data.
  - talent nodes remain clickable when blocked and display explicit prerequisite requirements in the tree cards.
- Character/inventory:
  - equipment layout kept compact/body-oriented.
  - inventory sell-by-quantity fixed to sell real stack item IDs, ensuring correct gold gain on multi-sell.
- Production:
  - production summary compacting adjusted to avoid hiding recipe context.
  - production preview popup flow now removes close `X` and keeps explicit `Cancelar` button.
  - added inventory-capacity gate in production prepare flow for craft/gather when output cannot fit (without changing CLI structure).
- Tavern and hub:
  - tavern rest/sleep keeps cost-free behavior when already full and still shows HP/MP/Ouro + debuff status.
  - hub HUD keeps compact debuff indicator (`☠`) and inventory capacity.
- Combat:
  - fixed-size effects panel to avoid layout jumps.
  - dedicated "acao pronta" indicator panel above action buttons.
  - log rendering kept sanitized and bounded.

### Validation Notes
- `./gradlew clean build --no-daemon` passed.
- `./gradlew :app-android:assembleDebug --no-daemon` passed.
- `./gradlew :app-android:installDebug --no-daemon` passed (emulator install successful).
- App launch command succeeded on emulator (`am start -n rpg.android/.MainActivity`), process remained active (`pidof rpg.android` returned PID).

## 2026-05-06 - Balanceamento de Atributos (Racas/Classes/Nivel)

### Updated Systems
- Rebalanceamento data-driven de racas em `data/races/*.json` para total liquido padrao:
  - `bonuses.attributes` padronizado para soma liquida `7` em todas as racas.
  - `growth` padronizado para soma `7` em todas as racas.
- Classes base permaneceram consistentes (sem mudanca de regras):
  - `bonuses.attributes` com soma `14` em todas as classes base.
  - `growth` com soma `0` em todas as classes base.
- Validacao automatizada adicionada:
  - novo teste `AttributeBalanceConsistencyTest` valida:
    - totais padrao de raca/classe,
    - pontos de atributo por nivel (`POINTS_PER_LEVEL = 5`),
    - distribuicao automatica de `2` pontos por nivel via `AttributeEngine.applyAutoPoints`,
    - aplicacao correta de bonus de raca+classe no `ClassSystem.totalBonuses`.

### Validation Notes
- `./gradlew clean build --no-daemon` passed.
- `./gradlew :app-android:assembleDebug --no-daemon` passed.

## 2026-05-06 - UI Compacta + Boss Global no Android

### Updated Systems
- UI Android compactada via componentes base:
  - `GameUiTokens` reduzido para densidade visual menor (painel/botao/nav).
  - `GamePanel` com largura maxima centralizada (sem ocupar 100% da tela).
  - `GameBottomNav` migrado para icones e estado visual compacto.
  - `GameButtons` recebeu `GameBackIconButton` para padronizar retorno por seta no topo esquerdo.
- Fluxo de navegacao Android ajustado:
  - telas de criacao/auxiliares (`NewGame`, `Racas/Classes`, `Distribuicao`) agora usam seta de retorno no topo.
  - `GenericMenuScreen` remove acao textual de voltar da lista e usa seta no topo.
- Home/Explorar:
  - botao de configuracoes reduzido.
  - adicionado atalho de Boss Global no lado direito da area central.
  - rodape principal (5 botoes) em icones.
- Integracao Boss Global:
  - `AndroidGameViewModel` expoe `openGlobalBoss()` chamando `GameAction.OpenGlobalBossMenu`.
  - `MainHubScreen` conecta o novo atalho diretamente ao fluxo existente.
- Apresentacao de Boss Global (sem recriar regra):
  - `GlobalBossQueryService` agora calcula e expõe tempo restante do ciclo semanal/mensal.
  - `GlobalBossScreenPresenter` mostra tempo restante no menu/detalhe do evento.
  - texto de ranking online marcado explicitamente como indisponivel no modo offline atual.

### Validation Notes
- `./gradlew clean build --no-daemon` passed.
- `./gradlew :app-android:assembleDebug --no-daemon` passed.

## 2026-05-06 - UX de Quantidade no Craft + Alertas de Quests

### Updated Systems
- Produção (craft) no Android:
  - fluxo de "Definir quantidade" agora usa popup com controle `[-] [input] [+]`.
  - input numérico com clamp automático para faixa válida (`1..CAP`).
  - cap exibido no popup e respeitando limite dinâmico (upgrade de lote + recursos disponíveis).
  - botão de confirmar da quantidade aplica `SetCraftRecipeQuantity` com valor validado.
- Produção (core/presenter):
  - `ProductionRecipeView` passou a expor `maxSelectableBatch`.
  - `ProductionQueryService` calcula esse limite via `ProductionActionDurationService` (cap real de lote).
  - `ProductionScreenPresenter` usa cap real na linha de quantidade e na ação de configurar lote.
- Progresso > Quests:
  - alerta global de quest agora considera também pool de aceitáveis com conteúdo.
  - seção `Aceitável` sinaliza alerta quando houver quests disponíveis.
  - itens individuais da lista de quests recebem estado de alerta quando acionáveis
    (aceitáveis disponíveis ou status `READY_TO_CLAIM` nas demais seções).
  - `ProgressionScreenPresenter` adiciona marcador `(!)` nas opções da lista para acionar destaque visual já usado no Android.

### Validation Notes
- `./gradlew clean build --no-daemon` passed.
- `./gradlew :app-android:assembleDebug --no-daemon` passed.
