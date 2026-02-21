package dev.nuclr.plugin.core.quick.viewer.torrent;

import java.util.List;

import lombok.Data;

@Data
public class TorrentMeta {

	/** Display name of the torrent (from info.name). */
	private String name;

	/** True for multi-file torrents (info.files present). */
	private boolean multiFile;

	/** Sum of all file lengths in bytes. */
	private long totalSize;

	/** File entries; single-file torrents have exactly one entry. */
	private List<TorrentFileEntry> files;

	/** Primary tracker URL from the announce field (may be null). */
	private String announce;

	/** Tracker tiers from announce-list (may be empty, never null). */
	private List<List<String>> trackerTiers;

	/** Creator application string (created by). */
	private String createdBy;

	/** Unix epoch creation timestamp (may be null). */
	private Long creationDate;

	/** Torrent comment (may be null). */
	private String comment;

	/** Whether the torrent is marked private (info.private == 1). */
	private boolean privateFlag;

	/** Piece length in bytes. */
	private long pieceLength;

	/** Number of pieces (pieces byte-string length / 20). */
	private int pieceCount;

	/** SHA-1 info hash as 40 lowercase hex characters (may be null on error). */
	private String infoHashHex;

	/** Full magnet link (may be null if info hash unavailable). */
	private String magnetLink;

}
