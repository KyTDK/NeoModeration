package com.neomechanical.neomoderation.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValueParserTest {
    @Test
    void parsesValueUsingExistingType() {
        assertEquals(true, ConfigValueParser.parseLikeExisting(false, "true"));
        assertEquals(12, ConfigValueParser.parseLikeExisting(1, "12"));
        assertEquals(12L, ConfigValueParser.parseLikeExisting(1L, "12"));
        assertEquals(12.5D, ConfigValueParser.parseLikeExisting(1.0D, "12.5"));
        assertEquals("abc 123", ConfigValueParser.parseLikeExisting("", "abc 123"));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(NumberFormatException.class, () -> ConfigValueParser.parseLikeExisting(1, "not-a-number"));
    }

    @Test
    void onlyScalarsAreEditable() {
        assertTrue(ConfigValueParser.isEditableScalar("text"));
        assertTrue(ConfigValueParser.isEditableScalar(true));
        assertTrue(ConfigValueParser.isEditableScalar(1));
        assertFalse(ConfigValueParser.isEditableScalar(java.util.List.of("x")));
    }
}
