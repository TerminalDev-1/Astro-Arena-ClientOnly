package io.github.projectwip.data

/** One item on the News tab. [tag] is NEW, BALANCE, EVENT, FIX or NEWS. */
data class NewsItem(val title: String, val date: String, val tag: String, val text: String)

/** The News tab's items, newest first. Add one here when a release changes what players see. */
object News {
    val items: List<NewsItem> = listOf(
        NewsItem(
            "Client only: no server needed", "2026-10-07", "NEW",
            "AstroArena now runs entirely on your device. There is no server, no account and no connection to make: " +
                "Spark Drops, the shop, upgrades, the Cup Track, the Spark Road and the Spark Pass all work offline, and " +
                "your progress is kept in the save on this device. The 1v1 and team modes needed other players on a " +
                "server, so they are gone, and so is the leaderboard.",
        ),
        NewsItem(
            "Byte is the new starter", "2026-10-06", "NEW",
            "Juno has left the arena, and Byte takes her place as the fighter everyone starts with. Her rifle fires a " +
                "scatter of five bits, her super is a wide blast that shoves back whoever it hits, and her hyper speeds " +
                "up her shots. Juno's levels and Cups went with her; everyone has Byte at level 1.",
        ),
        NewsItem(
            "Ranks without end, and Cups that fly", "2026-10-06", "NEW",
            "A fighter's rank no longer stops at 20: past 1,000 Cups every 150 more is another rank, for as long as " +
                "you keep winning. And the Cups a match pays now fly across the result screen into your total.",
        ),
    )
}
