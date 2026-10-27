package com.nobodybuilds.listens.data.repository

import android.content.Context
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.FolderSource
import com.nobodybuilds.listens.data.model.Genre
import com.nobodybuilds.listens.data.model.MusicFolder
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.model.StorageFilter
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.ui.theme.GenreThemeUtils
import com.nobodybuilds.listens.utils.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Genre and folder browsing for [MusicRepositoryImpl]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LibraryGenresAndFolders(
    private val context: Context,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val folderTreeBuilder: FolderTreeBuilder,
    private val allowedDirsResolver: AllowedDirsResolver,
) {

    private companion object {
        private const val UNKNOWN_GENRE_NAME = "Unknown"
        private const val UNKNOWN_GENRE_ID = "unknown"
    }

    private suspend fun computeAllowedDirs(allowedDirs: Set<String>, blockedDirs: Set<String>) =
        allowedDirsResolver(allowedDirs, blockedDirs)

    private fun StorageFilter.toFilterMode(): Int =
        com.nobodybuilds.listens.data.model.LibrarySourceFilter.toFilterMode(this)

    fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.mockGenresEnabledFlow,
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { mockEnabled, allowedDirs, blockedDirs ->
            Triple(mockEnabled, allowedDirs, blockedDirs)
        }.flatMapLatest { (mockEnabled, allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                val genreName = if (mockEnabled) "Mock" else genreId
                emit(
                    if (genreName.equals("unknown", ignoreCase = true)) {
                        musicDao.getSongsWithNullGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    } else {
                        musicDao.getSongsByGenre(
                            genreName = genreName,
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    }
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    fun getGenres(): Flow<List<Genre>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    combine(
                        musicDao.getUniqueGenres(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        ),
                        musicDao.hasUnknownGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    ) { genreNames, hasUnknown ->
                        val knownGenres = genreNames
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { buildGenre(it) }
                            .distinctBy { it.id }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                        val unknownAlreadyPresent = knownGenres.any { it.id == UNKNOWN_GENRE_ID }
                        if (hasUnknown && !unknownAlreadyPresent) {
                            knownGenres + buildGenre(UNKNOWN_GENRE_NAME)
                        } else {
                            knownGenres
                        }
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private fun buildGenre(genreName: String): Genre {
        val id = if (genreName.equals(UNKNOWN_GENRE_NAME, ignoreCase = true)) {
            UNKNOWN_GENRE_ID
        } else {
            genreName
                .lowercase()
                .replace(" ", "_")
                .replace("/", "_")
        }
        val lightThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = false)
        val darkThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = true)
        return Genre(
            id = id,
            name = genreName,
            lightColorHex = lightThemeColor.container.toHexString(),
            onLightColorHex = lightThemeColor.onContainer.toHexString(),
            darkColorHex = darkThemeColor.container.toHexString(),
            onDarkColorHex = darkThemeColor.onContainer.toHexString()
        )
    }

    fun getMusicFolders(storageFilter: StorageFilter): Flow<List<MusicFolder>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow,
            userPreferencesRepository.foldersSourceFlow
        ) { allowedDirs, blockedDirs, isFolderFilterActive, folderSource ->
            FolderFlowConfig(
                allowedDirs = allowedDirs,
                blockedDirs = blockedDirs,
                isFolderFilterActive = isFolderFilterActive,
                folderSource = folderSource
            )
        }.flatMapLatest { config ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) = computeAllowedDirs(
                    allowedDirs = config.allowedDirs,
                    blockedDirs = config.blockedDirs
                )
                emit(
                    musicDao.getFolderSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        filterMode = storageFilter.toFilterMode()
                    ).map { folderSongs ->
                        folderTreeBuilder.buildFolderTree(
                            folderSongs = folderSongs,
                            allowedDirs = config.allowedDirs,
                            blockedDirs = config.blockedDirs,
                            isFolderFilterActive = config.isFolderFilterActive,
                            folderSource = config.folderSource,
                            context = context
                        )
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private data class FolderFlowConfig(
        val allowedDirs: Set<String>,
        val blockedDirs: Set<String>,
        val isFolderFilterActive: Boolean,
        val folderSource: FolderSource
    )
}
