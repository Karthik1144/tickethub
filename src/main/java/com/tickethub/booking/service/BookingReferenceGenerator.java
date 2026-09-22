package com.tickethub.booking.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 12-character, non-guessable, human-readable booking references (no I, O, 0, 1). */
@Component
public class BookingReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PREFIX = "TKT";
    private static final int RANDOM_LENGTH = 9;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
