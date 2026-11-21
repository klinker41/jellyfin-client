package com.makd.afinity.util.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSignatureTest {

    @Test
    fun ofExtractsUrlsUuidsHexStringsQuotesAndNumbers() {
        val sig =
            LogSignatures.of(
                """Request https://jf.local/a id=550e8400-e29b-41d4-a716-446655440000 hex=deadbeef title="Dune" in 42.5 ms""",
            )
        assertEquals(
            listOf(
                "https://jf.local/a",
                "550e8400-e29b-41d4-a716-446655440000",
                "deadbeef",
                "\"Dune\"",
                "42.5",
            ),
            sig.values,
        )
    }

    @Test
    fun labelAndPlainLabelCollapseVaryingValuesAndPreserveConstants() {
        assertTrue(LogSignatures.label(emptyList()).isEmpty())
        assertEquals("", LogSignatures.plainLabel(emptyList()))

        val sig1 = LogSignatures.of("Loaded 12 items for library \"Movies\" from https://a.local")
        val sig2 = LogSignatures.of("Loaded 24 items for library \"Shows\" from https://a.local")

        val parts = LogSignatures.label(listOf(sig1, sig2))
        assertEquals(
            listOf(
                LabelPart.Literal("Loaded "),
                LabelPart.Variable,
                LabelPart.Literal(" items for library "),
                LabelPart.Variable,
                LabelPart.Literal(" from https://a.local"),
            ),
            parts,
        )
        assertEquals(
            "Loaded n items for library n from https://a.local",
            LogSignatures.plainLabel(listOf(sig1, sig2)),
        )
    }
}
