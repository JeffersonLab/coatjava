package org.jlab.geom.detector.alert.AHDC;

public class AlertDCWireIdentifier {
    /** Number between 0 and 575 */
    private int num;

    /** Always 1 */
    private int sector;

    /** Can be 11, 21, 22, 31, 32, 41, 42, 51 */
    private int layer;
    
    /** component id on a given layer, numerotation starting at 1 */
    private int component;
    
    /**
     * Ahdc wire id defined with a num ranging from 0 to 575
     * @param _num
     */
    public AlertDCWireIdentifier(int _num) {
        num = _num;
        int[] res = wire2slc(_num);
        sector = res[0];
        layer = res[1];
        component = res[2];
    }

    /**
     * Ahdc wire id defined with sector, layer, component identifiers
     * @param _sector
     * @param _layer can be 11, 21, 22, 31, 32, 41, 51
     * @param _component
     */
    public AlertDCWireIdentifier(int _sector, int _layer, int _component) {
        sector = _sector;
        layer = _layer;
        component = _layer;
        num = slc2wire(_sector, _layer, _component);
    }

    public int getNumber() { return num;}

    public int getSectorId() {return sector;}

    public int getLayerId() {return layer;}

    public int getComponentId() {return component;}
 
    /**
     * Convert wire number (number from 0 to 575) to (sector,layer,component) ids
     * 
     * This is the invert operation of  {@link #slc2wire(int, int, int)}
     * 
     * @param wire wire number between 0 and  575
     * @return a triplet (sector, layer, component) in int[]
     */
    public static int[] wire2slc(int wire) {
        int layer = -1;
        int component = -1;
        if (wire < 47) {
            layer = 11;
            component = wire + 1;
        }
        else if ((47 <= wire) && (wire < 47 + 56)) {
            layer = 21;
            component = wire - 47 + 1;
        }
        else if ((47 + 56 <= wire) && (wire < 47 + 56 + 56)) {
            layer = 22;
            component = wire - 47 - 56 + 1;
        }
        else if ((47 + 56 + 56 <= wire) && (wire < 47 + 56 + 56 + 72)) {
            layer = 31;
            component = wire - 47 - 56 - 56 + 1;
        }
        else if ((47 + 56 + 56 + 72 <= wire) && (wire < 47 + 56 + 56 + 72 + 72)) {
            layer = 32;
            component = wire - 47 - 56 - 56 - 72 + 1;
        }
        else if ((47 + 56 + 56 + 72 + 72 <= wire) && (wire < 47 + 56 + 56 + 72 + 72 + 87)) {
            layer = 41;
            component = wire - 47 - 56 - 56 - 72 - 72 + 1;
        }
        else if ((47 + 56 + 56 + 72 + 72 + 87 <= wire) && (wire < 47 + 56 + 56 + 72 + 72 + 87 + 87)) {
            layer = 42;
            component = wire - 47 - 56 - 56 - 72 - 72 - 87 + 1;
        }
        else { // ((47 + 56 + 56 + 72 + 72 + 87 + 87 <= wire) && (wire < 47 + 56 + 56 + 72 + 72 + 87 + 87 + 99)) {
            layer = 51;
            component = wire - 47 - 56 - 56 - 72 - 72 - 87 - 87 + 1;
        }
        return new int[] {1, layer, component};
    }

    /**
     * Convert (sector, layer, component) to a unique wire id (number betwwen 0 and 575)
     * 
     * @param sector (not used)
     * @param layer 
     * @param component 
     * @return unique wire id
     */
    public static int slc2wire(int sector, int layer, int component) {
        if (layer == 11) {
            return component - 1;
        } 
        else if (layer == 21) {
            return 47 + component - 1;
        } 
        else if (layer == 22) {
            return 47 + 56 + component - 1;
        } 
        else if (layer == 31) {
            return 47 + 56 + 56 + component - 1;
        } 
        else if (layer == 32) {
            return 47 + 56 + 56 + 72 + component - 1;
        } 
        else if (layer == 41) {
            return 47 + 56 + 56 + 72 + 72 + component - 1;
        } 
        else if (layer == 42) {
            return 47 + 56 + 56 + 72 + 72 + 87 + component - 1;
        } 
        else if (layer == 51) {
            return 47 + 56 + 56 + 72 + 72 + 87 + 87 + component - 1;
        } else {
            return -1; // not a ahdc wire
        }
    }

    /**
     * Convert the layer digits (11,21,...,51) to layer number between 0 and 7
     * 
     * @param digit 
     * @return layer number
     */
    public static int layer2number(int digit) {
        if      (digit == 11) {
            return 0;
        } 
        else if (digit == 21) {
            return 1;
        } 
        else if (digit == 22) {
            return 2;
        } 
        else if (digit == 31) {
            return 3;
        } 
        else if (digit == 32) {
            return 4;
        } 
        else if (digit == 41) {
            return 5;
        } 
        else if (digit == 42) {
            return 6;
        } 
        else if (digit == 51) {
            return 7;
        } else {
            return -1;
        }
    }

    /**
     * Convert layer number (from 0 to 7) to the superlayer-layer id (11,21,...,51)
     * 
     * @param digit between 1 and 8
     * @return layer number between (11,21,...,51)
     */
    public static int number2layer(int num) {
        if      (num == 0) {
            return 11;
        } 
        else if (num == 1) {
            return 21;
        } 
        else if (num == 2) {
            return 22;
        } 
        else if (num == 3) {
            return 31;
        } 
        else if (num == 4) {
            return 32;
        } 
        else if (num == 5) {
            return 41;
        } 
        else if (num == 6) {
            return 42;
        } 
        else if (num == 7) {
            return 51;
        } else {
            return -1;
        }
    }

    /**
     * 
     * @param _layer (number 11, 21, 22, ..., 51)
     * @return the radius of the _layer
     */
    public static double layer2Radius(int _layer) {
        if (_layer == 11) {
            return 32.0;
        }
        else if (_layer == 21) {
            return 38.0;
        }
        else if (_layer == 22) {
            return 42.0;
        }
        else if (_layer == 31) {
            return 48.0;
        }
        else if (_layer == 32) {
            return 52.0;
        }
        else if (_layer == 41) {
            return 58.0;
        }
        else if (_layer == 42) {
            return 62.0;
        }
        else if (_layer == 51) {
            return 68.0;
        } else {
            return 0.0;
        }
    }

    /**
     * 
     * @param _layer_num between 1 and 8
     * @return the radius of the _layer. See {@link #layer2Radius(int)}
     */
    public static double layerNum2Radius(int _layer_num) {
        return layer2Radius(number2layer(_layer_num));
    }
}
