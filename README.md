# Hearts Game - Mobile Version

A beautiful, fully-featured Hearts card game for Android, inspired by the classic Microsoft Windows 7 Hearts. Built with Kotlin and Android Canvas for smooth 60fps animations.

## Features

- **Classic Hearts Rules**: 4-player trick-taking, hearts = 1 point, Q♠ = 13 points, shoot the moon for -26 to others
- **Passing Phase**: Rotating pass directions (Left → Right → Across → None)
- **Three AI Personalities**: Balanced, Aggressive (moon-shooting), Defensive
- **Beautiful Visuals**: 
  - Custom CardView with gradients, shadows, and Windows 7 nostalgia
  - Felt table background with subtle texture
  - Particle effects: heart bursts, Q♠ sparkles, moon celebration
  - Smooth animations: deal, play, pass, trick collection, flip
- **Landscape Optimized**: Perfect for mobile in landscape mode
- **Full Game Flow**: Deal → Pass → Play 13 tricks → Score → Next Round → Game Over at 100 points

## Screenshots

(Add screenshots here)

## Building

### GitHub Actions (Recommended)
Push to any branch - debug APK builds automatically.
For release: tag a commit `v1.0.0` and run the workflow with `build_type: release`.

### Local Termux Build (ARM64)
```bash
cd hearts-game
chmod +x build-termux.sh
./build-termux.sh
```
Note: Requires Termux with `pkg install aapt2 gradle openjdk-17`

### Desktop Build (Linux/macOS/Windows)
```bash
cd hearts-game
./gradlew assembleDebug
```
APK at `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

```
hearts-game/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/heartsgame/
│   │   │   ├── game/
│   │   │   │   ├── Card.kt          # Card, Suit, Rank, PlayerPosition, PassDirection
│   │   │   │   ├── Deck.kt          # Deck management
│   │   │   │   └── HeartsGame.kt    # Core game logic, AI, scoring
│   │   │   ├── ui/
│   │   │   │   ├── CardView.kt          # Beautiful card rendering
│   │   │   │   ├── TableBackgroundView.kt # Felt table
│   │   │   │   ├── ParticleSystem.kt    # Heart bursts, moon celebration
│   │   │   │   └── AnimationHelper.kt   # All animations
│   │   │   └── GameActivity.kt      # Main activity, 4-player AI loop
│   │   ├── res/
│   │   │   ├── layout/activity_hearts.xml  # Landscape layout
│   │   │   ├── values/strings.xml
│   │   │   ├── values/themes.xml
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── .github/workflows/build.yml
├── build-termux.sh
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
└── gradlew
```

## Game Rules

### Objective
Have the **lowest score** when any player reaches 100 points.

### Scoring
- Each Heart ♥ = 1 point
- Queen of Spades ♠ = 13 points
- **Shoot the Moon**: Take ALL 26 points → You get 0, everyone else gets +26

### Game Flow
1. **Deal**: 13 cards each
2. **Pass**: Choose 3 cards to pass (direction rotates each round)
3. **Play**: 13 tricks
   - First trick: Must lead 2♣
   - Must follow suit if possible
   - No hearts or Q♠ on first trick
   - Hearts can't be led until "broken"
4. **Score**: Count points, check for moon shot
5. **Next Round**: Repeat until someone hits 100

### Passing Rotation
- Round 1: Pass Left
- Round 2: Pass Right
- Round 3: Pass Across
- Round 4: No Pass
- Round 5: Repeat from Left

## AI Personalities

| Personality | Strategy |
|-------------|----------|
| **Balanced** | Solid all-around play, avoids points, tracks cards |
| **Aggressive** | Tries to shoot the moon when possible, leads hearts |
| **Defensive** | Aggressively passes penalty cards, avoids winning tricks |

## Animations

| Animation | Duration | Description |
|-----------|----------|-------------|
| Deal | 300ms | Staggered from deck to hands |
| Play Card | 400ms | Hand → center with rotation |
| Pass Cards | 500ms | Flip down → move → flip up |
| Collect Trick | 600ms | Cards fly to winner with spin |
| Select Bounce | 150ms | Selection feedback |
| Heart Burst | 800ms | Particles when heart played |
| Q♠ Glow | 1000ms | Special sparkle effect |
| Moon Celebration | 3000ms | Full screen confetti + particles |

## Requirements

- Android 7.0 (API 24)+
- Landscape orientation
- ~10MB APK size

## License

MIT License - feel free to use and modify!

## Credits

Inspired by Microsoft Windows 7 Hearts. Built with Kotlin, Android Canvas, and love for classic card games.