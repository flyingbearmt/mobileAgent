from __future__ import annotations

from typing import Dict, List, Optional

from .base import Skill


_REGISTRY: Dict[str, Skill] = {}


def register_skill(skill: Skill) -> None:
    _REGISTRY[skill.name] = skill


def unregister_skill(name: str) -> None:
    _REGISTRY.pop(name, None)


def try_get_skill(name: str) -> Optional[Skill]:
    return _REGISTRY.get(name)


def get_skill(name: str) -> Skill:
    skill = _REGISTRY.get(name)
    if skill is None:
        skill = _REGISTRY.get("general_v1")
    if skill is None:
        raise ValueError("no skills registered")
    return skill


def list_skills() -> List[str]:
    return sorted(_REGISTRY.keys())


def list_skill_infos() -> List[dict]:
    items: List[dict] = []
    for name in sorted(_REGISTRY.keys()):
        s = _REGISTRY[name]
        items.append(
            {
                "name": s.name,
                "source": s.source,
                "editable": s.editable,
                "system_prompt": s.system_prompt,
                "user_prompt_template": s.user_prompt_template,
            }
        )
    return items
