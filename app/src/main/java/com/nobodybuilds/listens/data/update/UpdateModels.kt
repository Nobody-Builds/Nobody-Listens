package com.nobodybuilds.listens.data.update

/** Latest release as reported by GitHub. */
data class ReleaseInfo(
    val version: String,
    val url: String,
    val notesFirstLine: String,
)

/** What the last check found. [latestVersion] is null until a check succeeded once. */
data class UpdateState(
    val latestVersion: String? = null,
    val url: String? = null,
    val notesFirstLine: String? = null,
    val checkedAt: Long = 0L,
    val skippedVersion: String? = null,
    val dismissedVersion: String? = null,
    val installedVersion: String = "",
) {
    /** True when the latest known release is newer than the installed build. */
    val isUpdateAvailable: Boolean
        get() = latestVersion != null && VersionComparator.isNewer(latestVersion, installedVersion)

    /** Update worth surfacing (not skipped by the user). */
    val availableVersion: String?
        get() = latestVersion?.takeIf { isUpdateAvailable && !it.equals(skippedVersion, ignoreCase = true) }

    /** Home banner: available and not dismissed for this version. */
    val showBanner: Boolean
        get() = availableVersion != null && !availableVersion.equals(dismissedVersion, ignoreCase = true)
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val latestVersion: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * Semantic version compare: "v1.2.10" > "1.2.9"; pre-release suffixes sort before the release.
 * A CI "-build.N" suffix (non-release builds, e.g. "1.2.0-build.57") is build metadata and
 * compares equal to its base version.
 */
object VersionComparator {
    fun normalize(version: String): String = version.trim().removePrefix("v").removePrefix("V")

    fun compare(a: String, b: String): Int {
        val (coreA, preA) = split(normalize(a))
        val (coreB, preB) = split(normalize(b))
        val max = maxOf(coreA.size, coreB.size)
        for (i in 0 until max) {
            val x = coreA.getOrElse(i) { 0 }
            val y = coreB.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return when {
            preA == null && preB == null -> 0
            preA == null -> 1
            preB == null -> -1
            else -> comparePreRelease(preA, preB)
        }
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private val CI_BUILD_SUFFIX = Regex("-build\\.\\d+$", RegexOption.IGNORE_CASE)

    private fun split(v: String): Pair<List<Int>, String?> {
        val withoutBuild = v.substringBefore('+').replace(CI_BUILD_SUFFIX, "")
        val core = withoutBuild.substringBefore('-')
        val pre = withoutBuild.substringAfter('-', missingDelimiterValue = "").ifEmpty { null }
        val parts = core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        return parts to pre
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val pa = a.split('.')
        val pb = b.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: return -1
            val y = pb.getOrNull(i) ?: return 1
            val xi = x.toIntOrNull()
            val yi = y.toIntOrNull()
            val c = when {
                xi != null && yi != null -> xi.compareTo(yi)
                xi != null -> -1
                yi != null -> 1
                else -> x.compareTo(y)
            }
            if (c != 0) return c
        }
        return 0
    }
}
