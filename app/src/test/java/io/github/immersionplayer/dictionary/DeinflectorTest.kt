package io.github.immersionplayer.dictionary

import io.github.immersionplayer.dictionary.Deinflector.ADJ_I
import io.github.immersionplayer.dictionary.Deinflector.V1
import io.github.immersionplayer.dictionary.Deinflector.V5
import io.github.immersionplayer.dictionary.Deinflector.VK
import io.github.immersionplayer.dictionary.Deinflector.VS
import org.junit.Assert.assertTrue
import org.junit.Test

class DeinflectorTest {
    private fun assertDeinflects(source: String, term: String, rules: Int) {
        val candidates = Deinflector.deinflect(source)
        val match = candidates.firstOrNull { it.term == term && (it.rules and rules) != 0 }
        assertTrue(
            "$source should deinflect to $term; got ${candidates.map { it.term + "/" + it.rules }}",
            match != null,
        )
    }

    @Test fun ichidan() {
        assertDeinflects("食べなかった", "食べる", V1)
        assertDeinflects("食べさせられなかった", "食べる", V1)
        assertDeinflects("見たくない", "見る", V1)
        assertDeinflects("起きて", "起きる", V1)
    }

    @Test fun godan() {
        assertDeinflects("読んでいる", "読む", V5)
        assertDeinflects("読めない", "読む", V5)
        assertDeinflects("書かれた", "書く", V5)
        assertDeinflects("泳いで", "泳ぐ", V5)
        assertDeinflects("死んだ", "死ぬ", V5)
        assertDeinflects("待って", "待つ", V5)
        assertDeinflects("飲みます", "飲む", V5)
        assertDeinflects("飲みませんでした", "飲む", V5)
        assertDeinflects("行った", "行く", V5)
        assertDeinflects("やってる", "やる", V5)
        assertDeinflects("言っちゃった", "言う", V5)
        assertDeinflects("話そう", "話す", V5)
        assertDeinflects("帰れば", "帰る", V5)
    }

    @Test fun irregular() {
        assertDeinflects("来ない", "来る", VK)
        assertDeinflects("きた", "くる", VK)
        assertDeinflects("勉強しました", "勉強", VS)
        assertDeinflects("しなかった", "する", VS)
    }

    @Test fun adjectives() {
        assertDeinflects("高くなかった", "高い", ADJ_I)
        assertDeinflects("美味しそう", "美味しい", ADJ_I)
        assertDeinflects("寒ければ", "寒い", ADJ_I)
        assertDeinflects("やる気が出なくて", "やる気が出ない", ADJ_I)
    }

    @Test fun matchesRespectWordClass() {
        val fromPast = Deinflector.deinflect("書いた").first { it.term == "書く" }
        assertTrue(Deinflector.matches(fromPast, Deinflector.entryRules("v5k vt")))
        assertTrue(!Deinflector.matches(fromPast, Deinflector.entryRules("n")))
    }
}
