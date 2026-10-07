# CLAUDE.md

AstroArena, client-only edition (the package id is still `io.github.projectwip`, so saves carry over): original
mobile 3D arena brawler for Android (landscape, touch, bots). Kotlin + Compose menus + custom OpenGL ES 3.0
renderer, no engine. All art and sound is generated in code and must stay original: no Brawl Stars/Supercell
assets, names, icons or UI copies. Fighters have first names only. Players see "Spark Drops", "Power Ups" and
"Crystals"; the code still calls them capsules, bolts and prisms.

There is no server and no network access: the app has no INTERNET permission. Everything the old Python game
server decided is done on the device (see "Game rules" below). Don't add networking back.

## How to work here

- Read what is necessary for the task you are trying to accomplish, and then stop reading. Don't go on long
  reading sprees, and don't keep reading and re-reading: it takes a lot of time. Be reasonable about it: find the
  spot, read that part, and start changing things. Don't re-read a file you have already read, or what this file
  already explains.
- Never go quiet for minutes. Say in a line what you are about to do before a long stretch of reading or building,
  and show progress as pieces land (a first edit, a passing test, a screenshot).

## Build

- Use the portable JDK (system Java 25 breaks Gradle 8.11):
  `export JAVA_HOME="/c/Users/gamer/AppData/Local/Android/tools/jdk-21.0.12.1+1"` and
  `export ANDROID_HOME="/c/Users/gamer/AppData/Local/Android/Sdk"`
- The Android project lives in `client/`: run Gradle from there. `./gradlew testDebugUnitTest` (add `--tests '*Name*'` for one) · `./gradlew assembleDebug`.
  `println` from tests lands in `client/app/build/test-results/testDebugUnitTest/*.xml`.

## Device

- adb is at `/c/Users/gamer/AppData/Local/Android/Sdk/platform-tools/adb`; the tablet is on wireless debugging
  (`adb mdns services`, the port changes). Set `MSYS_NO_PATHCONV=1` for `adb shell`.
- Start a screen directly: `adb shell am start -S -n io.github.projectwip/.MainActivity --es screen match`
  (`match|boss|train|fighters|roster|kito|varun|shop|road|pass|track|settings|result|news`; `roster` is the fighter grid with every model shown unlocked, `tryvarun` the Training Area as Varun, or `haul` to preview an "open all", or `capsule0`..`capsule5` to preview a capsule opening, suffix `s` splits into eight, `f` gives a fighter, `b` a bundle). Save file: `adb shell run-as io.github.projectwip cat files/save.json`.
- UI changes must be checked with a screenshot (`adb exec-out screencap -p`) and `adb logcat -b crash -d`.
- The tablet is the user's everyday device. Before every `input tap` or `am start`, confirm
  `dumpsys window | grep mCurrentFocus` shows `io.github.projectwip` or the home screen (`com.miui.home`): on
  the home screen the tablet is free, so starting the game is fine. In any other app, skip it and say so.
- Phone-size check: `wm size 1080x2400 && wm density 420`, then always `wm size reset` / `wm density reset`.

## Rules of the codebase

- `sim/`, `ai/`, `data/` and `audio/SfxSynth.kt` are pure Kotlin (no Android imports, except `SaveStore` and `GameRepository` in `data/`); they run in JVM tests.
- Humans and bots drive fighters through the same `Control`. Visibility goes through `World.isVisibleTo`.
- The starter fighter is Byte (`FighterId.BYTE`, `STARTING_FIGHTER`); Juno was removed, by the user's decision, and
  accounts that had her lost her. Byte's kit is modelled on a familiar shotgun brawler at the user's request; her
  name, look and words are ours and must stay so.
- Boss Mode bosses are their own things (`BossKind`, `Balance.bosses`), not giant fighters: each fights through
  moves of its own in `sim/Boss.kt` (telegraphed ground hazards, sweeps, rings, charges) and has its own model.
  Keep their names, looks and moves original.
