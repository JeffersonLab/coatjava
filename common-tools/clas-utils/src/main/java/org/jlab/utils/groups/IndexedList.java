package org.jlab.utils.groups;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A generic class representing a collection of elements identified 
 * by a series of indices which length can vary.
 * The indices are hashed into a single long key indexing the collection.
 *
 * @param <T> the type of elements stored in the list
 *
 * @author gavalian
 */
public class IndexedList<T> {
    //Collection of elements
    private final Map<Long,T>  collection = new LinkedHashMap<>();
    //Number of indices
    private int indexSize;
    //index generator used for hashing the multiple 
    //indices into a single long key
    private IndexGenerator indexGenerator;
    
    /**
     * Constructs an empty IndexedList with the default index size (3).
     */
    public IndexedList() {
        this(3);
    }
    
    /**
     * Constructs an IndexedList with the specified number of indices.
     *
     * @param indsize the number of indices
     */
    public IndexedList(int indsize){
        this.indexSize = indsize;
        //Initialization with the index size
        this.indexGenerator = new IndexGenerator(indsize);
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
    */
    private void validateIndex(int... index) {
    if (!isValidIndex(index)) {
        throw new IllegalArgumentException("Index length mismatch: expected " + this.indexSize);
    }

    int maxValue = (indexGenerator.shiftsToConsider == IndexGenerator.BYTE_SHIFTS) ? 0xFFFF : 0x7F;

    for (int i : index) {
        if (i < 0 || i > maxValue) {
            throw new IllegalArgumentException(
                String.format("Index value out of range (0–%d): %d", maxValue, i)
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
    public void add(T item, int... index){
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
    public boolean hasItem(int... index){
        if(!isValidIndex(index)) return false;
        long code = indexGenerator.hashCode(index);
        return this.collection.containsKey(code);
    }
    
    /**
     * Retrieves an item by its index.
     *
     * @param index the index to find
     * @return the item at the index, null if not found
     */
    public T getItem(int... index){
        if (!isValidIndex(index)) return null;
        long code = indexGenerator.hashCode(index);
        return this.collection.get(code);
    }
    
    /**
     * Clears items from the collection.
     */
    public void clear(){this.collection.clear();}
    /**
     * Gets the number of indices used to identify elements.
     *
     * @return the index size
     */
    public int  getIndexSize(){ return this.indexSize;}
    /**
     * Returns the collection of items.
     *
     * @return the map of hashed keys to items
     */
    public Map<Long,T> getMap(){ return this.collection;}
    /**
     * Returns the index generator for this collection.
     *
     * @return the index generator
     */
    public IndexGenerator getIndexGenerator(){ return this.indexGenerator;}
    
    /**
     * Displays the collection
     */
    public void show(){
        for(Map.Entry<Long,T>  entry : this.collection.entrySet()){
            String indexString = indexGenerator.getString(entry.getKey(), this.indexSize);
            System.out.println(String.format("[%s] : ", 
                    indexString) + entry.getValue());
        }
    }

    /**
     * Utility class for generating and decoding a long key from a multi-dimensional index.
     * either up to 4 indices with 16 bits, or up to 9 indices with 7 bits (int up to 128) are handled
     */
    public static class IndexGenerator {
        
        //Nominal case, only works up to four indices. 4 times 16 bits = 64 bits for a long
        static int[] BYTE_SHIFTS = new int[]{48,32,16,0};
        //Case where you can use up to 9 indices that are int up to 128
        //2^7 = 128 and 9*7 = 63 < 64
        static int[] COMPRESSED_SHIFTS = new int[]{56, 49, 42, 35, 28, 21, 14, 7, 0};
        //This is the shifts that will be considered for all computations
        private int[] shiftsToConsider;
        
        /**
         * Constructs an IndexGenerator with generic index size.
         *
         */
        public IndexGenerator(){
            this(3);
        }
        
        /**
         * Constructs an IndexGenerator for the specified index size.
         *
         * @param indsize the number of indices to encode
         * @throws IllegalArgumentException if the number of indices exceeds the supported limit
         */
        public IndexGenerator(int indsize){
            if (indsize > this.COMPRESSED_SHIFTS.length) {
                throw new IllegalArgumentException("# indices is larger than "+ this.COMPRESSED_SHIFTS.length);
            }
            else if (indsize > this.BYTE_SHIFTS.length) {
                this.shiftsToConsider = this.COMPRESSED_SHIFTS;
            }
            else this.shiftsToConsider = this.BYTE_SHIFTS;
        }

        /**
         * Generates a long key from the given array of indices.
         *
         * @param indices the index array
         * @return a long key representing the hashed index
         * @throws IllegalArgumentException if the number of indices exceeds supported length
         */
        public long hashCode(int... indices){
            long result = (long) 0;
            
            if (indices.length > this.shiftsToConsider.length) {
                throw new IllegalArgumentException("# indices is larger than "+ this.shiftsToConsider.length);
            }
            
            for(int loop = 0; loop < indices.length; loop++){
                long patern = (((long) indices[loop])&0x000000000000FFFF)<<this.shiftsToConsider[loop]; 
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
        public int getIndex(long hashcode, int order){
            int result = (int) (hashcode>>this.shiftsToConsider[order])&0x000000000000FFFF;
            return result;
        }
        
        /**
         * Returns a formatted string representing all indices in the hash key.
         *
         * @param hashcode the encoded long key
         * @param length the number of indices to extract
         * @return a string representation of the indices
         */
        public String  getString(long hashcode, int length){
            StringBuilder str = new StringBuilder();
            for(int loop = 0; loop <length; loop++){
                str.append(String.format("%5d", this.getIndex(hashcode, loop)));
            }
            return str.toString();
        }
    }
}
