# LMusic

A local music player for Android 13 and newer, written in Kotlin with Jetpack Compose.
LMusic is built on top of [PixelPlayer](https://github.com/theovilardo/PixelPlayer)
(the last MIT-licensed version) and adds a Yuyu-styled look, a richer tag editor with
metadata autofill, and deeper listening analytics.

Made by **Abhiram NJ** ([Twilight Abhi](https://github.com/twilighty-abhi)).

## Install

1. Open the [latest release](https://github.com/twilighty-abhi/LMusic-player/releases/latest)
   on your phone and download `LMusic-<version>.apk`.
2. Open the file. The first time, Android asks you to allow installs from your browser
   or **My Files** — allow it once.
3. Later releases install as updates on top. Your library, playlists and stats stay.

Every push to `main` also builds an APK — open the run under **Actions** and download it
from **Artifacts**.

## What LMusic adds

**Nobody Builds "Daylight" design, on Material 3**
- The [NobodyBuilds-Design](https://github.com/Nobody-Builds/NobodyBuilds-Design) tokens mapped onto
  Material 3 colour roles: Paper and Night Shift canvases, Blueprint blue does the work, Hard Hat yellow
  is the one bold moment per screen. Flat: no shadows, 10/16/20 dp radii, pills for buttons and chips.
- Gilroy (open Light and ExtraBold weights) for headings, Google Sans Flex for text, Unbounded for an
  accent phrase. Material Rounded icons only; service logos keep their own artwork.
- The now-playing screen still takes its colors from the album art.
- Settings → Appearance → **App colors** switches to Material You wallpaper colors.

**Tag editor writes into the file**
- Title, artist, album, **album artist**, **year**, genre, track, disc, **composer**,
  lyrics, ReplayGain and embedded cover art — MP3, FLAC, M4A/AAC (plus OGG/Opus/WAV).

**Metadata autofill** (Edit song → **Autofill**)
- *From your files*: offline guesses from the filename (`01 - Artist - Title`), the
  album folder (`Artist - Album (2019)`) and what the other tracks in the folder agree on.
- *Online*: MusicBrainz search, ranked by title, artist, album and duration match,
  plus front covers from Cover Art Archive. Free, no account or API key, and only
  contacted when you tap Autofill.
- Pick a match, see each field as *now → new*, tick what you want, then Save.
  MusicBrainz IDs are stored in the file too.

**Analytics**
- A play counts after **30 seconds** of listening (shorter tracks: nearly all of it).
- Today, week, month, year, all time — or a **custom date range**.
- Listening time, plays, top songs / artists / albums / genres, listening by time of
  day and weekday, sessions, streaks — plus **skip rate**, **new songs** and **new
  artists** discovered in the period.
- History is kept for life (PixelPlayer trimmed it to two years).

### New in 1.4

- Noise generator in Focus, Sleep space, charging screensaver (OLED-safe, Open-Meteo weather), Quick
  Settings tiles, six new widgets plus a lock-screen player, drag-to-select bottom bar.
- Screen-off gestures with pocket safeguards, source-filtered whole-library shuffle, reliable widgets,
  wider backups.

### New in 1.3

- Spaces: on-device lofi generator, lofi radio and ambience; focus timer; listening garden (with widget);
  party queue over local Wi-Fi; taste match by QR code; cover studio for songs without artwork.
- Living artwork, double-tap to like, artist story, mini-player swipes, swipe between pages, stronger
  haptics, optional shake / face-down gestures, crash reports and safe mode.

### New in 1.2

- New Home, Yuyu redesign of every screen and widget, smaller now-playing layout with sleep timer and song menu.
- Refined haptics (Off / Subtle / Full), smart shuffle, music folders to include, weekly update alerts.
- Library health with batch autofill of empty tags; weekly and monthly recaps.
- Experimental, off by default: on-device mood/genre tagging with YAMNet (Apache-2.0, bundled), mixes,
  song radio and insights. Indexing runs only while charging, idle and above 50% battery; nothing leaves the phone.

## Everything from PixelPlayer is still there

Queue with drag-and-drop, shuffle and repeat, playlists, gapless playback, sleep timer
(including end of track), folders, genres, search, Daily Mix, lyrics (LRCLIB), equalizer,
crossfade/transitions, widgets, Chromecast, Android Auto, Wear OS companion, backups,
and the online sources (Jellyfin, Navidrome, Google Drive, Telegram, Netease, QQ Music).

Not part of LMusic's own plan but kept as-is: lyrics, equalizer, crossfade, widgets,
Chromecast, Android Auto, Wear OS and the online music sources.

## Build it yourself

Open the project in Android Studio (Ladybug or newer, JDK 21) and run the `app`
configuration, or:

```bash
./gradlew :app:assembleRelease   # APK per ABI; LMusic releases ship the arm64-v8a one
```

### Signing

APKs install as updates only when every build is signed with the same key.

- **CI default:** on its first `main` build, GitHub Actions creates a signing key and
  keeps it in the Actions cache (`lmusic-signing-key-v1`). The *Keep CI signing key
  alive* workflow touches it twice a week so it never expires.
- **Your own key (recommended for the long term):** add three repository secrets —
  `LMUSIC_KEYSTORE_B64` (the `.jks` file, base64), `LMUSIC_KEYSTORE_PASSWORD` and
  `LMUSIC_KEY_ALIAS` — and CI uses them instead. On a Mac:

  ```bash
  keytool -genkeypair -keystore lmusic.jks -alias lmusic -keyalg RSA -keysize 4096 -validity 36500
  base64 -i lmusic.jks | pbcopy   # paste as LMUSIC_KEYSTORE_B64
  ```

  Switching keys changes the signature once: uninstall the CI-signed app first
  (back up from Settings → Backup if you want to keep stats).

## Releasing

Bump `APP_VERSION_NAME` in `gradle.properties` (e.g. `1.1.0`), add
`release-notes/v1.1.0.md`, and push to `main`. The workflow sees there's no release
for that version yet, builds the APK and publishes **LMusic v1.1.0** with it (and creates
the tag). Pushing a `v*` tag yourself works too. Other pushes just build an APK artifact.

## Credits and licence

- [PixelPlayer](https://github.com/theovilardo/PixelPlayer) by theovilardo and
  contributors — this project is based on upstream commit `3903015` (12 May 2026),
  the last version released under the MIT License. See [LICENSE](LICENSE).
- Fonts: Gilroy Light and ExtraBold by Radomir Tinkov (free font EULA), and
  [Unbounded](https://github.com/googlefonts/unbounded) (SIL Open Font License) — see
  [licenses/](licenses/). Google Sans Flex for body text.
- On-device audio model: [YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) (Apache-2.0), run with TensorFlow Lite.
- Metadata: [MusicBrainz](https://musicbrainz.org) (CC0) and
  [Cover Art Archive](https://coverartarchive.org).
