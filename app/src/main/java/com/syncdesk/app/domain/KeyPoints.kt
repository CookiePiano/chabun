package com.syncdesk.app.domain

/**
 * Picks the key point out of one transcribed utterance, for the spoken summary.
 *
 * A meeting heard while driving is summarized by rules, on the device, with no network: a
 * sentence is a key point when it carries something the driver must not forget (a date or time,
 * a quantity) together with what was decided or asked. Small talk and running the meeting
 * ("let's start with...", "same time next week") are left out. The chosen sentence is shortened
 * so it can be read in one breath: a leading reason clause is dropped when the rest still holds
 * the key facts, and the polite sentence ending gives way to the ending of a written note
 * ("납기를 11월 28일로 옮겨야 합니다" becomes "납기를 11월 28일로 옮기기").
 *
 * Built for Korean as the speech recognizer writes it (no final punctuation, sentences separated
 * by spaces), with the common English cues for the English demo text.
 */
object KeyPoints {

    /** A sentence needs at least this score to be a key point. */
    private const val THRESHOLD = 3

    private val WHEN = Regex(
        "\\d+\\s*월|\\d+\\s*일(?![이은을])|[월화수목금토일]요일|오전|오후|\\d+\\s*시(?!간)|오늘|내일|모레|" +
            "다음\\s*(주|달)|이번\\s*(주|달)|이달|주말|월말|월초|말일|까지|올해|내년|상반기|하반기|\\d\\s*분기|" +
            "(?i)\\b(today|tomorrow|tonight|next week|this week|(mon|tues|wednes|thurs|fri|satur|sun)day|" +
            "january|february|march|april|may|june|july|august|september|october|november|december|\\d{1,2}\\s*(a\\.m\\.|p\\.m\\.|am|pm))\\b",
    )
    private val HOW_MUCH = Regex(
        "\\d+(\\.\\d+)?\\s*(퍼센트|%|명|개|건|원|억|대|시간|분|곳|장|군데|번|개월)|" +
            // Amounts said the Korean way: "3만 원", "5천만 원".
            "\\d+\\s*(천|만|백)\\s*(만|억)?\\s*원|" +
            "(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*(명|개|건|대|가지|곳|군데|번|달)|하루|사흘|이틀|나흘|열흘|일주일|보름|" +
            "(?i)\\b(\\d+|two|three|four|five)\\s+(percent|people|inspectors|units|days|hours)\\b",
    )
    /** A decision is worth remembering on its own, with or without a date: "2안으로 정했습니다". */
    private val DECIDED = Regex(
        "(정했|결정했|확정했|확정됐|기로\\s*했)습니다|확정입니다|기로\\s*했어요|" +
            "(합의|승인|통과|채택|체결)(했|됐|되었)습니다|(선정|선택|압축|낙점)했습니다|" +
            // Picking an option: "A안으로 가겠습니다", "2안으로 하죠".
            "[A-Za-z0-9가-힣]안으로\\s*(가|하|정|결정)",
    )

    /** Something went up, down or late: "20% 늘었습니다", "하루 늦어질 예정입니다". */
    private val CHANGED = Regex(
        "(늘었|줄었|올랐|내렸|떨어졌|증가했|감소했|늦어졌|밀렸|당겨졌|바뀌었|변경됐|미뤄졌|멈췄|중단됐|지연됐|연기됐|재개됐)습니다|" +
            // Any "~아졌/어졌습니다": 높아졌, 빨라졌, 길어졌.
            "[가-힣](아|어)졌습니다|예정입니다",
    )

    /** A shortage worth hearing: "50개밖에 안 남았습니다". */
    private val SHORT = Regex("밖에|남았습니다|부족합니다|모자랍니다")

    /** A question or a suggestion to the room ends like this; it is not a point to remember. */
    private val QUESTION = Regex("(나요|까요|가요|죠|니까|셨어요|세요\\?|\\?)$")

    /** Remarks on how things feel ("날씨가 좋네요", "그렇군요") are not points to remember. */
    private val REMARK = Regex("(네요|군요|구나)$")

