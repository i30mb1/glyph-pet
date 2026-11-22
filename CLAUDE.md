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
- **Check every animation yourself before showing it**: render its frames (`tools/DeskSheet`, see its doc: `DESK_OUT`,
  `DESK_KINDS`, `DESK_GAME=1|sig|eat`, `DESK_FACES`, `DESK_EVERY`, `DESK_CELL`), look at the PNG, fix what is wrong.
  The owner should not have to find blur or stray LEDs.
  For anything that moves, look at every frame: `tools/FramePlayer` (`PLAY_OUT=x.html`, `PLAY_KIND`, `PLAY_STAGE`)
  writes a frame-by-frame HTML player (meal, then game, then home; numbered frames the owner can point at). Scan all
  frames for small loose lit bits (connected groups of 1-3 LEDs apart from the hero): they read as bugs (sparkles,
  a paw hanging apart, a crumb left over). Check transitions too: two pictures mixed show two pairs of eyes.
- Build the APK separately from the tests (`:app:assembleDebug` on its own) and check its time before installing: a
  failing test stops a combined run before the APK is rebuilt, and the old one gets installed.
- **Crisp, never blurred**: no stretching or shrinking a sprite, no fractional positions, no crossfading two forms.
  Smaller means a smaller form, or a small sprite drawn by hand, put on the LEDs cell for cell.
- **Readable faces**: on a small hero the eyes are two dark (off) LEDs in a lit head; never grey smudges, never a face
  drowned by a bright outline or mane. Nothing may erase a hero's outline.
- **No clutter**: loose dots (stars, hearts, specks) read as bugs; whatever is added belongs to the hero or its scene.
- Growth (the typing scene) is one scene that grows in clear, visible steps, not separate animations.
- When a design is not obvious, make visual mockups first (an HTML page with live 25x25 matrices) and wait for approval.
- Drawn heroes (pet/drawn/) are whole-matrix drawings, not sprites: every state is a clip in `Drawn.COLS`; on the
  matrix they run at the full frame rate (the atlas only records them every 160 ms). Living fire, glow and smoke go
  through `Fire` (Glyph Flame's model), never row-by-row shapes.
- State changes cross-dissolve (`PetEngine.FADE_MS`); a clip that follows another (a meal after hunger) should start
  from the pose it follows (`Drawn.cameFrom`).

## Build and check
- Build `./gradlew.bat :app:assembleDebug -q`, tests `./gradlew.bat :app:testDebugUnitTest -q`.
- Atlas: `ATLAS_OUT=docs/design/atlas ./gradlew.bat :app:testDebugUnitTest --tests n7.glyphpet.tools.AtlasExport --rerun -q`.
