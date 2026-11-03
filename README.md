# Language Learner (Fabric, Minecraft 26.3)

Every time an item goes into your inventory, a card pops up at the top of the screen with that item's name in the language you're learning. Say it out loud before the timer runs out, or the item is deleted from your inventory.

- Picked up from the ground: **10 seconds**.
- From chests, crafting, furnaces, trades, `/give` and so on: **20 seconds**. For chests and other blocks with a menu, the card appears when you close the menu, and only for what you actually kept. Putting something into an anvil or crafting grid and taking it back doesn't count.

- Works with **every language Minecraft has** (~130). The words come from Minecraft's own translation files.
- **Russian, Chinese and Japanese** (and any other non-Latin script) show the pronunciation under the word: *Алмазный меч → Almaznyy mech*, *钻石剑 → zuàn shí jiàn*, *ダイヤモンドの剣 → daiyamondo no ken*.
- Offline speech recognition (Vosk). The free model for your language downloads once the first time (40–130 MB). Nothing is sent online after that.
- Languages with no speech model use a typing fallback: type the word (or its romanization) in chat.
- While you speak, the pronunciation line turns **green** for the letters you got right and **red** for the ones you missed. What it heard is shown in Latin letters.
- Once you say a word correctly, it's **unlocked** and never asked again. Failed words show `NEVER LEARNED (0/x)`.

## 1. Build the jar

You need **JDK 25** (for example Eclipse Temurin 25 from adoptium.net).

Open a terminal in this folder and run:

```
gradlew.bat build        (Windows)
./gradlew build          (Mac/Linux)
```

The first build downloads Gradle, Minecraft and Fabric, so it takes a few minutes. The mod ends up at `build/libs/langlearn-0.4.0.jar`.

**No JDK?** Push this folder to a GitHub repo. The included GitHub Action builds it automatically, and the jar appears under *Actions → latest run → Artifacts*.

## 2. Install

1. Install **Fabric Loader 0.20.1+** for Minecraft **26.3**.
2. Put `langlearn-0.4.0.jar` and **Fabric API** (0.165.0+26.3 or newer) in `.minecraft/mods`.
3. Singleplayer works out of the box. For multiplayer, the server also needs the mod, because the server is what deletes the items.

## 3. Play

| Command | What it does |
|---|---|
| `/langlearn language ru_ru` | Pick the language (tab-complete shows all codes). Examples: `zh_cn`, `zh_tw`, `ja_jp`, `es_es`, `de_de` |
| `/langlearn languages` | List every language; `[voice]` means you can speak it |
| `/langlearn` | Status: language, words unlocked, speech model state |
| `/langlearn test` | Practice the item in your hand (nothing gets deleted) |
| `/langlearn timer pickup 10` | Seconds to answer for ground pickups (3–60) |
| `/langlearn timer other 20` | Seconds to answer for chests, crafting and everything else (3–60) |
| `/langlearn threshold 80` | How accurate you must be, in % (default 80) |
| `/langlearn mode auto\|typing\|both` | auto = voice if available, otherwise typing |
| `/langlearn grammar true\|false` | On by default: a second recognizer that can only answer with Minecraft item names, so it hears real words instead of gibberish |
| `/langlearn on` / `off` | Pause the mod (your items are safe while it's off) |
| `/langlearn reset` | Forget your progress for the current language |

**Microphone:** the mod records only while a card is on screen. Nothing is saved or sent anywhere. The small green bar on the card shows your mic level; if it doesn't move when you talk, Windows is using a different microphone. Quiet microphones are boosted automatically. On Windows, if nothing is heard, check *Settings → Privacy → Microphone → Let desktop apps access your microphone*, and that the right mic is the default input device.

Files live in `.minecraft/config/langlearn/`: `config.json`, your progress per language, and `models/` (the downloaded speech models).

## Notes

- Chinese is checked as pinyin **without tones**. The recognizer can't judge tones, but the pronunciation line shows them so you learn them.
- `zh_hk` (Hong Kong) uses the Mandarin model. There's no free offline Cantonese model.
- Voice models exist for: Russian, Chinese, Japanese, Korean, English, Spanish, German, French, Italian, Portuguese, Dutch, Polish, Turkish, Ukrainian, Vietnamese, Catalan, Czech, Swedish, Persian, Hindi, Esperanto, Kazakh, Georgian, Kyrgyz, Uzbek, Tajik and Breton. Every other language is typed.
- Creative mode only quizzes ground pickups, so taking items from the creative menu doesn't spam cards.

## How it works

```
server: ItemEntityMixin   →  player picked up N items  →  PickupPayload  →  client
        InventoryTracker  →  inventory gained N items (chest, crafting...)  →  PickupPayload
client: PromptManager     →  queue → card on screen (HudOverlay) → mic → Vosk (SpeechEngine)
        Matcher           →  romanize both words, fuzzy-compare, score 0–100%
        fail              →  FailPayload → server removes the N items
```
