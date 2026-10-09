# DOOM3 Kotlin

Inspired by [DJOOM 3 by Blackbeard334](https://github.com/blackbeard334/djoom3/)
</br>
Compilation is recommended under JDK 25 & Kotlin ver. 2.3.21.

## How to build and run

What you need:

- JDK 25 (e.g. Azul Zulu) and Maven
- A legal copy of DOOM 3 installed (Steam/GOG) — the game data is not part of this repository

Build once:

    mvn package

That compiles the code, runs the tests and packages the game into `target/`, then drops a launcher
script into the project root: `run-game.sh` on macOS/Linux, `run-game.bat` on Windows.

Then play:

    ./run-game.sh        # macOS / Linux
    run-game.bat         # Windows

The launcher runs the already-built jar, so you only need `mvn package` again after changing code.
The engine locates a default DOOM 3 installation on its own, so if your game is installed in the
usual place, there is nothing else to configure. If it is somewhere non-default, pass it through:

    ./run-game.sh +set fs_basepath '/path/to/doom 3'

#### 21-06-2022

Version 0.0.1

This version is the first milestone: re-write djoom 3 in kotlin.
</br>
A lot of stuff works to my surprise, however, currently it only compiles and does some basic initializing stuff. Fails
at Script Compiling.
</br>
Next version will feature at least playable menu, hopefully :)

#### 29-08-2023
Version 0.0.2
</br>
-Fixed Script Compilation
</br>
-Moved to Java 17 and Kotlin 1.7.0
</br>
-Fixed nasty Filesystem issues (e.g. reading wrong bits due to Kotlin's difference of operator precedence)
</br>
-A lot of fixes to make more inline with Java version

#### 09-10-2026

~~Version 0.0.3~~ → Version 1.0.0
</br>
The engine now plays the whole game.
</br>
-Full Doom 3 single player campaign, playable from start to finish
</br>
-Resurrection of Evil (D3XP) expansion supported
</br>
-Save games and loading games implemented
</br>
-Multiplayer support
</br>
-Renderer: stencil shadows fixed, skybox rendering, MSAA, proper full screen scaling, reworked macOS rendering path
</br>
-Sound system reworked, with HRTF support for headphone playback
</br>
-AI, scripting and GUI fixes throughout the game
</br>
-Performance: much lower memory usage, faster startup and save times
</br>
-Moved to Java 25 and Kotlin 2.3.21
</br>
-and many more bugs I don't remember to list

