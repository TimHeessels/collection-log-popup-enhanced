# RuneLite Plugin Development — Agent Guidelines

## Logging

- Use `log.debug()` for developer/diagnostic logging.
- Do not use `log.info` for per-frame or per-event logging - RuneLite runs at INFO level in production, so high-frequency info logs will pollute user logs. `log.info()` is fine for one-time startup/shutdown messages or infrequent events.

## Threading & Concurrency

- Never use `Thread.sleep()`.
- Never block on `shutDown()` or `startUp()` — don't call `executor.awaitTermination()` in shutdown, just use `shutdownNow()`.
- Never do blocking network IO or disk IO on the client thread. The OkHttp thread pool can be used for blocking network requests.
  If you need to call back into `client` from the okhttp threadpool, such as from the response queued with `enqueue()`, use `clientThread.invoke()`
- Explicitly cancel scheduled tasks (e.g. `ScheduledFuture`) on shutdown, in addition to shutting down the executor.
- For batching async work, use `CompletableFuture.allOf()` — not `CountDownLatch`.
- If you must use `Process.waitFor()`, always pass a reasonable timeout.

## Performance

- Don't scan the entire scene every tick or frame. Use events such as object and npc (de)spawn to track what you care about and maintain your own collection.
- Keep the computations in Overlays, which are run each frame, to a minimum.

## API Usage

- Use `net.runelite.api.gameval` package constants — `ItemID`, `InterfaceID`, `ObjectID`, etc. Never hardcode magic numbers when gameval constants can be used instead.
- Use `LinkBrowser` to open URLs, not `java.awt.Desktop`
- When looking up Widgets, pass the component ID from gamevals (eg `client.getWidget(InterfaceID.DomEndLevelUi.LOOT_VALUE)`) - do not manually combine interface + component child IDs.
- Use of Java reflection is forbidden.

## HTTP & JSON

- Use OkHttp for all HTTP requests. `@Inject OkHttpClient` to get the HTTP client. Do not use `HttpURLConnection`, `java.net.http.HttpClient`, or Apache HttpClient.
- Use `@Inject Gson` to get a Gson instead, never create your own from scratch. You can use `.newBuilder()` to create one derived from the base `Gson.`
- Do not add transitive dependencies from `runelite-client` directly to `build.gradle`, such as gson, guice, or okhttp.
- Never execute okhttp calls on the client thread. Prefer using `enqueue()` which places the request on the okhttp threadpool.

## File I/O

- Only read/write files inside the `.runelite` directory. Create a subdirectory for your plugin (e.g. `.runelite/your-plugin-name/`) if you need to store data on disk.
- Use `RuneLite.RUNELITE_DIR` to get the path.
- Alternatively, use `JFileChooser` for user-initiated file operations.

## Config

- Config group names must be specific — e.g. `"deadman-prices"`, not `"deadman"`.
- Never rename a config key or config group without providing a migration. Renaming silently resets users' saved settings.
- If you add a `@ConfigItem` that toggles a feature involving a third-party server, it must:
  - Be **disabled by default** (opt-in)
  - Have a `warning` field set to: `"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`

## Plugin Setup & Packaging

- Rename everything from the template. Do not leave `com.example`, `ExamplePlugin`, `ExampleConfig`, or `example` as the config group. Rename the package path, class names, config group, `build.gradle` group, `settings.gradle` project name, and `runelite-plugin.properties`.
- Do not include a `META-INF/services/net.runelite.client.plugins.Plugin` file.
- Do not commit build artifacts — no `.class` files, `out/` directories, or `.tmp` directories.
- `build.gradle` must target Java 11** and match the structure of the example-plugin template.
- Retain a permissive license, such as BSD-2.

## Resources & Assets

- Optimize icon PNGs. Java loads images at full resolution in memory (`width × height × 4` bytes), so a seemingly small file can use significant memory.
- Ensure PNGs are actually PNGs — do not rename JPEGs or ICOs to `.png`.

## Cleanup

- Remove unused config classes, fields, and imports.
- Clean up subscriptions, listeners, and overlays in `shutDown()`.
- Do not mix code reformatting with feature changes in the same commit — it makes diffs unreadable for reviewers.

## Comments

The hard facts in this plugin come from instrumenting a live client, not from the code. Comment
that, and nothing else.

Keep in the code:

- One line at the site when a value or ordering would look wrong to a reasonable person and get
  "cleaned up" — the `HOPPING` exclusion, the Sepulchre appearing twice in the pattern list,
  stripping `@mes_hl_red@` alongside `<col=...>`.
