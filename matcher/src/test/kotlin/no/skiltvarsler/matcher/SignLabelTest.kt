package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignLabelTest {
    @Test
    fun signNumberBecomesReadableName() {
        assertThat(SignLabel.displayName("146.1", "Fareskilt")).isEqualTo("Elg")
        assertThat(SignLabel.displayName("100.1", "Fareskilt")).isEqualTo("Farlig sving til høyre")
        assertThat(SignLabel.displayName("156", "Fareskilt")).isEqualTo("Annen fare")
    }

    @Test
    fun numberPrefixIsDroppedWhenNameFollows() {
        assertThat(SignLabel.displayName("146.1 Elg", "Fareskilt")).isEqualTo("Elg")
        assertThat(SignLabel.displayName("110 Vegarbeid", "Fareskilt")).isEqualTo("Vegarbeid")
    }

    @Test
    fun unknownNumberFallsBackInsteadOfShowingCode() {
        assertThat(SignLabel.displayName("999.9", "Fareskilt")).isEqualTo("Fareskilt")
        assertThat(SignLabel.displayName("", "Fareskilt")).isEqualTo("Fareskilt")
    }

    @Test
    fun plainTextPayloadIsKept() {
        assertThat(SignLabel.displayName("Elg", "Viltfare")).isEqualTo("Elg")
    }
}
