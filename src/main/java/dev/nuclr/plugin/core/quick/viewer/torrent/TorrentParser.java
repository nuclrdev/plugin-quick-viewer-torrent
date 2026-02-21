package dev.nuclr.plugin.core.quick.viewer.torrent;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a raw .torrent file byte array into a {@link TorrentMeta} object.
 *
 * <p>No network access is performed. The info hash is computed from the exact
 * raw bytes of the "info" dictionary using SHA-1, per the BitTorrent spec.
 */
public final class TorrentParser {

	private static final int MAX_FILES_DISPLAY = 500;

	private TorrentParser() {}

	/**
	 * Parse a .torrent file.
	 *
	 * @param data raw bytes of the .torrent file
	 * @return populated {@link TorrentMeta}
	 * @throws BencodeException if the data is not valid bencode or the root/info
	 *                          dictionaries are missing
	 */
	@SuppressWarnings("unchecked")
	public static TorrentMeta parse(byte[] data) throws BencodeException {
		BencodeParser parser = new BencodeParser(data);
		Object root = parser.parse();

		if (!(root instanceof Map<?, ?> rawRoot)) {
			throw new BencodeException("Root element is not a dictionary");
		}
		Map<String, Object> rootDict = (Map<String, Object>) rawRoot;

		TorrentMeta meta = new TorrentMeta();

		// Top-level fields
		meta.setAnnounce(getString(rootDict, "announce"));
		meta.setTrackerTiers(parseTrackerTiers(rootDict.get("announce-list")));
		meta.setCreatedBy(getString(rootDict, "created by"));
		meta.setComment(getString(rootDict, "comment"));

		Object creationDateObj = rootDict.get("creation date");
		if (creationDateObj instanceof Long ld) {
			meta.setCreationDate(ld);
		}

		// Info dictionary
		Object infoObj = rootDict.get("info");
		if (!(infoObj instanceof Map<?, ?> rawInfo)) {
			throw new BencodeException("Missing or invalid 'info' dictionary");
		}
		Map<String, Object> info = (Map<String, Object>) rawInfo;

		// Name
		byte[] nameBytes = getBytes(info, "name");
		meta.setName(nameBytes != null ? decodeString(nameBytes) : "Unknown");

		// Piece parameters
		meta.setPieceLength(getLong(info, "piece length", 0));
		byte[] pieces = getBytes(info, "pieces");
		meta.setPieceCount(pieces != null ? pieces.length / 20 : 0);

		// Private flag
		meta.setPrivateFlag(getLong(info, "private", 0) == 1L);

		// Files
		Object filesObj = info.get("files");
		if (filesObj instanceof List<?> rawFiles) {
			// Multi-file torrent
			meta.setMultiFile(true);
			List<TorrentFileEntry> files = parseFiles(rawFiles);
			meta.setFiles(files);
			meta.setTotalSize(files.stream().mapToLong(TorrentFileEntry::getLength).sum());
		} else {
			// Single-file torrent
			meta.setMultiFile(false);
			long length = getLong(info, "length", 0);
			meta.setTotalSize(length);
			meta.setFiles(List.of(new TorrentFileEntry(meta.getName(), length)));
		}

		// Info hash from raw bytes
		String infoHashHex = computeInfoHash(data, parser.infoStart, parser.infoEnd);
		meta.setInfoHashHex(infoHashHex);

		// Magnet link
		meta.setMagnetLink(buildMagnetLink(meta));

		return meta;
	}

	// ---- private helpers -----------------------------------------------

	@SuppressWarnings("unchecked")
	private static List<List<String>> parseTrackerTiers(Object announceList) {
		if (!(announceList instanceof List<?> rawTiers)) return List.of();
		List<List<String>> tiers = new ArrayList<>();
		for (Object tierObj : rawTiers) {
			if (!(tierObj instanceof List<?> rawTier)) continue;
			List<String> tier = new ArrayList<>();
			for (Object trackerObj : rawTier) {
				if (trackerObj instanceof byte[] bytes) {
					tier.add(decodeString(bytes));
				}
			}
			if (!tier.isEmpty()) tiers.add(List.copyOf(tier));
		}
		return List.copyOf(tiers);
	}

	@SuppressWarnings("unchecked")
	private static List<TorrentFileEntry> parseFiles(List<?> filesList) {
		List<TorrentFileEntry> files = new ArrayList<>();
		for (Object fileObj : filesList) {
			if (!(fileObj instanceof Map<?, ?> rawFile)) continue;
			Map<String, Object> fileDict = (Map<String, Object>) rawFile;
			long length = getLong(fileDict, "length", 0);
			String path = buildFilePath(fileDict.get("path"));
			files.add(new TorrentFileEntry(path, length));
		}
		return files;
	}

	private static String buildFilePath(Object pathObj) {
		if (!(pathObj instanceof List<?> parts)) return "unknown";
		List<String> segments = new ArrayList<>();
		for (Object part : parts) {
			if (part instanceof byte[] bytes) {
				segments.add(decodeString(bytes));
			}
		}
		return String.join("/", segments);
	}

	private static String computeInfoHash(byte[] data, int start, int end) {
		if (start < 0 || end <= start || end > data.length) return null;
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(data, start, end - start);
			byte[] hash = sha1.digest();
			StringBuilder sb = new StringBuilder(40);
			for (byte b : hash) {
				sb.append(String.format("%02x", b & 0xFF));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return null; // SHA-1 is mandatory in every JDK
		}
	}

	private static String buildMagnetLink(TorrentMeta meta) {
		String hash = meta.getInfoHashHex();
		if (hash == null || hash.isEmpty()) return null;

		StringBuilder sb = new StringBuilder("magnet:?xt=urn:btih:");
		sb.append(hash);

		if (meta.getName() != null) {
			sb.append("&dn=").append(URLEncoder.encode(meta.getName(), StandardCharsets.UTF_8));
		}

		// Deduplicated tracker set: announce first, then announce-list
		Set<String> seen = new LinkedHashSet<>();
		if (meta.getAnnounce() != null) seen.add(meta.getAnnounce());
		for (List<String> tier : meta.getTrackerTiers()) seen.addAll(tier);
		for (String tracker : seen) {
			sb.append("&tr=").append(URLEncoder.encode(tracker, StandardCharsets.UTF_8));
		}

		return sb.toString();
	}

	// ---- map accessors -------------------------------------------------

	private static String getString(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return (val instanceof byte[] bytes) ? decodeString(bytes) : null;
	}

	private static byte[] getBytes(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return (val instanceof byte[] bytes) ? bytes : null;
	}

	private static long getLong(Map<String, Object> map, String key, long def) {
		Object val = map.get(key);
		return (val instanceof Long l) ? l : def;
	}

	/**
	 * Decode a bencode byte string as UTF-8 if valid; otherwise fall back to
	 * ISO-8859-1 (covers most non-UTF-8 torrents created on Windows/Latin-1).
	 */
	static String decodeString(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString();
		} catch (CharacterCodingException e) {
			return new String(bytes, StandardCharsets.ISO_8859_1);
		}
	}

}
