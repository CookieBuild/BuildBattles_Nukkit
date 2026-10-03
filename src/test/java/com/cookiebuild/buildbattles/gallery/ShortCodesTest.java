package com.cookiebuild.buildbattles.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ShortCodesTest {
    @Test
    void generatesEightCharacterBase62Codes() {
        assertEquals(62, ShortCodes.ALPHABET.length());
        Set<String> codes = new HashSet<>();
        Set<Character> seen = new HashSet<>();
        for (int index = 0; index < 5_000; index++) {
            String code = ShortCodes.next();
            assertEquals(8, code.length());
            assertTrue(ShortCodes.isValid(code), code);
            assertTrue(code.matches("[0-9A-Za-z]{8}"), code);
            codes.add(code);
            code.chars().forEach(character -> seen.add((char) character));
        }
        assertEquals(5_000, codes.size(), "8 base62 chars make collisions vanishingly rare");
        assertEquals(62, seen.size(), "every symbol is reachable");
    }

    @Test
    void isDeterministicForASeedAndValidatesInput() {
        assertEquals(ShortCodes.next(new Random(7)), ShortCodes.next(new Random(7)));
        assertFalse(ShortCodes.isValid(null));
        assertFalse(ShortCodes.isValid("abc"));
        assertFalse(ShortCodes.isValid("abcd-efg"));
        assertFalse(ShortCodes.isValid("abcdefghi"));
        assertTrue(ShortCodes.isValid("Ab3dE9gZ"));
    }
}
