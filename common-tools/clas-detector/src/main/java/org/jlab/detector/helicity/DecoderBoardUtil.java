package org.jlab.detector.helicity;

import org.jlab.io.base.DataBank;
import org.jlab.jnp.hipo4.data.Bank;

/**
 * Delay correction and integrity checking for the JLab helicity decoder board's
 * HEL::decoder HIPO bank.
 * 
 * NOTE:  To avoid repetition, generic objects are used to support different
 * types of HIPO banks.
 * 
 * @author baltzell
 */
public class DecoderBoardUtil extends SequenceUtil {

    public static final DecoderBoardUtil PAIR = new DecoderBoardUtil((byte)2); 
    public static final DecoderBoardUtil QUARTET = new DecoderBoardUtil((byte)4); 
    public static final DecoderBoardUtil OCTET = new DecoderBoardUtil((byte)8); 

    private final byte patternLength;

    private DecoderBoardUtil(byte patternLength) {
        this.patternLength = patternLength; 
    }

    /**
     * Window delay correction, for any pattern type.
     * @param bank instance of HEL::decoder from the event of interest
     * @param delayWindows number of delay windows
     * @return delay-corrected helicity for the event of interest
     */
    public int getWindowHelicity(Object bank, byte delayWindows) {
        final byte phase = (byte)getInt(bank,"phase");
        final byte patternDelay = (byte)(delayWindows/this.patternLength);
        final byte windowIndex = (byte)((delayWindows+phase)%this.patternLength);
        final byte patternHelicity = getPatternHelicity(getInt(bank,"helicityPArray"),patternDelay);
        return getWindowHelicity(patternHelicity, windowIndex);
    }

    /**
     * @param bank the HEL::decoder bank to check
     * @return whether its pattern sequence looks good
     */
    public boolean checkPatterns(Object bank) {
        return checkPatterns(
                getInt(bank,"patternArray"), 
                this.patternLength);
    }

    /**
     * @param bank the HEL::decoder bank to check
     * @return whether its helicity sequence looks good
     */
    public boolean checkHelicities(Object bank) {
        return checkHelicities(
                getInt(bank,"patternArray"), 
                getInt(bank,"helicityArray"), 
                this.patternLength);
    }

    /**
     * Run all available integrity checking on a HEL::decoder bank
     * @param bank the HEL::decoder bank to check
     * @return whether everything looks good 
     */
    public boolean check(Object bank) {
        return check(
            getInt(bank,"pairArray"),
            getInt(bank,"patternArray"),
            getInt(bank,"helicityArray"),
            this.patternLength);
    }

    /**
     * Wrap different types of HIPO banks.
     * @param bank the HEL::decoder bank to access
     * @param name name of its variable to get
     * @return value of the bank variable
     */
    private int getInt(Object bank, String name) {
        if (bank instanceof DataBank)
            return ((DataBank)bank).getInt(name,0);
        else if (bank instanceof Bank)
            return ((Bank)bank).getInt(name,0);
        else
            throw new IllegalArgumentException();
    }

    /**
     * Get a string representation of a HEL::decoder bank for human consumption.
     * @param b the HEL::decoder bank to stringify
     * @return 
     */
    public static String toString(Bank b) {
        StringBuilder s = new StringBuilder();
        s.append("Timestamp/nPair/seed: ");
        s.append(b.getLong("timestamp",0));
        s.append("/");
        s.append(b.getInt("nPair",0));
        s.append("/");
        s.append(b.getInt("helicitySeed",0));
        s.append("\nPair:      ");
        s.append(toString(b.getInt("pairArray",0)));
        s.append("\nPattern:   ");
        s.append(toString(b.getInt("patternArray",0)));
        s.append("\nHelicity:  ");
        s.append(toString(b.getInt("helicityArray",0)));
        s.append("\nHelicityP: ");
        s.append(toString(b.getInt("helicityPArray",0)));
        s.append("\nHelicityP0:");
        StringBuilder s3 = new StringBuilder();
        for (int i=31; i>=0; --i) {
            if (((b.getInt("patternArray",0)>>i)&1) == 1) {
                 s3.append((b.getInt("helicityArray",0)>>i)&1); 
            }
        }
        s.append(String.format("%32s",s3));
        s.append("\nHelicityP1:");
        StringBuilder s2 = new StringBuilder();
        for (int i=31; i>=0; --i) {
            if (((b.getInt("patternArray",0)>>i)&1) == 0) {
                 s2.append((b.getInt("helicityArray",0)>>i)&1); 
            }
        }
        s.append(String.format("%32s",s2));
        s.append("\n");
        return s.toString();
    }

}
