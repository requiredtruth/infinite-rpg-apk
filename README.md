# Infinite RPG

Current release: **0.5.3 Model Bank & Prompt Repair**

The world includes exact tap-to-position building placement with confirmation, separate icon-only Inventory and Crafting controls, empty house interiors, randomized enterable caves, ambient wildlife, anchored clustered scenery, multiple house sizes, and deterministic named world saves. Audio now contains 50 original adventure themes: 30 completely new tracks and 15 substantially remixed classics, plus separate ambient-sound and music controls.

Infinite RPG is a native, offline-first Android 2D exploration/crafting RPG whose local world catalog grows while you play. AI Workshop now offers four downloadable GGUF profiles and local GGUF import. The recommended default is **Qwen2.5-0.5B-Instruct Q8_0** because it is instruction-tuned for structured JSON. Models are kept as a reusable bank in `Documents/Infinite RPG/Models/`; the selected model is staged privately for reliable native memory mapping.

## What is implemented

- Infinite deterministic map with continuous biome crossfading, high-resolution terrain materials, no hard biome seams, no edge, and the same world seed after restart.
- Live AI-authored biomes, tile palettes/patterns, materials, items, recipes, creatures, quests, structures, weather, and music definitions.
- Bag interface with visible persistent inventory, ingredient counts, functional recipes, and a build-placement tab.
- Separate icon-only Inventory and Crafting buttons; Inventory uses a compact square-icon grid while recipes keep their detailed cards.
- Build placement opens a live world preview: tap the exact location, then Confirm or Cancel before materials are consumed.
- Craftable and placeable campfire, timber house with working door and separate interior, persistent storage chest, bedroll, torch, fence, table, and cooking pot.
- Campfire cooking, persistent placed structures, chest transfers, dynamic weather, and a 25-minute light cycle: 15-minute day plus 10-minute night.
- Passive woolhorns, rabbits, boars, and chickens with directional walk animation, combat health, resource drops, feeding, mate matching, breeding cooldowns, and babies.
- Deterministic world-anchored trees, flower clusters, berry shrubs, rock variants, flying birds, ambient wind/bird/insect audio, and naturally generated cave entrances. Scenery uses stable absolute cells, regional forest/meadow/scrub clustering, structure clearings, and natural open trails. Caves are separately enterable and may contain a persistent chest with zero to two items.
- Small cabin, medium timber cottage, and grand lodge variants. Every newly built house starts empty and has a working exit door.
- Craftable Sunstone Clock: the HUD shows general day/night without it and exact simulated time while carrying it.
- Named deterministic worlds with editable seeds, manual save/load, two-minute autosaves, and complete JSON snapshots in `Documents/Infinite RPG/`. Snapshots include player/world state, inventory, structures, chest contents, mobs, AI-generated content, and disable controls.
- Exact pixel renderer: AI item/tile recipes contain a six-color palette plus sixteen rows of sixteen pixels. Every accepted pixel becomes part of the actual texture.
- Production art atlases for terrain, item/crafting icons, survival resources, three house sizes, cave entrances, a sixteen-frame four-direction semi-realistic player, and four passive animals with fully bounded front/left/right/rear cells.
- Layered stereo soundtrack with eight-section forms, four long-range movements, alternate chord/melody/bass/rhythm material, counter-melodies, fills, dynamics, panning, and cross-delay. Distinct flute, whistle, ocarina, strings, clarinet, brass, bell, celesta, choir, accordion, marimba, guitar, harp, and pizzicato families combine with varied orchestral, brush, march, waltz, clockwork, festival, and hand-drum percussion.
- Fifty original tracks rotate every five minutes while continuing to evolve within each performance. Thirty are new for 0.5.0 and fifteen of the original twenty were remixed with new forms, motifs, instrumentation, and percussion.
- Required-content-first queue. Initial targets include biomes, materials, items, recipes, tile styles, creatures, quests, 50 music pieces, structures, and weather.
- Endless novelty queue begins after the required catalog is complete.
- Two-pass validation: Qwen drafts, then judges the draft against the prompt and existing catalog.
- Deterministic local validation and uniqueness constraints remain authoritative even if the model approves a bad result.
- SQLite WAL catalog, atomic job claiming, unique `(type, identity_key)` index, and token-signature similarity rejection.
- Separate Start/Stop download controls, exact byte progress bar, automatic resume after network interruption/restart, resumable `.part` file, GGUF validation, and atomic final rename.
- Crash-contained foreground generator process with a visible notification. A native model failure can no longer terminate the game process; an interrupted pass is detected on restart and automatic generation pauses until the user explicitly starts it again.
- Quality runtime: Q8_0 weights, a 1,280-token context on phones below 6 GiB total RAM, a 1,536-token context on larger devices, one inference thread, a 420-token draft cap, a 112-token judge cap, compact prompts, and strict JSON grammar-constrained sampling. There is no fixed free-RAM cutoff; generation pauses only when Android reports critical memory pressure.
- Buffered JNI token streaming safely assembles UTF-8 fragments off the UI thread and publishes snapshots every 250 ms. The dedicated AI Stream tab shows the exact active prompt, live model text, token count, tokens/second, pass, status, phase, and active job.
- AI Stream no longer forces the view to the bottom on every token update. It preserves the user's reading position and provides an explicit **Latest** button.
- Model manager presets: Qwen2.5 0.5B Instruct Q8 (recommended structured JSON), Qwen3 0.6B Q8, SmolLM2 360M Instruct Q8 (fastest), and Llama 3.2 1B Instruct Q4_K_M (strongest/largest), plus arbitrary local GGUF import.
- Per-model context, output-token limit, thread count, temperature, and ChatML/Llama-3/plain prompt-template settings are visible and editable. Switching models unloads the old native model before staging the new selection.
- A corner rotate button switches between portrait and landscape and remembers the choice.
- Prompts now use each model's actual instruction template and natural-language field/type contracts instead of a pseudo-schema the model could imitate. Invalid drafts receive one constrained repair pass before the job is retried.
- Separate Start/Stop generation controls and immediate Generate Now action.
- AI Workshop screen: model/download state, queue, current phase/job, accept/reject log, recent creations with art previews, memory, thermal state, and generation controls. The separate AI Stream screen provides the full live prompt and model output without squeezing the telemetry columns.
- In-app creation notices show the new content's preview, type, name, and description as soon as it is accepted. Recent AI creations can be disabled, re-enabled, or deleted; deletion queues a genuinely different replacement.
- Custom adaptive/round icon in every launcher density.

