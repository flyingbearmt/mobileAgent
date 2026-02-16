import asyncio
import time
import uuid
from typing import Any, Dict, Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="MobileAgent Mock Gateway")


class CreateTaskRequest(BaseModel):
    client_task_id: Optional[str] = None
    instruction: str
    context: Dict[str, Any] = Field(default_factory=dict)
    capabilities: Dict[str, Any] = Field(default_factory=dict)


class TaskError(BaseModel):
    code: str
    message: str


class TaskResult(BaseModel):
    type: str = "text"
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


async def _run_task(task_id: str) -> None:
    now = int(time.time())
    task = _tasks[task_id]
    task.update({"status": "RUNNING", "stage": "LLM", "progress": 0.2, "updated_at": now})

    await asyncio.sleep(1.0)
    task.update({"stage": "TOOL_EXECUTION", "progress": 0.6, "updated_at": int(time.time())})

    await asyncio.sleep(1.0)

    instruction = task.get("instruction", "")
    shared_text = (task.get("context") or {}).get("sharedText")
    summary_target = shared_text or instruction

    task.update(
        {
            "status": "SUCCEEDED",
            "stage": "DONE",
            "progress": 1.0,
            "result": {"type": "text", "text": f"Mock result for: {summary_target}"},
            "updated_at": int(time.time()),
        }
    )


@app.post("/v1/tasks")
async def create_task(req: CreateTaskRequest) -> TaskResponse:
    task_id = f"t_{uuid.uuid4().hex}"
    now = int(time.time())

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
        now = int(time.time())
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
