package mihon.core.extension.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionPolicyTest {
    @Test
    fun `replacement checks preserve version and signing rules`() {
        assertEquals(ReplacementIssue.DOWNGRADE, ExtensionPolicy.replacementIssue(2, setOf("a"), 1, setOf("a")))
        assertEquals(ReplacementIssue.UNSIGNED, ExtensionPolicy.replacementIssue(2, setOf("a"), 3, emptySet()))
        assertEquals(ReplacementIssue.SIGNATURE_CHANGED, ExtensionPolicy.replacementIssue(2, setOf("a"), 3, setOf("b")))
        assertNull(ExtensionPolicy.replacementIssue(2, setOf("a"), 2, setOf("a")))
    }

    @Test
    fun `library and content policy are platform independent`() {
        assertTrue(ExtensionPolicy.supportsLibVersion(1.6))
        assertFalse(ExtensionPolicy.supportsLibVersion(9.0))
        assertTrue(ExtensionPolicy.allowsContentWarning("SAFE", setOf("SAFE")))
        assertFalse(ExtensionPolicy.allowsContentWarning("NSFW", setOf("SAFE")))
    }
}