## Build an installable APK

On a 64-bit Linux machine with Java 17, `git`, `curl`, and `unzip`:

```bash
chmod +x install.sh run.sh cli.sh android-build.sh build-release.sh
./run.sh
```

The PySide6 build manager runs installation automatically. Choose **Build APK**, or run `./cli.sh build`; `android-build.sh` downloads an isolated Android SDK/NDK, pinned llama.cpp source, and Gradle into `.tools/` without modifying the system installation. It builds:

```text
Infinite-RPG-debug.apk
```

Install over USB:

```bash
.tools/android-sdk/platform-tools/adb install -r Infinite-RPG-debug.apk
```

Choose a model in AI Workshop and press **Use model**. If it already exists in `Documents/Infinite RPG/Models/`, it is reused; otherwise the preset downloads with resumable progress and is then banked there. **Import GGUF** copies any local GGUF into the same bank. The model is deliberately not embedded in the APK.

## Phone behavior

The defaults target a Moto G Play–class device with 4 GB RAM:

- Qwen2.5-0.5B-Instruct Q8_0 is the recommended default; Qwen3, SmolLM2, Llama 3.2, and imported GGUF files are selectable alternatives.
- Default 1,280-token context, 420-token draft cap, one thread, and 0.32 temperature; safe limits are editable per model.
- One low-priority inference thread in a dedicated process.
- One inference at a time; memory-mapped model; no duplicate model copy.
- A generation attempt every two minutes when Android is not reporting critical memory pressure, the phone is cool, and the active game world is not on screen.
- Below 20% battery, inference waits unless charging.

Android will always show a foreground-service notification while background generation is active. This is intentional: modern Android does not permit honest, unlimited invisible background CPU work.

## Source map

- `game/GameView.java`: infinite map, placement preview/confirmation, scenery, caves, time/weather, directional player and mobs, interiors, combat, feeding, and touch controls.
- `audio/ProceduralMusic.java`: real-time generated soundtrack.
- `data/ContentRepository.java`: catalog, jobs, inventory, recipes, structures, chests, mobs, saves, logs, controls, and deduplication.
- `ai/WorldGeneratorService.java`: lifecycle, throttling, automatic download, generation loop.
- `ai/GenerationEngine.java`: draft/judge prompts, JSON extraction, validation.
- `cpp/native_llama.cpp`: llama.cpp JNI inference runtime.
- `ui/SettingsView.java`: live local-model and generation control screen.
- `data/WorldSaveManager.java`: MediaStore world snapshots under Documents/Infinite RPG.
- `audio/AmbientSoundscape.java`: lightweight procedural wind, birds, insects, and woodland ambience.

## Privacy

Generation and gameplay stay on the device. Network requests are limited to user-selected/resumable GGUF downloads from the model preset sources. There is no account, telemetry endpoint, advertising SDK, or cloud inference.

## Support

Donations can fund additional production time and may request priority for a compatible direction through the funded-direction issue template using a public transaction hash. They do not guarantee implementation or purchase ownership, returns, deadlines, or support. See [SUPPORT.md](SUPPORT.md) and verify the asset and exact network before sending.


## Standard launcher

`./run.sh` is the normal desktop entry point. It auto-runs `./install.sh` when needed and opens a PySide6 build/status console with live logs and actions for validation, repair, stopping jobs, and APK builds. Use `./cli.sh` for terminal-only status, tests, debug builds, or release builds. APK deliverables remain published through GitHub Releases.
