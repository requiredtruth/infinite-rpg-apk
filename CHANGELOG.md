# Changelog

## 0.5.3 Model Bank & Prompt Repair

- Replaced forced stream auto-scrolling with stable reading position and a manual **Latest** button.
- Added a persistent corner control that rotates between portrait and landscape and remembers the selected orientation.
- Added selectable Qwen2.5 0.5B Instruct Q8, Qwen3 0.6B Q8, SmolLM2 360M Instruct Q8, and Llama 3.2 1B Instruct Q4_K_M presets, plus arbitrary local GGUF import.
- Added a reusable `Documents/Infinite RPG/Models/` GGUF bank. Downloads and imports are banked once; switching reuses the bank while staging only the active model for reliable native memory mapping.
- Added visible per-model context, output-token, thread, temperature, and ChatML/Llama-3/plain prompt-template controls.
- Fixed the core prompt mismatch by wrapping prompts in the selected instruction model's real chat template instead of passing raw completion text.
- Replaced pseudo-JSON schemas that the model imitated with explicit natural-language field/type contracts, lower-temperature structured defaults, stronger color/biome validation, and a constrained AI repair pass for invalid drafts.
- Defaults new upgrades to Qwen2.5 0.5B Instruct Q8, whose official model description specifically emphasizes structured-output and JSON instruction following.

## 0.5.2 Quality Stream

- Upgraded the on-device generator to the approximately 609 MiB Qwen3-0.6B Q8_0 model after the low-memory Q4 build was observed returning truncated schema placeholders instead of usable game content.
- Added a dedicated AI Stream tab with the exact active prompt, buffered live model text, token count, tokens/second, pass, status, phase, active job, and a Generate Now control.
- Added strict JSON grammar-constrained native sampling so drafts, judging, and endless-planner decisions cannot drift into markdown or malformed JSON syntax.
- Increased the compact-phone context to 1,280 tokens and the large-device context to 1,536 tokens, retained bounded 64-token prompt decoding, and kept a single inference thread for predictable memory use.
- Publishes token snapshots every 250 ms rather than writing to SQLite from each native token callback, keeping the UI responsive while inference runs in the protected generator process.
- Preserved resumable Start/Stop download controls, crash recovery, required-content-first generation, two-pass validation, deterministic deduplication, and the endless planner that chooses and builds new live game content after the required catalog is complete.

## 0.5.1 Core Forge Repair

- Fixed the native crash loop that prevented both Start Generations and Generate Now from producing content. Prompts are now decoded in bounded 64-token chunks matching the low-memory llama.cpp batch size instead of being submitted as one oversized batch.
- Made Generate Now explicitly enable generation, acknowledge the request in the live phase/result fields, and retain the request until device safety checks allow it to run.
- Requeues jobs left in `running` state when Android recovers the protected native process, so interrupted work can never become permanently unclaimable.
- Added exact native-stage crash recovery reporting and automatic crash-counter reset after a completed generation pass.
- Uses the 1,024-token phone-safe context profile on devices below 6 GiB, including the 3.6 GiB device shown in the failure report.
- Added an endless Qwen planner pass after required jobs finish. Qwen chooses the next content type and concept, then the normal draft/judge pipeline builds it; the queue automatically replenishes to three future creations.
- Every accepted planned creation is inserted into the same live catalog used by terrain, textures, inventory, crafting, mobs, structures, weather, quests, and music, then broadcast to the running game for immediate reload and preview.
- Rebuilt every native arm64 library with flexible 16 KiB ELF page alignment and aligned the APK's uncompressed libraries for current Android 15/16 devices.

## 0.5.0 Living Score

- Expanded the built-in soundtrack from 20 to 50 original themes with 30 new compositions.
- Substantially remixed 15 of the original 20 themes with new melodies, alternate harmony, counterlines, bass motion, rhythms, instrumentation, and percussion while retaining five familiar originals.
- Rebuilt the procedural arrangement engine around eight musical sections and four long-range movements, so a track develops through contrasting passages instead of repeating one short loop.
- Added flute, whistle, ocarina, strings, clarinet, reed, brass, bell, celesta, organ, choir, accordion, marimba, guitar, harp, and pizzicato synthesis families with instrument-specific envelopes.
- Added clockwork, brush, march, waltz, orchestral, festival, and hand-drum percussion families, section-ending fills, swing, climax voicing, and per-track echo.
- Updated the Audio catalog to expose each track's lead, ensemble, percussion, tempo, remix status, and expanded 50-track selection while preserving five-minute automatic rotation and manual previous/next controls.

## 0.4.2 Low-Memory Wilds

