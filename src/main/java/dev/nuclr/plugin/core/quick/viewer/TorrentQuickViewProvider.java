package dev.nuclr.plugin.core.quick.viewer;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResourcePath;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TorrentQuickViewProvider implements NuclrPlugin {

	private NuclrPluginContext context;
	private TorrentViewPanel panel;
	private volatile AtomicBoolean currentCancelled;

	@Override
	public JComponent panel() {
		if (this.panel == null) {
			this.panel = new TorrentViewPanel();
		}
		return panel;
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResourcePath source) {
		return List.of();
	}

	@Override
	public void load(NuclrPluginContext context) {
		this.context = context;
	}

	@Override
	public boolean supports(NuclrResourcePath resource) {
		if (resource == null || resource.getExtension() == null) {
			return false;
		}
		return TorrentViewPanel.EXTENSIONS.contains(resource.getExtension().toLowerCase(Locale.ROOT));
	}

	@Override
	public boolean openResource(NuclrResourcePath resource, AtomicBoolean cancelled) {
		if (currentCancelled != null) {
			currentCancelled.set(true);
		}
		this.currentCancelled = cancelled;
		panel();
		return this.panel.load(resource, cancelled);
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
	public int priority() {
		return 1;
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
	
	private String name = "Torrent Quick Viewer";
	private String id = "dev.nuclr.plugin.core.quickviewer.torrent";
	private String version = "1.0.0";
	private String description = "A quick viewer for .torrent files — displays metadata, trackers, file listing, info hash and magnet link.";
	private String author = "Nuclr Development Team";
	private String license = "Apache-2.0";
	private String website = "https://nuclr.dev";
	private String pageUrl = "https://nuclr.dev/plugins/core/torrent-quick-viewer.html";
	private String docUrl = "https://nuclr.dev/plugins/core/torrent-quick-viewer.html";

	@Override
	public String id() {
		return id;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String version() {
		return version;
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public String author() {
		return author;
	}

	@Override
	public String license() {
		return license;
	}

	@Override
	public String website() {
		return website;
	}

	@Override
	public String pageUrl() {
		return pageUrl;
	}

	@Override
	public String docUrl() {
		return docUrl;
	}

	@Override
	public Developer type() {
		return Developer.Official;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {
	}
	
}
