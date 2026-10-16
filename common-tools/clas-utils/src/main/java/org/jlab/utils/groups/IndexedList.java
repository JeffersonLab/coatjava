package org.jlab.utils.groups;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A generic class representing a collection of elements identified by a series
 * of indices which length can vary. The indices are hashed into a single long
 * key indexing the collection.
 *
 * @param <T> the type of elements stored in the list
 *
 * @author gavalian
 */
public class IndexedList<T> {

    //Collection of elements
    private final Map<Long, T> collection = new LinkedHashMap<>();
    //Number of indices
    private int indexSize;
    //index generator used for hashing the multiple 
    //indices into a single long key
    private IndexGenerator indexGenerator;

    /**
     * Constructs an empty IndexedList with the default index size (3).
     */
    public IndexedList() {
        this.indexSize = 3;
        //Initialization with the default index
        this.indexGenerator = new IndexGenerator();
    }

    /**
     * Constructs an IndexedList with the specified number of indices.
     *
     * @param indsize the number of indices
     * @throws IllegalArgumentException if indsize is greater than 4, in which
     * case a custom byte shift array should be used
     */
    public IndexedList(int indsize) {
        if (indsize > 4) {
            throw new IllegalArgumentException("Index size cannot be greater than 4, use custom byte shift array instead.");
        }
        this.indexSize = indsize;
        //Initialization with the default index
        this.indexGenerator = new IndexGenerator();
    }

    /**
     * Constructs an IndexedList with the specified number of indices.
     *
     * @param byteShifts the byte shifts to consider to build the index
     */
    public IndexedList(int[] byteShifts) {
        this.indexSize = byteShifts.length;
        //Initialization with the index size
        this.indexGenerator = new IndexGenerator(byteShifts);
    }

    /**
     * Helper method to check if an index can be looked up.
     *
     * @param index array to check
     * @return true or false if the index is valid or not
     */
    private boolean isValidIndex(int... index) {
        return index.length == this.indexSize;
    }

    /**
     * Helper method to handle errors for wrong indices
     *
     * @param index array to check
     * @throws IllegalArgumentException if the number of indices or their values
     * exceeds supported parameters
     */
    private void validateIndex(int... index) {
        if (!isValidIndex(index)) {
            throw new IllegalArgumentException("Index length mismatch: expected " + this.indexSize);
        }
        for (int i = 0; i < index.length - 1; i++) {
            // Check if the index value is within the allowed range
            if (index[i] < 0 || index[i] > indexGenerator.getMaxValues()[i]) {
                throw new IllegalArgumentException(
                        String.format("Index value out of range (0–%d) : %d", indexGenerator.getMaxValues()[i], index[i])
                );
            }
        }
    }

    /**
     * Adds an item to the collection with its index.
     *
     * @param item the item to be added
     * @param index the index array used to identify the item
     */
    public void add(T item, int... index) {
        validateIndex(index);
        long code = this.indexGenerator.hashCode(index);
        this.collection.put(code, item);
    }

    /**
     * Checks whether an item exists for the specified index.
     *
     * @param index the index to look up
     * @return true if an item exists at the index; false otherwise
     */
    public boolean hasItem(int... index) {
        if (!isValidIndex(index)) {
            return false;
        }
        long code = indexGenerator.hashCode(index);
        return this.collection.containsKey(code);
    }

    /**
     * Checks whether an item exists for the specified hash code.
     *
     * @param hash the hash code to look up
     * @return true if an item exists at the index; false otherwise
     */
    public boolean hasItemByHash(long hash) {
        return this.collection.containsKey(hash);
    }
    
    /**
     * Retrieves an item by its index.
     *
     * @param index the index to find
     * @return the item at the index, null if not found
     */
    public T getItem(int... index) {
        if (!isValidIndex(index)) {
            return null;
        }
        long code = indexGenerator.hashCode(index);
        return this.collection.get(code);
    }

    /**
     * Retrieves an item by its hash.
     *
     * @param hash the hash to find
     * @return the item with the hash, null if not found
     */
    public T getItemByHash(long hash){
        if (!this.collection.containsKey(hash)) return null;
        return this.collection.get(hash);
    }

    /**
     * Clears items from the collection.
     */
    public void clear() {
        this.collection.clear();
    }

    /**
     * Gets the number of indices used to identify elements.
     *
     * @return the index size
     */
    public int getIndexSize() {
        return this.indexSize;
    }

    /**
     * Returns the collection of items.
     *
     * @return the map of hashed keys to items
     */
    public Map<Long, T> getMap() {
        return this.collection;
    }

    /**
     * Returns the index generator for this collection.
     *
     * @return the index generator
     */
    public IndexGenerator getIndexGenerator() {
        return this.indexGenerator;
    }

    /**
     * Sets the index generator for this collection.
     *
     * @param indexGenerator, the {
     * @IndexGenerator} to be set
     */
    public void setIndexGenerator(IndexGenerator indexGenerator) {
        this.indexGenerator = indexGenerator;
    }