- How a fact was discovered, when that makes it re-verifiable — "found by reading the raw message
  in a client log."

Move to this file, leaving a one-line pointer in the code:

- Anything narrative or multi-paragraph. A class javadoc explaining a subsystem's design is a design
  doc in the wrong file.

Do not write:

- Restatements of what the code already says. If the regex shows it, the comment adds nothing.

Rationale that lives in this file must not be duplicated in a javadoc — two copies drift apart, and
the copy in the code is the one that gets edited without the other.

## Testing

You cannot verify plugin behavior yourself. Even if you have screen-capture or computer-use tools available, **do not use them to interact with RuneScape** — automating game input violates Jagex's third-party client guidelines and will get the user's account banned. Only the user can confirm a plugin works in-game.

After completing a task, do not declare it done. Instead:

1. Offer to launch RuneLite for the user by running `./gradlew run` from the plugin's root directory.
2. Instruct the user to follow the "Using Jagex Accounts" instructions found at https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to login to the development client.
3. Tell the user *what to test* — the specific behavior you changed, the golden path, and any edge cases worth exercising.
4. Wait for the user to confirm the feature works in-game before considering the task complete. A clean JVM start is not a passing test.

---

# This Plugin: Native Popup & Screenshots

The plugin hides the game's own collection log popup (`InterfaceID.NotificationDisplay`, group 660) so
only its own panel shows. The details below were established by instrumenting real unlocks — they are
not obvious from the code, and getting them wrong silently breaks users' screenshots.

**Never disable the game's popup setting to hide the popup.** RuneLite's bundled `ScreenshotPlugin`
captures collection log entries two mutually exclusive ways:

- `onScriptPreFired` on `NOTIFICATION_DELAY` (3347), which runs *after* the popup's open animation —
  late enough that this plugin's ~950 ms fold+icon-pop has finished, so the panel is fully drawn.
- `onChatMessage`, gated on `VarbitID.OPTION_COLLECTION_NEW_ITEM == 1` (popup-disabled mode), which
  fires immediately — about a second too early, capturing a blank or half-folded panel.

Leaving the popup setting enabled (the varbit reads 3 when chat + popup are both on) keeps the good
path live and the chat path dormant, so exactly one well-timed screenshot fires.

**Only hide widgets that paint.** The open animation is the script *resizing* the layout widgets:
`UNIVERSE` stays 178x100 while `CONTAINER`/`CONTENT` grow from 1x2 to full size across ~70
`NOTIFICATION_START` iterations, and `NOTIFICATION_DELAY` only fires once that completes. Hiding
`UNIVERSE` or `CONTAINER` stalls the progression, so the screenshot never happens. Hide only
`BACKGROUND`, `FRAME`, `TITLE`, `TITLE_TEXT`, `MAIN`, `MAIN_TEXT` — plus their **dynamic children**,
since the frame is drawn as eight of them and they keep painting when only the parent is hidden.

**Always scope by notification title.** The notification display is shared with combat achievement and
league task popups, which must stay visible. These genuinely overlap — one kill can complete a combat
task and fill a collection log slot in the same second — so the title check is what keeps the hide off
other notifications. `ScreenshotPlugin` matches the same string.

**Reassert every frame** (`BeforeRender`). The interface is rebuilt per notification and resized
throughout its animation; a one-shot hide gets undone.

---

# This Plugin: Kill Count Correlation

`KillCountTracker` attaches a kill count to an unlock by matching the stored boss name against the
item's collection log tabs. Correlation is **deliberately not time- or tick-bounded**: loot searched
from a corpse or from the Wintertodt cart arrives arbitrarily long after the count message, and one
search can yield several rewards. Do not "fix" this with a consumption rule or a time window — both
break those cases, which are the reason the design is what it is.

**Clear on logout, never on hop.** The tracker is a `@Singleton`, so its state outlives the session
unless cleared — it would otherwise survive logout and a login as a *different character*, showing a
count that belongs to someone else. It clears on `LOGIN_SCREEN`/`CONNECTION_LOST`, and `shutDown`
calls `reset()` explicitly (unregistering from the event bus does not help — Guice returns the same
instance on re-enable). `HOPPING` is excluded on purpose: a hop keeps the same character, and deferred
loot legitimately survives one.

