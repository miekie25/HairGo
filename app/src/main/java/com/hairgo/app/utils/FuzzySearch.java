package com.hairgo.app.utils;

import java.util.Locale;

/**
 * Helps the search bar work even when the user makes a typo.
 *
 * It works out how similar two words are by counting how many letters
 * you would need to change to turn one word into the other.
 * For example "gloe" only needs one letter added to become "glow",
 * so they are very similar and the search still finds it.
 *
 * Every comparison gives a score from 0.0 (nothing alike) to
 * 1.0 (exactly the same). Words that score at least
 * {@link #FUZZY_THRESHOLD} count as a match.
 */
public class FuzzySearch {

    /**
     * How similar a word needs to be before it counts as a match.
     * 0.6 out of 1.0 means it catches normal typos (like a missing
     * or wrong letter) but won't match completely different words.
     */
    public static final double FUZZY_THRESHOLD = 0.6;

    private FuzzySearch() {} // this class only has static methods, so nobody should create it

    /**
     * Scores how well what the user typed matches one salon field.
     *
     * @return a score from 0.0 (no match) to 1.0 (perfect match).
     */
    public static double score(String query, String target) {
        if (query == null || target == null) return 0.0;

        String q = query.trim().toLowerCase(Locale.getDefault());
        String t = target.trim().toLowerCase(Locale.getDefault());

        if (q.isEmpty() || t.isEmpty()) return 0.0;

        // If the text simply contains what was typed, it's a perfect match.
        // For example searching "sandton" inside "Sandton, Johannesburg".
        if (t.contains(q)) return 1.0;

        // Otherwise compare each typed word against each word in the salon
        // field and keep the closest one. If even one typed word has nothing
        // similar in the field, it's not a match at all.
        String[] queryWords = q.split("\\s+");
        String[] targetWords = t.split("\\s+");

        double total = 0.0;
        for (String qw : queryWords) {
            double bestWordScore = 0.0;
            for (String tw : targetWords) {
                double s = similarity(qw, tw);
                if (s > bestWordScore) bestWordScore = s;
            }
            // If this typed word doesn't look like any word in the field,
            // the whole search is a miss.
            if (bestWordScore < FUZZY_THRESHOLD) return 0.0;
            total += bestWordScore;
        }

        // Return the average of how well each word matched.
        return total / queryWords.length;
    }

    /**
     * Checks the salon's name and location and returns whichever
     * field is the better match.
     */
    public static double bestScore(String query, String name, String location) {
        double nameScore = score(query, name);
        double locationScore = score(query, location);
        return Math.max(nameScore, locationScore);
    }

    /**
     * Works out how similar two words are.
     *
     * @return 0.0 if they are completely different, 1.0 if they are identical.
     */
    static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int distance = levenshteinDistance(a, b);
        int longest = Math.max(a.length(), b.length());
        return 1.0 - ((double) distance / longest);
    }

    /**
     * Counts the smallest number of letter changes needed to turn
     * one word into another. A change means adding, removing,
     * or swapping a single letter.
     *
     * The method keeps two small rows of numbers instead of one big
     * grid, which saves memory. The shorter word is always used for
     * the row size to keep those numbers as small as possible.
     */
    private static int levenshteinDistance(String a, String b) {
        // Use 'a' as the shorter word so the number rows stay small.
        if (a.length() > b.length()) {
            String tmp = a;
            a = b;
            b = tmp;
        }

        int[] prev = new int[a.length() + 1];
        int[] curr = new int[a.length() + 1];

        // The first row counts up because turning "" into "abc"
        // takes one change per letter.
        for (int i = 0; i <= a.length(); i++) prev[i] = i;

        for (int j = 1; j <= b.length(); j++) {
            curr[0] = j;
            for (int i = 1; i <= a.length(); i++) {
                // It costs nothing if the letters match, otherwise one change.
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                // Take the cheapest of three options: add a letter,
                // remove a letter, or change a letter.
                curr[i] = Math.min(
                        Math.min(curr[i - 1] + 1,      // add a letter
                                 prev[i] + 1),         // remove a letter
                        prev[i - 1] + cost);           // change a letter
            }
            // Swap the rows so the current one becomes the previous
            // for the next round, instead of making a whole new grid.
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }

        return prev[a.length()];
    }
}
