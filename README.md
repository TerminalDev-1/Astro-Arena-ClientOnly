# AstroArena

A mobile-first, landscape, **3D arena brawler for Android phones and tablets** — quick 3v3 matches,
twin-stick touch controls, fighters you level up, Cups, a Cup Track, a Shop — and **bots as a first-class
way to play**. Launch it, pick a fighter, press PLAY, and you're in a match against bots in seconds. No
account, no matchmaking and no connection: **the whole game runs on your device**, with nothing to host.

**Repository:** https://github.com/TerminalDev-1/Astro-Arena-Exp

This is the offline edition of AstroArena: the game server is gone and everything it used to decide (what a
match is worth, what comes out of a drop, what things cost, the shop, the Spark Pass) is done on the device by
[`data/Economy.kt`](client/app/src/main/java/io/github/projectwip/data/Economy.kt). There is no online play: the 1v1
and team modes, the leaderboard and the update check needed a server or the network, and were removed.

> **Preview software.** This is an experiment. Anything — rules, balance, saves, code — may change without
> notice. There is no promise of maintenance.

Everything in the game is original: 3D characters and arena (all meshes generated in code), icons, the
Cup emblem, sounds (synthesised at runtime), UI and rules. No third-party game assets are used.

## What's in the first vertical slice

| | |
|---|---|
| **Modes** | **Last Spark** — 10-fighter free-for-all, last one standing; break Spark Crates for stacking Power Cells (+10% health & damage each) while the Static Storm closes in. **Knockout Rush** — 3v3, first team to 10 KOs, your team starts at the bottom. **Boss Mode** — you against one of three bosses with moves of their own; knock it out to win, with unlimited lives. The boss's strength is fixed. **Training Area** — a practice ground with four dummies, a swarm of twelve minis, a sentry gun and a boss, none of which move; nothing at stake |
| **Arenas** | *Static Canyon* (44×44, free-for-all) and *Foundry Yard* (vertical 3v3): walls, tall-grass thickets (hide inside), coolant pools (block movement, not shots), destructible crates |
| **Fighters** | **Byte** (scatter rifle, knockback super, starter) · **Brakk** (shotgun tank, ram super) · **Mira** (sniper, piercing super) · **Kito** (fast blade assassin, dash super) · **Varun** (rocket firefighter: three rockets a shot, a super of eight seekers that fly over walls, hunt enemies and never land the knockout). Every fighter also has a **Hyper**: 8 seconds of +25% damage, health and shield |
| **Graphics** | Custom OpenGL ES 3.0: toon lighting, real-time shadows, inked outlines, 4× MSAA, up to 120 Hz |
| **Controls** | Floating/fixed move stick · drag-to-aim attack stick (tap anywhere on it = auto-aim that locks the nearest enemy and leads moving targets; visible target marker; drag back to centre = cancel) · super stick · optional aim assist · camera centred on you |
| **Bots** | Easy / Normal / Hard / Elite — behaviour only (reaction, aim, leading, dodging, spacing, targeting, supers) |
| **Progression** | Levels 1–10 with linear, fully visible stat gains · Bolts (upgrades) · Prisms (shop) · Cups · Cup Track rewards |
| **Glitch Drops** | Earned from your first three good finishes a day (a team win, or top 4 in Last Spark). Tap to charge one through six tiers — Scrap, Tuned, Charged, Overclocked, Prismatic, Ultra — then it bursts open: Bolts, Prisms, a colourway or a new fighter, never a duplicate. A drop can split into two, four or eight, and the pieces roll better than a plain one |
| **Sound** | Music (a sixteen-bar dark-electro lobby loop in four sections, plus separate victory and defeat themes) and every effect are designed in code by a small synth (`audio/SfxSynth.kt`): band-limited oscillators, FM bells, filtered noise, drive, echo and reverb |
| **Shop** | Daily free gift · fighter unlocks · Bolt supplies · colourways · **Offer Creator**: design your own deals (bundle contents, price in Bolts/Prisms/free, discount display, expiry, purchase limit, colour theme) |
| **Menus** | A live 3D lobby behind every screen (camera glides between shots), your fighter on a pedestal you can spin, 3D portraits, mode picker |
| **Settings** | Bot difficulty, player name, control size/opacity/mode, auto-aim, volume/mute, haptics, frame rate, damage numbers, FPS, reset |
| **Persistence** | Everything above is saved to a JSON file on the device and survives restarts |

## Layout

| | |
|---|---|
| [`client/`](client) | The Android game: Kotlin, Jetpack Compose menus, a custom OpenGL ES 3.0 renderer. Nothing else is needed to run it |

What is earned, spent, upgraded, claimed or opened is decided by pure functions in
[`data/Economy.kt`](client/app/src/main/java/io/github/projectwip/data/Economy.kt) (with the Cups each mode pays in
`Trophies` and the Spark Pass in `SparkPass`), applied to the save by `GameRepository` and `LocalGame`. The
Developer tab in Settings switches on a debug menu with drop luck, upgrade cost and hand-outs.

## Build & run

Requirements: **JDK 17 or 21** (Gradle 8.11 can't run on Java 24+), Android SDK with `platforms;android-35`,
`build-tools;35.0.0`, `platform-tools`. Android Studio is *not* required.

```bash
# local.properties must point at your SDK, e.g.  sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk
cd client && ./gradlew assembleDebug          # APK -> client/app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on the connected device
./gradlew testDebugUnitTest      # JVM tests: progression, economy, collision, full bot matches
```

Wireless debugging: `adb mdns services` lists devices advertising it; `adb connect <ip:port>` attaches.
On Xiaomi/HyperOS, ADB installs show an "Install via USB" prompt on the device that must be accepted.

Debug builds accept a start screen for automated testing:

```bash
adb shell am start -S -n io.github.projectwip/.MainActivity --es screen match   # or fighters|shop|track|settings
adb shell run-as io.github.projectwip cat files/save.json                        # inspect the save
```

## Changing the rules

Almost every number lives in two files:

* [`data/Balance.kt`](client/app/src/main/java/io/github/projectwip/data/Balance.kt) — fighters, stats
  (`StatLine(base, perLevel)`: e.g. damage `100 + 5/level`), upgrade costs, match length, reward formulas,
  bot difficulty reward bonuses.
* [`data/Catalog.kt`](client/app/src/main/java/io/github/projectwip/data/Catalog.kt) — Cup Track milestones, Shop
  items, daily gift, Glitch Drop tiers, odds and rewards.

Bot behaviour per difficulty: [`ai/BotProfile.kt`](client/app/src/main/java/io/github/projectwip/ai/BotProfile.kt).
The arena is ASCII: edit the quadrant in [`sim/Arena.kt`](client/app/src/main/java/io/github/projectwip/sim/Arena.kt).

## Roadmap (not promises)

* Layout editor for the touch controls, more fighters/arenas, music.

## License

Copyright (C) 2026 Talmeez Ahmad. AstroArena is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License, version 3](LICENSE), or (at your option) any later version. It comes with no warranty. If you share a version of it, you must share that version's source under the same license and keep this credit.
