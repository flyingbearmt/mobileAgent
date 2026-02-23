from __future__ import annotations

import json
from typing import Any, Dict

from .base import Skill
from .registry import register_skill


_SCHEMA_HINT: Dict[str, Any] = {
    "version": 1,
    "summary": "string|null",
    "todos": [{"text": "string", "due_at": "ISO-8601|null"}],
    "answer": "string|null",
    "actions": [
        {
            "type": "CREATE_CALENDAR_EVENT|SEND_SMS|DIAL",
            "title": "string?",
            "start_at": "ISO-8601?",
            "end_at": "ISO-8601?",
            "to": "string?",
            "body": "string?",
            "number": "string?",
        }
    ],
}


_BASE_SYSTEM_PROMPT = (
    "You are an assistant that produces a single JSON object only. "
    "Do not include markdown, code fences, or extra text. "
    "Follow the schema strictly. If you are unsure, set fields to null/empty. "
    "All actions must be safe suggestions that require user confirmation."
)


def _base_user_prompt(instruction: str, context: Dict[str, Any], schema_hint: Dict[str, Any]) -> str:
    shared_text = context.get("sharedText")
    source_app = context.get("sourceApp")
    locale = context.get("locale")
    return (
        f"Instruction: {instruction}\n"
        f"SourceApp: {source_app}\n"
        f"Locale: {locale}\n"
        f"SharedText: {shared_text}\n\n"
        f"Output JSON schema (example types): {json.dumps(schema_hint)}"
    )


def _summarize_user_prompt(instruction: str, context: Dict[str, Any], schema_hint: Dict[str, Any]) -> str:
    return (
        "Task: Summarize the shared text. "
        "Put the summary in summary. "
        "Leave todos/actions empty unless explicitly requested by the instruction.\n\n"
        + _base_user_prompt(instruction, context, schema_hint)
    )


def _extract_user_prompt(instruction: str, context: Dict[str, Any], schema_hint: Dict[str, Any]) -> str:
    return (
        "Task: Extract actionable items. "
        "Prefer filling todos and safe actions when the user intent is clear. "
        "If unsure, leave fields null/empty.\n\n"
        + _base_user_prompt(instruction, context, schema_hint)
    )


def _agent_user_prompt(instruction: str, context: Dict[str, Any], schema_hint: Dict[str, Any]) -> str:
    return (
        "Task: Answer the user's instruction using the shared text as context. "
        "Put your final response in answer. "
        "You may also provide a short summary.\n\n"
        + _base_user_prompt(instruction, context, schema_hint)
    )


register_skill(
    Skill(
        name="general_v1",
        source="builtin",
        editable=False,
        system_prompt=_BASE_SYSTEM_PROMPT,
        user_prompt_template=None,
        user_prompt_builder=_base_user_prompt,
        schema_hint=_SCHEMA_HINT,
    )
)

register_skill(
    Skill(
        name="summarize_v1",
        source="builtin",
        editable=False,
        system_prompt=_BASE_SYSTEM_PROMPT,
        user_prompt_template=None,
        user_prompt_builder=_summarize_user_prompt,
        schema_hint=_SCHEMA_HINT,
    )
)

register_skill(
    Skill(
        name="extract_v1",
        source="builtin",
        editable=False,
        system_prompt=_BASE_SYSTEM_PROMPT,
        user_prompt_template=None,
        user_prompt_builder=_extract_user_prompt,
        schema_hint=_SCHEMA_HINT,
    )
)

register_skill(
    Skill(
        name="agent_v1",
        source="builtin",
        editable=False,
        system_prompt=_BASE_SYSTEM_PROMPT,
        user_prompt_template=None,
        user_prompt_builder=_agent_user_prompt,
        schema_hint=_SCHEMA_HINT,
    )
)
