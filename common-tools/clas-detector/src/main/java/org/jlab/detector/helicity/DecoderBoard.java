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
public class DecoderBoard {

    // Whether to invert the bits during error-checking (just for debugging):
    static boolean INVERTED_CHECK = false;

    /**
     * Window delay correction, only for quartet patterns.
     * @param b instance of HEL::decoder from the event of interest
     * @param windowDelay number of windows
     * @return delay-corrected helicity for the event of interest
     */
    public static byte getQuartetWindowHelicity(Bank b, int windowDelay) {
        return getWindowHelicity(b, windowDelay, 4);
    }

    /**
     * Window delay correction, for any pattern type.
     * @param b instance of HEL::decoder from the event of interest
     * @param windowDelay number of delay windows
     * @param patternLength numner of windows in the pattern
     * @return delay-corrected helicity for the event of interest
     */
    public static byte getWindowHelicity(Bank b, int windowDelay, int patternLength) {
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
     * @param helicities the first helicity of the previous 30 patterns 
     * @param patternDelay number of patterns
     * @return delay-corrected helicity of the first window in the pattern 
     */
    public static byte getPatternHelicity(int helicities, int patternDelay) {
        int bit = 0;
        int register = 0;
        for (int i=HelicityGenerator.REGISTER_SIZE-1; i>=0; --i)
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
     * @param pairs the previous 30 windows of the pair signal
     * @return whether they're consistent with a good sequence
     */
    public static boolean checkPairs(int pairs) {
        for (int i=0; i<29; ++i) {
            if ( Integer.bitCount((pairs>>i) & 0x3) != 1) {
                Logger.getLogger(DecoderBoard.class.getName()).log(Level.WARNING,
                    "Bad pairs: {0}", pairs);
                return false;
            }
        }
        return true;
    }

    /**
     * @param patterns the previous 30 windows of the pattern signal
     * @return whether they're consistent with a good quartet sequence
     */
    public static boolean checkQuartets(int patterns) {
        for (int i=0; i<27; ++i) {
            if ( Integer.bitCount((patterns>>i) & 0xF) != (INVERTED_CHECK?3:1)) {
                Logger.getLogger(DecoderBoard.class.getName()).log(Level.WARNING,
                    "Bad patterns: {0}", patterns);
                return false;
            }
        }
        return true;
    }

    /**
     * @param patterns the previous 30 windows of the pattern signal
     * @param helicities the previous 30 windows of the helicity signal
     * @return whether the helicities are consistent with a good quartet sequence
     */
    public static boolean checkQuartetHelicities(int patterns, int helicities) {
        for (int i=29; i>0; --i) {
            if ( ((patterns>>i)&1) == (INVERTED_CHECK?0:1) ) {
                for (int j=1; j<4 && i-j>=0; ++j) {
                    if ( ((helicities>>(i-j))&1) != getWindowHelicity((helicities>>i)&1,j) ) {
                        Logger.getLogger(DecoderBoard.class.getName()).log(Level.WARNING,
                            "Bad quartet/helicity: {0}/{1}", new Object[]{patterns, helicities});
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
     * @return whether everything looks good 
     */
    public static boolean check(Bank b) {
        boolean x = checkPairs(b.getInt("pairArray",0));
        boolean y = checkQuartets(b.getInt("patternArray",0));
        boolean z = checkQuartetHelicities(b.getInt("patternArray",0),b.getInt("helicityArray",0));
        return x && y && z;
    }

}
