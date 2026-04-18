# Pattern: Selective Context Checkpointing

## Description
Use this pattern to preserve only the durable, future-useful summary of a change before context compaction, handoff, or a long pause.

## When to Use
- Before compacting the IDE/context window
- Before handing work off to another agent/session
- After a meaningful architectural or data-structure change
- After a milestone where future work benefits from a short state summary

## Pattern
1. Read the modified source/data files first
2. Decide what is truly worth remembering later
3. Write the smallest useful summary into the most specific place:
   - feature file for behavior
   - decision file for technical choice
   - pattern file for reusable implementation guidance
   - changelog for milestone/state
   - working-memory only if none of the above fits well
4. Keep the note short and factual
5. Avoid duplicating code details that can be recovered cheaply from source

## Example
Good checkpoint:

```md
- Class armor progression now uses `data/item_templates/*_template.json`.
- Static armor files in `data/items/a_*`, `m_*`, `s_*` were removed.
- Class quest rewards and drop tables should target templates, not fixed armor items.
```

Bad checkpoint:

```md
- Long file-by-file diff
- Full command history
- Repeated explanation of code that already exists in source
```

## Files Using This Pattern
- [AGENTS.md](/a:/Projects/anything/AGENTS.md) - instructs agents to checkpoint only durable, high-signal information
- [context/.context-mesh-framework.md](/a:/Projects/anything/context/.context-mesh-framework.md) - defines selective loading and pre-compaction checkpoint rules
- [context/evolution/working-memory.md](/a:/Projects/anything/context/evolution/working-memory.md) - stores concise partial history when no more specific file fits

## Related
- [Decision: Selective Context Memory](../../decisions/008-selective-context-memory.md)

## Status
- **Created**: 2026-04-18
- **Status**: Active
