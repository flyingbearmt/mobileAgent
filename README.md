# MobileAgent

MobileAgent is an Android-first “mobile AI assistant” MVP that turns **system entry points** (Share Sheet / Quick Settings tile) into an **asynchronous task workflow**:

- Android collects context and sends an instruction to a Gateway.
- Gateway executes the task (Mock or real LLM via Ollama) and returns a `task_id`.
- Android polls task status in the background and delivers a notification.
- Results can be plain text or **structured JSON** (v1) with safe, user-confirmed **actions** (dial / SMS draft / calendar insert).

## Repository layout

- `client/`
  - Android app (Kotlin + Jetpack Compose)
- `server/`
  - `mock_gateway/` : mock FastAPI gateway returning text results (good for early UI/dev)
  - `gateway/` : FastAPI gateway that calls **Ollama** and returns structured JSON results
- `docs/`
  - `PROJECT_PLAN.md` : architecture + API contract (v1) + milestones
  - `PRACTICAL_PLAN.md` : practical v1 spec (structured results + actions)

## Key concepts

## Task model (v1)

Android uses a Task ID based protocol:

- `POST /v1/tasks` creates a task and returns `task_id`.
- `GET /v1/tasks/{task_id}` returns current status.

Typical lifecycle:

`PENDING` -> `RUNNING` -> `SUCCEEDED` | `FAILED`

## Structured Result (v1)

When using `server/gateway/`, the task result is returned as:

- `result.type = "json"`
- `result.text = "{...}"` (a JSON string)

Schema (v1):

```json
{
  "version": 1,
  "summary": "string | null",
  "todos": [{"text": "string", "due_at": "ISO-8601 | null"}],
  "answer": "string | null",
  "actions": [
    {
      "type": "CREATE_CALENDAR_EVENT|SEND_SMS|DIAL",
      "title": "string?",
      "start_at": "ISO-8601?",
      "end_at": "ISO-8601?",
      "to": "string?",
      "body": "string?",
      "number": "string?"
    }
  ]
}
```

Android parses this structure and:

- Displays `summary` / `todos` / `answer`
- Shows buttons for `actions`
- Requires a **second confirmation** before launching a system Intent

## Quickstart

## Prerequisites

- **Android Studio** (for the app)
- **JDK** compatible with your Android Gradle Plugin
- **Python 3.10+** (recommended) for the gateway

Optional (for real LLM):

- **Ollama** installed and running locally

## Run the server

You can run either the mock gateway (`8000`) or the Ollama gateway (`8001`).

## Option A: Mock Gateway (Fastest)

From `server/mock_gateway/`:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

## Option B: Ollama Gateway (Structured JSON results)

1. Start Ollama and pull a model (example):

```bash
ollama pull qwen2.5:7b
```

2. From `server/gateway/`:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8001
```

Environment variables:

- `OLLAMA_BASE_URL` (default `http://127.0.0.1:11434`)
- `OLLAMA_MODEL` (default `qwen2.5:7b`)

Health check:

```bash
curl http://127.0.0.1:8001/health
```

## Run the Android app

Open `client/` in Android Studio and run the `app` configuration.

Or build from CLI (from `client/`):

```bash
./gradlew :app:installDebug
```

## Networking: emulator vs real device

The app uses:

- **Emulator**: `http://10.0.2.2:8001` (or `:8000` if you run mock gateway)
- **Real device**: `http://127.0.0.1:8001`

This is controlled in `client/app/src/main/java/com/example/mobileagent/AppConfig.kt`.

Important notes:

- For a **real device**, `127.0.0.1` points to the phone itself. The intended workflow is to use **ADB reverse** while the phone is USB-connected:

```bash
adb reverse tcp:8001 tcp:8001
```

Then the phone can reach your laptop’s gateway at `http://127.0.0.1:8001`.

- For the **mock gateway** on port `8000`:

```bash
adb reverse tcp:8000 tcp:8000
```

## How to use

## Share Sheet flow

1. In any app, share a piece of text to **MobileAgent**.
2. A bottom sheet shows the suggested intent type and an editable instruction.
3. Tap **Confirm**.
4. The app creates a task and exits.
5. A notification appears when the task finishes; tap it to open the result viewer.

## Quick Settings tile (Clipboard)

The app registers a Quick Settings tile `Clipboard`.

1. Add the tile from Android’s Quick Settings editor.
2. Tap the tile to open the clipboard entry flow.

Clipboard reading is “best-effort” and subject to Android restrictions.

## API reference (v1)

## Create task

`POST /v1/tasks`

Request body:

```json
{
  "instruction": "...",
  "context": {
    "sourceApp": "...",
    "sharedText": "...",
    "timestamp": 0,
    "locale": "...",
    "deviceState": {"network": "wifi|cell|offline|other"}
  },
  "capabilities": {}
}
```

Response body (example):

```json
{
  "task_id": "t_...",
  "status": "PENDING",
  "stage": "QUEUED",
  "progress": 0.0,
  "result": null,
  "error": null,
  "created_at": 0,
  "updated_at": 0
}
```

## Get task

`GET /v1/tasks/{task_id}`

Returns the same envelope with updated `status` / `stage` / `progress` and optional `result`.

## Troubleshooting

- **No notification on Android 13+**
  - Ensure notification permission is granted (`POST_NOTIFICATIONS`). The entry activities request it.

- **Real device cannot reach the gateway**
  - Use `adb reverse tcp:8001 tcp:8001` (or `8000`) and keep USB connected.
  - Verify the gateway listens on `0.0.0.0` and the port is correct.

- **Ollama gateway returns fallback output**
  - The gateway tries to parse the model output as JSON. If parsing fails, it falls back to a minimal structured object.
  - Try a more instruction-following model or reduce temperature (see `server/gateway/main.py`).

- **Cleartext HTTP**
  - The app enables cleartext traffic (`android:usesCleartextTraffic="true"`) for local development.

## Security & safety notes

- The gateway currently has **no authentication** and should be treated as **local-dev only**.
- All Android “actions” are **user-confirmed** and implemented using safe system intents:
  - `DIAL` uses `ACTION_DIAL` (not `ACTION_CALL`)
  - `SEND_SMS` uses `ACTION_SENDTO` with `smsto:` (draft)
  - `CREATE_CALENDAR_EVENT` uses `ACTION_INSERT`

## Roadmap / plans

See:

- `docs/PROJECT_PLAN.md`
- `docs/PRACTICAL_PLAN.md`

## License

See `LICENSE`.
