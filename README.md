# SPGE Schedule — Wear OS + Android

A native Wear OS app showing the school timetable from
[spgeparse.netlify.app](https://spgeparse.netlify.app), fed by a companion
Android app that scrapes the site and relays the data over Bluetooth.

## Why two apps

Wear OS **does not ship a WebView**, so the timetable cannot be rendered by a
web view on the watch. The phone app hosts a WebView, extracts the parsed
schedule, and pushes it to the watch, which renders it with native Compose.

```
┌─────────────────────┐        Wearable Data Layer        ┌──────────────────┐
│  Phone (mobile)     │  ── messages, chunked ≤60 KB ──▶  │  Watch (wear)    │
│                     │                                    │                  │
│  WebView ──▶ scrape │  ◀── "/spge/request" ────────────  │  Compose UI      │
│  localStorage +DOM  │                                    │  cached on disk  │
└─────────────────────┘                                    └──────────────────┘
```

## Modules

| Module   | Package                    | What it is                                              |
|----------|----------------------------|---------------------------------------------------------|
| `shared` | `com.chernuga.spge.shared` | Data model, period table, report parser, filter index    |
| `mobile` | `com.chernuga.spge.mobile` | WebView host, DOM scraper, sender                        |
| `wear`   | `com.chernuga.spge.wear`   | Native Compose UI, receiver, picker                      |

Both apps share the application ID `com.chernuga.spge` and **must be signed with
the same certificate** — the Wearable Data Layer only delivers between apps that
match on both.

## Building

Requires JDK 17–21 and Android SDK 34.

```bash
# Debug
./gradlew :wear:assembleDebug :mobile:assembleDebug

# Release (needs keystore/spge-release.jks — see below)
./gradlew :wear:assembleRelease :mobile:assembleRelease

# Unit tests for the filter/parse logic
./gradlew :shared:test
```

Release signing reads from `keystore/spge-release.jks` and the
`SPGE_STORE_PASSWORD`, `SPGE_KEY_ALIAS`, `SPGE_KEY_PASSWORD` environment
variables. **The keystore is not in this repository** and must be supplied
separately by whoever maintains the published releases.

## Features

**Filters** — mirror the web app's three dimensions:

- **Клас** (class), **Учител** (teacher), **Стая** (room)
- A tap opens a full-screen picker rather than stepping with arrows; a school
  has far too many teachers and rooms to cycle through one at a time
- The last choice **per dimension** is remembered, so switching back restores it
- The default is the first entry that has lessons **today**
- Compare mode is intentionally omitted: two columns do not work on a 396 px round screen

**Rendering** — current lesson highlighted, day headers with "денес" for today,
room and teacher per card.

## Architecture notes

Things worth knowing before changing this code:

- **Transport is `MessageClient`, not `DataClient`.** DataItems were accepted by
  the phone but never reached the watch's data model on a Galaxy Watch6
  (`DataItem SET (6)` on the phone, `DataItem SET (0)` on the watch). Every
  first-party companion service on that watch registers `MESSAGE_RECEIVED` and
  none registers `DATA_CHANGED`. Messages are not durable, so the receiver
  persists every accepted snapshot and the watch renders offline from cache.
- **Payloads are chunked.** A whole-school timetable is ~270 KB and the channel
  rejects items near 100 KB, so sends are split at 60 KB. Each chunk carries a
  generation timestamp and total count; the receiver only commits once a whole
  generation has arrived, so a partial send cannot replace a good timetable.
- **`ScalingLazyColumn` must not be wrapped in a `Box`.** It applies a
  negative-padding modifier that asserts on unbounded height and crashes with
  `height should be bounded`. Pass it the Scaffold's content slot directly.
- **The phone scrape is two-tier:** the site's structured
  `localStorage["schedule_viewer_data"].allLessons` first, then a DOM scrape of
  `.lesson-card` as fallback.
- **Period times are duplicated** from the web app's `constants.js` into
  `Periods.java` so the watch can render times offline. There is a unit test
  pinning them; if the school changes its timetable, both must be updated.

## Privacy

The app stores a timetable locally on the phone and watch. It sends no data
anywhere except between the paired phone and watch. The WebView loads only the
schedule site and the PDFs it fetches from school hosts.
