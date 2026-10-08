# Prebuilt APKs

Signed release builds, committed so the artifacts ship with the source.

| File | Device | Size | SHA-256 |
|---|---|---|---|
| `spge-phone-v1.0.apk` | Android phone (companion) | 2.71 MB | `4d0efce117937ed56c046bd220080c060f060525e1dc782a680df06722944020` |
| `spge-wear-v1.0.apk` | Wear OS watch | 9.68 MB | `0bab5f7e0ce4f94ac3a985ed2fc22f5ca779c47c181aa7923fe8b4431d0dfc57` |

Both are signed with certificate
`SHA-256: 4B:39:4F:A4:E1:CE:9D:47:B6:5C:13:9E:C5:2D:2B:15:EA:D4:52:F8:5D:6B:59:3B:D0:68:C3:62:09:FB:F9:74`.

**That they share one certificate is a requirement, not a convenience.** The
Wearable Data Layer only delivers data between apps that match on both package
name and signing certificate. Installing a build signed with a different key
will break the phone → watch sync silently — the apps will launch normally and
simply never exchange data.

## Installing

Install the **phone app first**, open it, and let the schedule site load. Then
install the watch app and tap **Send to watch** in the phone app.

- On phones with MIUI/HyperOS you will get a confirmation popup when installing
  over ADB; approve it within about 10 seconds or the install is cancelled.
- Both APKs must be updated together. A phone build and a watch build from
  different commits may disagree about the payload format.

## Verifying a download

```bash
sha256sum spge-wear-v1.0.apk
# expect 0bab5f7e0ce4f94ac3a985ed2fc22f5ca779c47c181aa7923fe8b4431d0dfc57
```

Or check the signing certificate directly:

```bash
apksigner verify --print-certs spge-wear-v1.0.apk
```

## Rebuilding these

These are outputs of `./gradlew :wear:assembleRelease :mobile:assembleRelease`.
Release builds are reproducible from the source in this repository given the
same keystore — but **the keystore is deliberately not committed** (see
`.gitignore`), so a rebuild signed by someone else produces a different
certificate and will not be accepted as an update by existing installs.

## Security note

These APKs are built from the source in this repository, but nothing verifies
that automatically. If you are installing a build you did not produce, prefer
compiling from source yourself or checking the signature and hash above.
