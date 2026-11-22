# Glyph Pet — rules for anyone working here

A pet on the Nothing Phone (3) Glyph Matrix (25×25, round), and the same game in the app for any phone.
The owner reviews everything on the real matrix and in the atlas (docs/design/atlas, built by `tools/AtlasExport`).

## Working with the owner
- Propose first, wait for an explicit «да» before editing or deploying. Never release to Play without an explicit command.
- Never commit secrets (`play-service-account.json`). Keep `todo.md`, `.claude/` and `docs/design/pet-state-board.html` untracked.
- Several sessions may work in this repo at once: commit only your own files, never sweep in someone else's work in progress.

## Mini-games and toys (pet/trick/)
- **No score on the matrix, anywhere.** No dots on the rim, no counters under the pet, no pile of collected things.
  Progress is felt, not shown: a haptic tick per point (`gate.feel`), a stronger one and a little party every few.
  (`Score.draw` exists but must stay unused.)
- **Played with the phone alone.** The Glyph button always ends a game, so no game may need the button.
  Inputs: the lean (both axes), holding still, a shake / sharp nod.
- Every game needs a clear goal you can see at once; a hero for whom a goal feels forced gets a **toy** instead
  (no goal, something fun to poke: the phoenix's flame always burns up, the pebble grows moss while left alone).
- A game ends by gathering the pet back home; the shared `Trick` base does the intro and outro.
- Each new game gets a test that it can really be played (a scripted lean scores at least a few points).

## Pets and animations
- Every pet is unique in every animation; a shared/template animation is a bug to fix, not a default.
- Catch visual glitches with tests over all species × stages (LEDs off the matrix, still frames, smears), not by eye.
- A pet's idle motion keeps its face still; no generic "breathing" that moves the forehead.
- Drawn heroes (pet/drawn/) are whole-matrix drawings, not sprites: every state is a clip in `Drawn.COLS`; on the
  matrix they run at the full frame rate (the atlas only records them every 160 ms). Living fire, glow and smoke go
  through `Fire` (Glyph Flame's model), never row-by-row shapes.
- State changes cross-dissolve (`PetEngine.FADE_MS`); a clip that follows another (a meal after hunger) should start
  from the pose it follows (`Drawn.cameFrom`).

## Build and check
- Build `./gradlew.bat :app:assembleDebug -q`, tests `./gradlew.bat :app:testDebugUnitTest -q`.
- Atlas: `ATLAS_OUT=docs/design/atlas ./gradlew.bat :app:testDebugUnitTest --tests n7.glyphpet.tools.AtlasExport --rerun -q`.
