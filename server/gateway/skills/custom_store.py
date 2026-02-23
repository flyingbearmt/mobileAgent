from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional

from .base import Skill
from .registry import register_skill, unregister_skill


def _store_path() -> Path:
    env = os.environ.get("SKILLS_STORE_PATH")
    if env:
        return Path(env)
    return Path(__file__).resolve().parent / "custom_skills.json"


def _render_user_prompt(template: str, instruction: str, context: Dict[str, Any], schema_hint: Dict[str, Any]) -> str:
    shared_text = context.get("sharedText")
    source_app = context.get("sourceApp")
    locale = context.get("locale")

    class _SafeDict(dict):
        def __missing__(self, key: str) -> str:
            return ""

    data = {
        "instruction": instruction,
        "shared_text": shared_text,
        "source_app": source_app,
        "locale": locale,
        "schema_hint": json.dumps(schema_hint),
        "context": json.dumps(context),
    }
    return template.format_map(_SafeDict(data))


def _custom_skill_from_record(name: str, record: Dict[str, Any]) -> Skill:
    system_prompt = record.get("system_prompt") or ""
    user_prompt_template = record.get("user_prompt_template") or ""

    schema_hint = {
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

    def builder(instruction: str, context: Dict[str, Any], hint: Dict[str, Any]) -> str:
        return _render_user_prompt(user_prompt_template, instruction, context, hint)

    return Skill(
        name=name,
        source="custom",
        editable=True,
        system_prompt=system_prompt,
        user_prompt_template=user_prompt_template,
        user_prompt_builder=builder,
        schema_hint=schema_hint,
    )


def load_custom_skills() -> None:
    path = _store_path()
    if not path.exists():
        return

    data = json.loads(path.read_text(encoding="utf-8") or "{}")
    if not isinstance(data, dict):
        return

    for name, record in data.items():
        if not isinstance(name, str) or not isinstance(record, dict):
            continue
        register_skill(_custom_skill_from_record(name, record))


def _read_store() -> Dict[str, Any]:
    path = _store_path()
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8") or "{}")
    if not isinstance(data, dict):
        return {}
    return data


def _write_store(store: Dict[str, Any]) -> None:
    path = _store_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(store, ensure_ascii=False, indent=2), encoding="utf-8")


def upsert_custom_skill(name: str, system_prompt: str, user_prompt_template: str) -> Skill:
    if not name:
        raise ValueError("name is required")
    store = _read_store()
    store[name] = {
        "system_prompt": system_prompt,
        "user_prompt_template": user_prompt_template,
    }
    _write_store(store)

    skill = _custom_skill_from_record(name, store[name])
    register_skill(skill)
    return skill


def delete_custom_skill(name: str) -> bool:
    if not name:
        return False
    store = _read_store()
    if name not in store:
        return False
    store.pop(name, None)
    _write_store(store)

    unregister_skill(name)
    return True


def get_custom_skill_record(name: str) -> Optional[Dict[str, Any]]:
    store = _read_store()
    record = store.get(name)
    if not isinstance(record, dict):
        return None
    return record


def list_custom_skill_names() -> List[str]:
    store = _read_store()
    names = [k for k in store.keys() if isinstance(k, str)]
    return sorted(names)
