package io.github.projectwip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.projectwip.BuildConfig
import io.github.projectwip.ui.ButtonStyle
import io.github.projectwip.ui.ChunkyButton
import io.github.projectwip.ui.GameIcon
import io.github.projectwip.ui.GameText
import io.github.projectwip.ui.IconKind
import io.github.projectwip.ui.Palette
import io.github.projectwip.ui.Panel
import io.github.projectwip.ui.PlainText
import io.github.projectwip.ui.ProgressBar
import io.github.projectwip.ui.Type
import io.github.projectwip.ui.rememberAnimTime
import kotlin.math.sin

/**
 * Shown while the game gets ready: it builds its sounds and music. [progress] is real (0..1 of that work), and
 * [status] says which part is happening.
 */
@Composable
fun LoadingScreen(progress: Float, status: String) {
    val time by rememberAnimTime()
    Box(
        // Swallows touches so nothing underneath can be pressed while loading.
        Modifier.fillMaxSize().background(Color(0xF00B0620)).clickable(remember { MutableInteractionSource() }, null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The roster, bobbing out of step with each other.
            Row(horizontalArrangement = Arrangement.spacedBy((-70).dp), verticalAlignment = Alignment.Bottom) {
                io.github.projectwip.data.Balance.fighters.forEachIndexed { i, def ->
                    io.github.projectwip.ui.FighterView(def, 0, Modifier.size(if (i == 1 || i == 2) 230.dp else 200.dp)
                        .graphicsLayer { translationY = 7.dp.toPx() * sin(time * 2.6f + i * 1.3f) }, pedestal = false)
                }
            }
            GameText("ASTROARENA", Type.Display.copy(fontSize = Type.Display.fontSize * 1.5f), color = Palette.Gold, outline = 5.dp)
            Spacer(Modifier.height(18.dp))
            // One, two, three dots, repeating.
            GameText("LOADING" + ".".repeat(1 + (time * 2.5f).toInt() % 3), Type.Title, outline = 3.5.dp, modifier = Modifier.width(190.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(progress, Modifier.width(360.dp).height(22.dp), animate = false)
                Spacer(Modifier.width(12.dp))
                GameText("${(progress * 100).toInt()}%", Type.Title, color = Palette.Gold, outline = 3.5.dp, modifier = Modifier.width(80.dp))
            }
            Spacer(Modifier.height(8.dp))
            PlainText(status, Type.Label, color = Palette.TextDim)
        }
        PlainText(TIPS[(time / 4f).toInt() % TIPS.size], Type.Body, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp), color = Color.White, align = TextAlign.Center)
        PlainText(BuildConfig.VERSION_NAME, Type.Small, Modifier.align(Alignment.BottomEnd).padding(12.dp))
    }
}

/** Shown one at a time on the loading and matchmaking screens. */
val TIPS = listOf(
    "Tip: tap the attack stick to fire at the nearest enemy.",
    "Tip: tall grass hides you until an enemy gets close.",
    "Tip: your super charges as you land hits.",
    "Tip: stay out of the fight for a few seconds and you start to heal.",
    "Tip: in Last Spark, break crates for Power Cells before the storm closes in.",
    "Tip: a top-four finish or a win earns a Spark Drop, up to three a day.",
    "Tip: upgrades raise a fighter's health and damage. Power Ups pay for them.",
    "Tip: you can move every control in Settings > Controls.",
)

/**
 * A new player's first screen: choose the name shown on the home screen and in matches.
 */
@Composable
fun NameScreen(onDone: (String) -> Unit) {
    var name by remember { androidx.compose.runtime.mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0620)).clickable(remember { MutableInteractionSource() }, null) { },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Near the top, so the keyboard doesn't cover it.
        Panel(Modifier.widthIn(max = 640.dp).padding(18.dp), cut = 20.dp) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameText("WHAT'S YOUR NAME?", Type.Display, color = Palette.Gold, outline = 4.dp)
                PlainText("This is the name shown in matches. Up to 16 letters and numbers; you can change it later in Settings.",
                    Type.Body, color = Color.White, align = TextAlign.Center, maxLines = 3)
                NameField("") { name = it }
                ChunkyButton({ onDone(name.trim()) }, Modifier.size(260.dp, 64.dp), ButtonStyle.GREEN, enabled = name.isNotBlank()) { GameText("LET'S GO", Type.Heading) }
            }
        }
    }
}
