# ZCode Remote: Android desktop attachment

## Scope of this build

The phone manages approved connections and displays the actual remote ZCode page.
It does not invent an agent, send shell commands through a second transport, copy
repositories, or need model-provider credentials. Desktop attach and direct web
server are explicit, separately labeled connection modes.

Implementation differs from the initial full-port plan: the first slice is a
small native Java Android shell, not Capacitor or a bundled React UI. The remote
host serves the page, preserving its origin, authentication bootstrap and upstream
protocol behavior. There is no native JavaScript bridge. This avoids claiming
support for an unimplemented wire protocol. Native task controls, platform-service
adaptation, persistent server packaging and device-issued pairing tokens remain
follow-up work; see android-roadmap.md.

## Attach to the desktop window

1. Keep desktop ZCode running and online. Open the project there first.
2. Select the phone icon in the desktop sidebar and enable Mobile Remote Control.
3. On Android, scan the QR or paste the complete link into Add desktop. You may
   also use Android's Share action on a plain-text link and select ZCode Remote.
4. Review the HTTPS origin and keep **Attach to desktop window** selected. Save,
   then open the saved connection. Imports never connect automatically.
5. Select a workspace/task in upstream ZCode's mobile UI and send instructions.

The upstream documentation says a single phone page can attach at a time and the
current desktop window must remain running. A QR refresh invalidates old links.
Forgetting a phone profile does NOT revoke desktop access: use Stop or refresh
the QR on the desktop. The app never fabricates a "stop task" request when you
close a phone view.

The repository's existing desktop package is still 3.11.2. The inspected open
source baseline documents 3.14.3. **3.11.2 attachment has not been device-tested**;
if that desktop does not expose a compatible phone link, use a desktop version
with the documented remote-control feature. This change does not silently upgrade
or replace the existing Nix package.

## Direct web server

Choose Direct web server only for a separately running ZCode web backend that
already serves its UI over authenticated HTTPS. A tailnet HTTPS endpoint is a
suitable deployment option. This build does not install that backend, generate
tokens, or claim to attach a standalone server to Electron's live sessions.

Use a publicly trusted TLS certificate. Cleartext HTTP and certificate bypasses
are deliberately not available, including on LANs. A plain private IP URL will
only work if its TLS certificate validates for that IP. Provider sign-in and
configuration should be completed on the desktop; cross-origin OAuth navigation
is not implemented in this alpha.

## What is implemented

- Up to eight saved connections with descriptive names and explicit attach mode.
- QR scanning using Google Code Scanner; no app camera permission. QR needs Play
  services and its scanner module. Paste/share does not require the scanner.
- AES-256-GCM authenticated vault using an Android Keystore key, an app-private
  no-backup file and atomic writes. A bounded single-worker mailbox owns all vault
  read/modify/write operations. Corruption/key failure never falls back to plaintext.
- Origin review, HTTPS-only links, no URL credentials in host fields, no automatic
  external navigation, no native JS bridge, no file/content access from web code,
  no mixed content, no remote camera/microphone grants and no WebView debugging.
- User-selected file attachment through the system document picker (one file).
  Files are handed to the upstream page, which determines upload support/limits.
- Manual reload confirmation, stale-generation guards, sticky page-load failures,
  explicit close-view behavior and sanitized diagnostics.
- Single-task Activity and orientation handling so ordinary rotation does not
  launch another remote page. Returning from the background does not force reload.

## Honest connection and storage boundaries

"Page loaded" does **not** mean the desktop agent is connected. Check upstream's
connection/run status in the page. Native diagnostics explicitly report
`agent_connection=unobserved` and `upstream_version=unverified`. The app does not
intercept task submissions, generate request IDs, acknowledge delivery, or replay
commands. Exactly-once delivery and offline queues are NOT claimed.

After a dropped connection or uncertain send, inspect the task before resending.
Manual reload discards the web view; unsent drafts may be lost. Background push
notifications and reconnect event replay are not implemented. Desktop execution
and upstream reconnect semantics remain the upstream runtime's responsibility.

Saved connection links are encrypted by the native vault. During an open session,
upstream JavaScript may store data in WebView's private cookie/DOM storage. That
storage is **not** wrapped by our Keystore vault. It is cleared before a new
connection/reload and when leaving a view normally. A process kill can prevent
cleanup until the next launch/attach; this is not a guarantee of forensic erasure.
The app opts out of Android backup, disables view-state saving and protects its
window from screenshots/recents capture. No raw URL is included in native logs,
errors, profile summaries or diagnostics. The host page and upstream service may
have their own logging/retention behavior.

## Build and install

Use JDK 17, **Gradle 8.13**, Android SDK platform 36 and build-tools 35.0.0. Set
ANDROID_HOME (or Android Studio's untracked android/local.properties). This
repository does not include a Gradle wrapper JAR; use the specified Gradle version.
The Android Remote Actions workflow installs the same toolchain.

```sh
bash scripts/test-core.sh
gradle -p android --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Application ID: `actor.starintel.zcode.remote`. Minimum Android: 8.0 / API 26.
The debug build is for testing, not a store release. GitHub runners generate their
own debug keys; APKs from different runs may not update in place. A stable signing
identity, supplied through a secret store, is required before release. Do not
commit signing files or secrets.

## Sources and compatibility baseline

Inspected upstream commit: `29628c9acdb81b703bbd4080c207a0e7ce5e276e`.
`upstream.lock` records the inspection baseline, **not** a pin of the host-served UI.
No upstream React code or assets are redistributed in this APK.

- https://zcode.z.ai/en/docs/remote-control
- https://github.com/zai-org/ZCode/blob/29628c9acdb81b703bbd4080c207a0e7ce5e276e/packages/web/src/main.tsx
- https://github.com/zai-org/ZCode/blob/29628c9acdb81b703bbd4080c207a0e7ce5e276e/README.en.md
- https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner
- https://developer.android.com/build/releases/agp-8-11-0-release-notes
