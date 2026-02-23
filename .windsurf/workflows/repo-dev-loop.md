# MobileAgent Repo Workflow (Plan-first)

## When to use

Use this workflow for any new feature, bugfix, or change request in this repo.

Goal order:
1. Confirm the user request (what “done” means).
2. Compare the request to existing docs (`docs/PROJECT_PLAN.md`, `docs/PRACTICAL_PLAN.md`).
3. Update the plan docs first.
4. Implement code changes guided by the updated plan.
5. Verify locally and summarize.

## Inputs you should always ask for (if missing)

- Target platform:
  - Android emulator
  - Android real device (USB + adb reverse)
- Target gateway:
  - `server/mock_gateway` (port 8000)
  - `server/gateway` (port 8001, Ollama)
- Acceptance criteria:
  - API behavior
  - UI behavior
  - Safety constraints for actions

## Step 1: Re-state the request as testable acceptance criteria

Write a short checklist:
- **Behavior**: what the user should observe in the app/server.
- **Contract**: which endpoints / JSON schema fields are involved.
- **Safety**: whether any action requires confirmation (default: yes).

## Step 2: Map the authoritative implementation points

Read these files first (or the closest equivalents):

- Android entry + context:
  - `client/.../entry/ShareEntryActivity.kt`
  - `client/.../entry/ClipboardEntryActivity.kt`
  - `client/.../context/ContextCollector.kt`
  - `client/.../intent/IntentRuleEngine.kt`
- Android async task loop:
  - `client/.../agent/AgentApi.kt`
  - `client/.../task/TaskPollWorker.kt`
  - `client/.../notify/TaskNotification.kt`
  - `client/.../ui/ResultViewerActivity.kt`
- Gateway task + LLM:
  - `server/gateway/main.py`
  - `server/gateway/skills/*`

Then build a quick data-flow note:
- Entry -> instruction/context -> `POST /v1/tasks` -> poll -> notify -> result viewer -> (optional) action confirm -> Intent.

## Step 3: Audit the plan docs against reality (before coding)

For each milestone/spec item, mark:
- **Already implemented** (point to file paths)
- **Partially implemented** (what’s missing)
- **Not implemented** (and whether it’s still desired)

Update plan docs first:
- `docs/PROJECT_PLAN.md`
- `docs/PRACTICAL_PLAN.md`

Rules:
- Prefer adding a small “Current status” section over rewriting the whole doc.
- Ensure API contract matches real server code (request/response fields and types).

## Step 4: Decide the minimal code change set

Turn the updated plan into a small set of code tasks:
- **API contract changes** (server + client)
- **UX changes** (entry UI, result UI, confirmation flows)
- **Reliability changes** (persistence, retries, timeouts)

Keep changes incremental and always maintain backward compatibility unless the plan explicitly bumps a version.

## Step 5: Implement and keep the loop tight

Recommended ordering:
1. Server contract/behavior (so you can test with curl)
2. Android networking + parsing
3. UI/UX polish
4. Reliability

## Step 6: Verify locally (commands)

### 6.1 Run mock gateway (8000)

From `server/mock_gateway/`:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### 6.2 Run Ollama gateway (8001)

Prereq: Ollama running (default base url `http://127.0.0.1:11434`).

From `server/gateway/`:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8001
```

### 6.3 Emulator vs real device networking

- Emulator: use `http://10.0.2.2:8001` (or `:8000`).
- Real device: use adb reverse and keep USB connected:

```bash
adb reverse tcp:8001 tcp:8001
adb reverse tcp:8000 tcp:8000
```

### 6.4 Smoke test endpoints

```bash
curl http://127.0.0.1:8001/health
curl -X POST http://127.0.0.1:8001/v1/tasks \
  -H 'content-type: application/json' \
  -d '{"instruction":"Summarize this: hello","context":{"sharedText":"hello"},"capabilities":{}}'
```

## Step 7: Close out

Deliver a short report:
- **What changed in plan** (files + key bullets)
- **What changed in code** (files + key bullets)
- **How to verify** (commands + expected output)
- **Known gaps** (explicit TODOs)

