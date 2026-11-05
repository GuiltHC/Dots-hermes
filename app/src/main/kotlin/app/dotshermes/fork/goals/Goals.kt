package app.dotshermes.fork.goals

import app.dotshermes.data.model.CronJob

/**
 * Фоновые цели (форк GuiltHC): цель — это обычная задача по расписанию Hermes (cronjob), которую агент
 * создаёт по нашему сообщению. Каждый запуск агент продвигается по цели и пишет в Dots только при
 * находке (иначе отвечает [SILENT], и Hermes ничего не доставляет). Сервер менять не нужно.
 */
object Goals {
    /** Метка в имени задачи и в начале её промпта: так мы отличаем цели от прочих задач по расписанию. */
    const val NAME_MARK = "🎯"
    const val PROMPT_MARK = "[Цель Dots]"

    fun isGoal(job: CronJob): Boolean =
        job.name.trimStart().startsWith(NAME_MARK) || job.prompt.trimStart().startsWith(PROMPT_MARK)

    /** Имя без метки. */
    fun title(job: CronJob): String = job.name.trimStart().removePrefix(NAME_MARK).trim().ifBlank {
        job.prompt.trimStart().removePrefix(PROMPT_MARK).trim().lineSequence().firstOrNull().orEmpty().take(60)
    }

    /** Короткое имя цели из её текста. */
    fun shortTitle(goal: String): String {
        val line = goal.trim().lineSequence().first().trim()
        return if (line.length <= 48) line else line.take(47).trimEnd() + "…"
    }

    /**
     * Сообщение агенту, которое создаёт цель. [schedule] — строка расписания Hermes («every 1h»,
     * «every day at 9am»…). [onlyFindings]: молчать, если нового нет.
     */
    fun createMessage(goal: String, schedule: String, onlyFindings: Boolean, notesId: String = "g${System.currentTimeMillis() / 1000}"): String {
        val title = shortTitle(goal)
        val report = if (onlyFindings) {
            "Если нового и важного для меня нет — ответь ровно [SILENT] (тогда я ничего не получу). " +
                "Если есть находка — начни ответ с «🎯 Находка:» и коротко опиши её, со ссылками."
        } else {
            "В конце каждого запуска коротко отчитайся, начав ответ с «🎯 $title:»."
        }
        return buildString {
            appendLine("🎯 Новая фоновая цель.")
            appendLine("Цель: ${goal.trim()}")
            appendLine()
            appendLine("Создай задачу по расписанию (инструмент cronjob):")
            appendLine("- имя: «$NAME_MARK $title»")
            appendLine("- расписание: $schedule")
            appendLine("- доставка: сюда, в Dots")
            appendLine("- промпт задачи (дословно):")
            appendLine("---")
            appendLine("$PROMPT_MARK ${goal.trim()}")
            appendLine("Ты работаешь над этой целью в фоне, по расписанию. Каждый запуск: продвинься по цели " +
                "(поищи, проверь, сравни). Сначала прочитай свои заметки ~/.hermes/goals/$notesId.md (создай файл, если его нет), " +
                "в конце допиши туда дату, что проверил и что нашёл, чтобы не повторяться. $report")
            appendLine("---")
            append("После создания ответь одной строкой: что создал и когда первый запуск.")
        }
    }

    fun deleteMessage(job: CronJob): String =
        "🎯 Удали задачу по расписанию с id ${job.id} (цель «${title(job)}»). Заметки цели не трогай. Ответь одной строкой."
}

/** Частота проверки цели: подпись в интерфейсе и строка расписания Hermes. */
enum class GoalFrequency(val schedule: String) {
    Hourly("every 1h"),
    Every3h("every 3h"),
    Daily("every day at 9am"),
    Weekly("every monday 9am"),
}
