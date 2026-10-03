package com.cookiebuild.buildbattles.gallery;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** 8-character base62 share codes for {@code buildbattle_builds.short_code}. */
public final class ShortCodes {
    public static final int LENGTH = 8;
    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShortCodes() {
    }

    public static String next() {
        return next(RANDOM);
    }

    public static String next(RandomGenerator random) {
        char[] code = new char[LENGTH];
        for (int index = 0; index < LENGTH; index++) {
            code[index] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }

    public static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH) return false;
        for (int index = 0; index < code.length(); index++) {
            if (ALPHABET.indexOf(code.charAt(index)) < 0) return false;
        }
        return true;
    }
}
