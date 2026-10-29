package com.example.chattranslator;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cheap local check so dialect modes can skip the web when a message is already English. */
public final class Lang {
    private Lang() {}

    private static final Pattern WORD = Pattern.compile("[\\p{L}']+");

    private static final Set<String> COMMON = Set.of(
        "the", "be", "to", "of", "and", "in", "that", "have", "i", "it", "for", "not", "on", "with", "he", "as",
        "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", "she", "or", "an", "will",
        "my", "one", "all", "would", "there", "their", "what", "up", "out", "if", "about", "who", "get", "which",
        "go", "when", "make", "can", "like", "time", "just", "him", "know", "take", "people", "into",
        "year", "your", "good", "some", "could", "them", "see", "other", "than", "then", "now", "look", "only", "come",
        "its", "over", "think", "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us", "is", "are", "was", "were",
        "im", "i'm", "dont", "don't", "cant", "can't", "yes", "yeah", "hello", "hi", "hey", "lol", "ok", "okay", "thanks",
        "thx", "pls", "please", "gg", "wtf", "omg", "bro", "dude", "yo", "nice", "here", "where", "why", "where's",
        "diamond", "diamonds", "creeper", "nether", "spawn", "server", "player", "help", "friend", "money"
    );

    public static boolean isProbablyEnglish(String text) {
        int words = 0, common = 0, letters = 0;
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            String w = m.group();
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                    if (c > 127) return false; // accented or non-latin letters: let the translator decide
                }
            }
            words++;
            if (COMMON.contains(w.toLowerCase(Locale.ROOT))) common++;
        }
        if (letters < 4 || words == 0) return true; // too short to bother
        return common >= 2 && common * 1.0 / words >= 0.3 || (words <= 2 && common >= 1);
    }
}
