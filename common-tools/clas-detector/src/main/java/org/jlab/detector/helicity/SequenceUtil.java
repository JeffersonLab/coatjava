package org.jlab.detector.helicity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Low-level static methods for delay-correcting and integrity-checking
 * the sequences of the helicity signals, based on basic pattern properties
 * and the pseudorandom generator.
 * 
 * Note, these methods require time-ordered bit sequence integers as inputs,
 * 
 * @author baltzell
 */
public class SequenceUtil {

    // Register size for the pseudorandom helicity generator:
    static final int RNG_REGISTER_SIZE = 30;

    // Number of bits of input helicity windows (32 for the decoder board):
    static int SEQUENCE_LENGTH = 32;

    // Whether to invert the bits during error-checking, for debugging:
    static boolean INVERT_BITS_CHECK = false;

    /**
     * Pattern delay correction.
     * @param helicities the first helicity of the previous SEQUENCE_LENGTH patterns 
     * @param patternDelay number of patterns
     * @return delay-corrected helicity of the first window in the pattern 
     */
    public static byte getPatternHelicity(int helicities, byte patternDelay) {
        int bit = 0;
        int register = 0;
        for (int i=RNG_REGISTER_SIZE-1; i>=0; --i)
            register = ( ((helicities>>i)&1) | (register<<1) ) & 0x3FFFFFFF;
        for (int i=0; i<patternDelay; ++i) {
            int bit7  = (register>>6)  & 1;
            int bit28 = (register>>27) & 1;
            int bit29 = (register>>28) & 1;
            int bit30 = (register>>29) & 1;
            bit = (bit30^bit29^bit28^bit7) & 1;
            register = ( bit | (register<<1) ) & 0x3FFFFFFF;
        }
        return (byte)bit;
    }

    /**
     * Get the expected helicity for one window in a pattern, based on the
     * first window in that pattern.
     * @param firstWindow helicity of the first window in the pattern
     * @param windowIndex index of the window of interest
     * @return helicity for the given window
     */
    public static byte getWindowHelicity(byte firstWindow, byte windowIndex) {
        return (byte) ( ((windowIndex+1)/2)%2 ^ firstWindow );
    }

    /**
     * @param patterns the previous SEQUENCE_LENGTH windows of the pattern signal
     * @param helicities the previous SEQUENCE_LENGTH windows of the helicity signal
     * @param patternLength number of windows per pattern
     * @return whether the helicities are consistent with a good pattern sequence
     */
    public static boolean checkHelicities(int patterns, int helicities, byte patternLength) {
        for (int i=SequenceUtil.SEQUENCE_LENGTH-1; i>0; --i) {
            if ( ((patterns>>i)&1) == (SequenceUtil.INVERT_BITS_CHECK?0:1) ) {
                for (int j=1; j<patternLength && i-j>=0; ++j) {
                    if ( ((helicities>>(i-j))&1) != 
                            SequenceUtil.getWindowHelicity((byte)((helicities>>i)&1),(byte)j) ) {
                        Logger.getLogger(SequenceUtil.class.getName()).log(Level.WARNING,
                            "Bad pattern/helicity: {0}/{1}", new Object[]{patterns, helicities});
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * @param pairs the previous SEQUENCE_LENGTH windows of the pair signal
     * @return whether they're consistent with a good sequence
     */
    public static boolean checkPairs(int pairs) {
        for (int i=0; i<SEQUENCE_LENGTH-1; ++i) {
            if ( Integer.bitCount((pairs>>i) & 0x3) != 1) {
                Logger.getLogger(SequenceUtil.class.getName()).log(Level.WARNING,
                    "Bad pairs: {0}", pairs);
                return false;
            }
        }
        return true;
    }

    /**
     * @param patterns the previous SEQUENCE_LENGTH windows of the pattern signal
     * @param patternLength number of windows per pattern
     * @return whether they're consistent with a good pattern sequence
     */
    public static boolean checkPatterns(int patterns, byte patternLength) {
        final int mask = (1<<patternLength)-1;
        for (int i=0; i<(SEQUENCE_LENGTH-patternLength+1); ++i) {
            if (Integer.bitCount((patterns>>i) & mask) != (INVERT_BITS_CHECK?patternLength-1:1)) {
                Logger.getLogger(SequenceUtil.class.getName()).log(Level.WARNING,
                    "Bad patterns: {0}", patterns);
                return false;
            }
        }
        return true;
    }

    /**
     * @param pairs the previous SEQUENCE_LENGTH windows of the pair signal
     * @param patterns the previous SEQUENCE_LENGTH windows of the pattern signal
     * @param helicities the previous SEQUENCE_LENGTH windows of the helicity signal
     * @param patternLength number of windows per pattern
     * @return whether everything looks good 
     */
    public static boolean check(int pairs, int patterns, int helicities, byte patternLength) {
        boolean x = checkPairs(pairs);
        boolean y = checkPatterns(patterns, patternLength);
        boolean z = checkHelicities(patterns, helicities, patternLength);
        return x && y && z;
    }
}
