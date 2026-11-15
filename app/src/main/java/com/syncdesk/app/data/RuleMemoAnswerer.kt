package com.syncdesk.app.data

import com.syncdesk.app.domain.CallMemo
import com.syncdesk.app.domain.MeetingNote

/**
 * Answers by rule, on the device, with no network: the sentence of the memo that shares the most
 * words with the question is the answer, said by its key point where it has one. Words that only
 * ask ("언제", "했지", "what") do not count. When no sentence shares a word with the question the
 * call did not cover it, and the answer is null.
 */
class RuleMemoAnswerer : MemoAnswerer {

    override suspend fun answer(memo: CallMemo, question: String): String? {
        val asked = words(question)
        if (asked.isEmpty()) return null
        val best = memo.notes
            .map { note -> note to shared(asked, words(note.text)) }
            .filter { (_, count) -> count > 0 }
            // Of sentences that match equally well, a key point is the better answer, then the later one.
            .maxWithOrNull(compareBy<Pair<MeetingNote, Int>>({ it.second }, { if (it.first.gist != null) 1 else 0 }, { it.first.id }))
            ?.first
            ?: return null
        return best.gist ?: best.text
    }

    private fun shared(asked: Set<String>, said: Set<String>): Int = asked.count { word -> said.any { matches(word, it) } }

    /**
     * Korean attaches particles and endings to a word ("납기" is "납기를" in the sentence), so two
     * words match when one begins with the other.
     */
    private fun matches(a: String, b: String): Boolean = a == b || a.startsWith(b) || b.startsWith(a)

    /** The words of [text] that carry meaning, without what only asks or only ends a sentence. */
    private fun words(text: String): Set<String> = text.lowercase()
        .split(SEPARATORS)
        .filter { word -> word.length >= MIN_WORD_LENGTH && ASKING.none { word.startsWith(it) } && word !in ENGLISH_FILLERS }
        .toSet()

    private companion object {
        val SEPARATORS = Regex("[\\s,.!?~…·'\"]+")

        /** A shorter word matches too much: one Korean syllable begins many words. */
        const val MIN_WORD_LENGTH = 2

        /** How Korean questions are asked and how sentences end; a word beginning with one of these is left out. */
        val ASKING = listOf(
            "언제", "어디", "누가", "누구", "무엇", "무슨", "뭐", "뭘", "얼마", "어떻게", "어떤", "어느", "왜", "몇",
            "했지", "했어", "했나", "했더라", "했죠", "했습니", "한다고", "하겠", "합니", "됐지", "됐어", "되지", "된다고",
            "그랬", "말했", "였지", "였어", "였나", "인가", "인지", "거야", "거지", "건가", "있어", "있었", "있나",
            "없어", "없었", "알려", "말해", "다시", "아까", "제가", "저희",
        )

        val ENGLISH_FILLERS = setOf(
            "what", "when", "where", "who", "why", "how", "which", "did", "does", "do", "is", "are", "was", "were",
            "the", "an", "of", "to", "in", "on", "at", "for", "and", "they", "we", "it", "you", "me", "my", "say",
            "said", "tell", "about", "again", "much", "many", "will", "be", "has", "have", "that", "this", "with",
        )
    }
}
