"""Fork (GuiltHC): view and edit the agent's curated memory from the phone.

Frames (phone only, never web sessions):
  -> {"type": "memory_get"}
  -> {"type": "memory_edit", "target": "memory"|"user", "action": "add"|"replace"|"remove",
      "old": "<full entry text, for replace/remove>", "content": "<new text, for add/replace>", "ref": "..."}
  <- {"type": "memory", "memory": [...], "user": [...], "limits": {"memory": n, "user": n},
      "ref": "...", "ok": bool, "error": "..."}

Goes through Hermes' own on-disk MemoryStore (same char limits, file lock, drift guard and
injection scan as the agent's memory tool and the /memory command). The running agent sees
the change from its next session (memory enters the prompt as a frozen snapshot).
"""
from __future__ import annotations

import asyncio
from typing import Any

FRAME_TYPES = ("memory_get", "memory_edit")
TARGETS = ("memory", "user")
MAX_CONTENT = 4000


def _load_store():
    from tools.memory_tool import load_on_disk_store
    return load_on_disk_store()


def _state(store, **extra: Any) -> dict[str, Any]:
    return {
        "type": "memory",
        "memory": list(store.memory_entries),
        "user": list(store.user_entries),
        "limits": {"memory": int(store.memory_char_limit), "user": int(store.user_char_limit)},
        **extra,
    }


def _edit_sync(frame: dict[str, Any], load=_load_store) -> dict[str, Any]:
    ref = frame.get("ref")
    target = str(frame.get("target") or "")
    action = str(frame.get("action") or "")
    old = str(frame.get("old") or "")
    content = str(frame.get("content") or "")[:MAX_CONTENT]
    store = load()
    if target not in TARGETS or action not in ("add", "replace", "remove"):
        return _state(store, ref=ref, ok=False, error="bad_request")
    if action == "add":
        result = store.add(target, content)
    elif action == "replace":
        result = store.replace(target, old, content, matched_entry=old or None)
    else:
        result = store.remove(target, old, matched_entry=old or None)
    ok = bool(result.get("success"))
    fresh = load()  # what is really on disk now
    return _state(fresh, ref=ref, ok=ok, error=None if ok else str(result.get("error") or "failed"))


def _get_sync(frame: dict[str, Any], load=_load_store) -> dict[str, Any]:
    return _state(load(), ref=frame.get("ref"), ok=True)


async def handle(frame: dict[str, Any]) -> dict[str, Any]:
    """Reply frame for a memory_* request; errors become an ok=false reply, never an exception."""
    fn = _get_sync if frame.get("type") == "memory_get" else _edit_sync
    try:
        return await asyncio.to_thread(fn, frame)
    except Exception as exc:  # store unavailable (older Hermes, disabled memory…)
        return {"type": "memory", "memory": [], "user": [], "limits": {}, "ref": frame.get("ref"),
                "ok": False, "error": f"unavailable: {type(exc).__name__}"}
