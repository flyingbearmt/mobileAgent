from __future__ import annotations

import json
import re
import shutil
from pathlib import Path
from typing import Optional

from .base import Skill
from .pack_loader import _skillpacks_root, load_skillpack_dir
from .registry import register_skill, unregister_skill


_NAME_RE = re.compile(r"^[a-zA-Z0-9_.-]{1,64}$")


def _custom_dir(name: str) -> Path:
    return _skillpacks_root() / "custom" / name


def _validate_name(name: str) -> None:
    if not _NAME_RE.fullmatch(name or ""):
        raise ValueError("invalid skill name")


def upsert_custom_skillpack(
    name: str,
    system_prompt: str,
    user_prompt_template: str,
) -> Skill:
    _validate_name(name)

    d = _custom_dir(name)
    d.mkdir(parents=True, exist_ok=True)

    (d / "system.txt").write_text(system_prompt or "", encoding="utf-8")
    (d / "user.txt").write_text(user_prompt_template or "", encoding="utf-8")
    (d / "meta.json").write_text(
        json.dumps(
            {
                "name": name,
                "source": "custom",
                "editable": True,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    skill = load_skillpack_dir(d, default_source="custom", default_editable=True)
    if skill is None:
        raise ValueError("failed to load skillpack")

    register_skill(skill)
    return skill


def delete_custom_skillpack(name: str) -> bool:
    _validate_name(name)

    d = _custom_dir(name)
    if not d.exists():
        return False

    shutil.rmtree(d)
    unregister_skill(name)
    return True


def get_custom_skillpack_dir(name: str) -> Optional[Path]:
    if not _NAME_RE.fullmatch(name or ""):
        return None
    d = _custom_dir(name)
    if d.exists() and d.is_dir():
        return d
    return None
