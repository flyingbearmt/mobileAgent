from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional


@dataclass(frozen=True)
class Skill:
    name: str
    source: str
    editable: bool
    system_prompt: str
    user_prompt_template: Optional[str]
    user_prompt_builder: Callable[[str, Dict[str, Any], Dict[str, Any]], str]
    schema_hint: Dict[str, Any]

    def build_messages(self, instruction: str, context: Dict[str, Any]) -> List[Dict[str, str]]:
        user_prompt = self.user_prompt_builder(instruction, context, self.schema_hint)
        return [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": user_prompt},
        ]
