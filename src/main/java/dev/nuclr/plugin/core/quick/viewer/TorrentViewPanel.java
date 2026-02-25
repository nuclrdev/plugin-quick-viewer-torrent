package dev.nuclr.plugin.core.quick.viewer;

import dev.nuclr.plugin.QuickViewItem;
import dev.nuclr.plugin.core.quick.viewer.torrent.TorrentFileEntry;
import dev.nuclr.plugin.core.quick.viewer.torrent.TorrentMeta;
import dev.nuclr.plugin.core.quick.viewer.torrent.TorrentParser;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TorrentViewPanel extends JPanel {

	static final Set<String> EXTENSIONS = Set.of("torrent");

	private static final Font MONO_FONT  = new Font(Font.MONOSPACED, Font.PLAIN, 12);
	private static final Font MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 11);
	private static final DateTimeFormatter DATE_FMT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private volatile Thread loadThread;

	public TorrentViewPanel() {
		setLayout(new BorderLayout());
		showMessage("No file selected.");
	}

	// ---- public API -------------------------------------------------------

	public boolean load(QuickViewItem item, AtomicBoolean cancelled) {
		Thread prev = loadThread;
		if (prev != null) prev.interrupt();
		showMessage("Loading\u2026");
		loadThread = Thread.ofVirtual().start(() -> {
			try {
				byte[] data;
				try (var in = item.openStream()) {
					data = in.readAllBytes();
				}
				if (cancelled.get()) return;
				TorrentMeta meta = TorrentParser.parse(data);
				if (cancelled.get()) return;
				SwingUtilities.invokeLater(() -> showMeta(meta));
			} catch (Exception e) {
				if (cancelled.get()) return;
				log.error("Failed to parse torrent: {}", item.name(), e);
				String msg = e.getMessage();
				if (msg == null) msg = e.getClass().getSimpleName();
				if (msg.length() > 300) msg = msg.substring(0, 300) + "\u2026";
				final String finalMsg = msg;
				SwingUtilities.invokeLater(() -> showError(finalMsg));
			}
		});
		return true;
	}

	public void clear() {
		Thread prev = loadThread;
		if (prev != null) prev.interrupt();
		loadThread = null;
		SwingUtilities.invokeLater(() -> showMessage(""));
	}

	// ---- display helpers --------------------------------------------------

	private void showMessage(String text) {
		removeAll();
		add(new JLabel(text, JLabel.CENTER), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void showError(String message) {
		removeAll();
		JPanel errPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.insets = new Insets(4, 0, 4, 0);

		JLabel title = new JLabel("Invalid torrent file");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		gbc.gridy = 0;
		errPanel.add(title, gbc);

		JLabel detail = new JLabel("<html><center>" + escapeHtml(message) + "</center></html>");
		detail.setForeground(UIManager.getColor("Label.disabledForeground"));
		gbc.gridy = 1;
		errPanel.add(detail, gbc);

		add(errPanel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void showMeta(TorrentMeta meta) {
		removeAll();
		JPanel content = buildContent(meta);
		JScrollPane scroll = new JScrollPane(content,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
		revalidate();
		repaint();
	}

	// ---- content builder --------------------------------------------------

	private JPanel buildContent(TorrentMeta meta) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		// 1. Summary
		FormSection summary = new FormSection("Summary");
		summary.addRow("Name",         meta.getName());
		summary.addRow("Mode",         meta.isMultiFile() ? "Multi-file" : "Single-file");
		summary.addRow("Total size",   formatSize(meta.getTotalSize()));
		if (meta.isMultiFile()) {
			summary.addRow("File count", String.valueOf(meta.getFiles().size()));
		}
		summary.addRow("Piece length", formatSize(meta.getPieceLength()));
		summary.addRow("Pieces",       String.valueOf(meta.getPieceCount()));
		if (meta.isPrivateFlag()) {
			summary.addRow("Private", "Yes");
		}
		panel.add(summary);
		panel.add(vgap(6));

		// 2. Metadata (only if at least one field is present)
		boolean hasMeta = meta.getCreatedBy() != null
				|| meta.getCreationDate() != null
				|| meta.getComment() != null;
		if (hasMeta) {
			FormSection metaSection = new FormSection("Metadata");
			if (meta.getCreatedBy() != null) {
				metaSection.addRow("Created by", meta.getCreatedBy());
			}
			if (meta.getCreationDate() != null) {
				String ts = DATE_FMT.format(Instant.ofEpochSecond(meta.getCreationDate()));
				metaSection.addRow("Created", ts);
			}
			if (meta.getComment() != null) {
				metaSection.addRow("Comment", meta.getComment());
			}
			panel.add(metaSection);
			panel.add(vgap(6));
		}

		// 3. Info hash
		if (meta.getInfoHashHex() != null) {
			FormSection hashSection = new FormSection("Info Hash");
			hashSection.addCopyRow(meta.getInfoHashHex(), meta.getInfoHashHex(), "Copy");
			panel.add(hashSection);
			panel.add(vgap(6));
		}

		// 4. Magnet link
		if (meta.getMagnetLink() != null) {
			FormSection magnetSection = new FormSection("Magnet Link");
			magnetSection.addCopyRow(meta.getMagnetLink(), meta.getMagnetLink(), "Copy Magnet");
			panel.add(magnetSection);
			panel.add(vgap(6));
		}

		// 5. Trackers
		int trackerCount = countTrackers(meta);
		if (trackerCount > 0) {
			FormSection trackersSection = new FormSection("Trackers (" + trackerCount + ")");
			List<List<String>> tiers = meta.getTrackerTiers();
			if (!tiers.isEmpty()) {
				int tierNum = 1;
				for (List<String> tier : tiers) {
					trackersSection.addSubheader("Tier " + tierNum++);
					for (String tracker : tier) trackersSection.addMonoText(tracker);
				}
			} else if (meta.getAnnounce() != null) {
				trackersSection.addMonoText(meta.getAnnounce());
			}
			panel.add(trackersSection);
			panel.add(vgap(6));
		}

		// 6. Files
		List<TorrentFileEntry> files = meta.getFiles();
		String filesHeader = "Files (" + files.size() + "  \u2014  " + formatSize(meta.getTotalSize()) + ")";
		FormSection filesSection = new FormSection(filesHeader);
		boolean truncated = files.size() > 500;
		List<TorrentFileEntry> display = truncated ? files.subList(0, 500) : files;
		for (TorrentFileEntry f : display) {
			filesSection.addFileRow(f.getPath(), f.getLength());
		}
		if (truncated) {
			filesSection.addSubheader("\u2026 (list truncated to 500 entries)");
		}
		panel.add(filesSection);
		panel.add(Box.createVerticalGlue());

		return panel;
	}

	// ---- utilities --------------------------------------------------------

	private static int countTrackers(TorrentMeta meta) {
		Set<String> seen = new LinkedHashSet<>();
		if (meta.getAnnounce() != null) seen.add(meta.getAnnounce());
		for (List<String> tier : meta.getTrackerTiers()) seen.addAll(tier);
		return seen.size();
	}

	static String formatSize(long bytes) {
		if (bytes < 0) return "Unknown";
		if (bytes == 0) return "0 B";
		if (bytes < 1_024) return bytes + " B";
		double kb = bytes / 1_024.0;
		if (kb < 1_024) return String.format("%.1f KB", kb);
		double mb = kb / 1_024.0;
		if (mb < 1_024) return String.format("%.1f MB", mb);
		double gb = mb / 1_024.0;
		if (gb < 1_024) return String.format("%.2f GB", gb);
		return String.format("%.2f TB", gb / 1_024.0);
	}

	static String escapeHtml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void copyToClipboard(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(text), null);
	}

	private static Component vgap(int height) {
		return Box.createVerticalStrut(height);
	}

	// ---- FormSection inner class ------------------------------------------

	/**
	 * Titled section panel. Rows are added with an explicit counter so that
	 * mixed single- and dual-column entries line up correctly in GridBagLayout.
	 */
	private static class FormSection extends JPanel {

		private final JPanel grid;
		private int row = 0;

		FormSection(String title) {
			setLayout(new BorderLayout(0, 2));
			setAlignmentX(LEFT_ALIGNMENT);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
			setOpaque(false);

			JLabel header = new JLabel(title);
			header.setFont(header.getFont().deriveFont(Font.BOLD));
			header.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
			add(header, BorderLayout.NORTH);

			grid = new JPanel(new GridBagLayout());
			grid.setOpaque(false);

			Color borderColor = UIManager.getColor("Separator.foreground");
			if (borderColor == null) borderColor = Color.GRAY;
			Border left = BorderFactory.createMatteBorder(0, 2, 0, 0, borderColor);
			Border pad  = BorderFactory.createEmptyBorder(2, 8, 2, 2);
			grid.setBorder(BorderFactory.createCompoundBorder(left, pad));
			add(grid, BorderLayout.CENTER);
		}

		/** Two-column key : value row. */
		void addRow(String label, String value) {
			GridBagConstraints kc = new GridBagConstraints();
			kc.gridx = 0; kc.gridy = row;
			kc.anchor = GridBagConstraints.NORTHWEST;
			kc.fill = GridBagConstraints.NONE;
			kc.insets = new Insets(1, 0, 1, 10);

			GridBagConstraints vc = new GridBagConstraints();
			vc.gridx = 1; vc.gridy = row;
			vc.anchor = GridBagConstraints.NORTHWEST;
			vc.fill = GridBagConstraints.HORIZONTAL;
			vc.weightx = 1.0;
			vc.insets = new Insets(1, 0, 1, 0);

			row++;

			JLabel keyLabel = new JLabel(label + ":");
			keyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

			JLabel valLabel = new JLabel("<html>" + escapeHtml(value) + "</html>");

			grid.add(keyLabel, kc);
			grid.add(valLabel, vc);
		}

		/** Full-width monospaced text with a copy-to-clipboard button. */
		void addCopyRow(String display, String toCopy, String buttonText) {
			GridBagConstraints c = span(row++);
			c.insets = new Insets(2, 0, 2, 0);

			JPanel rowPanel = new JPanel(new BorderLayout(6, 0));
			rowPanel.setOpaque(false);

			JTextArea text = new JTextArea(display);
			text.setFont(MONO_FONT);
			text.setEditable(false);
			text.setLineWrap(true);
			text.setWrapStyleWord(false);
			text.setOpaque(false);
			text.setBorder(null);

			JButton btn = new JButton(buttonText);
			btn.setFocusPainted(false);
			btn.addActionListener(e -> copyToClipboard(toCopy));

			rowPanel.add(text, BorderLayout.CENTER);
			rowPanel.add(btn, BorderLayout.EAST);
			grid.add(rowPanel, c);
		}

		/** Bold sub-heading spanning both columns (e.g. "Tier 1"). */
		void addSubheader(String text) {
			GridBagConstraints c = span(row++);
			c.insets = new Insets(4, 0, 1, 0);
			JLabel label = new JLabel(text);
			label.setFont(label.getFont().deriveFont(Font.BOLD));
			grid.add(label, c);
		}

		/** Indented monospaced label spanning both columns. */
		void addMonoText(String text) {
			GridBagConstraints c = span(row++);
			c.insets = new Insets(1, 12, 1, 0);
			JLabel label = new JLabel(escapeHtml(text));
			label.setFont(MONO_FONT);
			grid.add(label, c);
		}

		/** Two-column file entry: path (expandable) and right-aligned size. */
		void addFileRow(String path, long size) {
			GridBagConstraints pathC = new GridBagConstraints();
			pathC.gridx = 0; pathC.gridy = row;
			pathC.anchor = GridBagConstraints.NORTHWEST;
			pathC.fill = GridBagConstraints.HORIZONTAL;
			pathC.weightx = 1.0;
			pathC.insets = new Insets(1, 0, 1, 6);

			GridBagConstraints sizeC = new GridBagConstraints();
			sizeC.gridx = 1; sizeC.gridy = row;
			sizeC.anchor = GridBagConstraints.NORTHEAST;
			sizeC.fill = GridBagConstraints.NONE;
			sizeC.insets = new Insets(1, 0, 1, 0);

			row++;

			JLabel pathLabel = new JLabel(escapeHtml(path));
			pathLabel.setFont(MONO_SMALL);

			JLabel sizeLabel = new JLabel(formatSize(size));
			sizeLabel.setFont(MONO_SMALL);
			sizeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

			grid.add(pathLabel, pathC);
			grid.add(sizeLabel, sizeC);
		}

		private GridBagConstraints span(int r) {
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0; c.gridy = r;
			c.gridwidth = 2;
			c.anchor = GridBagConstraints.NORTHWEST;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.weightx = 1.0;
			c.insets = new Insets(1, 0, 1, 0);
			return c;
		}
	}

}
