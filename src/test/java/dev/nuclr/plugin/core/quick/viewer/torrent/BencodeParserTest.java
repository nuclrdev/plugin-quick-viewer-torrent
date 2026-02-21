package dev.nuclr.plugin.core.quick.viewer.torrent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BencodeParserTest {

	// ---- integers --------------------------------------------------------

	@Test
	void parseZero() throws Exception {
		assertEquals(0L, parse("i0e"));
	}

	@Test
	void parsePositiveInteger() throws Exception {
		assertEquals(42L, parse("i42e"));
	}

	@Test
	void parseNegativeInteger() throws Exception {
		assertEquals(-5L, parse("i-5e"));
	}

	@Test
	void parseLargeInteger() throws Exception {
		assertEquals(Long.MAX_VALUE, parse("i" + Long.MAX_VALUE + "e"));
	}

	@Test
	void unterminatedInteger_throws() {
		assertThrows(BencodeException.class, () -> parse("i42"));
	}

	@Test
	void invalidInteger_throws() {
		assertThrows(BencodeException.class, () -> parse("iXe"));
	}

	// ---- byte strings ----------------------------------------------------

	@Test
	void parseEmptyString() throws Exception {
		assertArrayEquals(new byte[0], (byte[]) parse("0:"));
	}

	@Test
	void parseAsciiString() throws Exception {
		assertArrayEquals("spam".getBytes(StandardCharsets.UTF_8), (byte[]) parse("4:spam"));
	}

	@Test
	void parseBinaryString() throws Exception {
		byte[] bytes = {0x00, (byte) 0xFF, 0x0A};
		byte[] encoded = ("3:" + new String(bytes, StandardCharsets.ISO_8859_1))
				.getBytes(StandardCharsets.ISO_8859_1);
		assertArrayEquals(bytes, (byte[]) new BencodeParser(encoded).parse());
	}

	@Test
	void missingColon_throws() {
		assertThrows(BencodeException.class, () -> parse("4spam"));
	}

	@Test
	void stringTruncated_throws() {
		assertThrows(BencodeException.class, () -> parse("10:short"));
	}

	// ---- lists -----------------------------------------------------------

	@Test
	void parseEmptyList() throws Exception {
		List<?> list = (List<?>) parse("le");
		assertTrue(list.isEmpty());
	}

	@Test
	void parseIntegerList() throws Exception {
		List<?> list = (List<?>) parse("li1ei2ei3ee");
		assertEquals(List.of(1L, 2L, 3L), list);
	}

	@Test
	void parseNestedList() throws Exception {
		// l [ [i1e] ] e
		List<?> outer = (List<?>) parse("lli1eee");
		assertEquals(1, outer.size());
		List<?> inner = (List<?>) outer.get(0);
		assertEquals(List.of(1L), inner);
	}

	@Test
	void unterminatedList_throws() {
		assertThrows(BencodeException.class, () -> parse("li1e"));
	}

	// ---- dictionaries ----------------------------------------------------

	@Test
	void parseEmptyDict() throws Exception {
		Map<?, ?> map = (Map<?, ?>) parse("de");
		assertTrue(map.isEmpty());
	}

	@Test
	void parseDictWithIntAndString() throws Exception {
		Map<?, ?> map = (Map<?, ?>) parse("d3:fooi1e3:bar4:teste");
		assertEquals(1L, map.get("foo"));
		assertArrayEquals("test".getBytes(), (byte[]) map.get("bar"));
	}

	@Test
	void unterminatedDict_throws() {
		assertThrows(BencodeException.class, () -> parse("d3:fooi1e"));
	}

	// ---- invalid token ---------------------------------------------------

	@Test
	void unknownToken_throws() {
		assertThrows(BencodeException.class, () -> parse("xyz"));
	}

	@Test
	void emptyInput_throws() {
		assertThrows(BencodeException.class, () -> parse(""));
	}

	// ---- safety guards ---------------------------------------------------

	@Test
	void maxDepthExceeded_throws() {
		// Build a list nested one level deeper than the limit
		int depth = BencodeParser.MAX_DEPTH + 2;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) sb.append('l');
		sb.append("i0e");
		for (int i = 0; i < depth; i++) sb.append('e');
		assertThrows(BencodeException.class, () -> parse(sb.toString()));
	}

	// ---- info-offset tracking -------------------------------------------

	@Test
	void infoOffsetsNotSetForNonDict() throws Exception {
		BencodeParser p = new BencodeParser("i0e".getBytes());
		p.parse();
		assertEquals(-1, p.infoStart);
		assertEquals(-1, p.infoEnd);
	}

	@Test
	void infoOffsetsSetCorrectly() throws Exception {
		// d 4:info d 3:key 5:value e e
		String bencode = "d4:infod3:key5:valueee";
		BencodeParser p = new BencodeParser(bencode.getBytes(StandardCharsets.US_ASCII));
		p.parse();
		// info value starts at "d3:key5:valuee"
		String infoRaw = bencode.substring(p.infoStart, p.infoEnd);
		assertEquals("d3:key5:valuee", infoRaw);
	}

	// ---- helper ----------------------------------------------------------

	private Object parse(String s) throws BencodeException {
		return new BencodeParser(s.getBytes(StandardCharsets.US_ASCII)).parse();
	}

}
