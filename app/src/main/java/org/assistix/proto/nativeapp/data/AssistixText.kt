package org.assistix.proto.nativeapp.data

/** Cleans ASSISTIX outputs for chat and composer tools. */
object AssistixText {
    private val translationParen =
        Regex(
            """\s*[\(\[]\s*(?:translation|перевод|in english|english|на английском|английский)\s*:[^)\]]*[\)\]]""",
            RegexOption.IGNORE_CASE,
        )
    private val loneParenLine = Regex("""^\s*[\(\[][^)\]]{2,}[\)\]]\s*$""", RegexOption.IGNORE_CASE)
    private val rolePrefix = Regex("""^(?:ASSISTIX|User|Assistant|USER)\s*:""", RegexOption.IGNORE_CASE)
    private val metaStart =
        Regex(
            """^(I apologize|I'm sorry|Let me know|I'll make sure|As an AI|Here is|Here's|Sure[,!]|Of course|Translation:)""",
            RegexOption.IGNORE_CASE,
        )

    fun forComposer(raw: String, originalDraft: String): String {
        var t = stripTranslationNotes(raw)
        if (t.isEmpty()) return t

        t = t.replace(Regex("""^(message|text|output|result|corrected|rewritten|ответ)\s*:\s*""", RegexOption.IGNORE_CASE), "")
        t = t.trim()
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("«") && t.endsWith("»"))) {
            t = t.substring(1, t.length - 1).trim()
        }

        val lines =
            t.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    line.replace(Regex("""^(message|text|output)\s*:\s*""", RegexOption.IGNORE_CASE), "").trim()
                }
                .filter { it.isNotEmpty() && !metaStart.containsMatchIn(it) && !loneParenLine.matches(it) }

        t = lines.firstOrNull() ?: t.trim()
        val cap = (originalDraft.length * 2 + 80).coerceIn(400, 2000)
        if (t.length > cap) t = t.take(cap)
        return t.trim()
    }

    fun forChat(raw: String): String {
        var t = stripTranslationNotes(raw)
        if (t.isEmpty()) return t

        val roleSplit = Regex("""\n\s*(?:ASSISTIX|User|Assistant|USER)\s*:""", RegexOption.IGNORE_CASE)
        if (roleSplit.containsMatchIn(t)) {
            t = t.split(roleSplit, limit = 2).firstOrNull()?.trim() ?: t
        }
        t = t.replace(Regex("""^(?:ASSISTIX|Assistant)\s*:\s*""", RegexOption.IGNORE_CASE), "").trim()

        val lines =
            t.lines()
                .map { it.trim() }
                .filter { line ->
                    line.isNotEmpty() &&
                        !rolePrefix.containsMatchIn(line) &&
                        !metaStart.containsMatchIn(line) &&
                        !loneParenLine.matches(line) &&
                        !looksLikeTranslationNote(line)
                }

        t = lines.take(3).joinToString("\n").trim()
        if (t.length > 320) t = t.take(320).trim()
        return stripTranslationNotes(t)
    }

    private fun stripTranslationNotes(text: String): String {
        var t = text.trim()
        repeat(4) {
            val next = t.replace(translationParen, "").trim()
            if (next == t) return t
            t = next
        }
        return t
    }

    /** e.g. "Hello" or "(Hello)" duplicate in English after Russian reply */
    private fun looksLikeTranslationNote(line: String): Boolean {
        val l = line.trim()
        if (loneParenLine.matches(l)) return true
        if (Regex("""^(?:translation|перевод)\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(l)) return true
        return false
    }
}