- Removed the fixed 700 MiB free-RAM threshold. Generation now pauses only when Android itself reports critical memory pressure.
- Replaced the 609 MiB Q8 model with the approximately 397 MB Q4_K_M quantization. Starting the new download removes the obsolete Q8 file so both models do not consume storage together.
- Added a compact-phone inference profile: 1,024-token context below 3.5 GiB total RAM, one thread, 360-token drafts, 112-token judging, and smaller catalogs that remain valid JSON.
- Added the active Q4/context profile to the AI Workshop status column and clear migration instructions after upgrading.
- Fixed scenery jumping while walking by anchoring its sampling lattice to absolute world coordinates instead of the player's changing tile parity.
- Enlarged trees and added four deterministic canopy/trunk families, regional forest/meadow/scrub clusters, flowering and berry shrubs, multicolor flower patches, rock variants, natural clear trails, and clearings around buildings.

## 0.4.1 Protected Forge

- Moved the Qwen/llama.cpp runtime into a dedicated Android process so a native model failure cannot terminate the game, music, or current world UI.
- Added an in-flight crash marker and restart recovery. Interrupted native passes pause automatic generation and report recovery in AI Workshop instead of repeating a crash loop.
- Removed native model calls from the game/UI process, including the blocking runtime-status mutex check that could freeze menus during inference.
- Replaced unsafe per-token JNI text callbacks with crash-guarded pass execution: exact prompts and live tokens/second remain visible, and the complete output is committed atomically when the pass ends.
- Reduced inference pressure to one thread, a 1,536-token context, a 420-token draft cap, a 112-token judge cap, bounded catalog prompts, two-minute scheduling, and a pre-load free-memory check.
- Generation now receives explicit gameplay-active/idle signals across the process boundary and cancels immediately when the player returns to the world.

## 0.4.0 Living Worlds

- Replaced automatic nearby placement with an exact tap-position preview and explicit Confirm/Cancel controls.
- Split Inventory and Crafting into separate icon-only world buttons; Inventory now uses square item tiles while recipe/build cards remain detailed.
- Made all newly built houses empty and added distinct small cabin, timber cottage, and grand lodge exteriors and interior sizes.
- Rebuilt the animal atlas from isolated connected sprites so no pig, sheep, rabbit, or chicken can be split between source cells.
- Improved player walking with body travel, planted-step sway, and vertical gait instead of stationary leg-only cycling.
- Added deterministic flowers, trees, rocks, flying birds, procedural ambient wildlife audio, and separate sound/music mute and volume controls under Audio.
- Added randomly generated enterable caves with deterministic presence, persistent cave interiors, optional chests, and zero to two possible loot items.
- Added a craftable Sunstone Clock that unlocks the exact simulated HUD time.
- Added named seeded worlds, new-world creation, save/load, two-minute autosave, and full portable snapshots in `Documents/Infinite RPG/`, including AI-generated catalog entries and content controls.
- Fixed the AI Workshop column sizing that clipped streamed/status text near the bottom.

## 0.3.0 Survival

- Replaced Craft with a bag that exposes real Inventory, Crafting, and Build tabs.
- Added functional ingredient consumption, recipe output, starter materials, and high-resolution survival item previews.
- Added placeable campfires, enterable timber houses with working doors and separate interiors, persistent chests, lighting, furniture, and campfire cooking.
- Added a 15-minute daytime, 10-minute nighttime, and rotating clear, rain, fog, storm, and snow weather.
- Added passive sheep, rabbits, boars, and chickens with combat, drops, food preferences, mate-based breeding, babies, maturity, and cooldowns.
- Added two-frame front, left, right, and true rear walking views for passive animals; retained the player's four-direction four-frame walk atlas.
- Expanded local AI generation to structures and weather, with pixel-defined creatures/buildings and reference-checked materials, items, drops, food, recipes, and build ingredients.
- Added per-creation Disable/Enable and Delete controls. Deleted AI content is replaced by a new queued generation job.

## 0.2.1

- Replaced full-resolution UI-thread terrain generation with fixed-resolution cached chunks sized to retain a complete screen.
- Corrected player sprite opacity and retained the game view across menu transitions.
- Reduced gameplay rendering to a stable low-end-phone 30 FPS target.
- Made local AI yield and cancel its current pass whenever active exploration resumes.
- Replaced once-per-second menu reconstruction with in-place telemetry updates.
- Added AI Workshop, Music, and Settings tabs.
- Expanded the soundtrack to 20 premade themes with automatic five-minute rotation, previous/next controls, play/pause, and volume.

## 0.2.0

- Replaced the single-oscillator music loop with a layered, evolving stereo composition engine.
- Added high-resolution terrain, item/crafting, and animated character atlases.
- Added continuous biome texture crossfading and cached infinite terrain chunks.
- Added exact 16×16 pixel-by-pixel AI texture and item-icon contracts.
- Added premade terrain texture recipes, items, materials, and crafting recipes.
- Added explicit model-download Start/Stop, a progress bar, partial preservation, and automatic retry/resume.
- Added explicit generation Start/Stop/Generate Now controls and native cancellation.
- Added live prompt, draft/judge pass, streamed output, and live tokens/second display.
- Added recent-creation previews plus nine-second in-game creation notifications.
