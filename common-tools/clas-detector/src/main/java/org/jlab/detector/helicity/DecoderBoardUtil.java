package org.jlab.detector.helicity;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.jnp.hipo4.data.Bank;

/**
 * Low-level static methods for delay-correcting and internal error-checking
 * the JLab helicity decoder board. 
 #
 * Note, the sequence bit words from the decoder board are time-ordered, with
 * the latest time being the lowest bit.
 *
 * @author baltzell
 */
public class DecoderBoardUtil {

    // Number of helicity windows (bits) provided by the decoder board:
    static final int SEQUENCE_LENGTH = 32;

    // Register size for the pseudorandom helicity generator:
    static final int RNG_REGISTER_SIZE = 30;

    // Whether to invert the bits during error-checking:
    static boolean INVERT_BITS_CHECK = false;

    /**
     * Window delay correction, only for quartet patterns.
     * @param b instance of HEL::decoder from the event of interest
     * @param windowDelay number of windows
     * @return delay-corrected helicity for the event of interest
     */
    public static byte getQuartetWindowHelicity(Bank b, int windowDelay) {
        return getWindowHelicity(b, windowDelay, (byte)4);
    }

    /**
     * @param patterns the previous SEQUENCE_LENGTH windows of the pattern signal
     * @return whether they're consistent with a good quartet sequence
     */
    public static boolean checkQuartetPatterns(int patterns) {
        return checkPatterns(patterns, (byte)4);
    }

    /**
     * @param patterns the previous SEQUENCE_LENGTH windows of the pattern signal
     * @param helicities the previous SEQUENCE_LENGTH windows of the helicity signal
     * @return whether the helicities are consistent with a good quartet sequence
     */
    public static boolean checkQuartetHelicities(int patterns, int helicities) {
        return checkHelicities(patterns, helicities, (byte)4);
    }

    /**
     * Run all available internal integrity checking on a HEL::decoder bank
     * @param b the HEL::decoder bank to check
     * @return whether everything looks good for a quartet configuration
     */
    public static boolean checkQuartetAll(Bank b) {
        return checkAll(b, (byte)4);
    }

    /**
     * Window delay correction, for any pattern type.
     * @param b instance of HEL::decoder from the event of interest
     * @param windowDelay number of delay windows
     * @param patternLength numner of windows in the pattern
     * @return delay-corrected helicity for the event of interest
     */
    public static byte getWindowHelicity(Bank b, int windowDelay, byte patternLength) {
        return getWindowHelicity(
            getPatternHelicity( b.getInt("helicityPArray",0), windowDelay/patternLength),
            windowDelay%patternLength );
    }

    /**
     * Get the expected helicity for one window in a pattern, based on the
     * first window in that pattern, for any pattern type.
     * @param firstWindow helicity of the first window in the pattern
     * @param windowIndex index of the window of interest
     * @return helicity for the given window
     */
    public static byte getWindowHelicity(int firstWindow, int windowIndex) {
        return (byte) ( ((windowIndex+1)/2)%2 ^ firstWindow );
    }

    /**
     * Pattern delay correction, for any pattern type.
     * @param helicities the first helicity of the previous SEQUENCE_LENGTH patterns 
     * @param patternDelay number of patterns
     * @return delay-corrected helicity of the first window in the pattern 
     */
    public static byte getPatternHelicity(int helicities, int patternDelay) {
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
     * @param pairs the previous SEQUENCE_LENGTH windows of the pair signal
     * @return whether they're consistent with a good sequence
     */
    public static boolean checkPairs(int pairs) {
        for (int i=0; i<SEQUENCE_LENGTH-1; ++i) {
            if ( Integer.bitCount((pairs>>i) & 0x3) != 1) {
                Logger.getLogger(DecoderBoardUtil.class.getName()).log(Level.WARNING,
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
                return false;
            }
        }
        return true;
    }
   
    /**
     * @param patterns the previous SEQUENCE_LENGTH windows of the pattern signal
     * @param helicities the previous SEQUENCE_LENGTH windows of the helicity signal
     * @param patternLength number of windows per pattern
     * @return whether the helicities are consistent with a good pattern sequence
     */
    public static boolean checkHelicities(int patterns, int helicities, byte patternLength) {
        for (int i=SEQUENCE_LENGTH-1; i>0; --i) {
            if ( ((patterns>>i)&1) == (INVERT_BITS_CHECK?0:1) ) {
                for (int j=1; j<patternLength && i-j>=0; ++j) {
                    if ( ((helicities>>(i-j))&1) != getWindowHelicity((helicities>>i)&1,j) ) {
                        Logger.getLogger(DecoderBoardUtil.class.getName()).log(Level.WARNING,
                            "Bad pattern/helicity: {0}/{1}", new Object[]{patterns, helicities});
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Run all available internal integrity checking on a HEL::decoder bank
     * @param b the HEL::decoder bank to check
     * @param patternLength number of windows per pattern
     * @return whether everything looks good 
     */
    public static boolean checkAll(Bank b, byte patternLength) {
        boolean x = checkPairs(b.getInt("pairArray",0));
        boolean y = checkPatterns(b.getInt("patternArray",0), patternLength);
        boolean z = checkHelicities(b.getInt("patternArray",0), b.getInt("helicityArray",0), patternLength);
        return x && y && z;
    }

}
