package mihon.core.extension.policy

/** Decisions shared by APK and JVM package adapters; package parsing and loading remain platform-specific. */
object ExtensionPolicy {
    val supportedLibVersions = setOf(1.4, 1.6)

    fun supportsLibVersion(version: Double?): Boolean = version in supportedLibVersions

    fun allowsContentWarning(warning: String, enabledWarnings: Set<String>): Boolean =
        warning in enabledWarnings

    fun replacementIssue(
        installedVersionCode: Long,
        installedFingerprints: Set<String>,
        candidateVersionCode: Long,
        candidateFingerprints: Set<String>,
    ): ReplacementIssue? = when {
        candidateVersionCode < installedVersionCode -> ReplacementIssue.DOWNGRADE
        candidateFingerprints.isEmpty() -> ReplacementIssue.UNSIGNED
        !candidateFingerprints.containsAll(installedFingerprints) -> ReplacementIssue.SIGNATURE_CHANGED
        else -> null
    }
}

enum class ReplacementIssue { DOWNGRADE, UNSIGNED, SIGNATURE_CHANGED }