    /**
     * Displays the collection
     */
    public void show() {
        for (Map.Entry<Long, T> entry : this.collection.entrySet()) {
            String indexString = indexGenerator.getString(entry.getKey());
            System.out.println(String.format("[%s] : ",
                    indexString) + entry.getValue());
        }
    }

    /**
     * Utility class for generating and decoding a long key from a
     * multi-dimensional index. Default is up to 4 indices with 16 bits, but any
     * byte shifts can be set up to a max of 64
     */
    public static class IndexGenerator {

        private int[] byteShifts;
        private final int[] bitWidths;
        private final int[] maxValues;

        /**
         * Constructs an IndexGenerator with generic index size.
         */
        public IndexGenerator() {
            //Nominal case, only works up to four indices. 4 times 16 bits = 64 bits for a long
            this(new int[]{48, 32, 16, 0});
        }

        /**
         * Constructs an IndexGenerator from a given byte shifts array.
         *
         * @param byteShifts the array of byte shifts to consider
         */
        public IndexGenerator(int[] byteShifts) {
            //Check if the byteShifts array is null
            if (byteShifts == null) {
                throw new IllegalArgumentException("byte shifts array must not be null.");
            }
            // Check that no byte shift exceeds 64
            for (int shift : byteShifts) {
                if (shift < 0 || shift >= 64) {
                    throw new IllegalArgumentException("Byte shift must be between 0 and 63.");
                }
            }
            // Ensure strictly decreasing order (e.g., [48, 32, 16, 0])
            for (int i = 1; i < byteShifts.length; i++) {
                if (byteShifts[i] >= byteShifts[i - 1]) {
                    throw new IllegalArgumentException("byte shifts array must be strictly decreasing.");
                }
            }
            this.byteShifts = byteShifts;
            //Computing max allowed for each index
            this.bitWidths = new int[byteShifts.length];
            this.maxValues = new int[byteShifts.length];
            //First byte shift is the lower limit and goes up to 64 for a long
            this.bitWidths[0] = 64 - this.byteShifts[0];
            //Check we can work with int
            if (this.bitWidths[0] <= 0 || this.bitWidths[0] > 32) {
                throw new IllegalArgumentException("Invalid bit width: " + this.bitWidths[0]);
            }
            this.maxValues[0] = (1 << this.bitWidths[0]) - 1;
            //Other widths are given from distance between two consecutive shifts
            for (int i = 1; i < byteShifts.length; i++) {
                this.bitWidths[i] = byteShifts[i - 1] - byteShifts[i];
                //Check we can work with int
                if (this.bitWidths[i] <= 0 || this.bitWidths[i] > 32) {
                    throw new IllegalArgumentException("Invalid bit width: " + this.bitWidths[0]);
                }
                this.maxValues[i] = (1 << this.bitWidths[i]) - 1;
            }

            //Check that we can work with int indices
        }

        /**
         * Get the byte shifts
         *
         * @return the array of byte shifts
         */
        public int[] getByteShifts() {
            return this.byteShifts;
        }

        /**
         * Get the max value allowed for each index
         *
         * @return the array of max values
         */
        public int[] getMaxValues() {
            return this.maxValues;
        }

        /**
         * Generates a long key from the given array of indices.
         *
         * @param indices the index array
         * @return a long key representing the hashed index
         * @throws IllegalArgumentException if the number of indices exceeds
         * supported length
         */
        public long hashCode(int... indices) {
            long result = 0L;

            if (indices.length > this.byteShifts.length) {
                throw new IllegalArgumentException("# indices is larger than " + this.byteShifts.length);
            }

            for (int i = 0; i < indices.length; i++) {
                int width = this.bitWidths[i];
                //long key from index
                long mask = (1L << width) - 1;
                long pattern = (((long) indices[i]) & mask) << this.byteShifts[i];
                result |= pattern;
            }
            return result;
        }

        /**
         * Retrieves a specific index from the encoded long key.
         *
         * @param hashcode the encoded long key
         * @param order the position of the index to retrieve
         * @return the decoded index
         */
        public int getIndex(long hashcode, int order) {
            int width = this.bitWidths[order];
            long mask = (1L << width) - 1;
            return (int) ((hashcode >> this.byteShifts[order]) & mask);
        }

        /**
         * Retrieves an array of the requested indices.
         * 
         * @param indices
         * @return 
         */
        public int[] getIndices(long hashcode, int... indices) {
            int[] ret = new int[indices.length];
            for (int i=0; i<ret.length; i++)
                ret[i] = getIndex(hashcode, i);
            return ret;
        }

        /**
         * Returns a formatted string representing all indices in the hash key.
         *
         * @param hashcode the encoded long key
         * @return a string representation of the indices
         */
        public String getString(long hashcode) {
            StringBuilder str = new StringBuilder();
            for (int loop = 0; loop < this.byteShifts.length; loop++) {
                str.append(String.format("%5d", this.getIndex(hashcode, loop)));
            }
            return str.toString();
        }
    }
}
