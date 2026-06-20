---
name: session-logging
description: Use when user requests conversation logging, step tracking, instruction recording, or session persistence for later review. Also use when told to save instructions/results by number+timestamp format, or when working in a .agents/sessions/ directory is expected.
---

# Session Logging

## Overview

Log every user instruction step and the final result report to `.agents/sessions/` with number+timestamp filenames (e.g., `001-20260620095309.md`), enabling the user to review the conversation flow later.

## Rule

For every interaction (including the initial request), save two things:

1. **Each instruction step** — the user's directive/query (NOT the agent's response or process)
2. **Final result report** — a concise summary of what was accomplished (NO internal process details)

## File Naming

```
NNN-YYYYMMDDHHMMSS.md
```

- `NNN`: 3-digit sequential number starting from `001`
- `YYYYMMDDHHMMSS`: Timestamp when the instruction was received
- Directory: `.agents/sessions/` (create if not exists)

## Format

### Instruction Step
```markdown
# NNN - {Brief Title}

**Time** YYYY-MM-DD HH:MM:SS

**Task**
{User's original instruction text}
```

### Final Result Report
```markdown
# NNN - {Summary Title}

## Information

- **Time** YYYY-MM-DD HH:MM:SS



**Result**
{Concise summary of what was accomplished — no internal process, no reasoning, no step-by-step}
```

## When to Log

| Trigger | Action |
|---------|--------|
| User sends a new instruction/request | Save as instruction step immediately |
| Task completes with a deliverable | Save final result report |
| User explicitly asks for result | Save final result report |

## Common Mistakes

- ❌ Saving agent's own response/thinking instead of the instruction
- ❌ Including process details in the result report
- ❌ Overwriting existing session files
- ❌ Using non-sequential numbering
