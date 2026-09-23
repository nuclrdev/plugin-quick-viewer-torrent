package dev.nuclr.plugin.core.quick.viewer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResource;
import dev.nuclr.platform.plugin.QuickViewNuclrPlugin;
import dev.nuclr.plugin.core.quick.viewer.torrent.TorrentFileEntry;
import dev.nuclr.plugin.core.quick.viewer.torrent.TorrentMeta;
import dev.nuclr.plugin.core.quick.viewer.torrent.TorrentParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TorrentQuickViewProvider implements QuickViewNuclrPlugin {

	private NuclrPluginContext context;
	private TorrentViewPanel panel;
	private volatile AtomicBoolean currentCancelled;
	private String uuid = java.util.UUID.randomUUID().toString();

	@Override
	public JComponent panel() {
		if (this.panel == null) {
			this.panel = new TorrentViewPanel();
		}
		return panel;
	}

	@Override
	public void preinit(NuclrPluginContext context) {
		this.context = context;
	}

	@Override
	public void init() {
	}

	@Override
	public NuclrPluginContext getContext() {
		return this.context;
	}

	@Override
	public boolean supports(NuclrResource resource) {
		String extension = extension(resource);
		if (extension == null) {
			return false;
		}
		return TorrentViewPanel.EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
	}

	private static String extension(NuclrResource resource) {
		if (resource == null || resource.getName() == null) {
			return null;
		}
		String name = resource.getName();
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return null;
		}
		return name.substring(dot + 1);
	}

	@Override
	public boolean openResource(NuclrResource resource, AtomicBoolean cancelled) {
		if (currentCancelled != null) {
			currentCancelled.set(true);
		}
		this.currentCancelled = cancelled;
		panel();
		return this.panel.load(resource, cancelled);
	}


	/** Metainfo files are small; anything past this is not one worth drawing. */
	private static final int MAX_THUMBNAIL_BYTES = 16 * 1024 * 1024;

	@Override
	public boolean supportsThumbnails() {
		return true;
	}

	/** A listing page: the torrent's name and totals, then the files it carries. */
	@Override
	public BufferedImage thumbnail(NuclrResource resource, int maxWidth, int maxHeight, AtomicBoolean cancelled) {
		if (maxWidth <= 0 || maxHeight <= 0 || !supports(resource) || resource.getLength() > MAX_THUMBNAIL_BYTES) {
			return null;
		}
		try {
			byte[] data;
			try (var in = resource.openInputStream()) {
				data = in.readNBytes(MAX_THUMBNAIL_BYTES);
			}
			if (cancelled != null && cancelled.get()) {
				return null;
			}
			TorrentMeta meta = TorrentParser.parse(data);
			List<PageThumbnail.Line> lines = new ArrayList<>();
			lines.add(PageThumbnail.Line.title(meta.getName() != null ? meta.getName() : resource.getName()));
			int fileCount = meta.getFiles() != null ? meta.getFiles().size() : 0;
			lines.add(PageThumbnail.Line.muted(fileCount + (fileCount == 1 ? " file · " : " files · ")
					+ TorrentViewPanel.formatSize(meta.getTotalSize())));
			lines.add(PageThumbnail.Line.blank());
			if (meta.getFiles() != null) {
				meta.getFiles().stream().limit(150).map(TorrentFileEntry::getPath)
						.forEach(path -> lines.add(PageThumbnail.Line.mono(path)));
			}
			return PageThumbnail.render(lines, maxWidth, maxHeight, cancelled);
		} catch (Exception e) {
			log.debug("No thumbnail for {}: {}", resource.getName(), e.toString());
			return null;
		}
	}

	@Override
	public void closeResource() {
		if (currentCancelled != null) {
			currentCancelled.set(true);
			currentCancelled = null;
		}
		if (this.panel != null) {
			this.panel.clear();
		}
	}

	@Override
	public void unload() {
		closeResource();
		this.panel = null;
		this.context = null;
	}


	@Override
	public boolean onFocusGained() {
		return false;
	}

	@Override
	public void onFocusLost() {
	}

	@Override
	public boolean isFocused() {
		return false;
	}
	


	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {
	}

	@Override
	public NuclrResource getCurrentResource() {
		return null;
	}

	@Override
	public String uuid() {
		return uuid;
	}
	

}
