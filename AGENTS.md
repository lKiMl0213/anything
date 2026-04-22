# AGENTS.md

## Setup Commands
- Install/build: `./gradlew build`
- Dev/run (CLI atual): `./gradlew run`
- Test: `./gradlew test` (o repo segue sem testes configurados)
- Package: `./gradlew packageWindowsPortable`

## Code Style
- Mantenha conteudo de gameplay data-driven em `data/*.json` quando o dominio ja suportar.
- Prefira `Engine`, `Service` ou `System` focados em vez de concentrar regras em CLI.
- Preserve copy PT-BR para o jogador.
- Em `app-cli`, mantenha `println/readLine` restritos a camada textual.

## Context Policy

Default behavior:
- Nao carregar tudo em `docs/context/`.
- Tratar `docs/context/` como memoria seletiva, nao preload obrigatorio.
- Ler codigo primeiro para bugs locais e refactors pequenos.

Carregar `docs/context/` apenas quando ajudar:
- trabalho amplo/cross-session;
- mudancas de arquitetura/estrutura;
- quando decisao/padrao/changelog antigo realmente importar;
- handoff/resumo antes de compactacao.

Preferred load order:
1. `AGENTS.md`
2. `@docs/context/intent/project-intent.md` (se precisar framing de produto)
3. arquivos especificos em `@docs/context/decisions/*`, `@docs/context/knowledge/patterns/*`, `@docs/context/intent/feature-*.md`
4. `@docs/context/evolution/working-memory.md` apenas para retomada/handoff

Never:
- carregar arvore inteira de `docs/context/` por padrao;
- recarregar contexto irrelevante "por garantia";
- tratar contexto antigo como mais forte que o codigo.

## Before Context Compaction Or Handoff
- Persistir apenas informacao duravel e de alto sinal em `docs/context/`.
- Atualizar primeiro o arquivo mais especifico:
  - feature (comportamento ao usuario),
  - decision (escolha tecnica),
  - pattern (guia reutilizavel),
  - `docs/context/evolution/changelog.md` (marcos).
- Usar `docs/context/evolution/working-memory.md` so para historico parcial realmente util.

## Project Structure
```text
root/
|-- AGENTS.md
|-- app-cli/
|   `-- src/main/kotlin/rpg/cli/...
|-- app-android/
|   `-- src/main/kotlin/rpg/android/...
|-- core/
|   `-- src/main/kotlin/rpg/{engine,combat,quest,inventory,...}
|-- data/
|   |-- classes/
|   |-- subclasses/
|   |-- specializations/
|   |-- talent_trees/
|   |-- items/
|   |-- item_templates/
|   |-- drop_tables/
|   |-- quest_templates/
|   `-- saves/
`-- docs/
    `-- context/
```

Data layout note:
- Registries JSON em `data/` sao carregados recursivamente.
- Prefira organizacao por dominio e hierarquia gameplay.
- Conteudo de classe deve refletir `base -> segunda classe -> especializacao`.

## AI Agent Rules

### Always
- Separar concerns de feature/decision/pattern.
- Usar `docs/context/` apenas quando relevante.
- Atualizar contexto apos mudancas duraveis e futuras.

### Never
- Colocar detalhe tecnico em arquivo de feature.
- Ignorar decisao relevante sem atualizar/substituir.
- Criar notas de contexto verbosas que duplicam o codigo.
- Deixar memoria desatualizada apos mudanca estrutural grande.

### After Any Meaningful Changes
- Atualizar contexto afetado somente se for duravel/futuro-relevante.
- Atualizar `docs/context/evolution/changelog.md` em mudancas de estado do projeto.
- Em trabalho com retomada futura, salvar checkpoint curto e util.

## Definition Of Done
- [ ] Codigo e dados sao a fonte primaria da tarefa.
- [ ] Apenas contexto relevante foi carregado.
- [ ] Conhecimento duravel foi escrito em `docs/context/` quando necessario.
- [ ] Comandos de validacao aplicaveis foram executados.
- [ ] Changelog atualizado quando o estado do projeto mudou materialmente.
