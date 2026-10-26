# Changelog

All notable changes to BedrockPlacing are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
BedrockPlacing is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.0.3] - 2026-10-26

### Added

- An icon in the mod list on Fabric, Forge and NeoForge.

### Changed

- Requires Mycel 1.4.1.

## [1.0.2] - 2026-10-25

### Changed

- Requires Mycel 1.4.0. Operators now get one chat line per session when a
  MyCelium mod has an update, and `/mycel mods` marks it.

### Fixed

- Players with the mod can join servers without it (and the other way round)
  on every loader. On NeoForge and Forge the channel used to be required.
  On a server without the mod the client shows no outline and places blocks
  the vanilla way.

## [1.0.1] - 2026-10-19

### Changed

- Updated Mycel lib to 1.3.0: Mycel's chat messages now use the MyCelium wiki's
  red style.
- Checks for new versions through the MyCelium wiki (one log line when an
  update is out; turn off with `enableUpdateChecker` in `config/mycel.json`).

## [1.0.0] - 2026-10-10

### Added

- Initial release.
- Bedrock-style air-click placement: right-click the air with any block in
  hand while standing on a block to extend that floor in the direction you
  face — no precise face click needed. The anchor is the solid block under
  your feet, or one cell behind under your feet if you already walked past
  the edge and are falling, so forward-bridging just works while walking.
- Same-layer lock: the assist only ever extends the layer you stand on.
  Grounded anchors re-lock instantly; airborne anchors must target the
  locked layer, so jumping or falling can never strand a block above or
  below your floor. Stale locks (5s idle, dimension change) re-lock
  leniently, so the mod never goes mysteriously silent.
- White preview outline on the candidate cell before you click (client side,
  through vanilla's gizmo pipeline).
- Fast repeat while holding right-click: the modded client notifies the
  server the tick the use key goes down, then at most every `repeatTicks`
  — bridging no longer waits on vanilla's 4-tick use repeat.
- Placement goes through vanilla's own state-for-placement path, so stairs
  orient, buttons attach and waterlogging behave exactly like a normal click.
- Server-authoritative with same-tick duplicate suppression between the fast
  packet and the vanilla air-click event, so a click never places twice.
  Works in multiplayer out of the box (vanilla clients fall back to the
  normal air-click path). Never places into your own body's cells; dead,
  removed and spectator players place nothing.
- Config (`config/bedrockplacing.json`): `enabled`, `sneakDisables`,
  `requireSupport`, `reach` (default 4.5, range 2-6), `preview`,
  `repeatTicks` (default 2, range 1-4). Reload live with
  `/mycel reload bedrockplacing`; config screen available from the mod list
  on Forge and NeoForge.
- Built on Mycel 1.2.1 (`com.github.MyCelium-GG:MyCel-Lib:1.2.1` via JitPack).