    private val DECIDED_OR_ASKED = Regex(
        "주세요|주십시오|바랍니다|부탁드립니다|부탁합니다|해야|야\\s*합니다|겠습니다|잡았습니다|합시다|필요합니다|옮깁니다|늘립니다|" +
            "밀렸습니다|미뤄졌습니다|당겨졌습니다|바뀌었습니다|변경됐습니다|" +
            "(?i)\\b(please|will|must|need|needs|has to|have to|is set|moves?)\\b",
    )
    private val PROCEDURAL = Regex(
        "부터\\s*(확인|시작|보겠|볼게)|(건|안건|것)으로\\s*시작|회의(를|는)?\\s*(시작|마치|마무리)|여기까지\\s*하|" +
            "(이만|여기서)\\s*(마치|마무리)|다음\\s*회의|이어서\\s*하|수고하셨|감사합니다|들리시나요|여보세요|잘\\s*들립니다|" +
            "(?i)\\b(let.s start|continue at the same time|thank you|can you hear|that.s all for today)\\b",
    )
    private val LEADING_FILLER = Regex("^(네|예|자|그럼|그리고|일단|음|어|아)[,\\s]+")
    private val MISHEARD_NE = Regex("^내[,\\s]+")

    /** Korean sentences end in these; the recognizer separates them with a space. */
    private val SENTENCE_END = Regex("(?<=니다|세요|어요|아요|해요|까요|죠|[.?!]),?\\s+")

    /** A reason or contrast clause in front of the main one: "...늦어져서 납기를...". */
    private val LEADING_CLAUSE = Regex("^(.+?(어서|아서|해서|져서|니까|는데|지만))\\s+(.+)$")

    /**
     * The speaker says they will do it. "~ㄹ게요" (볼게요, 챙길게요, 드릴게요) is a promise by itself,
     * even with the subject left out as Korean usually does. "~겠습니다" can also announce a plan,
     * so it counts only with the speaker named ("제가") or in the humble "드리겠습니다".
     */
    private val WILL_DO = Regex("(겠습니다|겠어요)$")

    /** "~겠습니다" that guesses or feels rather than promises: "잘 모르겠습니다", "어렵겠습니다". */
    private val NOT_A_DEED = Regex("(모르|알|좋|같|싶|있|없|되|맞|괜찮|어렵|힘들)겠(습니다|어요)$")

    /** Words that only point at something said before; a to-do of these alone says nothing. */
    private val VAGUE = setOf("그렇게", "이렇게", "저렇게", "그거", "그건", "그걸", "그럼", "네", "예")
    private val HUMBLE_WILL_DO = Regex("드리겠습니다$")
    private val L_GEYO = Regex("([가-힣])게요$")
    private val FIRST_PERSON = Regex("(^|\\s)(제가|저희가|제 ?쪽에서|저희 ?쪽에서|내가)\\s")
    private val ENGLISH_PROMISE = Regex("(?i)^(?:sure,?\\s+)?(I will|I'll|I can)\\s+(.+)$")
    private val SOFTENERS = Regex("(^|\\s)(제가|저희가|제 ?쪽에서|저희 ?쪽에서|내가|바로|일단|그럼|우선)(?=\\s)")

    /**
     * What the driver promised in [text], as a to-do ("공문을 오후에 보내기"), or null when they
     * promised nothing. Only for the driver's own words: they cannot write anything down while
     * driving, so a promise made on the call is the easiest thing to forget.
     */
    fun commitment(text: String): String? {
        ENGLISH_PROMISE.find(text.trim())?.let { match ->
            val task = match.groupValues[2].trimEnd('.', '!')
            return task.replaceFirstChar { it.uppercaseChar() }
        }
        val promise = sentences(text)
            .map { sentence ->
                // The recognizer often hears a leading "네," as "내" (my). Nobody says the casual
                // "내" and the polite "제가" in one breath, so with "제가" it is the "네" it was.
                val heard = if (FIRST_PERSON.containsMatchIn(" $sentence ")) sentence.replace(MISHEARD_NE, "") else sentence
                heard.replace(LEADING_FILLER, "")
            }
            .firstOrNull { sentence ->
                !PROCEDURAL.containsMatchIn(sentence) && !NOT_A_DEED.containsMatchIn(sentence) && (
                    promisesWithLGeyo(sentence) ||
                        HUMBLE_WILL_DO.containsMatchIn(sentence) ||
                        (WILL_DO.containsMatchIn(sentence) && FIRST_PERSON.containsMatchIn(" $sentence "))
                    )
            }
            ?: return null
        var task = promise.replace(SOFTENERS, "").replace(Regex("\\s+"), " ").trim()
        // The speaker does it, so what the sentence was about is what gets done: 공문은 → 공문을.
        task = task.replace(Regex("^([가-힣0-9 ]*?[가-힣0-9])(은|는)\\s")) { match ->
            match.groupValues[1] + (if (match.groupValues[2] == "은") "을 " else "를 ")
        }
        // 볼게요 → 보기, 챙길게요 → 챙기기; 보내겠습니다 → 보내기.
        task = task.replace(L_GEYO) { match -> withoutRieul(match.groupValues[1][0]) + "기" }
            .replace(Regex("겠(습니다|어요)$"), "기")
        // "그렇게 하기" names nothing to do.
        val words = task.split(" ")
        if (words.dropLast(1).all { it in VAGUE }) return null
        return task
    }

