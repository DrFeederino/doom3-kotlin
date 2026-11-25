# DOOM 3 Kotlin

A Kotlin reimplementation of the DOOM 3 engine, inspired by [DJOOM 3](https://github.com/blackbeard334/djoom3/)
and informed by the original id Software source code and the [dhewm3](https://github.com/dhewm/dhewm3)
source port.

## Current status

The engine version of this Kotlin port is not a one-to-one snapshot of
upstream but very largely the same as upstream and is 1.5.5 as of 25/11/2026.
Under the hood the project runs on Kotlin's, Java's and LWJGL's APIs, unlike the dhewm3 source port this project is
coded to support both base and Resurrection of Evil (D3XP) campaigns without the need to recompile. Known bugs in the
original campaign due to D3XP code were addressed.

## Game data

Game assets are not included. You need legally obtained retail **DOOM 3** data, (preferably and tested) updated to
version 1.3.1.
The original DOOM 3 and Resurrection of Evil data are supported; **DOOM 3: BFG Edition is not supported**.
The data remains subject to the original game's EULA and must not be redistributed with this source.

The engine needs the game installation root—the directory containing `base/` (and, for Resurrection of
Evil, `d3xp/`). The retail `base/` directory contains the `pak000.pk4` through `pak008.pk4` archives.
Keep the original directory layout rather than copying game archives into this repository. For Steam
installations and platform-specific data extraction details, see the dhewm3's wiki as this project mirrors the behavior:
[dhewm3 game-data installation guide](https://dhewm3.org/#how-to-install) and its
[FAQ](https://github.com/dhewm/dhewm3/wiki/FAQ).

## About AI usage

In the first few years of me porting the Java code to Kotlin was done by me, with bare hands, for the purpose of
learning and seeing if I can do it. I spent tons of restless hours identifying the bugs that came from Java code and
even finding the vanilla bugs that I kept forgetting to report back to dhewm3 but they were fixed earlier this or last
year. Earlier this year as the project matured and LLMs became more capable, I adopted the usage and used them
extensively to polish the project to the current state. I do not include Agents file yet as I deem it unnecessary and my
local version needs a huge revise of it.

## Future work

I see this project as fun pet project that introduced me to various different game engine inner workings and graphical
codebase. Needless to say, I learned quite a lot in the past few years that I have been working, mainly - debugging is a
bi...! I am currently working on the tooling to port e3 assets to retail version of the game (from playable maps to
complete walkthrough), I will likely include the conversion tools here as I am lazy to create another repo.
Additionally, I am very early days into the ray tracing, but first I would need to add support for another frontend
renderer - Vulkan, which is another bag of doritos I haven't opened yet. No promises, just ramblings and wishes.

## Launching

When using a packaged distribution, start its platform launcher:

```
java -jar doom3-kotlin-1.0.0.jar
```

Note: Additional engine console arguments can be appended to the launcher command at the end, e.g.:

```
java -jar doom3-kotlin-1.0.0.jar "+set fs_basepath '/path/to/doom3'"
```"

## Multiplayer safety

**Do not connect this engine, vanilla DOOM 3, or other DOOM 3 source ports to public multiplayer servers,
and do not host public servers.** The inherited multiplayer protocol is considered fundamentally unsafe:
malicious servers or clients may be able to execute code on a peer's system. Single-player is fine when
using trusted game data and mods; multiplayer should be limited to a LAN or VPN with trusted participants.
See the warning in the [dhewm3 README](https://github.com/dhewm/dhewm3/blob/master/README.md) for context.

## Credits and license

- id Software for DOOM 3 and its GPL source release.
- Daniel Gibson and contributors for dhewm3 and its engine fixes.
- Blackbeard334 for DJOOM 3, which inspired this Kotlin reimplementation.
- Dr. Feederino for the Kotlin port.

The source is distributed under the GNU GPL v3 or later and is subject to the additional terms attached
to the original DOOM 3 source code. See [`LICENSE.txt`](LICENSE.txt) and [`COPYING.txt`](COPYING.txt).

