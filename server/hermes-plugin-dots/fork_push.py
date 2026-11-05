"""Fork (GuiltHC): push the agent's FINAL text, not the streaming placeholder.

Hermes streams a reply as send("…" or a first chunk) followed by edit_message() calls. The upstream
adapter notifies on send(), so the notification showed "…". Here a message notification waits:
  * during a turn (chat busy): until on_processing_complete — only the turn's last message rings,
    with its final text (preambles like "let me check…" don't ring);
  * outside a turn (cron delivery, proactive message): until edits have been quiet for QUIET_S.
"""
from __future__ import annotations

import asyncio
from typing import Any, Callable, Optional

QUIET_S = 2.0
CURSORS = ("▉", "▌", "█", "▍", "▎", "▏", "▋", "▊")


def clean(text: str) -> str:
    text = (text or "").rstrip()
    while text and text[-1] in CURSORS:
        text = text[:-1].rstrip()
    return text


def meaningful(text: str) -> bool:
    return any(ch.isalnum() for ch in clean(text))


class DeferredPush:
    def __init__(self, notify: Callable[[str, str, str, str], None], busy: Callable[[str], bool]):
        self._notify = notify      # (kind, event_id, body, chat)
        self._busy = busy          # chat -> a turn is running
        self._pending: dict[str, dict[str, Any]] = {}  # message id -> {chat, text, timer}

    def on_send(self, event_id: str, chat: str, content: str) -> None:
        self._pending[event_id] = {"chat": chat, "text": content, "timer": None}
        if not self._busy(chat):
            self._arm(event_id)

    def on_edit(self, message_id: str, content: str, finalize: bool) -> None:
        entry = self._pending.get(message_id)
        if entry is None:
            return
        entry["text"] = content
        if finalize and not self._busy(entry["chat"]):
            self._flush(message_id)
        elif not self._busy(entry["chat"]):
            self._arm(message_id)

    def on_turn_complete(self, chat: str) -> None:
        """The turn is over: ring for its messages with their final text, in order (placeholders dropped).
        Nothing is lost: a cron finding delivered mid-turn rings here too."""
        for mid in [mid for mid, e in self._pending.items() if e["chat"] == chat]:
            self._flush(mid)

    def _arm(self, message_id: str) -> None:
        entry = self._pending[message_id]
        if entry["timer"] is not None:
            entry["timer"].cancel()
        entry["timer"] = asyncio.get_event_loop().call_later(QUIET_S, self._flush, message_id)

    def _drop(self, message_id: str) -> None:
        entry = self._pending.pop(message_id, None)
        if entry and entry["timer"] is not None:
            entry["timer"].cancel()

    def _flush(self, message_id: str) -> None:
        entry = self._pending.pop(message_id, None)
        if entry is None:
            return
        if entry["timer"] is not None:
            entry["timer"].cancel()
        if meaningful(entry["text"]):
            self._notify("message", message_id, clean(entry["text"]), entry["chat"])
