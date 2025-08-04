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
     */
    public IndexedList(int indsize) {
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
        return index != null && index.length == this.indexSize;
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
        for (int i = 0; i < index.length-1; i++) {
            int bits = (i != 0)
                    ? indexGenerator.getByteShifts()[i] - indexGenerator.getByteShifts()[i + 1]
                    : 64 - indexGenerator.getByteShifts()[i]; // First field: number of bits from shift to 64

            int maxValue = (1 << bits) - 1;

            // Check if the index value is within the allowed range
            if (index[i] < 0 || index[i] > maxValue) {
                throw new IllegalArgumentException(
                        String.format("Index value out of range (0–%d) for byte shift %d: %d", maxValue, bits, index[i])
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
            String indexString = indexGenerator.getString(entry.getKey(), this.indexSize);
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

        private int[] byteShifts = new int[]{48, 32, 16, 0};

        /**
         * Constructs an IndexGenerator with generic index size.
         */
        public IndexGenerator() {
            //Nominal case, only works up to four indices. 4 times 16 bits = 64 bits for a long
            this.byteShifts = new int[]{48, 32, 16, 0};
        }

        /**
         * Constructs an IndexGenerator from a given byte shifts array.
         *
         * @param byteShifts the array of byte shifts to consider
         */
        public IndexGenerator(int[] byteShifts) {
            // Check that no byte shift exceeds 64
            for (int shift : byteShifts) {
                if (shift < 0 || shift >= 64) {
                    throw new IllegalArgumentException("Byte shift must be between 0 and 63.");
                }
            }
            this.byteShifts = byteShifts;
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
         * Generates a long key from the given array of indices.
         *
         * @param indices the index array
         * @return a long key representing the hashed index
         * @throws IllegalArgumentException if the number of indices exceeds
         * supported length
         */
        public long hashCode(int... indices) {
            long result = (long) 0;

            if (indices.length > this.byteShifts.length) {
                throw new IllegalArgumentException("# indices is larger than " + this.byteShifts.length);
            }

            for (int loop = 0; loop < indices.length; loop++) {
                long patern = (((long) indices[loop]) & 0x000000000000FFFF) << this.byteShifts[loop];
                result = (result | patern);
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
            int result = (int) (hashcode >> this.byteShifts[order]) & 0x000000000000FFFF;
            return result;
        }

        /**
         * Returns a formatted string representing all indices in the hash key.
         *
         * @param hashcode the encoded long key
         * @param length the number of indices to extract
         * @return a string representation of the indices
         */
        public String getString(long hashcode, int length) {
            StringBuilder str = new StringBuilder();
            for (int loop = 0; loop < length; loop++) {
                str.append(String.format("%5d", this.getIndex(hashcode, loop)));
            }
            return str.toString();
        }
    }
}
