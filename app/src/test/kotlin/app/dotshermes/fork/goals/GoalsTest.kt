package app.dotshermes.fork.goals

import app.dotshermes.data.model.CronJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsTest {
    private fun job(name: String, prompt: String = "") =
        CronJob("abc123", name, "every 60m", "scheduled", true, null, null, null, prompt)

    @Test fun recognisesGoalsByNameOrPrompt() {
        assertTrue(Goals.isGoal(job("🎯 Видеокарта")))
        assertTrue(Goals.isGoal(job("Видеокарта", "[Цель Dots] следи за ценой")))
        assertFalse(Goals.isGoal(job("Morning email triage", "Sorts the inbox")))
    }

    @Test fun titleDropsMark() {
        assertEquals("Видеокарта", Goals.title(job("🎯 Видеокарта")))
        assertEquals("следи за ценой", Goals.title(job("", "[Цель Dots] следи за ценой\nещё")))
    }

    @Test fun createMessageCarriesScheduleSilentRuleAndMarks() {
        val m = Goals.createMessage("Следи за RTX 5070 дешевле 50 000 ₽", GoalFrequency.Hourly.schedule, onlyFindings = true, notesId = "g1")
        assertTrue(m.contains("- расписание: every 1h"))
        assertTrue(m.contains("«🎯 Следи за RTX 5070 дешевле 50 000 ₽»"))
        assertTrue(m.contains("[Цель Dots] Следи за RTX 5070"))
        assertTrue(m.contains("[SILENT]"))
        assertTrue(m.contains("~/.hermes/goals/g1.md"))
        val loud = Goals.createMessage("x", GoalFrequency.Daily.schedule, onlyFindings = false)
        assertFalse(loud.contains("[SILENT]"))
    }

    @Test fun shortTitleIsTrimmed() {
        assertEquals(48, Goals.shortTitle("а".repeat(100)).length)
    }

    @Test fun deleteMessageNamesJobId() {
        assertTrue(Goals.deleteMessage(job("🎯 X")).contains("id abc123"))
    }
}