    /** Whether [sentence] ends in "~ㄹ게요": a syllable closed by ㄹ, then 게요. */
    private fun promisesWithLGeyo(sentence: String): Boolean {
        val syllable = L_GEYO.find(sentence)?.groupValues?.get(1)?.get(0) ?: return false
        return jongseong(syllable) == RIEUL
    }

    private const val HANGUL_BASE = 0xAC00
    private const val RIEUL = 8

    private fun jongseong(syllable: Char): Int = (syllable.code - HANGUL_BASE) % 28

    /** 볼 → 보, 챙길 → 길 → 기: the same syllable without its closing ㄹ. */
    private fun withoutRieul(syllable: Char): String =
        if (jongseong(syllable) == RIEUL) (syllable.code - RIEUL).toChar().toString() else syllable.toString()

    /** The key point of [text] as one short line, or null when it has none. */
    fun of(text: String): String? {
        val best = sentences(text)
            .map { it to score(it) }
            .filter { (_, score) -> score >= THRESHOLD }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?: return null
        return shorten(best)
    }

    /**
     * Whether [text] only runs the meeting: it has no key point, and one of its sentences opens,
     * hands over or closes the meeting ("울산 2공장 납품 일정부터 확인하겠습니다", "다음 회의는 같은
     * 시간에 이어서 하겠습니다", "수고하셨습니다"). The summary leaves such a line out, and so does
     * the part the driver missed.
     */
    fun runsTheMeeting(text: String): Boolean =
        of(text) == null && sentences(text).any { PROCEDURAL.containsMatchIn(it) }

    internal fun sentences(text: String): List<String> =
        text.trim().split(SENTENCE_END).map { it.trim().trimEnd('.', '?', '!') }.filter { it.isNotEmpty() }

    internal fun score(sentence: String): Int {
        if (PROCEDURAL.containsMatchIn(sentence) || QUESTION.containsMatchIn(sentence) || REMARK.containsMatchIn(sentence)) return 0
        var score = 0
        if (DECIDED.containsMatchIn(sentence)) score += 3
        if (WHEN.containsMatchIn(sentence)) score += 3
        if (HOW_MUCH.containsMatchIn(sentence)) score += 2
        if (DECIDED_OR_ASKED.containsMatchIn(sentence) || CHANGED.containsMatchIn(sentence) || SHORT.containsMatchIn(sentence)) {
            score += 1
        }
        return score
    }

    private fun shorten(sentence: String): String {
        var line = sentence.replace(LEADING_FILLER, "")
        LEADING_CLAUSE.matchEntire(line)?.let { match ->
            val main = match.groupValues[3]
            if (score(main) >= THRESHOLD) line = main
        }
        line = line
            .replace(Regex("(오늘|내일|이번\\s*주)\\s*중으로"), "$1까지")
            // "이번 주 금요일" is just "금요일"; "3일에서 10일로" keeps only where it moved to.
            .replace(Regex("이번\\s*주\\s+(?=[월화수목금토일]요일)"), "")
            .replace(Regex("\\d+\\s*일에서\\s+(?=\\d+\\s*일로)"), "")
            .replace(Regex("에는\\s"), "에 ")
            .replace(Regex("\\s+"), " ")
            .trim()
        line = asNote(line)
        if (line.length > ONE_LINE) line = trimModifiers(line)
        return line
    }

