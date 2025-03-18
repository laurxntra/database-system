package simpledb;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {
    private int[] histogramBuckets;
    private int min;
    private int max;
    private int numTuples;
    private double width;


    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
    	this.histogramBuckets = new int[buckets];
        this.min = min;
        this.max = max;
        this.numTuples = 0;
        this.width = (max - min + 1.0) / buckets;

    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        // we need to check if the value is within our valid range for the histogram
    	if (v >= min && v <= max) {
            // we need to calculate which bucket the value falls into, so after we need to
            // count of the appropriate bucket and increment the total values in our
            // histogram
            histogramBuckets[(int) ((v - min) / width)]++;
            numTuples++;
        }
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        // we will use a switch case to handle the different types of predicates
        switch(op) {
            case GREATER_THAN:
                // for our > v, the complement of the values is <= v, bc the probability
                // of all possible values must add up to 1
                return 1 - estimateSelectivity(Predicate.Op.LESS_THAN_OR_EQ, v);
            case EQUALS:
                // for our = v, is the difference between the values <= v and the number of
                // values that are strictly < v, so this will give us the values that are = v
                return estimateSelectivity(Predicate.Op.LESS_THAN_OR_EQ, v) -
                        estimateSelectivity(Predicate.Op.LESS_THAN,v);
            case LESS_THAN:
                // checking if the value v is <= min value in our histogram
                if (v <= min) {
                    // if so then the selectivity would be 0
                    return 0.0;
                    // checking if our value v is >= the max value in our histogram
                } else if (v >= max) {
                    // if so then the selectivity would be 1
                    return 1.0;
                    // now we need to handle general cases for v between min and max...
                } else {
                    // so we need to calculate which bucket the value v falls into
                    int index = (int) ((v - min) / width);
                    // then have a tuple that accumulates the total number of values that
                    // are less than v
                    double tuple = 0;

                    // so, we then need to add up the values from all the buckets that
                    // are less than v
                    for (int i = 0; i < index; i++) {
                        tuple += histogramBuckets[i];
                    }
                    // once we checked all the values, we need to add the partial count of values from
                    // the bucket where v falls
                    tuple += (1.0 * histogramBuckets[index] / width) * (v - index * width - min);
                    // finally, we normalize the count by dividing the total number of values in the histogram
                    // to finally get our less than v selectivity.
                    return tuple / numTuples;
                }
            case GREATER_THAN_OR_EQ:
                // selectivity for >= is the same as >, but applied v - 1, bc >= includes
                // all the values that are greater than or equal to v, which is what the
                // v - 1 represents
                return estimateSelectivity(Predicate.Op.GREATER_THAN, v - 1);
            case LESS_THAN_OR_EQ:
                // less than has the similar concept as >=, except we apply v + 1, bc it includes
                // all values that are less than or equal to v
                return estimateSelectivity(Predicate.Op.LESS_THAN, v + 1);
            case NOT_EQUALS:
                // != is the opposite value that is equal, so we need to subtract
                // the fraction of equal values from 1
                return 1 - estimateSelectivity(Predicate.Op.EQUALS, v);
            default:
                throw new IllegalArgumentException("Illegal predicate operation");
        }
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        int count = 0;
        // sum all bucket counts
        for (int bucket : histogramBuckets) {
            count += bucket;
        }
        // if there are no values that are added to our histogram, then return 0
        if (count == 0) return 0;
        // otherwise we now calculate the average selectivity based on the total tuple count
        return (count * 1.0 / numTuples);
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        return "IntHistogram(buckets = " + histogramBuckets.length + ", min = " + min + ", max = " + max + ")";
    }
}