- Team code must not assume two teams when `rules.freeForAll`.
- Every fighter has a hyper (`Control.hyper`, the `HYPER_*` numbers in `Balance.kt`): a third button that charges from
  main-attack hits. Shields are a share of health (`SHIELD_FRACTION`), and there are none in Boss Mode (`World.shields`).
- All balance numbers live in `data/Balance.kt` and `data/Catalog.kt`; the rules for earning, spending and claiming
  are pure functions in `data/Economy.kt`.
- New save field: update both `toJson` and `fromJson` in `SaveStore`, with an `opt*` default.
- Changed a sound: bump `CACHE` in `audio/Sfx.kt`, or devices keep the old WAVs.
- Mesh triangle winding matters (outlines are inverted hulls). Sim (x, y) maps to world (x, 0, y).
- The lobby `TextureView` must be removed during matches.

## Game rules (all on the device)

- The save (`SaveData`, kept by `GameRepository`) is the player's whole account: Cups, Spark Drops, Bolts, Prisms,
  Credits, Glory, fighters (unlocked, level, colourways, own Cups), Cup Track claims, the daily gift, today's
  offers bought, the Spark Pass, and deals made with the Offer Creator.
- `Economy` (pure, tested in `EconomyTest`) is the only place anything is bought, upgraded, claimed, rolled or
  settled. Each rule takes a `SaveData` and returns a `Done(newSave, value)` or throws `Refused`. `LocalGame` turns
  those into the calls the menus make, through `GameCall` (`ui/GameCall.kt`): `ask({ buy(key) }) { reward -> ... }`.
  Menus read the shop, the pass and the clock from `rememberAccount(save)`.
- A match is played on the device and its report is its result: `GameRepository.applyMatch` settles it with
  `Economy.settleMatch` (Bolts, Crystals, Credits, Pass points, fighter Cups, Spark Drop) and `Progression.applyMatch`
  (Cups, drop count, stats). Cups per mode are `Trophies`; they don't depend on bot difficulty. The Training Area
  pays nothing.
- Prices and tables the menus show (`Balance.kt`, `Catalog.kt`) are the ones `Economy` charges, so there is one copy.
  A new fighter or skin needs a place on the Spark Road (`SparkRoad` in `Catalog.kt`, from rarity) and a price.
- Fighters are unlocked on the Spark Road with Credits (or bought with Crystals): drops and the Cup Track pay
  Credits, never a fighter. Credits are not a wallet and must never be shown as one: they go straight onto the
  road toward the next fighter along it (a fixed order; rarity decides the cost), and become Glory, a cosmetic rank, once
  every fighter is unlocked. The Spark Pass is `SparkPass` (28-day seasons, 30 tiers of 100 points).
- The Spark Road and Spark Pass are our own take on a familiar idea. Keep their names, art and layout original.
- Days are this device's calendar days (`GameRepository.today`); the day's offers are a fixed shuffle of
  `Economy.dailyPool` by day number, and they change at local midnight.
- Settings > Developer switches on the "D" debug menu (drop luck, free drops, upgrade cost, hand-outs) and the Offer
  Creator. Cheats only count while that switch is on.
- There is no 1v1, team play, leaderboard, update check or News from a server: News is the list in `data/News.kt`.
  Add an item there when a change shows players something new.

## Git

- Checkpoint as you go: once a piece is verified (tests or device), commit just that piece and
  `git push origin main`. Never checkpoint unverified or non-compiling work.
- **Never commit changes the user made on their own.** Commit only what you changed in this task, file by file
  (`git add <path>`, never `git add -A` or `git commit -a`). Anything else that shows up in `git status` is the
  user's: leave it uncommitted and untouched, and mention it.
- **The version is "Beta", and it stays "Beta".** `versionName = "Beta"` is all players see; `versionCode` counts builds.
- `gh` needs normal path conversion: don't run it with `MSYS_NO_PATHCONV=1` set.
- End commit messages with the co-author line used in history. `screenshots/` is gitignored scratch.
