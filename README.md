# ACMX2 Android

ACMX2 Android is the mobile build of the ACMX2 interactive visualizer. It runs a local, GPU-accelerated shader/3D visual pipeline inside Android WebView and packages the native visual engine as WebAssembly assets.

Project page: https://lostsidedead.biz/acmx2-android.html

## Version

Current bundled app/runtime version: **v1.7.0**

The Android host loads the visualizer `ACMX2 Interactive Visualizer v1.7.0` at startup.

## What the app is

ACMX2 Android is a fullscreen visual effects app that lets you:

- Render realtime GLSL effects and model-based visuals
- Cycle and select shader presets
- Use camera input or video file input
- Edit and compile custom GLSL ES 3.0 fragment shaders live
- Build ordered multi-pass shader chains
- Control uniforms (speed, amplitude, color, transform, camera controls)
- Record output (WebM/MP4) and save snapshots to Gallery

## Backend architecture (C++ / Java / JavaScript / OpenGL ES 3)

The app uses a layered runtime:

### 1) C++ engine (compiled to WebAssembly)

- Core rendering/visual logic is provided by a C++ engine compiled with Emscripten and shipped as:
	- `acmx2/app/src/main/assets/visualizer/MX_app.wasm`
	- `acmx2/app/src/main/assets/visualizer/MX_app.js`
	- `acmx2/app/src/main/assets/visualizer/MX_app.data`
- The JavaScript layer calls exported engine APIs through `Module.*` (examples in `index.html`: `Module.getShaderCount`, `Module.getShaderNameAt`, `Module.compileCustomShader`, `Module.setUniform`, `Module.nextShaderWeb`, `Module.prevShaderWeb`, `Module.saveImage`, `Module.resize`).

### 2) JavaScript application layer

- `acmx2/app/src/main/assets/visualizer/index.html` contains the UI and runtime logic.
- It pre-initializes a `webgl2` context (fallback to `webgl`) and uses GLSL ES 3.0 shader code/templates (`#version 300 es`) for custom shader editing.
- JS handles:
	- UI state and controls
	- camera/video ingestion
	- shader list population
	- multipass shader ordering
	- recording pipeline and optional FFmpeg.wasm conversion

### 3) OpenGL ES 3 backend path

- On Android WebView, WebGL2 maps to an OpenGL ES 3 class backend.
- The Emscripten runtime in `MX_app.js` explicitly handles GLES/WebGL2 compatibility and context setup.
- In practice: the visual engine renders through the WebGL2/OpenGL ES3 path when available.

### 4) Java Android host layer

- `acmx2/app/src/main/java/biz/lostsidedead/acmx2/MainActivity.java` hosts the visualizer in a fullscreen `WebView`.
- `WebViewAssetLoader` serves local assets from `https://appassets.androidplatform.net/assets/visualizer/index.html`.
- Java configures permissions (camera/mic), immersive mode, file picker, and cache behavior.
- Java exposes `AndroidInterface` methods via `@JavascriptInterface` so JS can save images/videos through Android MediaStore.

## Why all four layers are used

- **C++**: high-performance rendering/engine logic
- **JavaScript**: fast iteration UI + shader tooling + browser APIs
- **Java**: Android lifecycle, permissions, storage, and WebView host integration
- **OpenGL ES 3 / WebGL2**: GPU rendering backend on device

This design keeps the rendering engine portable while still integrating cleanly with Android-native features.

## v1.7.0 highlights

Based on the current packaged app/runtime:

- Runtime/versioned boot flow for `v1.7.0`
- Fullscreen immersive WebView host and local asset loading
- Custom GLSL ES 3.0 shader editor with compile/apply and templates
- Multipass shader chain UI (ordered passes, enable/clear workflow)
- Camera and video-file input workflows
- 3D model selection and touch controls (rotate/pinch/pan)
- Recording options (WebM + MP4 path with native support or FFmpeg.wasm conversion fallback)
- Android save bridge for snapshots and chunked video export to Gallery

## Android requirements

- `minSdk = 30`
- `targetSdk = 36`
- Java 11 toolchain for the Android project

Permissions used:

- `CAMERA`
- `RECORD_AUDIO`
- `MODIFY_AUDIO_SETTINGS`
- `INTERNET`
- `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 32)

## Build

From this repository folder:

```bash
cd acmx2
./gradlew assembleDebug
```

Debug APK output:

`acmx2/app/build/outputs/apk/debug/`

For release builds:

```bash
cd acmx2
./gradlew assembleRelease
```

## Project layout

- `acmx2/app/src/main/java/.../MainActivity.java` — Android host app + JS bridge
- `acmx2/app/src/main/res/layout/activity_main.xml` — fullscreen WebView layout
- `acmx2/app/src/main/assets/visualizer/index.html` — JS UI/runtime
- `acmx2/app/src/main/assets/visualizer/MX_app.js` — Emscripten loader/runtime glue
- `acmx2/app/src/main/assets/visualizer/MX_app.wasm` — compiled C++ engine
- `acmx2/app/src/main/assets/visualizer/MX_app.data` — packaged shader/model/data assets

## Notes

- This Android app packages a prebuilt visual runtime (`MX_app.*`) as assets.
- If the C++ engine is updated, regenerate and replace the `MX_app.js/.wasm/.data` bundle before shipping a new app release.
