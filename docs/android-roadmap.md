# Implementation and acceptance gates

## Local evidence

`bash scripts/test-core.sh` compiles the pure-Java core with JDK 17 release
compatibility and warnings treated as errors. It runs 577 assertions covering
HTTPS/authority parsing, exact origin matching, IPv6, bounded malformed input,
secret-safe error formatting, stale callbacks, sticky failure states, GCM
round trips, random IVs, wrong keys, tampering, truncation and size limits.
Static checks verify manifest restrictions, XML, and the absence of an exposed
native JavaScript bridge/certificate bypass. These are not 577 independent JUnit
test cases; the same suite is wrapped by one Android Gradle JUnit test.

## Required Android/live-desktop checks (not claimed as run locally)

- [ ] Android compilation, lint and APK assembly on exact-head CI.
- [ ] Install on the user's S21 / Android 15; verify keyboard and system insets.
- [ ] Scan a real desktop QR; verify the displayed approved origin.
- [ ] Open an existing workspace, send one coding instruction, confirm desktop
      execution, and inspect the result from both devices.
- [ ] Rotate, lock/unlock, switch Wi-Fi/LTE and kill/reopen the phone app. Ensure
      no wrapper-generated duplicate instruction and no false connected label.
- [ ] Expired/refreshed QR, desktop shutdown, broken TLS and an incompatible
      upstream page yield understandable status rather than fake success.
- [ ] Verify attachments, canceling the picker, declining provider permissions,
      blocked cross-origin navigation and renderer termination.
- [ ] Multiple profiles, forget/re-add, vault corruption/key invalidation,
      screenshot protection and backup exclusion on-device.
- [ ] Test no-Play-services devices using paste/share; QR failure must not block
      those paths.

## Full-port follow-up slices

1. Bundle a pinned upstream React UI with an Android IPlatformService adapter.
   Preserve the existing mobile desktop-attach contract, including origin/auth
   requirements. Do not replace it with a made-up JSON/WebSocket protocol.
2. Implement a version/capability handshake, authoritative task snapshots,
   interrupted-delivery reconciliation and supported approval/diff views. Only
   claim replay/exactly-once behavior with real upstream evidence and tests.
3. Add a separately named persistent desktop server package and systemd user
   unit; leave the existing AppImage launcher intact. Service sessions must stay
   explicitly separate until shared ownership is implemented and tested.
4. Add revocable device-issued credentials and native credential access without
   exposing a bridge to arbitrary host-rendered content. Bind credentials to
   approved origins and keep provider keys on the desktop.
5. Add durable local drafts, native notifications, a stable signing identity and
   Android instrumentation/E2E coverage before marking a production release.
