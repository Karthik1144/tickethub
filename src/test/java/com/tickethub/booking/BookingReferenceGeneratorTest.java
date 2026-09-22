package com.tickethub.booking;

import com.tickethub.booking.service.BookingReferenceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReferenceGeneratorTest {

    private final BookingReferenceGenerator generator = new BookingReferenceGenerator();

    @Test
    @DisplayName("references are 12 characters and fit the CHAR(12) column")
    void referenceLengthMatchesSchema() {
        assertThat(generator.generate()).hasSize(12).startsWith("TKT");
    }

    @Test
    @DisplayName("references do not collide across many generations")
    void referencesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(20_000);
    }

    @Test
    @DisplayName("ambiguous characters are never used")
    void noAmbiguousCharacters() {
        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate().substring(3)).doesNotContain("0", "1", "I", "O");
        }
    }
}
