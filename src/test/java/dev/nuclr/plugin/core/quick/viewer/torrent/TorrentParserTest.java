package dev.nuclr.plugin.core.quick.viewer.torrent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TorrentParserTest {

	// ---- single-file torrent ---------------------------------------------

	@Test
	void singleFileTorrent_basicFields() throws Exception {
		byte[] data = new Builder()
				.dictStart()
					.str("announce").str("http://tracker.example.com/announce")
					.str("created by").str("TestClient/1.0")
					.str("creation date").integer(1_700_000_000L)
					.str("comment").str("a test torrent")
					.str("info").dictStart()
						.str("name").str("hello.txt")
						.str("length").integer(12_345L)
						.str("piece length").integer(524_288L)
						.str("pieces").bytes(new byte[20]) // 1 piece
					.dictEnd()
				.dictEnd()
				.toBytes();

		TorrentMeta meta = TorrentParser.parse(data);

		assertEquals("hello.txt", meta.getName());
		assertFalse(meta.isMultiFile());
		assertEquals(12_345L, meta.getTotalSize());
		assertEquals(1, meta.getFiles().size());
		assertEquals("hello.txt", meta.getFiles().get(0).getPath());
		assertEquals(12_345L, meta.getFiles().get(0).getLength());
		assertEquals(524_288L, meta.getPieceLength());
		assertEquals(1, meta.getPieceCount());
		assertFalse(meta.isPrivateFlag());
		assertEquals("http://tracker.example.com/announce", meta.getAnnounce());
		assertEquals("TestClient/1.0", meta.getCreatedBy());
		assertEquals(1_700_000_000L, meta.getCreationDate());
		assertEquals("a test torrent", meta.getComment());
	}

	// ---- multi-file torrent ----------------------------------------------

	@Test
	void multiFileTorrent_filesAndTotalSize() throws Exception {
		byte[] data = new Builder()
				.dictStart()
					.str("info").dictStart()
						.str("name").str("MyAlbum")
						.str("piece length").integer(262_144L)
						.str("pieces").bytes(new byte[40]) // 2 pieces
						.str("files").listStart()
							.dictStart()
								.str("path").listStart().str("track01.flac").listEnd()
								.str("length").integer(50_000_000L)
							.dictEnd()
							.dictStart()
								.str("path").listStart().str("track02.flac").listEnd()
								.str("length").integer(48_000_000L)
							.dictEnd()
						.listEnd()
					.dictEnd()
				.dictEnd()
				.toBytes();

		TorrentMeta meta = TorrentParser.parse(data);

		assertTrue(meta.isMultiFile());
		assertEquals("MyAlbum", meta.getName());
		assertEquals(2, meta.getFiles().size());
		assertEquals("track01.flac", meta.getFiles().get(0).getPath());
		assertEquals(50_000_000L, meta.getFiles().get(0).getLength());
		assertEquals("track02.flac", meta.getFiles().get(1).getPath());
		assertEquals(98_000_000L, meta.getTotalSize());
		assertEquals(2, meta.getPieceCount());
	}

	// ---- nested path in multi-file ---------------------------------------

	@Test
	void multiFileTorrent_nestedPath() throws Exception {
		byte[] data = new Builder()
				.dictStart()
					.str("info").dictStart()
						.str("name").str("Project")
						.str("piece length").integer(262_144L)
						.str("pieces").bytes(new byte[20])
						.str("files").listStart()
							.dictStart()
								.str("path").listStart()
									.str("src").str("main").str("App.java")
								.listEnd()
								.str("length").integer(1_024L)
							.dictEnd()
						.listEnd()
					.dictEnd()
				.dictEnd()
				.toBytes();

		TorrentMeta meta = TorrentParser.parse(data);
		assertEquals("src/main/App.java", meta.getFiles().get(0).getPath());
	}

	// ---- announce-list ---------------------------------------------------

	@Test
	void announceListTiers() throws Exception {
		byte[] data = new Builder()
				.dictStart()
					.str("announce").str("http://tier1a.com/announce")
					.str("announce-list").listStart()
						.listStart()
							.str("http://tier1a.com/announce")
							.str("http://tier1b.com/announce")
						.listEnd()
						.listStart()
							.str("http://tier2a.com/announce")
						.listEnd()
					.listEnd()
					.str("info").dictStart()
						.str("name").str("test")
						.str("length").integer(1L)
						.str("piece length").integer(262_144L)
						.str("pieces").bytes(new byte[20])
					.dictEnd()
				.dictEnd()
				.toBytes();

		TorrentMeta meta = TorrentParser.parse(data);

		List<List<String>> tiers = meta.getTrackerTiers();
		assertEquals(2, tiers.size());
		assertEquals(List.of("http://tier1a.com/announce", "http://tier1b.com/announce"), tiers.get(0));
		assertEquals(List.of("http://tier2a.com/announce"), tiers.get(1));
	}

	// ---- private flag ----------------------------------------------------

	@Test
	void privateFlag_whenSet() throws Exception {
		byte[] data = new Builder()
				.dictStart()
					.str("info").dictStart()
						.str("name").str("priv")
						.str("length").integer(100L)
						.str("piece length").integer(262_144L)
						.str("pieces").bytes(new byte[20])
						.str("private").integer(1L)
					.dictEnd()
				.dictEnd()
				.toBytes();

		TorrentMeta meta = TorrentParser.parse(data);
		assertTrue(meta.isPrivateFlag());
	}

	// ---- info hash -------------------------------------------------------

	@Test
	void infoHash_matchesSha1OfInfoBytes() throws Exception {
		// Build a minimal torrent and compute expected hash independently
		byte[] infoDict = new Builder()
				.dictStart()
					.str("name").str("hash-test")
					.str("length").integer(999L)
					.str("piece length").integer(262_144L)
					.str("pieces").bytes(new byte[20])
				.dictEnd()
				.toBytes();

		byte[] data = new Builder()
				.dictStart()
					.str("info").rawBytes(infoDict)
				.dictEnd()
				.toBytes();

		// Expected: SHA-1 of infoDict bytes
		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] expectedHash = sha1.digest(infoDict);
		StringBuilder expected = new StringBuilder();
		for (byte b : expectedHash) expected.append(String.format("%02x", b & 0xFF));

		TorrentMeta meta = TorrentParser.parse(data);
		assertEquals(expected.toString(), meta.getInfoHashHex());
	}

	// ---- magnet link -----------------------------------------------------

	@Test
	void magnetLink_containsInfoHashAndName() throws Exception {
		byte[] data = new Builder()
				.dictStart()
					.str("announce").str("http://tracker.example.com/")
					.str("info").dictStart()
						.str("name").str("MyFile")
						.str("length").integer(1L)
						.str("piece length").integer(262_144L)
						.str("pieces").bytes(new byte[20])
					.dictEnd()
				.dictEnd()
				.toBytes();

		TorrentMeta meta = TorrentParser.parse(data);

		assertNotNull(meta.getMagnetLink());
		assertTrue(meta.getMagnetLink().startsWith("magnet:?xt=urn:btih:"));
		assertTrue(meta.getMagnetLink().contains("dn=MyFile"));
		assertTrue(meta.getMagnetLink().contains("tr="));
	}

	// ---- robustness / malformed bencode ----------------------------------

	@Test
	void emptyBytes_throwsBencodeException() {
		assertThrows(BencodeException.class, () -> TorrentParser.parse(new byte[0]));
	}

	@Test
	void nonDictRoot_throwsBencodeException() {
		// root is a list, not a dict
		assertThrows(BencodeException.class, () -> TorrentParser.parse("li1ee".getBytes()));
	}

	@Test
	void missingInfoDict_throwsBencodeException() {
		byte[] data = ("d7:comment4:teste").getBytes(StandardCharsets.US_ASCII);
		assertThrows(BencodeException.class, () -> TorrentParser.parse(data));
	}

	@Test
	void truncatedData_throwsBencodeException() {
		byte[] data = "d4:infod3:key".getBytes(StandardCharsets.US_ASCII);
		assertThrows(BencodeException.class, () -> TorrentParser.parse(data));
	}

	@Test
	void deeplyNestedMalicious_doesNotCrash() {
		int depth = BencodeParser.MAX_DEPTH + 5;
		StringBuilder sb = new StringBuilder("d4:info");
		for (int i = 0; i < depth; i++) sb.append('l');
		sb.append("i0e");
		for (int i = 0; i < depth; i++) sb.append('e');
		sb.append('e');
		assertThrows(BencodeException.class,
				() -> TorrentParser.parse(sb.toString().getBytes(StandardCharsets.US_ASCII)));
	}

	// ---- decodeString ----------------------------------------------------

	@Test
	void decodeString_validUtf8() {
		byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_8);
		assertEquals("héllo", TorrentParser.decodeString(bytes));
	}

	@Test
	void decodeString_invalidUtf8_fallsBackToLatin1() {
		// 0xFF is not valid UTF-8 but is valid ISO-8859-1
		byte[] bytes = {(byte) 0xFF};
		String result = TorrentParser.decodeString(bytes);
		assertNotNull(result);
		assertEquals(1, result.length());
	}

	// ---- Builder helper --------------------------------------------------

	/** Minimal bencode serializer for constructing test data. */
	private static class Builder {

		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		Builder integer(long val) {
			write("i" + val + "e");
			return this;
		}

		Builder str(String s) {
			return bytes(s.getBytes(StandardCharsets.UTF_8));
		}

		Builder bytes(byte[] b) {
			write(b.length + ":");
			try { out.write(b); } catch (IOException e) { throw new RuntimeException(e); }
			return this;
		}

		/** Insert already-bencoded bytes verbatim (used for rawBytes embedding). */
		Builder rawBytes(byte[] b) {
			try { out.write(b); } catch (IOException e) { throw new RuntimeException(e); }
			return this;
		}

		Builder dictStart()  { out.write('d'); return this; }
		Builder dictEnd()    { out.write('e'); return this; }
		Builder listStart()  { out.write('l'); return this; }
		Builder listEnd()    { out.write('e'); return this; }

		byte[] toBytes() { return out.toByteArray(); }

		private void write(String s) {
			try { out.write(s.getBytes(StandardCharsets.US_ASCII)); }
			catch (IOException e) { throw new RuntimeException(e); }
		}
	}

}
