import asyncio
import json
import os
import time
import uuid
from typing import Any, Dict, Optional

import httpx
from fastapi import FastAPI
from pydantic import BaseModel, Field

import skills

app = FastAPI(title="MobileAgent Gateway (Ollama)")


class CreateTaskRequest(BaseModel):
    client_task_id: Optional[str] = None
    instruction: str
    context: Dict[str, Any] = Field(default_factory=dict)
    capabilities: Dict[str, Any] = Field(default_factory=dict)


class TaskError(BaseModel):
    code: str
    message: str


class TaskResult(BaseModel):
    type: str = "json"
    text: str


class TaskResponse(BaseModel):
    task_id: str
    status: str
    stage: str
    progress: float
    result: Optional[TaskResult] = None
    error: Optional[TaskError] = None
    created_at: int
    updated_at: int


_tasks: Dict[str, Dict[str, Any]] = {}


def _now() -> int:
    return int(time.time())


def _structured_fallback(text: str) -> Dict[str, Any]:
    return {
        "version": 1,
        "summary": text,
        "todos": [],
        "answer": None,
        "actions": [],
    }


def _validate_structured(obj: Any) -> Dict[str, Any]:
    if not isinstance(obj, dict):
        raise ValueError("not an object")
    v = obj.get("version")
    if v != 1:
        raise ValueError("unsupported version")

    if "actions" not in obj or not isinstance(obj["actions"], list):
        raise ValueError("actions must be list")

    if "todos" in obj and obj["todos"] is not None and not isinstance(obj["todos"], list):
        raise ValueError("todos must be list")

    return obj


async def _ollama_generate_structured(instruction: str, context: Dict[str, Any]) -> Dict[str, Any]:
    ollama_base_url = os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
    model = os.environ.get("OLLAMA_MODEL", "qwen2.5:7b")

    skill_name = (context.get("capabilities") or {}).get("skill")
    skill = skills.get_skill(skill_name or "general_v1")

    payload = {
        "model": model,
        "stream": False,
        "messages": skill.build_messages(instruction=instruction, context=context),
        "options": {
            "temperature": 0.2,
        },
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        r = await client.post(f"{ollama_base_url}/api/chat", json=payload)
        r.raise_for_status()
        data = r.json()

    content = (
        (data.get("message") or {}).get("content")
        or data.get("response")
        or ""
    )

    try:
        parsed = json.loads(content)
        return _validate_structured(parsed)
    except Exception:
        return _structured_fallback(content.strip() or "(empty)")


async def _run_task(task_id: str) -> None:
    task = _tasks[task_id]

    task.update({"status": "RUNNING", "stage": "LLM", "progress": 0.2, "updated_at": _now()})

    try:
        ctx = dict(task.get("context") or {})
        ctx["capabilities"] = task.get("capabilities") or {}
        structured = await _ollama_generate_structured(
            instruction=task.get("instruction", ""),
            context=ctx,
        )
        task.update(
            {
                "status": "SUCCEEDED",
                "stage": "DONE",
                "progress": 1.0,
                "result": {"type": "json", "text": json.dumps(structured, ensure_ascii=False)},
                "updated_at": _now(),
            }
        )
    except Exception as e:
        task.update(
            {
                "status": "FAILED",
                "stage": "DONE",
                "progress": 1.0,
                "result": None,
                "error": {"code": "LLM_ERROR", "message": str(e)},
                "updated_at": _now(),
            }
        )


@app.get("/health")
async def health() -> Dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/tasks")
async def create_task(req: CreateTaskRequest) -> TaskResponse:
    task_id = f"t_{uuid.uuid4().hex}"
    now = _now()

    _tasks[task_id] = {
        "task_id": task_id,
        "status": "PENDING",
        "stage": "QUEUED",
        "progress": 0.0,
        "result": None,
        "error": None,
        "created_at": now,
        "updated_at": now,
        "instruction": req.instruction,
        "context": req.context,
        "capabilities": req.capabilities,
    }

    asyncio.create_task(_run_task(task_id))

    return TaskResponse(**_tasks[task_id])


@app.get("/v1/tasks/{task_id}")
async def get_task(task_id: str) -> TaskResponse:
    task = _tasks.get(task_id)
    if not task:
        now = _now()
        return TaskResponse(
            task_id=task_id,
            status="FAILED",
            stage="DONE",
            progress=1.0,
            result=None,
            error=TaskError(code="NOT_FOUND", message="task not found"),
            created_at=now,
            updated_at=now,
        )

    return TaskResponse(**task)
