# BedrockPlacing

Never aim at the edge again. Stand on a block, look straight ahead and
right-click the air: the block is placed on the side of the floor you stand
on, in the direction you face — just like Bedrock (Education) placing.
A white outline previews exactly which cell will be filled, and holding
right-click bridges fast without vanilla's sluggish repeat delay.

Built on [Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`), the shared
core for the MyCelium ecosystem — config, platform abstraction, networking
and diagnostics are reused.

| | |
|---|---|
| Mod id | `bedrockplacing` |
| Minecraft | **26.3**, **26.2**, **26.1** (26.1-26.1.2) · Java **25** |
| Loaders | Fabric, Forge, NeoForge |

## Behavior

- Right-click the **air** (nothing targeted) with any block in hand while
  standing on a block: one block is placed on the anchor's side you face.
- The anchor is the solid block under your feet — or one cell behind under
  your feet if you already walked past the edge and are falling — so
  forward-bridging just works while walking.
- **Same-layer lock:** the assist only ever extends the layer you stand on.
  The layer locks from the block under your feet; airborne placements (walked
  off the edge) must target that same layer. Jumping or falling can never
  strand a block a layer above or below your floor — building a flat floor at
  Y=0 stays at Y=0. Stepping onto a new height re-locks instantly, and a stale
  lock (5s idle, dimension change) re-locks leniently on next use, so the mod
  never goes mysteriously silent.
- A **preview outline** marks the candidate cell before you click (client
  side, drawn through vanilla's gizmo pipeline — no shaders, no per-loader
  render code).
- **Fast repeat:** the modded client notifies the server the tick the use key
  goes down, then at most every `repeatTicks` while held — bridging no longer
  waits on vanilla's 4-tick use repeat. Single clicks are unaffected in speed
  (one server round trip, like any placement).
- Placement goes through vanilla's own state-for-placement path, so stairs
  orient, buttons attach and waterlogging behave exactly like a normal click.
- Server-authoritative: the server re-validates every request and drops
  same-tick duplicates between the fast packet and the vanilla air-click
  event, so a click never places twice. Works in multiplayer out of the box
  (vanilla clients fall back to the normal air-click path).
- Never places into the cells your own body occupies; dead, removed and
  spectator players place nothing.

## Configuration (`config/bedrockplacing.json`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch (also toggleable live via `Mycel.setEnabled`). |
| `sneakDisables` | `true` | Hold sneak to skip the assist and right-click air normally. |
| `requireSupport` | `true` | Anchor must have a sturdy side face in your direction. |
| `reach` | `4.5` | Maximum eye-to-target distance in blocks (2-6). |
| `preview` | `true` | Show the white placement outline (client side). |
| `repeatTicks` | `2` | Client ticks between assisted placements while holding right-click, 1-4 (vanilla uses 4). |

Reload live with `/mycel reload bedrockplacing`. A config screen is
available from the mod list on Forge and NeoForge.

## Building

JDK 25, then:

```sh
./gradlew build            # all loaders + tests
./gradlew :common:test     # unit tests only
```

Mycel itself comes from [JitPack](https://jitpack.io/#MyCelium-GG/MyCel-Lib)
as one universal jar for all loaders
(the version is `mycel_version` in `gradle.properties`). No local setup needed.
