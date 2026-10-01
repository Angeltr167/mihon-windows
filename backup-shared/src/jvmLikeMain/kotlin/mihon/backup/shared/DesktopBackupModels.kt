@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package mihon.backup.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** The stable protobuf subset used to migrate Mihon backups without depending on Android models. */
@Serializable
data class MihonBackup(
    @ProtoNumber(1) val manga: List<BackupManga>,
    @ProtoNumber(2) val categories: List<BackupCategory> = emptyList(),
    @ProtoNumber(1000) val desktopPreferences: List<BackupDesktopPreference> = emptyList(),
)

@Serializable
data class BackupDesktopPreference(
    @ProtoNumber(1) val key: String,
    @ProtoNumber(2) val value: String,
)

@Serializable
data class BackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(16) val chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val chapterFlags: Int = 0,
    @ProtoNumber(103) val viewerFlags: Int? = null,
    @ProtoNumber(104) val history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) val updateStrategy: Int = 0,
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) val version: Long = 0,
    @ProtoNumber(110) val notes: String = "",
    @ProtoNumber(111) val initialized: Boolean = false,
    @ProtoNumber(112) val memo: ByteArray = "{}".encodeToByteArray(),
)

@Serializable
data class BackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0F,
    @ProtoNumber(10) val sourceOrder: Long = 0,
    @ProtoNumber(11) val lastModifiedAt: Long = 0,
    @ProtoNumber(12) val version: Long = 0,
    @ProtoNumber(13) val memo: ByteArray = "{}".encodeToByteArray(),
)

@Serializable
data class BackupTracking(
    @ProtoNumber(1) val syncId: Int,
    @ProtoNumber(2) val libraryId: Long = 0,
    @ProtoNumber(3) val mediaIdInt: Int = 0,
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastChapterRead: Float = 0F,
    @ProtoNumber(7) val totalChapters: Int = 0,
    @ProtoNumber(8) val score: Float = 0F,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(10) val startedReadingDate: Long = 0,
    @ProtoNumber(11) val finishedReadingDate: Long = 0,
    @ProtoNumber(12) val private: Boolean = false,
    @ProtoNumber(100) val mediaId: Long = 0,
)

@Serializable
data class BackupHistory(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val lastRead: Long,
    @ProtoNumber(3) val readDuration: Long = 0,
)

@Serializable
data class BackupCategory(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val order: Long = 0,
    @ProtoNumber(3) val id: Long = 0,
    @ProtoNumber(100) val flags: Long = 0,
)

data class BackupImportSummary(
    val manga: Int,
    val chapters: Int,
    val categories: Int,
    val trackerEntries: Int,
)
