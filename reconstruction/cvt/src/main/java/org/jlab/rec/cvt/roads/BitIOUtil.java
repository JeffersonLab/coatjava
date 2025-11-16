/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author veronique
 */
public class BitIOUtil {
    
    public static CompactRoad readRoadAtOffset(byte[] data, long offsetBytes) throws IOException {
        try (BitIO.BitInputStream bis =
                     new BitIO.BitInputStream(new ByteArrayInputStream(data))) {

            skipBytes(bis,offsetBytes);

            int nElements = (int) bis.readBits(4);
            List<CompactElement> elems = new ArrayList<>(nElements);
            for (int i = 0; i < nElements; i++) {
                int layer  = (int) bis.readBits(4) + 1;
                int sector = (int) bis.readBits(5) + 1;
                int strip  = (int) bis.readBits(11) + 1;
                elems.add(new CompactElement(sector, layer, strip));
            }
            bis.alignToByte();

            return new CompactRoad(elems);
        }
    }

    /**
    * Skip a number of bytes in a BitInputStream.
    * @param bis the BitInputStream
    * @param nBytes number of bytes to skip
    * @throws IOException if I/O fails
    */
   public static void skipBytes(BitIO.BitInputStream bis, long nBytes) throws IOException {
       long bitsToSkip = nBytes * 8;
       while (bitsToSkip > 0) {
           // skipBits returns void, so just call it
           bis.skipBits((int) Math.min(bitsToSkip, Integer.MAX_VALUE));
           bitsToSkip -= Math.min(bitsToSkip, Integer.MAX_VALUE);
       }
   }



}
