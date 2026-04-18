# AGENTS.md

## Setup Commands
- Install/build: `./gradlew build`
- Dev/run: `./gradlew run`
- Test: `./gradlew test` (the repo currently has no test sources configured)
- Package: `./gradlew packageWindowsPortable`

## Code Style
- Keep gameplay content data-driven in `data/*.json` when the domain already supports it.
- Prefer focused `Engine`, `Service`, or `System` classes instead of pushing more rules into `GameCli`.
- Use Kotlin data classes for serialized models under `src/main/kotlin/rpg/model`.
- Preserve the CLI-first UX and PT-BR player-facing copy.
- Follow documented patterns only when they are relevant to the task.

## Context Policy

Default behavior:
- Do not bulk-load `context/`.
- Treat `context/` as selective memory, not mandatory preload.
- Prefer reading the code first for local bugs, balance tweaks, and narrow refactors.

Load `context/` only when it helps:
- Broad or cross-session work
- Architecture or data-structure changes
- When a prior decision, pattern, or changelog entry is likely to matter
- When handing work off or resuming after a long gap

Preferred load order:
1. `AGENTS.md`
2. `@context/intent/project-intent.md` only if the task needs product/project framing
3. Specific `@context/decisions/*.md`, `@context/knowledge/patterns/*.md`, or `@context/intent/feature-*.md` only for the subsystem being changed
4. `@context/evolution/working-memory.md` only when resuming, handing off, or compacting context

Never:
- Load the whole `context/` tree by default
- Reload unrelated feature/decision/pattern files "just in case"
- Treat stale context as more authoritative than the code

## Before Context Compaction Or Handoff
- Persist only durable, high-signal information into `context/` before compacting the IDE/context window.
- Prefer updating the most specific existing file first:
  - feature file for user-visible behavior
  - decision file for technical choices
  - pattern file for reusable implementation guidance
  - `context/evolution/changelog.md` for milestone-level state
- Use `context/evolution/working-memory.md` only for concise partial history that matters later and does not fit better elsewhere.
- Keep notes short, factual, and future-useful to avoid token waste.

## Project Structure
```text
root/
|-- AGENTS.md
|-- context/
|   |-- .context-mesh-framework.md
|   |-- intent/
|   |-- decisions/
|   |-- knowledge/
|   |-- agents/
|   `-- evolution/
|-- src/main/kotlin/rpg/
|   |-- cli/
|   |-- engine/
|   |-- combat/
|   |-- classquest/
|   |-- talent/
|   |-- inventory/
|   |-- item/
|   |-- economy/
|   |-- quest/
|   |-- crafting/
|   |-- gathering/
|   |-- achievement/
|   `-- ...
`-- data/
    |-- classes/
    |-- subclasses/
    |-- specializations/
    |-- talent_trees/
    |-- items/
    |-- item_templates/
    |-- drop_tables/
    |-- quest_templates/
    `-- ...
```

Data layout note:
- JSON registries under `data/` are loaded recursively.
- Prefer organizing content by domain and gameplay hierarchy instead of keeping large flat folders.
- Class-line content should mirror `base -> second class -> specialization` where applicable.

## AI Agent Rules

### Always
- Keep feature, decision, and pattern concerns separated.
- Use `context/` only when it is relevant to the current task.
- Update `context/` after meaningful changes that future work would benefit from remembering.

### Never
- Put technical implementation details inside feature files.
- Ignore a relevant documented decision without updating/superseding it.
- Create verbose context notes that duplicate the code.
- Leave stale memory in `context/` after major technical changes.

### After Any Meaningful Changes
- Update the affected context files only if the change is durable and future-relevant.
- Update `context/evolution/changelog.md` for notable project-state changes.
- If the work may be resumed later, checkpoint only the important summary into `context/`.

## Definition Of Done
- [ ] Code and data are the primary source of truth for the task.
- [ ] Only relevant context files were loaded.
- [ ] Durable new knowledge was written back into `context/` if needed.
- [ ] Validation commands were run when applicable.
- [ ] Changelog updated when project state materially changed.

---

**Note**: This AGENTS.md now enforces selective Context Mesh usage so `context/` works as useful memory without wasting tokens.
