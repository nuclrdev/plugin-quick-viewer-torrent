# Torrent Quick Viewer

A [Nuclr Commander](https://nuclr.dev) plugin that renders a rich read-only preview of `.torrent` files directly in the Quick View panel — no network access, no tracker calls, no external tools required.

---

## Preview

| Section | What you see |
|---|---|
| **Summary** | Name, mode (single / multi-file), total size, file count, piece length & count, private flag |
| **Metadata** | Created-by string, creation date (local time), comment |
| **Info Hash** | 40-char lowercase SHA-1 hex + **Copy** button |
| **Magnet Link** | Full `magnet:?xt=urn:btih:…` URI with trackers + **Copy Magnet** button |
| **Trackers** | All unique trackers; announce-list tiers shown grouped (Tier 1, Tier 2 …) |
| **Files** | Path and size for every file; large lists capped at 500 entries |

---

## Installation

Copy the signed plugin archive and its detached signature into the Nuclr Commander `plugins/` directory:

```
quick-view-torrent-1.0.0.zip
quick-view-torrent-1.0.0.zip.sig
```

Nuclr Commander verifies the RSA-SHA256 signature against `nuclr-cert.pem` on load. The plugin is active immediately — no restart required.

---

## Building

Prerequisites: **Java 21+**, **Maven 3.9+**, and the `plugins-sdk` installed locally (`mvn install` in `plugins-sdk/`).

```bash
# Compile, test, package and sign
mvn clean verify -Djarsigner.storepass=<keystore-password>

# Artifacts produced in target/
#   quick-view-torrent-1.0.0.zip      — plugin archive
#   quick-view-torrent-1.0.0.zip.sig  — detached RSA-SHA256 signature
```

The signing step requires the keystore at `C:/nuclr/key/nuclr-signing.p12` with alias `nuclr`.

### Quick deploy to local commander

```bat
deploy.bat
```

Runs `mvn clean verify` then copies both artifacts into `C:\nuclr\sources\commander\plugins\`.

---

## How it works

### Bencode parser

A zero-dependency parser operates directly on the raw `byte[]` of the torrent file. It supports all four bencode types (integer, byte string, list, dictionary) and tracks the exact byte-range of the `info` dictionary value so the SHA-1 info hash is computed over the original bytes — matching what BitTorrent clients produce.

Safety limits prevent malformed or adversarial files from hanging or crashing the host application:

| Guard | Limit |
|---|---|
| Max nesting depth | 64 |
| Max total list/dict entries | 100 000 |
| Max byte-string length | 50 MB |

### String encoding

Byte strings are decoded as **UTF-8** when valid; otherwise fall back to **ISO-8859-1** (covers the majority of older Windows-created torrents).

### Info hash

SHA-1 is computed over the verbatim bencoded bytes of the `info` dictionary — identical to the hash used by BitTorrent clients and trackers.

### Asynchronous loading

Parsing runs on a virtual thread. The UI shows a "Loading…" indicator immediately and switches to the full panel (or an error panel) once parsing completes, so the Swing EDT is never blocked.

---

## Plugin manifest

```json
{
  "id":      "dev.nuclr.plugin.core.quickviewer.torrent",
  "name":    "Torrent Quick Viewer",
  "version": "1.0.0",
  "type":    "Official",
  "quickViewProviders": [
    "dev.nuclr.plugin.core.quick.viewer.TorrentQuickViewProvider"
  ]
}
```

---

## Source layout

```
src/
├── main/java/dev/nuclr/plugin/core/quick/viewer/
│   ├── TorrentQuickViewProvider.java   # QuickViewProvider entry point
│   ├── TorrentViewPanel.java           # Swing UI panel
│   └── torrent/
│       ├── BencodeParser.java          # Low-level bencode parser
│       ├── BencodeException.java       # Parse error
│       ├── TorrentParser.java          # High-level .torrent parser
│       ├── TorrentMeta.java            # Parsed metadata model
│       └── TorrentFileEntry.java       # Single file entry (path + size)
├── main/resources/
│   └── plugin.json
└── test/java/dev/nuclr/plugin/core/quick/viewer/torrent/
    ├── BencodeParserTest.java          # Bencode decoding unit tests
    └── TorrentParserTest.java          # Torrent parsing & robustness tests
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
