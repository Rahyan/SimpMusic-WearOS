package com.maxrave.common

object ForkReleases {
    const val PAGE_URL = "https://github.com/Rahyan/SimpMusic-WearOS/releases"
    const val API_URL = "https://api.github.com/repos/Rahyan/SimpMusic-WearOS/releases"
}

/** SemVer ordering, including numeric prerelease identifiers. Build metadata does not affect order. */
data class ReleaseVersion private constructor(
    private val core: List<Long>,
    private val identifiers: List<String>,
) : Comparable<ReleaseVersion> {
    val isPrerelease: Boolean get() = identifiers.isNotEmpty()

    override fun compareTo(other: ReleaseVersion): Int {
        core.zip(other.core).forEach { (left, right) ->
            left.compareTo(right).takeIf { it != 0 }?.let { return it }
        }
        if (identifiers.isEmpty() != other.identifiers.isEmpty()) return if (identifiers.isEmpty()) 1 else -1
        identifiers.zip(other.identifiers).forEach { (left, right) ->
            val numericLeft = left.all(Char::isDigit)
            val numericRight = right.all(Char::isDigit)
            val order = when {
                numericLeft && numericRight -> left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)
                numericLeft != numericRight -> if (numericLeft) -1 else 1
                else -> left.compareTo(right)
            }
            if (order != 0) return order
        }
        return identifiers.size.compareTo(other.identifiers.size)
    }

    companion object {
        private val pattern = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value) ?: return null
            val core = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
            val identifiers = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (identifiers.any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
            return ReleaseVersion(core, identifiers)
        }
    }
}