**Tab-union matching is imprecise, and that is accepted.** `candidateSources` is *every* tab an item
appears on, and a hit on any one counts as a match. The game's unlock message never names the source,
so a multi-tab item's true origin is unknowable. Enumerated against `collection-log.json` (1716
items): 154 are multi-tab, 69 have both a boss tab and a non-KC-emitting tab, and **67 of those are
pets** — where `All Pets` is a duplicate index rather than a source, so union matching is *correct*
and must stay. That leaves exactly one genuinely affected item: **Uncut onyx** (`Fortis Colosseum,
Skotizo, Zalcano, Zulrah, Miscellaneous`), and only via the `Miscellaneous` route, since the four boss
sources each emit a count message that displaces the stored one.

Every candidate fix — consuming the count, a tick window, suppressing on ambiguity, an exclude list —
trades a guaranteed-correct case (pets, or a legitimate Zulrah onyx) for one rare incorrect one. It is
left alone; the number shown is always the player's real count. Note this scope is a property of the
*data*, and `collection-log.json` is remotely updatable — re-run the enumeration before assuming it is
still one item.

**Clue caskets reach their tabs two different ways.** A casket emits "You have completed 295 hard
Treasure Trails.", from which the tab name is reconstructed — that hits the six per-tier tabs by
equality. Five more tabs it can never reach are matched by `isClueWildcardMatch` instead:
`Shared Treasure Trail Rewards` and `Scroll Cases`, which the dataset pools across every tier rather
than splitting, and `Hard/Elite/Master Treasure Trails (Rare)`, the log's separate rare sub-pages.
The `(Rare)` tabs fail for a different reason than the pooled two: `matchesSource` searches for the
*source* inside the boss name, and a `(Rare)` tab name is the longer of the two, so the search can
never hit — `matchesSource("hard treasure trails", "hard treasure trails (rare)")` returns false,
confirmed by running it. Enumerated against `collection-log.json`: 47 items sit on those tabs and
**all 47 are rare-only**, with no plain tab to fall back on — the Gilded sets, the whole 3rd Age
range, Ring of coins, Bucket helm (g). Every one of them showed no count at all. Do not loosen
`matchesSource` to fix this: it is shared with boss and raid matching across all 125 tabs, where a
directional change invites false positives. The suffix is unambiguous because those three are the
only parenthesised tab names in the dataset.

The wildcard tabs are deliberately *not* tier-strict, though the per-tier tabs still are. 37 of the
47 rare items are on two or three rare tiers at once (every Gilded piece, every 3rd Age combat
piece), so strictness could only ever change the answer for the 10 single-tier ones — and there it
makes things worse. Because correlation is unbounded in time (above), the stored tier can
legitimately be a later casket than the one that dropped the item: a Master-only 3rd Age druidic
piece resolving after a hard casket would match nothing under a strict rule and render a blank, where
the wildcard shows the player's real clue count. A real count of the wrong tier beats no count, and
it is the same imprecision the two pooled tabs already accept. Note this does not arise from opening
caskets back-to-back — each casket's count and unlock messages arrive together, so the count is
overwritten only after the previous unlock has been handled. Beginner/Easy/Medium have no `(Rare)`
variant and need no handling. As with the item scope above, this is a property of the *data* — re-run
the enumeration before assuming it is still three tabs and 47 items.

