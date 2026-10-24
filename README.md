# Fire TV terminal home

Debloat an Amazon Fire TV over ADB and replace its ad-filled home screen with a
fast, text-only launcher that looks like a macOS Terminal session:

```
♪ spotify ── playing                                        6:14:14 PM
Strange                                                Fri Oct 23 2026
Celeste · Not Your Muse (Deluxe)
0:39  ━━━━━━━──────────────  4:15
|◀   ▮▮   ▶|



me@firetv ~ % ls dock/
netflix     youtube     prime_video     apple_tv
spotify     crunchyroll     youtube_tv
me@firetv ~ % ls apps/
airplay     appstore     settings
me@firetv ~ % open netflix█
```

- **Debloat, reversibly.** Ads, telemetry, content recognition (ACR) and unused
  Amazon features are *disabled* (`pm disable-user`), never uninstalled.
  `restore.sh` puts everything back from a backup taken first.
- **Terminal home screen.** Your favourite apps in `ls dock/`, everything else
  in `ls apps/`, a clock, and a mini player for whatever is playing (Spotify or
  any app with a media session). No images, ~60 MB while showing, ~1% CPU, and
  it only redraws when something changes.
- **Plain tools.** Bash scripts plus a ~25 KB Java app built with the Android SDK
  command-line tools. No Gradle, no Android Studio, no libraries.

> **Use at your own risk.** Tested only on an Insignia F50 Fire TV
> (AFTHY7ABBA, Fire OS 8.1.8 / Android 11). Other Fire TV models and Fire OS
> versions may ship different packages. Everything here is reversible, but you
> are changing your own device. Not affiliated with or endorsed by Amazon.

## Requirements

- macOS or Linux on the same network as the TV
- [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)
- For building the launcher: JDK 17 and the Android SDK with `platforms;android-30`
  and `build-tools;34.0.0`

On macOS:

```bash
brew install --cask android-platform-tools android-commandlinetools
```

```bash
sdkmanager "platforms;android-30" "build-tools;34.0.0"
```

`launcher/build.sh` looks for the SDK in `~/Library/Android/sdk` (override with
`ANDROID_HOME`) and a JDK via `JAVA_HOME`.

## Setup

1. **Turn on ADB on the TV.** Settings → My Fire TV → About → click the device
   name 7 times. Then My Fire TV → Developer options → **ADB debugging: On**.
2. **Configure.** Copy the example config and set your TV's IP (Settings → My
   Fire TV → About → Network) and the name shown in the prompt:

   ```bash
   cp config.example.sh config.sh
   ```

3. **Back up** (accept the "Allow debugging?" prompt on the TV, tick *Always allow*):

   ```bash
   ./backup.sh
   ```

4. **Debloat.** Pick tiers. `telemetry`, `ads` and `settings` are safe for
   everyone; the rest turn off features, so only run the ones you don't use:

   ```bash
   ./debloat.sh telemetry ads settings
   ```

   | Tier | What it turns off |
   |---|---|
   | `telemetry` | metrics, logging, crash upload, ACR (content recognition) |
   | `ads` | sponsored rows, advertising ID, demos, shopping, tutorials |
   | `settings` | turns on *limit ad tracking*, off usage collection and the screensaver |
   | `alexa` | Alexa voice (the remote's mic button stops working) |
   | `livetv` | antenna tuner, channel scan, Amazon's free-channel feed |
   | `smarthome` | Matter / smart-home controls |
   | `apps` | Silk, Amazon Photos, Amazon Music, Luna, Amazon Kids, Kindle |
   | `screensaver` | Amazon's ambient screensaver |
   | `updates` | the OTA helper apps (see limits below) |

   Casting, AirPlay, Prime Video, the Appstore, Settings and the remote are
   never touched. Add your own packages to `packages.local.txt` (same format,
   ignored by git). `./debloat.sh all` runs every tier.

5. **Install the home screen:**

   ```bash
   ./install-launcher.sh
   ```

   This builds the app if needed, installs it, enables its accessibility
   service (the Home-button hook) and grants notification access (the mini
   player).

### Using it

| Remote | Does |
|---|---|
| Arrows | move the highlight through apps and player buttons |
| OK | open the app / press the player button |
| Menu, or hold OK | move an app between `dock/` and `apps/` |
| Home | back to the home screen, highlight on the first dock app |

## Undo everything

```bash
./restore.sh
```

Re-enables every package that wasn't disabled at backup time, resets the
settings it changed, removes the launcher's permissions and uninstalls it.
Reboot afterwards. Back up again with `./backup.sh` if you start over.

## Fire OS 8 limits (what doesn't work, and why)

- **The default home app can't be changed.** Fire OS always sends Home to
  Amazon's launcher. The launcher's accessibility service watches for Amazon's
  home screen and swaps itself in, so you'll see a brief flash. Amazon's
  launcher (~160 MB) also stays loaded in memory regardless.
- **The Home key can't be intercepted.** Accessibility key filtering never sees
  Home or the D-pad on Fire OS 8.
- **Some packages are protected.** `pm disable-user` refuses the metrics,
  logging, crash and OTA services; `debloat.sh` restricts them from running in
  the background instead. So updates can't be fully blocked, and an update may
  re-enable things: re-run `./debloat.sh` and `./install-launcher.sh` after one.
- **Alexa's UI comes back at boot.** `com.amazon.vizzini` is re-enabled by Fire
  OS on every restart. With the rest of the `alexa` tier off it does nothing.
- **Reinstalling drops the Home hook.** Always reinstall with
  `./install-launcher.sh`, not a plain `adb install`.

After setup you can turn **ADB debugging off** again; the launcher keeps
working. Turn it back on only to run the scripts.

## Files

| Path | |
|---|---|
| `backup.sh` | snapshot packages, settings and installed APKs to `backup/` |
| `debloat.sh`, `packages.txt` | disable packages by tier |
| `install-launcher.sh` | build, install and enable the home screen |
| `restore.sh` | undo everything from the latest backup |
| `shot.sh` | screenshot the TV to `shots/` |
| `config.example.sh` | copy to `config.sh` |
| `launcher/` | the home screen app (`build.sh` builds `build/home.apk`) |

`backup/`, `shots/`, `state/`, `config.sh` and the signing key stay on your
machine (see `.gitignore`). The backup contains your device's details and
copies of other companies' apps, so don't publish it.

## License

MIT, see [LICENSE](LICENSE).
