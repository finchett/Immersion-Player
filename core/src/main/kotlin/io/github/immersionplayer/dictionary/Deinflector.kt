package io.github.immersionplayer.dictionary

/**
 * Undoes Japanese verb and adjective inflections, Yomichan-style.
 *
 * Each rule rewrites a suffix and records what word class the result must be. Rules chain,
 * so 食べさせられなかった → 食べさせられない → 食べさせられる → 食べさせる → 食べる.
 */
object Deinflector {
    const val V1 = 1
    const val V5 = 2
    const val VS = 4
    const val VK = 8
    const val ADJ_I = 16
    const val MASU = 32
    const val TE = 64

    data class Candidate(val term: String, val rules: Int, val reasons: List<String>)

    private data class Rule(val from: String, val to: String, val rulesIn: Int, val rulesOut: Int, val reason: String)

    /** Word-class flags from a dictionary entry's space-separated rules field. */
    fun entryRules(rules: String): Int {
        var flags = 0
        for (token in rules.split(' ')) {
            flags = flags or when {
                token == "v1" || token.startsWith("v1-") -> V1
                token.startsWith("v5") -> V5
                token == "vs" || token.startsWith("vs-") || token == "vz" -> VS
                token == "vk" -> VK
                token == "adj-i" -> ADJ_I
                else -> 0
            }
        }
        return flags
    }

    /** True when an entry with [entryRules] can be the dictionary form of [candidate]. */
    fun matches(candidate: Candidate, entryRules: Int): Boolean =
        candidate.rules == 0 || (candidate.rules and entryRules) != 0

    fun deinflect(source: String): List<Candidate> {
        val results = mutableListOf(Candidate(source, 0, emptyList()))
        val seen = hashSetOf(source to 0)
        var i = 0
        while (i < results.size && results.size < 256) {
            val current = results[i++]
            for (rule in rulesBySuffixEnd[current.term.lastOrNull()] ?: continue) {
                if (current.rules != 0 && (current.rules and rule.rulesIn) == 0) continue
                if (!current.term.endsWith(rule.from)) continue
                val term = current.term.dropLast(rule.from.length) + rule.to
                if (term.isEmpty()) continue
                if (seen.add(term to rule.rulesOut)) {
                    results.add(Candidate(term, rule.rulesOut, listOf(rule.reason) + current.reasons))
                }
            }
        }
        return results
    }

    private class VerbClass(
        val dict: String,
        val rules: Int,
        val a: String,       // negative stem
        val i: String,       // masu stem
        val e: String,       // conditional stem (before ば)
        val volitional: List<String>,
        val imperative: List<String>,
        val te: String,      // te/ta stem
        val voiced: Boolean, // て→で, た→だ
        val potential: List<String>,
        val passive: List<String>,
        val causative: List<String>,
    )

    private val rules: List<Rule> = buildRules()
    private val rulesBySuffixEnd: Map<Char, List<Rule>> = rules.groupBy { it.from.last() }

