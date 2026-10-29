package app.mizune.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TitlesTest {
    private fun clean(name: String) = Titles.clean(name)

    @Test
    fun dropsReleaseTagsFromFolderNames() {
        assertEquals("K-On!", clean("[IndieSubs] K-On! (1080p BD 10bit) [ABCD1234]"))
        assertEquals("K-On! (2009)", clean("[Group] K-On! (2009) [BD 1080p x265 FLAC]"))
        assertEquals("Frieren", clean("[SubsPlease] Frieren (Complete) [1080p]"))
        assertEquals("Sousou no Frieren Season 1", clean("Sousou.no.Frieren.S01.1080p.WEB-DL.H.264-GROUP"))
        assertEquals("Bocchi the Rock!", clean("Bocchi the Rock! [BDRip 1080p HEVC Dual-Audio]"))
        assertEquals("Hibike! Euphonium 2", clean("Hibike!_Euphonium_2_[Hi10P]"))
        assertEquals("Mushishi", clean("【字幕组】Mushishi【1080p】"))
        assertEquals("Yuru Camp (Season 2)", clean("[Judas] Yuru Camp (Season 2) [1080p][HEVC x265 10bit][Eng-Subs]"))
        assertEquals("Weathering With You 2019", clean("Weathering.With.You.2019.1080p.BluRay.x264-GROUP"))
        assertEquals("Steins;Gate", clean("Steins;Gate (BD 1080p Dual Audio)"))
    }

    @Test
    fun episodesWithTheirOwnTags() {
        val names = listOf(
            "[Erai-raws] Kusuriya no Hitorigoto - 01 [1080p][Multiple Subtitle][ABCD1234].mkv",
            "[Erai-raws] Kusuriya no Hitorigoto - 02 END [1080p][Multiple Subtitle][ABCD1235].mkv",
        )
        assertEquals(listOf("Episode 1", "Episode 2"), Titles.episodes(names))
    }

    @Test
    fun leavesPlainNamesAlone() {
        assertEquals("Lectures", clean("Lectures"))
        assertEquals("Season 1", clean("Season 1"))
        assertEquals("Dr. Stone", clean("Dr. Stone"))
        assertEquals("Charlotte's Web", clean("Charlotte's Web"))
        assertEquals("ゆるキャン△", clean("ゆるキャン△"))
        assertEquals("Lesson 10", clean("Lesson 10.mkv"))
    }

    @Test
    fun cleansEpisodeFiles() {
        assertEquals("K-On! - 01", clean("[IndieSubs] K-On! - 01 (1080p bd1230) [9F3A21BC].mkv"))
        assertEquals("K-On! - 01", clean("[IndieSubs] K-On! - 01v2 [1080p].mkv"))
        assertEquals("Frieren S01E05 Phantoms of the Dead", clean("Frieren.S01E05.Phantoms.of.the.Dead.1080p.WEB.H264-GROUP.mkv"))
    }

    @Test
    fun aNameMadeOnlyOfBracketsKeepsItsTitle() {
        assertEquals("Bocchi the Rock! 01", clean("[Nekomoe kissaten][Bocchi the Rock!][01][1080p][CHS].mp4"))
    }

    @Test
    fun episodesDropTheSeriesTheyShare() {
        val names = listOf(
            "[IndieSubs] K-On! - 01 (1080p BD) [9F3A21BC].mkv",
            "[IndieSubs] K-On! - 02 (1080p BD) [1B2C3D4E].mkv",
            "[IndieSubs] K-On! - 10 (1080p BD) [AA00BB11].mkv",
        )
        assertEquals(listOf("Episode 1", "Episode 2", "Episode 10"), Titles.episodes(names))
    }

    @Test
    fun episodeTitlesAreKept() {
        val names = listOf(
            "Frieren.S01E05.Phantoms.of.the.Dead.1080p.WEB.H264-GROUP.mkv",
            "Frieren.S01E06.A.Found.Mage.1080p.WEB.H264-GROUP.mkv",
        )
        assertEquals(
            listOf("Episode 5 · Phantoms of the Dead", "Episode 6 · A Found Mage"),
            Titles.episodes(names),
        )
    }

    @Test
    fun episodesFromSeveralSeasonsSayWhichSeason() {
        val names = listOf("Show S01E12.mkv", "Show S02E01.mkv")
        assertEquals(listOf("S1 E12", "S2 E1"), Titles.episodes(names))
    }

    @Test
    fun japaneseEpisodeNumbers() {
        val names = listOf("けいおん！ 第1話.mkv", "けいおん！ 第2話.mkv")
        assertEquals(listOf("Episode 1", "Episode 2"), Titles.episodes(names))
    }

    @Test
    fun differentVideosKeepTheirOwnTitles() {
        val names = listOf("Lesson 1.mkv", "Lesson 2.mkv", "Lesson 10.mkv")
        assertEquals(listOf("Lesson 1", "Lesson 2", "Lesson 10"), Titles.episodes(names))
        assertEquals(listOf("Your Name (2016)"), Titles.episodes(listOf("Your Name (2016) [BD 1080p].mkv")))
        assertEquals(
            listOf("Spirited Away", "Totoro"),
            Titles.episodes(listOf("Spirited Away [1080p].mkv", "Totoro [1080p].mkv")),
        )
    }
}
