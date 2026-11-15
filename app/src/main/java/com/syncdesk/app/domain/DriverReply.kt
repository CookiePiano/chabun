package com.syncdesk.app.domain

/**
 * What the driver says back in the talk after a call. Only a short reply made of the few words
 * below counts as yes, no or stop; anything longer is [Other], which the car takes for a question
 * once it has asked for one.
 */
sealed interface DriverReply {
    /** "네", "응", "그래", "좋아", "해 줘". */
    data object Yes : DriverReply

    /** "아니", "괜찮아", "됐어", "나중에". */
    data object No : DriverReply

    /** "그만", "닫아", "멈춰". */
    data object Stop : DriverReply

    /** Anything else, as it was heard. */
    data class Other(val text: String) : DriverReply

    companion object {
        private val YES = setOf(
            "네", "예", "응", "어", "그래", "그래요", "좋아", "좋아요", "좋습니다", "해줘", "해주세요",
            "yes", "yeah", "yep", "sure", "ok", "okay", "please",
        )
        private val NO = setOf(
            "아니", "아니요", "아니오", "아뇨", "괜찮아", "괜찮아요", "괜찮습니다", "됐어", "됐어요", "됐습니다", "나중에",
            "no", "nope", "later",
        )
        private val STOP = setOf(
            "그만", "그만해", "그만해요", "그만해줘", "닫아", "닫아요", "닫아줘", "멈춰", "멈춰요", "멈춰줘",
            "stop", "close", "enough",
        )

        /** Words that may come along with a reply without changing it: "이제 그만", "해 줘". */
        private val FILLERS = setOf("이제", "좀", "그냥", "해", "줘", "주세요", "thanks")

        private val SEPARATORS = Regex("[\\s,.!?~…·]+")

        /**
         * The reply in [heard] (what a recognizer made of the driver's words), or null when
         * nothing was said. Stopping wins over declining, and declining over agreeing.
         */
        fun of(heard: String): DriverReply? {
            val text = heard.trim()
            val words = text.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            // "해 줘" is two words to a recognizer and one request to the driver.
            val joined = words.joinToString("")
            return when {
                words.any { it in STOP } && words.all { it in STOP || it in NO || it in FILLERS } -> Stop
                words.any { it in NO } && words.all { it in NO || it in FILLERS } -> No
                (words.any { it in YES } || joined in YES) && words.all { it in YES || it in FILLERS } -> Yes
                else -> Other(text)
            }
        }
    }
}
