from __future__ import annotations

from typing import Dict, List

from .base import Skill


_REGISTRY: Dict[str, Skill] = {}


def register_skill(skill: Skill) -> None:
    _REGISTRY[skill.name] = skill


def get_skill(name: str) -> Skill:
    skill = _REGISTRY.get(name)
    if skill is None:
        skill = _REGISTRY.get("general_v1")
    if skill is None:
        raise ValueError("no skills registered")
    return skill


def list_skills() -> List[str]:
    return sorted(_REGISTRY.keys())
