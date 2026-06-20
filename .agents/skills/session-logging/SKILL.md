---
name: session-logging
description: Use when user requests conversation logging, step tracking, instruction recording, or session persistence for later review. Also use when told to save instructions/results by number+timestamp format, or when working in a .agents/sessions/ directory is expected.
---

# Session Logging

## Overview

Log every user instruction and its final result into a single file per interaction under `.agents/sessions/` using number+timestamp filenames (e.g., `001-20260620095309.md`). Each file captures the instruction at the start and the result upon completion, enabling the user to review the entire interaction in one place.

## Rule

For every user interaction (single command/request):

1. **On receiving the instruction**: Create a new file and write the instruction step.
2. **After delivering the result**: Append the result report to the same file.

This ensures a single file contains the original instruction and what was accomplished, with no separate result files.

## File Naming

```
NNN-YYYYMMDDHHMMSS.md
```

- `NNN`: 3-digit sequential number starting from `001`
- `YYYYMMDDHHMMSS`: Timestamp when the instruction was received
- Directory: `.agents/sessions/` (create if not exists)

## Format

The file is structured with the instruction block at the top, followed by a separator and the result block appended later.

### Template (initial write — when instruction is received)

```markdown
# NNN - {Brief Title}

**Time**: YYYY-MM-DD HH:MM:SS

**Task**
{User's original instruction text}
```
### Template (result append — when result is available)

Append the following after a horizontal rule (---) below the existing content:

```
---

## Result

**Time**: YYYY-MM-DD HH:MM:SS

{Concise summary of what was accomplished — no internal process, no reasoning, no step-by-step}

```

#### Example final file content:

```markdown
# 001 - Install dependencies

**Time**: 2026-06-20 09:53:09

**Task**
Please install the required npm packages and confirm the versions.

---

## Result

**Time**: 2026-06-20 09:54:23

Installed packages: react@18.2.0, express@4.18.2. Confirmed via package.json.
```

## When to Log

| Trigger | Action |
|---------|--------|
| User sends a new instruction/request | Create new file with instruction block (do NOT append to previous file) |
| Task completes with a deliverable | Append result block to the current instruction file |
| User explicitly asks for result | Append result block to the current instruction file (if not already added) |

## Common Mistakes

- ❌ Saving instruction and result in separate files for the same interaction
- ❌ Appending result to a file that does not belong to that instruction
- ❌ Including agent's internal process or step-by-step reasoning in the result block
- ❌ Overwriting or recreating the file when appending the result (always append)
- ❌ Using non-sequential numbering (always increment from last used NNN)
- ❌ Forgetting the horizontal rule separator between instruction and result