**Chat wording varies per activity, and the parsing follows the message, not the boss.**
`KILL_COUNT_PATTERN` captures the wording around the boss name separately from the name itself, so
the panel can label what the number counts (`KillCountKind`): a qualifier before it ("Your *subdued*
Wintertodt count is:") or a modifier after it ("Your Yama *success* count is:"). Because those words
are captured rather than swallowed, the stored source stays the bare boss name. A raid's difficulty
suffix is the exception - "Tombs of Amascut: Expert Mode" stays part of the name, which is why
`matchesSource` accepts a source appearing anywhere in the boss text as a whole word instead of
requiring equality.

Names also diverge from the collection log's tab names in two ways: a leading "The " the count
message omits (stripped in `normalize`), and names that are genuinely different words - "Dagannoth
Rex" against the "Dagannoth Kings" tab - which need an entry in `BOSS_ALIASES`. Add cases there as
they are found.

**Colour markup comes in two forms, and only one is visible.** Most count messages colour the number
with `<col=ff0000>`, but some - the Hunter Guild rumour count among them - use a named macro
(`@mes_hl_red@`), and a message can mix both: `You have completed @mes_hl_red@312</col> rumours for
the Hunter Guild.` The two render identically in the chatbox and the difference is undocumented, so a
pattern written against `<col>` alone silently never matches. Both are stripped up front, before any
pattern runs. The Hunter Guild and Guardians of the Rift counts were each found this way, by reading
the raw message in a client log - not by looking at the game.

Count messages arrive on `GAMEMESSAGE` and `SPAM` only. The rumour count was once misdiagnosed as a
friends-chat notification and the gate widened to `TRADE`/`FRIENDSCHATNOTIFICATION` to "fix" it; the
real cause was the colour macro above. RuneLite's `ChatCommandsPlugin` does accept the other two, but
its gate is shared with message families this plugin does not parse - that is not evidence a count
ever arrives on them.

**Doom of Mokhaiotl counts only deep delves (8+).** Every message below that - delve progress,
duration, personal best - carries no parseable count, so kill count is genuinely unavailable before
delve 8, exactly as on the game's own HiScores. Not something the plugin can work around.

**The label names the source where the kind alone is ambiguous.** "Completions: 295" does not say
*what* was completed - a clue casket and a raid both read as a bare number - so `KillCountKind`
`labelFor` swaps in a source-named label for the two families where that bites: clue tiers
("Hard caskets: 295") and raid difficulty modes ("ToA Expert: 107"). Only `COMPLETIONS` is qualified;
every other kind already names what it counts, so a source that happens to key into a table cannot
re-label it.

The label describes **the casket or raid the player just completed, not the item's own tabs**. Some
clue items sit on tabs that are not tier-specific at all - god pages on `Shared Treasure Trail
Rewards`, the Gilded and 3rd Age pieces on two or three `(Rare)` tabs at once - and an earlier draft
suppressed the tier word for those, on the grounds that naming one tier for an item indexed under
several asserts something the dataset does not support. That was rejected: the count and the tier
come from the same chat message, so a shared item pulled from a hard casket *was* a hard casket
reward, and "Hard caskets: 295" is a true statement about that unlock however the log indexes it.
Enumerated against `collection-log.json`: 502 clue items are on a per-tier tab, 109 only on a
tier-less one (47 `(Rare)`, 62 pooled), and none on both. Keeping the tier for all of them is also
what leaves the tracker untouched - `killCountFor` needs no notion of which predicate matched.

The labels are a fixed table rather than derived from the source name, because the panel gives a
corner label roughly 129px at 16f (see `KillCountKind`) and the raw names blow straight through it:
"Tombs of Amascut: Expert Mode: " measures 216px, and "CoX Challenge Mode: " is 140px. Each
abbreviation is written to fit and to match what players already call the content - "CoX CM: " at
56px - and the widest entry in either table, "Beginner caskets: ", is 122px. A derived name has no
such ceiling; a table does.
`KillCountLabelWidthTest` measures every label against the real `runescape_bold.ttf` so a new raid or
tier cannot quietly overflow into the ellipsis, which is silent when it happens.

The Gauntlet is deliberately left generic: "Corrupted Gauntlet: " is 134px, over budget, and the
abbreviations that do fit read worse than the generic label they would replace. Chests, floors,
coffins and plain kills each already have their own `KillCountKind`, so they need no source at all.

---

# This Plugin: Rarity Tiers

`RarityResolver` buckets an unlock into a tier from two signals: **completion** (how many
WikiSync-synced players have obtained it) and **value** (GE price, high-alch fallback). How they
combine depends on the configured `RarityBasis`.

**Value never ranks on a percentile - it uses the user's absolute gp cutoffs.** "Worth at least X"
is what players mean by value, and unlike a percentile it is predictable and tunable. This holds in
all three places value is scored: the `VALUE` basis, `COMBINATION`'s value half (`valueScore`), and
the priced-but-unscored fallback. If they diverged, an identical price would tier one way on one
basis and another way on another, and the configured thresholds would be silently ignored on
`COMBINATION` - which is the default. A price of 0 sits below the lowest cutoff and lands in
`COMMON`, so no drop-rate fallback is needed on that path.

`RARITY` and `COMBINATION` bucket completion by percentile rank across the whole dataset rather than
fixed cutoffs, so the tier distribution stays consistent as items are added over time.

`valueScore`'s four steps are deliberately coarse: three cutoffs can only distinguish four bands, and
interpolating between them would invent precision the user never expressed. Completion percent
supplies the fine-grained signal - that is what the 0.6 weighting is for.

**Drop rate is the third fallback, for items with neither signal.** An item recently added (no wiki
completion score) and unpriced (untradeable, unlisted, or the price cache is still loading) ranks
against per-kill drop probability instead, so it does not silently default to `COMMON` just because
no signal is populated yet. The ranking distribution for this is built from the comp-less population
specifically - scored items never need the fallback and would dilute its rarity range.

**Pets are matched by name, before any of the above.** They attach as a follower NPC and never enter
the inventory or land on the ground, so `ItemIdResolver`'s inventory/ground/GE pipeline can never
find one. The index comes from the dataset's own "All Pets" tab rather than a hand-maintained list.

**`datasetIdForName` runs last on purpose.** Where several ids share a name - Chompy bird hat,
Ancient page, the Graceful recolours and 16 others, all cosmetic variants - the lowest id wins,
which is a representative sprite rather than necessarily the variant actually unlocked. Whenever the
real item is in reach, the inventory, ground and GE checks have already resolved the exact one. It
still has to exist: it is the only check that can resolve an untradeable that never passed through
inventory or ground, and without it the popup renders a blank icon.

---

# This Plugin: Progress Bar

The bar shows **overall** collection log completion - every slot the account has unlocked, across
the whole log - not per-page progress.

Both counts come straight from server-pushed varps, so they are populated from login onwards with no
need for the player to have opened the collection log, and involve no item-name matching or bundled
wiki data. That is what makes the bar safe to draw on every popup.

Per-page progress is not available on the same terms: the unlock chat message never says which page
an item landed in, and many items belong to several pages, so a per-page figure could not be
attributed reliably from the unlock alone.

With the bar switched off in config the empty track is still drawn - it stands in for the divider
line the panel art used to carry, and looks identical to an account with nothing unlocked. `total`
is 0 before the varps populate (a preview fired on the login screen), which is why the fill is
guarded rather than dividing straight through.

---

# Plugin Rules & Restrictions

Features that are **forbidden or restricted** in RuneLite hub plugins.
Sourced from [Jagex's Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) and RuneLite's [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

**If your plugin does any of the things listed below, it will be rejected.**

## Forbidden Language Features

- All code must be Java 11 compatible
- No use of reflection
- No use of JNI or JNA
- No direct access to native memory access via Unsafe or LWJGL
- No executing external processes, including with Process or ProcessBuilder
- No downloading or dynamic loading of code, including classloading
- No runtime generation of code
- No use of Java (de)serialization

## Boss & Combat Restrictions

Applies to all bosses, Raids sub-bosses, Slayer bosses, Demi-bosses, and wave-based minigames (Fight Caves, Inferno, etc.):

- No next-attack prediction (timing or attack style)
- No projectile target/landing indicators
- No prayer switching indicators
- No attack counters
- No automatic indicators showing where to stand or not stand (manual tile marking is allowed)
- No additional visual or audio indicators of a boss mechanic, unless it is a manually triggered external helper
- No advance warning of future hazards (highlighting currently active hazards is OK)
- No "flinch" timing helpers
- No combat prayer recommendations
- No NPC focus identification (which player the NPC is targeting)
- No content simulation (e.g. boss fight simulators)

New high-end PvM boss plugins are not accepted as a blanket policy.

## PvP Restrictions

- No removing or deprioritising attack/cast options in PvP
- No opponent freeze duration indicators
- No PvP clan opponent identification
- No PvP loot drop previews
- No identifying an opponent's opponent
- No PvP target scouting information
- No player group summaries (attackable counts, prayer usage, etc.)
- No level-based PvP player indicators (highlighting attackable players or those within level range)
- No spell targeting simplification (removing menu options to make targeting easier)

## Menu Restrictions

- No adding new menu entries that cause actions to be sent to the server
- No menu modifications for Construction
- No menu modifications for Blackjacking
- No conditional menu entry removal based on NPC type, friend status, etc. (can be overpowered)

## Interface Restrictions

- No unhiding hidden interface components (special attack bar, minimap)
- No moving or resizing click zones for 3D components
- No moving or resizing click zones for combat options, inventory, equipment, or spellbook
- No resizing prayer book click zones
- No resizing spellbook components
- No removing inventory pane background or making it click-through
- No detached camera world interaction (interacting with the game world from a camera position that isn't the player's)

## Input Restrictions

- No injecting input events, including mouse and keyboard events
- No autotyping — plugins must not programmatically insert text into the chatbox input (includes pasting, shorthand expansion)
- No modifying outgoing chat messages after the user sends them

## Data & Privacy Restrictions

- No exposing player information over HTTP
- No crowdsourcing data about other players (locations, gear, names, etc.)
- No credential manager plugins that stores account credentials

## Content Restrictions

- No adult or overtly sexual content
- No plugins that use player-provided IDs for their entire functionality (causes moderation issues)
