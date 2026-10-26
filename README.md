<picture>
  <source media="(prefers-color-scheme: dark)" srcset="assets/lockup-dark.png">
  <img alt="Nobody Listens" src="assets/lockup-light.png" width="420">
</picture>

A local music player for Android 13 and newer, written in Kotlin with Jetpack Compose, with a
Wear OS app for the watch. Part of [Nobody Builds](https://github.com/Nobody-Builds).

Developers: **Shaan Shoukath** ([@Shaan-Shoukath](https://github.com/Shaan-Shoukath)) and
**Abhiram NJ** ([@twilighty-abhi](https://github.com/twilighty-abhi)), the author of
[LMusic](https://github.com/twilighty-abhi/lmusic-player).

> **A fork of LMusic.** Nobody Listens is a fork of [LMusic](https://github.com/twilighty-abhi/lmusic-player)
> by Abhiram NJ ([Twilight Abhi](https://github.com/twilighty-abhi)), which is itself built on
> [PixelPlayer](https://github.com/theovilardo/PixelPlayer) (the last MIT-licensed version). Most of what's
> below was built there. This fork adds the Nobody Builds brand and "Daylight" design, YouTube Music (also
> contributed to LMusic) and its own fixes. It installs as its own app (`com.nobodybuilds.listens`), so it sits
> beside LMusic: move your library over with Settings → Backup & Restore, since LMusic backups restore here.

## Install

1. Open the [latest release](https://github.com/Nobody-Builds/Nobody-Listens/releases/latest)
   on your phone and download the APK for it:
   - `NobodyListens-<version>-arm64-v8a.apk`: almost every phone from the last several years.
   - `NobodyListens-<version>-armeabi-v7a.apk`: older 32-bit phones.
   - `NobodyListens-<version>-x86_64.apk`: emulators and Chromebooks.
   - `NobodyListens-<version>-universal.apk`: works everywhere, but is the largest.
2. Open the file. The first time, Android asks you to allow installs from your browser
   or **My Files** — allow it once.
3. Later releases install as updates on top. Your library, playlists and stats stay.

Every push to `main` also builds APKs — open the run under **Actions** and download them
from **Artifacts**.

### Galaxy Watch (Wear OS)

`NobodyListens-Watch-<version>.apk` runs on Galaxy Watch 4 and newer, and other Wear OS 3+ watches, round or
square. On its own, the watch plays songs already saved to it through its speaker or Bluetooth headphones. With the
phone nearby it also works as a remote for the phone app, browses the phone's library and receives songs from it.
Search, playlist import and YouTube Music on the watch are not there yet. The watch app is signed with the same
key as the phone app, which is how the two find each other.

1. On the watch: **Settings → About watch → Software information**, tap **Software version** five times, then
   **Settings → Developer options → ADB debugging** and **Wireless debugging** on. Note the IP and port shown.
2. On a computer on the same Wi-Fi, with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):
   `adb pair <ip>:<pairing port>` (enter the code from **Pair new device**), then `adb connect <ip>:<port>` and
   `adb install NobodyListens-Watch-<version>.apk`.

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
./gradlew :app:assembleRelease   # phone APK per ABI (arm64-v8a, armeabi-v7a)
./gradlew :app:assembleRelease -Pnobodylistens.allApks=true   # adds x86_64 and a universal APK
./gradlew :wear:assembleRelease  # the Wear OS watch app
```

Code lives in `com.nobodybuilds.listens`. A few stored names keep their LMusic form so LMusic backups and taste
codes still import: the `lmusic_*` DataStore and preference files, and the `LMT1:` taste-code prefix.

### Signing

APKs install as updates only when every build is signed with the same key.

- **CI default:** on its first `main` build, GitHub Actions creates a signing key and
  keeps it in the Actions cache (`nobody-listens-signing-key-v1`). The *Keep CI signing key
  alive* workflow touches it twice a week so it never expires.
- **Your own key (recommended for the long term):** add three repository secrets —
  `NOBODY_LISTENS_KEYSTORE_B64` (the `.jks` file, base64), `NOBODY_LISTENS_KEYSTORE_PASSWORD` and
  `NOBODY_LISTENS_KEY_ALIAS` — and CI uses them instead. On a Mac:

  ```bash
  keytool -genkeypair -keystore nobody-listens.jks -alias nobody-listens -keyalg RSA -keysize 4096 -validity 36500
  base64 -i nobody-listens.jks | pbcopy   # paste as NOBODY_LISTENS_KEYSTORE_B64
  ```

  Switching keys changes the signature once: uninstall the CI-signed app first
  (back up from Settings → Backup if you want to keep stats).

## Releasing

Bump `APP_VERSION_NAME` in `gradle.properties` (e.g. `1.1.0`), add
`release-notes/v1.1.0.md`, and push to `main`. The workflow sees there's no release
for that version yet, builds every APK (arm64-v8a, armeabi-v7a, x86_64, universal and the
watch) and publishes **Nobody Listens v1.1.0** with them (and creates the tag). Pushing a `v*`
tag yourself works too. Other pushes just build the arm64-v8a, armeabi-v7a and watch APKs as
artifacts.

## Credits and licence

- [LMusic](https://github.com/twilighty-abhi/lmusic-player) by Abhiram NJ
  ([Twilight Abhi](https://github.com/twilighty-abhi)), the app this one is forked from (MIT).
- [PixelPlayer](https://github.com/theovilardo/PixelPlayer) by theovilardo and
  contributors — this project is based on upstream commit `3903015` (12 May 2026),
  the last version released under the MIT License. See [LICENSE](LICENSE).
- Fonts: Gilroy Light and ExtraBold by Radomir Tinkov (free font EULA), and
  [Unbounded](https://github.com/googlefonts/unbounded) (SIL Open Font License) — see
  [licenses/](licenses/). Google Sans Flex for body text.
- On-device audio model: [YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) (Apache-2.0), run with TensorFlow Lite.
- Metadata: [MusicBrainz](https://musicbrainz.org) (CC0) and
  [Cover Art Archive](https://coverartarchive.org).
- Logo: the Nobody Listens mark from the Nobody Builds brand kit.
