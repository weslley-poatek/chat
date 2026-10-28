# UI

`:core:ui` is the Chat design system for Android and iOS: `ChatTheme`, its design tokens and
the components built on them. Every value comes from the Chat design prototype in Claude Design;
the structure follows Orbit's `:core:ui`.

| Type | Package | Role |
| --- | --- | --- |
| `ChatTheme` | `core.ui` | Provides every token below, then wraps the content in a debug `MaterialTheme` |
| `ChatPalette` | `core.ui.theme` | One entry per model, each with a light and a dark `ChatColors` |
| `ChatColors` | `core.ui.theme` | `surface`, `border`, `text`, `accent`, `status` and `syntax` groups |
| `ChatTypography` | `core.ui.theme` | The 15 Material roles plus `input`, `code`, `eyebrow` and `caption` |
| `ChatShapes`, `ChatSpacing`, `ChatSizes`, `ChatElevation`, `ChatGradients` | `core.ui.theme` | The remaining token groups |

## Usage

Add `implementation(projects.core.ui)` to the consuming module's `commonMain` dependencies, and
add `CoreUiModule` to the `includes` of the application's root Koin module (`AppModule`).

Wrap the UI in `ChatTheme` and read tokens through it:

```kotlin
ChatTheme(palette = ChatPalette.GEMINI) {
    Text(
        text = "new chat",
        style = ChatTheme.typography.labelMedium,
        color = ChatTheme.colors.text.tertiary,
    )
}
```

`darkTheme` defaults to the system setting. Never read `MaterialTheme.colorScheme`:
`ChatTheme` sets every slot of it to magenta, so anything that does shows up on screen.

To give part of the screen another accent, nest a second `ChatTheme` and pass the outer one's
light or dark on, so a theme chosen above it is kept:

```kotlin
ChatTheme(palette = model.palette, darkTheme = ChatTheme.colors.isDark) { ... }
```

The app does this for the chat, which wears its own model's palette over the root's default-model
palette.

## Palettes

The accent follows the active model. Models from the same provider share a hue, so `SONNET` and
`OPUS`, and `GPT5` and `O3`, have the same colors. Everything except `accent` is the same in every
palette.

| Palette | Accent hue (OKLCH) |
| --- | --- |
| `SONNET`, `OPUS` | 35 |
| `GPT5`, `O3` | 150 |
| `GEMINI` | 255 |
| `LLAMA` | 290 |
| `DEEPSEEK` | 220 |
| `MISTRAL` | 80 |

The prototype defines colors in OKLCH. The Kotlin primitives are their sRGB conversion, as the
browser renders them. `accent.glow` and `accent.particle` are the voice mode colors, which the
prototype computes in script.

## Components

Components live in `core.ui.components`. They are stateless, read only `ChatTheme`, and each file
has `@Preview`s for every variant and state in light and dark.

| Component | Variants | Use |
| --- | --- | --- |
| `ChatButton` | `Primary`, `Secondary`, `Outlined`, `Text`, `Danger` × `Large`, `Small` | Actions, with an optional loading spinner |
| `ChatIconButton` | `Standard`, `Accent` | Icon-only actions such as the menu and send buttons. A disabled `Accent` button is `surface.secondary` with a `text.tertiary` icon, easing between the two over 150 ms, instead of fading |
| `ChatTextField` | — | Single-line mono input with a placeholder and an optional trailing control |
| `ChatSwitch` | — | On/off settings; the track takes the palette's accent when checked |
| `ChatSegmentedControl`, `ChatSegment` | — | Single choice among a few short options, such as theme or speech rate |
| `ChatBottomSheet` | — | Modal sheets such as the model picker, with the prototype's handle and top border |
| `ChatVoiceSphere` | `Listening`, `Thinking`, `Speaking`, and `muted` | The voice mode particle sphere and its glow, tinted with the palette's accent; `muted` stills the listening sphere only, as the prototype's mute does |
| `ChatToast` | — | A one-line notice in inverted colours that fades in; pass `null` to hide it. The screen's state clears it after `ChatToastDefaults.Duration`, 1400 ms |

## Fonts and icons

IBM Plex Sans (Regular, Medium, SemiBold) and IBM Plex Mono (Regular) are bundled under
`composeResources/font`. Mono SemiBold is not bundled, so the mono styles that ask for it are
synthesized from Regular. Icons are vector drawables named `ic_*`. Sources and licenses are in
[`LICENSES`](LICENSES/README.md).

The module's `CoreUiRes` class is internal, so other modules reach the icons through
`core.ui.icons.ChatIcons`:

```kotlin
Icon(painter = painterResource(ChatIcons.Send), contentDescription = null)
```

## Tests

The tests are in `commonTest`, so they run on the Android host and on the iOS simulator:

```bash
./gradlew :core:ui:testAndroidHostTest
```

```bash
./gradlew :core:ui:iosSimulatorArm64Test
```

They check the palettes, the gradients and each component's colors in every palette ×
light/dark, and that the previews cover every state.
