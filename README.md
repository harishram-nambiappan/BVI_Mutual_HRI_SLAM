# BVI_Mutual_HRI_SLAM

A research prototype for **Mutual Human-Robot Interaction (HRI)** aimed at **Blind and Visually-Impaired (BVI)** users in a **SLAM** (Simultaneous Localization and Mapping) context.

The repository currently contains an **Android mobile client** that lets a user send a **spoken command** to a robot, optionally paired with a **captured image**. The user records audio, reviews/plays it back, can attach a front-camera photo, and confirms the request.

> Status: early prototype. Audio and images are captured and stored **locally on the device**; the network/upload layer to the robot is not implemented yet (the permissions are already declared in anticipation of it).

---

## Requirements

* Android Studio (with the Android SDK) and a JDK installed
* An Android device or emulator running **Android 7.0 (API 24)** or higher
* The device needs a **camera** and **microphone**

---

## How to build & run

```bash
cd Mobile_Application
# point Gradle at your SDK (or create local.properties with sdk.dir=...)
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug          # build a debug APK
./gradlew installDebug           # build + install on a connected device/emulator
```

Or simply open the `Mobile_Application/` folder in Android Studio and press **Run**.

The launcher entry point is the `Intro` activity.

---

## Project structure

```
BVI_Mutual_HRI_SLAM/
├── README.md                     # this file
└── Mobile_Application/           # Android app (Java, XML layouts)
    ├── build.gradle              # root Gradle config (AGP 8.2.2)
    ├── settings.gradle
    └── app/
        ├── build.gradle          # app module: deps, SDK levels
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/example/slam_hri_mobile_application/
            │   ├── Intro.java
            │   ├── Menu.java
            │   ├── SpeechRequest.java
            │   ├── SpeechConfirm.java
            │   ├── CamImg.java
            │   └── ImageReview.java
            └── res/
                ├── layout/        # one XML layout per screen
                ├── values/        # colors, dimens, strings, themes, styles
                ├── values-night/  # dark-theme color/theme overrides
                ├── drawable/      # vector icons + backgrounds
                └── mipmap-*/      # launcher icons
```

---

## Tech stack

| Aspect | Choice |
|--------|--------|
| Language | Java |
| UI | Android XML layouts + `ConstraintLayout` (no Jetpack Compose) |
| Components | Material Components (`com.google.android.material:material`) |
| Camera | CameraX (`androidx.camera:*`) |
| Audio | `android.media.MediaRecorder` / `MediaPlayer` |
| Min / Target SDK | 24 / 34 |
| Package | `com.example.slam_hri_mobile_application` |

---

## Screen / class reference

Each screen is a single `AppCompatActivity` with one matching layout in `res/layout/`. Navigation is manual, using `Intent`s. The action bar is hidden on every screen.

| Class | Layout | What it does |
|-------|--------|--------------|
| `Intro` | `activity_intro.xml` | Splash screen. Shows the app logo, title, and a loading spinner for ~3 seconds, then auto-navigates to `Menu`. Launcher activity. |
| `Menu` | `activity_menu.xml` | Main menu. Two choices: **Speech command** (go to `SpeechRequest`) or **Speech with image** (go to `CamImg`). |
| `SpeechRequest` | `activity_speech_request.xml` | Records audio. Requests `RECORD_AUDIO` permission, starts/stops a `MediaRecorder`, saves the clip locally, then opens `SpeechConfirm`. Also offers "Back to menu". |
| `SpeechConfirm` | `activity_speech_confirm.xml` | Plays back the recorded clip with `MediaPlayer`. "Send request" returns to `Menu`; "Re-record" goes back to `SpeechRequest`. |
| `CamImg` | `activity_cam_img.xml` | Live **front-camera** preview via CameraX. Requests `CAMERA` permission, captures a photo to the device gallery, then opens `ImageReview`. Also offers "Back to menu". |
| `ImageReview` | `activity_image_review.xml` | Displays the captured photo (loaded from the path passed via Intent). "Add speech" continues to `SpeechRequest`; "Retake" returns to `CamImg`. |

### Navigation flow

```
Intro ──(3s)──▶ Menu
                 ├─ "Speech command" ───────▶ SpeechRequest ──▶ SpeechConfirm ──▶ Menu
                 │                                 ▲                  │
                 │                                 └──── "Re-record" ─┘
                 └─ "Speech with image" ─▶ CamImg ──▶ ImageReview ──▶ SpeechRequest ...
                                              ▲            │
                                              └─ "Retake" ─┘
```

### Class details

* **`Intro`** — Uses a `Handler.postDelayed` timer to wait 3 seconds before launching `Menu`. Pure splash; no user interaction.
* **`Menu`** — Wires two clickable controls to `Intent`s that open the speech-only or speech-plus-image flow.
* **`SpeechRequest`** — Holds a `MediaRecorder` (`mr`). On "start" it configures the mic source / MPEG-4 output and writes to `getFilesDir()/slam_hri_request_sample_1.mp4`. On "stop" it finalizes the file and opens `SpeechConfirm`.
* **`SpeechConfirm`** — Creates a `MediaPlayer` pointed at the saved clip so the user can verify it before confirming.
* **`CamImg`** — Binds a CameraX `Preview` + `ImageCapture` to the activity lifecycle using the **front lens**, renders into a `PreviewView`, and saves captures through `MediaStore`. Passes the saved image path to `ImageReview` via an Intent extra (`image_path`).
* **`ImageReview`** — Decodes the captured file into a `Bitmap` and shows it, then lets the user proceed to attach speech or retake.

---

## Resources & design system

UI styling is centralized so screens stay consistent and theming is easy.

| File | Purpose |
|------|---------|
| `res/values/colors.xml` / `res/values-night/colors.xml` | Semantic color palette (brand, accent, danger, surfaces, text) with a dark-mode variant. |
| `res/values/themes.xml` / `res/values-night/themes.xml` | App theme (Material), gradient window background, status/nav bar colors, and shared text/button styles. |
| `res/values/dimens.xml` | Spacing, sizing, corner-radius, and typography size tokens. |
| `res/values/strings.xml` | All UI copy and accessibility content descriptions. |
| `res/drawable/` | Vector icons (`ic_mic`, `ic_stop`, `ic_play`, `ic_camera`, `ic_send`, `ic_arrow_back`), gradient backgrounds, circular button backgrounds, and the launcher icon. |

**Accessibility notes (relevant for BVI users):** large touch targets, high-contrast colors, full dark-mode support, and `contentDescription`s on interactive elements.

---

## Permissions (declared in `AndroidManifest.xml`)

`INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `READ/WRITE_EXTERNAL_STORAGE`, `CAMERA`, `RECORD_AUDIO`. Camera and microphone are requested at runtime.

---

## Known limitations / next steps

* **No upload yet** — "Send request" only navigates back to the menu; audio/image are not transmitted to a robot or server.
* **Audio recording on older devices** — `MediaRecorder` is only constructed on API 31+, so recording will not work below that despite `minSdk 24`.
* **Hardcoded image path** in the capture → review handoff.
* Future work: wire up the network layer to the SLAM/HRI backend and stream commands to the robot.
