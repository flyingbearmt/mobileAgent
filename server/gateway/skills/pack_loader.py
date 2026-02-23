from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, Optional

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


def _skillpacks_root() -> Path:
    env = os.environ.get("SKILLPACKS_ROOT")
    if env:
        return Path(env)
    return Path(__file__).resolve().parent.parent / "skillpacks"


def _read_text(p: Path) -> str:
    return p.read_text(encoding="utf-8")


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


def load_skillpack_dir(
    skill_dir: Path,
    default_source: str,
    default_editable: bool,
) -> Optional[Skill]:
    if not skill_dir.exists() or not skill_dir.is_dir():
        return None

    system_path = skill_dir / "system.txt"
    user_path = skill_dir / "user.txt"
    if not system_path.exists() or not user_path.exists():
        return None

    meta: Dict[str, Any] = {}
    meta_path = skill_dir / "meta.json"
    if meta_path.exists():
        try:
            meta = json.loads(meta_path.read_text(encoding="utf-8") or "{}")
            if not isinstance(meta, dict):
                meta = {}
        except Exception:
            meta = {}

    name = meta.get("name") if isinstance(meta.get("name"), str) else skill_dir.name
    source = meta.get("source") if isinstance(meta.get("source"), str) else default_source
    editable = meta.get("editable") if isinstance(meta.get("editable"), bool) else default_editable

    system_prompt = _read_text(system_path)
    user_template = _read_text(user_path)

    def builder(instruction: str, context: Dict[str, Any], hint: Dict[str, Any]) -> str:
        text = _render_user_prompt(user_template, instruction, context, hint)
        if "{schema_hint}" not in user_template and "Output JSON schema" not in user_template:
            text = text + "\n\n" + f"Output JSON schema (example types): {json.dumps(hint)}"
        return text

    return Skill(
        name=name,
        source=source,
        editable=editable,
        system_prompt=system_prompt,
        user_prompt_template=user_template,
        user_prompt_builder=builder,
        schema_hint=_SCHEMA_HINT,
    )


def load_all_skillpacks() -> None:
    root = _skillpacks_root()

    builtin_root = root / "builtin"
    if builtin_root.exists():
        for d in builtin_root.iterdir():
            if not d.is_dir():
                continue
            skill = load_skillpack_dir(d, default_source="builtin", default_editable=False)
            if skill is not None:
                register_skill(skill)

    custom_root = root / "custom"
    if custom_root.exists():
        for d in custom_root.iterdir():
            if not d.is_dir():
                continue
            skill = load_skillpack_dir(d, default_source="custom", default_editable=True)
            if skill is not None:
                register_skill(skill)