    private val REQUESTED = Regex("\\s?(주세요|주십시오|주시기\\s?바랍니다|주시길\\s?바랍니다)$")
    private val MUST = Regex("야\\s*(합니다|해요|됩니다|돼요|하겠습니다|겠습니다)$")
    private val WILL = Regex("겠(습니다|어요)$")
    private val LETS = Regex("([가-힣])시다$")
    private val ASKED_FOR = Regex("부탁\\s?(드립니다|합니다)$")
    private val HOPED_FOR = Regex("(?<=[가-힣])\\s?바랍니다$")
    private val IS = Regex("(?<=\\S)입니다$")
    /** A noun with "합니다": 필요합니다, 진행합니다. The noun says it. */
    private val NOUN_DOES = Regex("(?<=[가-힣]{2})합니다$")
    private val FORMAL_AFTER_CONSONANT = Regex("([가-힣])습니다$")
    private val FORMAL_AFTER_VOWEL = Regex("([가-힣])니다$")

    /**
     * [sentence] with the ending of a written note instead of the polite spoken one.
     *
     * What is to be done ends in "~기", whoever is to do it: asked ("보내 주세요" → "보내기"),
     * needed ("옮겨야 합니다" → "옮기기"), announced ("다시 내겠습니다" → "다시 내기") or proposed
     * ("진행합시다" → "진행하기"). What is the case ends in a noun or in "~음": "필요합니다" →
     * "필요", "예정입니다" → "예정", "정했습니다" → "정했음", "옮깁니다" → "옮김". A sentence
     * ending any other way, and English, is left as it is.
     */
    internal fun asNote(sentence: String): String {
        val line = sentence.trim()
        if (line.isEmpty() || !isHangul(line.last())) return line
        REQUESTED.find(line)?.let { ending ->
            val before = line.substring(0, ending.range.first)
            // "연락 주세요" asks for a thing, not for a deed done for the speaker.
            return doing(before) ?: "$before 주기"
        }
        MUST.find(line)?.let { ending -> doing(line.substring(0, ending.range.first))?.let { return it } }
        if (WILL.containsMatchIn(line)) {
            // "어렵겠습니다" guesses; it is not something to do.
            return line.replace(WILL, if (NOT_A_DEED.containsMatchIn(line)) "겠음" else "기")
        }
        if (promisesWithLGeyo(line)) return line.replace(L_GEYO) { withoutRieul(it.groupValues[1][0]) + "기" }
        LETS.find(line)?.let { ending ->
            val syllable = ending.groupValues[1][0]
            if (jongseong(syllable) == BIEUP) return line.substring(0, ending.range.first) + withJongseong(syllable, 0) + "기"
        }
        if (ASKED_FOR.containsMatchIn(line)) return line.replace(ASKED_FOR, "부탁")
        if (HOPED_FOR.containsMatchIn(line)) return line.replace(HOPED_FOR, "")
        if (IS.containsMatchIn(line)) return line.replace(IS, "")
        if (NOUN_DOES.containsMatchIn(line)) return line.replace(NOUN_DOES, "")
        FORMAL_AFTER_CONSONANT.find(line)?.let { ending ->
            // 정했습니다 → 정했음, 없습니다 → 없음.
            if (jongseong(ending.groupValues[1][0]) != 0) return line.substring(0, ending.range.first + 1) + "음"
        }
        FORMAL_AFTER_VOWEL.find(line)?.let { ending ->
            // 옮깁니다 → 옮김, 됩니다 → 됨.
            val syllable = ending.groupValues[1][0]
            if (jongseong(syllable) == BIEUP) return line.substring(0, ending.range.first) + withJongseong(syllable, MIEUM)
        }
        return line
    }