    private fun buildRules(): List<Rule> {
        val out = mutableListOf<Rule>()
        fun add(from: String, to: String, rulesIn: Int, rulesOut: Int, reason: String) {
            if (from.isNotEmpty() && from != to) out.add(Rule(from, to, rulesIn, rulesOut, reason))
        }

        val classes = mutableListOf<VerbClass>()
        val godan = listOf(
            // dict, a, i, e, o, te stem, voiced
            listOf("う", "わ", "い", "え", "お", "っ", ""),
            listOf("く", "か", "き", "け", "こ", "い", ""),
            listOf("ぐ", "が", "ぎ", "げ", "ご", "い", "v"),
            listOf("す", "さ", "し", "せ", "そ", "し", ""),
            listOf("つ", "た", "ち", "て", "と", "っ", ""),
            listOf("ぬ", "な", "に", "ね", "の", "ん", "v"),
            listOf("ぶ", "ば", "び", "べ", "ぼ", "ん", "v"),
            listOf("む", "ま", "み", "め", "も", "ん", "v"),
            listOf("る", "ら", "り", "れ", "ろ", "っ", ""),
        )
        for (row in godan) {
            val (dict, a, i, e, o) = row
            val te = row[5]
            val voiced = row[6] == "v"
            classes += VerbClass(
                dict = dict, rules = V5, a = a, i = i, e = e,
                volitional = listOf(o + "う"), imperative = listOf(e),
                te = te, voiced = voiced,
                potential = listOf(e + "る"), passive = listOf(a + "れる"),
                causative = listOf(a + "せる", a + "す"),
            )
        }
        classes += VerbClass(
            dict = "る", rules = V1, a = "", i = "", e = "れ",
            volitional = listOf("よう"), imperative = listOf("ろ", "よ"),
            te = "", voiced = false,
            potential = listOf("られる", "れる"), passive = listOf("られる"), causative = listOf("させる"),
        )
        classes += VerbClass(
            dict = "くる", rules = VK, a = "こ", i = "き", e = "くれ",
            volitional = listOf("こよう"), imperative = listOf("こい"),
            te = "き", voiced = false,
            potential = listOf("こられる", "これる"), passive = listOf("こられる"), causative = listOf("こさせる"),
        )
        classes += VerbClass(
            dict = "来る", rules = VK, a = "来", i = "来", e = "来れ",
            volitional = listOf("来よう"), imperative = listOf("来い"),
            te = "来", voiced = false,
            potential = listOf("来られる", "来れる"), passive = listOf("来られる"), causative = listOf("来させる"),
        )
        classes += VerbClass(
            dict = "する", rules = VS, a = "し", i = "し", e = "すれ",
            volitional = listOf("しよう"), imperative = listOf("しろ", "せよ"),
            te = "し", voiced = false,
            potential = emptyList(), passive = listOf("される"), causative = listOf("させる"),
        )

        for (c in classes) {
            val d = c.dict
            val r = c.rules
            add(c.a + "ない", d, ADJ_I, r, "negative")
            add(if (c.rules == VS) "せず" else c.a + "ず", d, 0, r, "-zu")
            add(if (c.rules == VS) "せぬ" else c.a + "ぬ", d, 0, r, "-nu")
            add(c.i + "ます", d, MASU, r, "polite")
            add(c.i + "たい", d, ADJ_I, r, "-tai")
            add(c.i + "たがる", d, V5, r, "-tagaru")
            add(c.i + "ながら", d, 0, r, "-nagara")
            add(c.i + "なさい", d, 0, r, "-nasai")
            add(c.i + "そう", d, 0, r, "-sou")
            add(c.i + "すぎる", d, V1, r, "-sugiru")
            add(c.i + "やすい", d, ADJ_I, r, "-yasui")
            add(c.i + "にくい", d, ADJ_I, r, "-nikui")
            add(c.e + "ば", d, 0, r, "-ba")
            c.volitional.forEach { add(it, d, 0, r, "volitional") }
            c.imperative.forEach { add(it, d, 0, r, "imperative") }
            c.potential.forEach { add(it, d, V1, r, "potential") }
            c.passive.forEach { add(it, d, V1, r, "passive") }
            c.causative.forEach { add(it, d, if (it.endsWith("す")) V5 else V1, r, "causative") }

            val t = if (c.voiced) "で" else "て"
            val ta = if (c.voiced) "だ" else "た"
            val cha = if (c.voiced) "じゃ" else "ちゃ"
            add(c.te + t, d, TE, r, "-te")
            add(c.te + ta, d, 0, r, "past")
            add(c.te + ta + "ら", d, 0, r, "-tara")
            add(c.te + ta + "り", d, 0, r, "-tari")
            add(c.te + cha, d, 0, r, "-te wa")
        }

        // 行く has an irregular te/ta form
        for (stem in listOf("行", "い")) {
            add(stem + "って", stem + "く", TE, V5, "-te")
            add(stem + "った", stem + "く", 0, V5, "past")
            add(stem + "ったら", stem + "く", 0, V5, "-tara")
            add(stem + "ったり", stem + "く", 0, V5, "-tari")
        }

        // polite forms collapse onto ます
        add("ました", "ます", 0, MASU, "past")
        add("ません", "ます", 0, MASU, "negative")
        add("ませんでした", "ます", 0, MASU, "past negative")
        add("ましょう", "ます", 0, MASU, "volitional")
        add("まして", "ます", 0, MASU, "-te")

        // te-form auxiliaries collapse onto the te-form
        for ((te, voiced) in listOf("て" to false, "で" to true)) {
            add(te + "いる", te, V1, TE, "-te iru")
            add(te + "る", te, V1, TE, "-te iru")
            add(te + "しまう", te, V5, TE, "-te shimau")
            add(te + "おく", te, V5, TE, "-te oku")
            add(te + "ある", te, V5, TE, "-te aru")
            add(te + "いく", te, V5, TE, "-te iku")
            add(te + "く", te, V5, TE, "-te iku")
            add(te + "くる", te, VK, TE, "-te kuru")
            add(te + "みる", te, V1, TE, "-te miru")
            add(te + "くれる", te, V1, TE, "-te kureru")
            add(te + "もらう", te, V5, TE, "-te morau")
            add(te + "あげる", te, V1, TE, "-te ageru")
            add(te + "ください", te, 0, TE, "-te kudasai")
            add(te + "も", te, 0, TE, "-te mo")
            if (voiced) {
                add("じゃう", te, V5, TE, "-te shimau")
                add("どく", te, V5, TE, "-te oku")
            } else {
                add("ちゃう", te, V5, TE, "-te shimau")
                add("ちまう", te, V5, TE, "-te shimau")
                add("とく", te, V5, TE, "-te oku")
            }
        }

        // i-adjectives
        add("かった", "い", 0, ADJ_I, "past")
        add("かったら", "い", 0, ADJ_I, "-tara")
        add("かったり", "い", 0, ADJ_I, "-tari")
        add("くて", "い", 0, ADJ_I, "-te")
        add("く", "い", 0, ADJ_I, "adverb")
        add("くない", "い", ADJ_I, ADJ_I, "negative")
        add("くなる", "い", V5, ADJ_I, "-naru")
        add("ければ", "い", 0, ADJ_I, "-ba")
        add("かろう", "い", 0, ADJ_I, "volitional")
        add("さ", "い", 0, ADJ_I, "noun")
        add("そう", "い", 0, ADJ_I, "-sou")
        add("すぎる", "い", V1, ADJ_I, "-sugiru")
        add("なきゃ", "ない", 0, ADJ_I, "-nakya")
        add("なくちゃ", "ない", 0, ADJ_I, "-nakucha")
        add("ないで", "ない", 0, ADJ_I, "-naide")

        // する verbs: 勉強する → 勉強 (entries marked vs)
        add("する", "", VS, VS, "suru verb")

        return out
    }
}
