# Rainbow Trail Quest

A single-player Android board game for young players. Each session generates a randomised trail of colour-coded spaces — draw cards to race to the castle.

## Rules

- Tap **Draw Card** to flip the top card from the shuffled deck.
- **Single colour cards** advance you to the next matching space; **double colour cards** advance you to the second matching space.
- **Magic cards** teleport you directly to a named landmark on the board.
- **Rainbow spaces** (🌈) slide you 4–7 extra spaces forward.
- **Honey spaces** (🍯) slip you 2 spaces back.
- Reach the **Castle** (🏰) to win. A fresh randomised board starts automatically.
- Tap **New Board** at any time to reset.
- Tap any space to learn about it. Tap the title for a surprise.

## Themes

Each game randomly picks one of five themes, each with three named landmarks:

| Theme | Landmarks |
|---|---|
| Candy Cove 🍬 | Lollipop Lagoon, Gumdrop Garden, Peppermint Pier |
| Dino Sprinkle Park 🦕 | Fossil Frosting, Volcano Vanilla, T-Rex Taffy |
| Mermaid Sherbet Sea 🧜 | Pearl Pop Reef, Bubblegum Bay, Coral Cupcake |
| Space Sundae Galaxy 🚀 | Moon Marshmallow, Comet Cookie, Starlight Swirl |
| Unicorn Jellybean Woods 🦄 | Rainbow Glade, Sparkle Bridge, Cloud Cake |

## Build

**Prerequisites:** Android Studio Ladybug or later, JDK 17, Android SDK platform 36.

```bash
# Build the debug APK
./gradlew assembleDebug

# Run unit tests (no emulator needed)
./gradlew test
```

The APK is written to `app/build/outputs/apk/debug/`.

## Architecture

**`GameEngine`** owns all game state and logic and has zero Android dependencies. It can be constructed with an optional `Random` for deterministic testing:

```java
GameEngine engine = new GameEngine(new Random(seed));
```

**`GameView`** extends `android.view.View` and handles rendering and touch input only. It holds a `GameEngine` instance and delegates all game logic to it.

**`MainActivity`** wires the two together: it creates the `GameView`, owns the status `TextView` and the Draw Card / New Board buttons, and connects them to `GameView` via the `StatusSink` callback interface.

## Testing

Unit tests live in `app/src/test/` and exercise `GameEngine` in isolation using a seeded `Random`. No emulator or Android framework is required.

```bash
./gradlew test
# HTML report: app/build/reports/tests/testDebugUnitTest/index.html
```

Tests cover board construction, deck composition, card movement logic, shortcut/sticky effects, the win condition, and game reset.

## Project Structure

```
app/src/main/java/com/example/rainbowtrailquest/
    GameEngine.java       — game state and logic (pure Java)
    MainActivity.java     — Activity, GameView (rendering/touch)

app/src/test/java/com/example/rainbowtrailquest/
    GameEngineTest.java   — JUnit 4 unit tests

app/build.gradle.kts      — Android build config, test dependency
```