    /**
     * "보내" → "보내기", "올려" → "올리기", "확인해" → "확인하기", "받아" → "받기", "서둘러" →
     * "서두르기": the deed named by a verb in the form it takes before "주세요" or "야 합니다".
     * Null when [connective] does not end in that form.
     */
    private fun doing(connective: String): String? {
        val text = connective.trimEnd()
        val last = text.lastOrNull() ?: return null
        if (!isHangul(last) || jongseong(last) != 0) return null
        val head = text.dropLast(1)
        val before = head.lastOrNull()?.takeIf { isHangul(it) }
        val stem = when {
            // 만들어 → 만들, 되어 → 되; 받아 → 받.
            last == '어' && before != null -> head
            last == '아' && before != null && jongseong(before) != 0 -> head
            // 서둘러 → 서두르, 골라 → 고르.
            (last == '러' || last == '라') && before != null && jongseong(before) == RIEUL ->
                head.dropLast(1) + withJongseong(before, 0) + "르"
            last == '해' -> head + "하"
            last == '써' -> head + "쓰"
            last == '꺼' -> head + "끄"
            else -> when (jungseong(last)) {
                YEO -> head + withJungseong(last, I)   // 올려 → 올리, 옮겨 → 옮기
                WO -> head + withJungseong(last, U)    // 바꿔 → 바꾸, 미뤄 → 미루
                WA -> head + withJungseong(last, O)    // 봐 → 보
                WAE -> head + withJungseong(last, OE)  // 돼 → 되
                A, AE, EO, E -> text                   // 가, 보내, 서, 세
                else -> return null
            }
        }
        return stem + "기"
    }

    // Hangul syllables are numbered (initial * 21 + vowel) * 28 + final from U+AC00.
    private const val A = 0
    private const val AE = 1
    private const val EO = 4
    private const val E = 5
    private const val YEO = 6
    private const val O = 8
    private const val WA = 9
    private const val WAE = 10
    private const val OE = 11
    private const val U = 13
    private const val WO = 14
    private const val I = 20
    private const val MIEUM = 16
    private const val BIEUP = 17

    private fun isHangul(char: Char): Boolean = char in '\uAC00'..'\uD7A3'

    private fun jungseong(syllable: Char): Int = (syllable.code - HANGUL_BASE) / 28 % 21

    private fun withJungseong(syllable: Char, vowel: Int): Char {
        val initial = (syllable.code - HANGUL_BASE) / (21 * 28)
        return (HANGUL_BASE + (initial * 21 + vowel) * 28 + jongseong(syllable)).toChar()
    }

    private fun withJongseong(syllable: Char, final: Int): Char =
        (syllable.code - jongseong(syllable) + final).toChar()

    /** About as many characters as fit on one summary line while driving. */
    private const val ONE_LINE = 20

    /** "기존 금액에서", "공유 폴더에" in the middle of a sentence: where, not what or when. */
    private val PLACE_PHRASE = Regex("\\s([가-힣]+\\s)?[가-힣]+(에서|에)\\s")

    /** Ends like a place but tells when or why: "이번 주 안에", "점검 때문에". These stay. */
    private val WHEN_OR_WHY = Regex("(안|중|전|후|뒤|말|초|때|때문)에\\s$")

    /** A subject of three or more words: "현장 안전 점검은". */
    private val LONG_SUBJECT = Regex("^(?:[가-힣0-9]+\\s)+([가-힣0-9]+\\s[가-힣0-9]+(은|는|이|가))\\s")

    /**
     * Drops what the driver can do without: a place in the middle of the sentence, and the first
     * words of a long subject. A phrase holding a date, time or quantity always stays.
     */
    private fun trimModifiers(sentence: String): String {
        var line = PLACE_PHRASE.replace(sentence) { match ->
            val stays = WHEN.containsMatchIn(match.value) || HOW_MUCH.containsMatchIn(match.value) ||
                WHEN_OR_WHY.containsMatchIn(match.value)
            if (stays) match.value else " "
        }.replace(Regex("\\s+"), " ").trim()
        // Only a line still too long loses the first words of its subject.
        if (line.length <= ONE_LINE) return line
        LONG_SUBJECT.find(line)?.let { match ->
            if (!WHEN.containsMatchIn(match.value) && !HOW_MUCH.containsMatchIn(match.value)) {
                line = match.groupValues[1] + " " + line.substring(match.range.last + 1)
            }
        }
        return line.replace(Regex("\\s+"), " ").trim()
    }
}
