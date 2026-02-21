package dev.nuclr.plugin.core.quick.viewer.torrent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal bencode parser operating on a raw byte array.
 * <p>
 * Tracks the exact byte range of the root-level "info" dictionary value so the
 * caller can compute the correct SHA-1 info hash over the original bytes.
 * <p>
 * Not thread-safe — create a new instance per parse operation.
 *
 * <h3>Guards</h3>
 * <ul>
 *   <li>Max nesting depth: {@value #MAX_DEPTH}</li>
 *   <li>Max total dict/list entries: {@value #MAX_ENTRIES}</li>
 *   <li>Max byte-string length: {@value #MAX_STRING_BYTES} bytes</li>
 * </ul>
 */
public class BencodeParser {

	static final int MAX_DEPTH = 64;
	static final int MAX_ENTRIES = 100_000;
	static final int MAX_STRING_BYTES = 50 * 1024 * 1024; // 50 MB

	private final byte[] data;
	private int pos;
	private int depth;
	private int totalEntries;

	/**
	 * Start offset (inclusive) of the raw bytes of the root dict's "info" value.
	 * Set to -1 if the key was not found.
	 */
	int infoStart = -1;

	/**
	 * End offset (exclusive) of the raw bytes of the root dict's "info" value.
	 * Set to -1 if the key was not found.
	 */
	int infoEnd = -1;

	public BencodeParser(byte[] data) {
		this.data = data;
	}

	/**
	 * Parse the entire byte array and return the root value.
	 *
	 * @return {@link Long}, {@code byte[]}, {@link List}, or {@link Map} depending
	 *         on the bencode type.
	 * @throws BencodeException on malformed input or safety-limit violations.
	 */
	public Object parse() throws BencodeException {
		Object result = parseValue();
		return result;
	}

	// ---- private parsing methods ----------------------------------------

	private Object parseValue() throws BencodeException {
		if (pos >= data.length) {
			throw new BencodeException("Unexpected end of data at position " + pos);
		}
		if (++depth > MAX_DEPTH) {
			throw new BencodeException("Max nesting depth " + MAX_DEPTH + " exceeded");
		}
		try {
			byte b = data[pos];
			if (b == 'i') return parseInteger();
			if (b == 'l') return parseList();
			if (b == 'd') {
				boolean isRoot = (depth == 1);
				return parseDict(isRoot);
			}
			if (b >= '0' && b <= '9') return parseByteString();
			throw new BencodeException(
					"Invalid bencode token 0x" + Integer.toHexString(b & 0xFF) + " at position " + pos);
		} finally {
			depth--;
		}
	}

	private Long parseInteger() throws BencodeException {
		pos++; // skip 'i'
		int start = pos;
		while (pos < data.length && data[pos] != 'e') pos++;
		if (pos >= data.length) {
			throw new BencodeException("Unterminated integer starting at " + start);
		}
		String s = new String(data, start, pos - start, StandardCharsets.US_ASCII);
		pos++; // skip 'e'
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			throw new BencodeException("Invalid integer: " + s);
		}
	}

	private byte[] parseByteString() throws BencodeException {
		int lenStart = pos;
		while (pos < data.length && data[pos] != ':') pos++;
		if (pos >= data.length) {
			throw new BencodeException("Missing ':' in byte string at " + lenStart);
		}
		String lenStr = new String(data, lenStart, pos - lenStart, StandardCharsets.US_ASCII);
		int len;
		try {
			len = Integer.parseInt(lenStr);
		} catch (NumberFormatException e) {
			throw new BencodeException("Invalid string length: " + lenStr);
		}
		if (len < 0 || len > MAX_STRING_BYTES) {
			throw new BencodeException("String length out of bounds: " + len);
		}
		pos++; // skip ':'
		if (pos + len > data.length) {
			throw new BencodeException("String data truncated at position " + pos + " (need " + len + " bytes)");
		}
		byte[] result = Arrays.copyOfRange(data, pos, pos + len);
		pos += len;
		return result;
	}

	private List<Object> parseList() throws BencodeException {
		pos++; // skip 'l'
		List<Object> list = new ArrayList<>();
		while (pos < data.length && data[pos] != 'e') {
			if (++totalEntries > MAX_ENTRIES) {
				throw new BencodeException("Max entry count " + MAX_ENTRIES + " exceeded");
			}
			list.add(parseValue());
		}
		if (pos >= data.length) {
			throw new BencodeException("Unterminated list");
		}
		pos++; // skip 'e'
		return list;
	}

	private Map<String, Object> parseDict(boolean isRoot) throws BencodeException {
		pos++; // skip 'd'
		Map<String, Object> map = new LinkedHashMap<>();
		while (pos < data.length && data[pos] != 'e') {
			if (++totalEntries > MAX_ENTRIES) {
				throw new BencodeException("Max entry count " + MAX_ENTRIES + " exceeded");
			}
			byte[] keyBytes = parseByteString();
			String key = new String(keyBytes, StandardCharsets.UTF_8);

			boolean captureInfo = isRoot && "info".equals(key);
			int valueStart = pos;
			Object value = parseValue();
			if (captureInfo) {
				infoStart = valueStart;
				infoEnd = pos;
			}
			map.put(key, value);
		}
		if (pos >= data.length) {
			throw new BencodeException("Unterminated dictionary");
		}
		pos++; // skip 'e'
		return map;
	}

}
