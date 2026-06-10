# 🧲 Torrent Quick Viewer

> Preview `.torrent` files inside **Nuclr Commander** with zero guesswork, zero network calls, and zero external tools.

![Torrent Quick Viewer screenshot](images/screenshot-1.jpg)

## ✨ What This Plugin Does

**Torrent Quick Viewer** is an official [Nuclr Commander](https://nuclr.dev) plugin that turns raw `.torrent` files into a clean, rich, read-only preview panel.

Instead of staring at bencoded data, you get the important details instantly:

- 📦 Torrent name, mode, total size, piece size, and piece count
- 🗂️ Full file listing for multi-file torrents
- 🌐 Trackers grouped by announce tier
- 🔐 SHA-1 info hash with copy action
- 🧲 Full magnet link with copy action
- 📝 Comment, creation date, and created-by metadata
- 🚫 No tracker calls, no downloads, no peer traffic

## 🖼️ Preview Panel

| Section | What you see |
|---|---|
| ⚡ Summary | Name, single vs multi-file mode, total size, file count, piece length, piece count, private flag |
| 📝 Metadata | Created-by string, creation date, comment |
| 🔐 Info Hash | 40-character lowercase SHA-1 hash with copy support |
| 🧲 Magnet Link | Full `magnet:?xt=urn:btih:...` URI including trackers |
| 🌐 Trackers | Unique trackers plus grouped announce-list tiers |
| 📂 Files | File path and size for every entry, capped at 500 rows |

## 📥 Installation

Copy the signed plugin archive and detached signature into the Nuclr Commander `plugins/` directory:

```text
quick-view-torrent-<version>.zip
quick-view-torrent-<version>.zip.sig
```

Nuclr Commander verifies the RSA-SHA256 signature against `nuclr-cert.pem` on load. No restart needed. Drop it in and it is live. ⚙️

## 🧠 How It Works

### 📚 Bencode parser

The plugin includes a zero-dependency parser that reads the raw `byte[]` content of the torrent file directly. It supports all four bencode types (integer, byte string, list, dictionary) and tracks the exact byte range of the `info` dictionary so the computed SHA-1 info hash matches what BitTorrent clients and trackers expect.

### 🛡️ Safety limits

| Guard | Limit |
|---|---|
| Max nesting depth | 64 |
| Max total list/dict entries | 100,000 |
| Max byte-string length | 50 MB |

### 🔤 String decoding

Byte strings are decoded as **UTF-8** when valid, with fallback to **ISO-8859-1** for broader compatibility with older torrents.

### 🧵 Async loading

Parsing runs on a virtual thread so the Swing EDT stays responsive. The panel shows a loading state first, then swaps to the rendered preview or an error view when parsing completes.

## 🗃️ Source Layout

```text
src/main/java/dev/nuclr/plugin/core/quick/viewer/
├── TorrentQuickViewProvider.java   plugin entry point
├── TorrentViewPanel.java           Swing panel and UI layout
└── torrent/
    ├── BencodeParser.java          low-level bencode parser
    ├── BencodeException.java       parse error type
    ├── TorrentParser.java          torrent-specific metadata extraction
    ├── TorrentMeta.java            parsed torrent model
    └── TorrentFileEntry.java       individual file entry model
```

## 📚 Dependencies

All dependencies are provided by Nuclr Commander at runtime — nothing extra is bundled in the plugin ZIP.

| Library | Version | Purpose |
|---|---|---|
| `dev.nuclr:platform-sdk` | `3.0.1` | Nuclr platform interfaces |

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE).
