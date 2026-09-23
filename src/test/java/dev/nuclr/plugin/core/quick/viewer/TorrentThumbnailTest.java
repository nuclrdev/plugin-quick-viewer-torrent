package dev.nuclr.plugin.core.quick.viewer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import dev.nuclr.platform.plugin.NuclrResource;

class TorrentThumbnailTest {

	private static final String TORRENT = "d4:infod5:filesld6:lengthi5e4:pathl5:a.txteed6:lengthi7e4:pathl3:sub5:b.txteee"
			+ "4:name6:bundle12:piece lengthi16384e6:pieces20:aaaaaaaaaaaaaaaaaaaaee";

	private final TorrentQuickViewProvider provider = new TorrentQuickViewProvider();

	@Test
	void drawsAListingPageWithinTheBoxBeforeInit() {
		assertTrue(provider.supportsThumbnails());

		BufferedImage image = provider.thumbnail(resource("bundle.torrent", TORRENT), 120, 120, new AtomicBoolean());

		assertNotNull(image);
		assertTrue(image.getWidth() <= 120 && image.getHeight() <= 120);
	}

	@Test
	void returnsNullForDamagedForeignOrCancelled() {
		assertNull(provider.thumbnail(resource("broken.torrent", "d4:info"), 120, 120, new AtomicBoolean()));
		assertNull(provider.thumbnail(resource("bundle.txt", TORRENT), 120, 120, new AtomicBoolean()));
		assertNull(provider.thumbnail(resource("bundle.torrent", TORRENT), 120, 120, new AtomicBoolean(true)));
	}

	private static NuclrResource resource(String name, String content) {
		byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);
		NuclrResource resource = new NuclrResource(null) {
			private static final long serialVersionUID = 1L;

			@Override
			public InputStream openInputStream(OpenOption... options) {
				return new ByteArrayInputStream(bytes);
			}
		};
		resource.setUuid(name);
		resource.setName(name);
		resource.setLength(bytes.length);
		return resource;
	}
}
