package simpledb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TableStats represents statistics (e.g., histograms) about base tables in a
 * query. 
 * 
 * This class is not needed in implementing lab1 and lab2.
 */
public class TableStats {
    private final int numPages;
    private int numTuples;
    private final Map<Integer, IntHistogram> intHistogram;
    private final Map<Integer, StringHistogram> stringHistogram;
    private final int givenIoCostPerPage;

    private static final ConcurrentHashMap<String, TableStats> statsMap = new ConcurrentHashMap<String, TableStats>();

    static final int IOCOSTPERPAGE = 1000;

    public static TableStats getTableStats(String tablename) {
        return statsMap.get(tablename);
    }

    public static void setTableStats(String tablename, TableStats stats) {
        statsMap.put(tablename, stats);
    }
    
    public static void setStatsMap(HashMap<String,TableStats> s)
    {
        try {
            java.lang.reflect.Field statsMapF = TableStats.class.getDeclaredField("statsMap");
            statsMapF.setAccessible(true);
            statsMapF.set(null, s);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, TableStats> getStatsMap() {
        return statsMap;
    }

    public static void computeStatistics() {
        Iterator<Integer> tableIt = Database.getCatalog().tableIdIterator();

        System.out.println("Computing table stats.");
        while (tableIt.hasNext()) {
            int tableid = tableIt.next();
            TableStats s = new TableStats(tableid, IOCOSTPERPAGE);
            setTableStats(Database.getCatalog().getTableName(tableid), s);
        }
        System.out.println("Done.");
    }

    /**
     * Number of bins for the histogram. Feel free to increase this value over
     * 100, though our tests assume that you have at least 100 bins in your
     * histograms.
     */
    static final int NUM_HIST_BINS = 100;

    /**
     * Create a new TableStats object, that keeps track of statistics on each
     * column of a table
     * 
     * @param tableid
     *            The table over which to compute statistics
     * @param ioCostPerPage
     *            The cost per page of IO. This doesn't differentiate between
     *            sequential-scan IO and disk seeks.
     */
    public TableStats(int tableid, int ioCostPerPage) {
        HeapFile file = (HeapFile) Database.getCatalog().getDatabaseFile(tableid); // dbFile for table in question
        DbFileIterator fileIter = file.iterator(new TransactionId()); // iterator for file
        int numFields = file.getTupleDesc().numFields();
        Map<Integer, Integer> fieldsMin = new HashMap<>();
        Map<Integer, Integer> fieldsMax = new HashMap<>();

        numTuples = 0;
        numPages = file.numPages();
        givenIoCostPerPage = ioCostPerPage;
        intHistogram = new HashMap<>();
        stringHistogram = new HashMap<>();

        // get the min and max values of each fields
        try {
            fileIter.open();
            // keep looping until there are no more tuples in file
            while (fileIter.hasNext()) {
                Tuple tuple = fileIter.next();
                // increase numTuples because we need to keep track of the number of tuples in this file for
                // estimateTableCardinality
                numTuples++;
                for (int i = 0; i < numFields; i++) {
                    // as we loop through the fields/attributes, we only check min/max if type is INT
                    if (tuple.getField(i).getType() == Type.INT_TYPE) {
                        int newVal = ((IntField) tuple.getField(i)).getValue();

                        // select the min or max between the newVal and the currentVal stored for field i
                        // if i doesnt exist in map yet, default to max_value or min_value accordingly before
                        // comparing min/max
                        int newMin = Math.min(fieldsMin.getOrDefault(i, Integer.MAX_VALUE), newVal);
                        int newMax = Math.max(fieldsMax.getOrDefault(i, Integer.MIN_VALUE), newVal);

                        fieldsMin.put(i, newMin);
                        fieldsMax.put(i, newMax);
                    }

                }
            }
            fileIter.close();
        } catch (TransactionAbortedException | DbException ignored) {
        }

        try {
            fileIter.open();
            // keep looping until there are no more tuples in file
            while (fileIter.hasNext()) {
                Tuple tuple = fileIter.next();
                for (int i = 0; i < numFields; i++) {
                    // as we loop through each field, depending on if INT or STRING type, build corresponding histogram
                    Type fieldType = tuple.getField(i).getType();
                    if (fieldType == Type.INT_TYPE) {
                        // get the int value
                        int newVal = ((IntField) tuple.getField(i)).getValue();
                        // if intHistogram doesnt have current field yet, create a new one and add it
                        if (!intHistogram.containsKey(i)) {
                            intHistogram.put(i, new IntHistogram(NUM_HIST_BINS, fieldsMin.get(i), fieldsMax.get(i)));
                        }
                        // add new int value to the histogram
                        IntHistogram h = intHistogram.get(i);
                        h.addValue(newVal);
                    } else if (fieldType == Type.STRING_TYPE) {
                        // get the string value
                        String newVal = ((StringField) tuple.getField(i)).getValue();
                        // if stringHistogram doesnt have current field yet, create a new one and add it
                        if (!stringHistogram.containsKey(i)) {
                            stringHistogram.put(i, new StringHistogram(NUM_HIST_BINS));
                        }
                        // add new string value to the histogram
                        StringHistogram h = stringHistogram.get(i);
                        h.addValue(newVal);
                    }
                }
            }
            fileIter.close();
        } catch (TransactionAbortedException | DbException ignored) {

        }
    }

    /**
     * Estimates the cost of sequentially scanning the file, given that the cost
     * to read a page is costPerPageIO. You can assume that there are no seeks
     * and that no pages are in the buffer pool.
     * 
     * Also, assume that your hard drive can only read entire pages at once, so
     * if the last page of the table only has one tuple on it, it's just as
     * expensive to read as a full page. (Most real hard drives can't
     * efficiently address regions smaller than a page at a time.)
     * 
     * @return The estimated cost of scanning the table.
     */
    public double estimateScanCost() {
        return givenIoCostPerPage * numPages;
    }

    /**
     * This method returns the number of tuples in the relation, given that a
     * predicate with selectivity selectivityFactor is applied.
     * 
     * @param selectivityFactor
     *            The selectivity of any predicates over the table
     * @return The estimated cardinality of the scan with the specified
     *         selectivityFactor
     */
    public int estimateTableCardinality(double selectivityFactor) {
        return (int) (selectivityFactor * numTuples);
    }

    /**
     * The average selectivity of the field under op.
     * @param field
     *        the index of the field
     * @param op
     *        the operator in the predicate
     * The semantic of the method is that, given the table, and then given a
     * tuple, of which we do not know the value of the field, return the
     * expected selectivity. You may estimate this value from the histograms.
     * */
    public double avgSelectivity(int field, Predicate.Op op) {
        return 1.0;
    }

    /**
     * Estimate the selectivity of predicate <tt>field op constant</tt> on the
     * table.
     * 
     * @param field
     *            The field over which the predicate ranges
     * @param op
     *            The logical operation in the predicate
     * @param constant
     *            The value against which the field is compared
     * @return The estimated selectivity (fraction of tuples that satisfy) the
     *         predicate
     */
    public double estimateSelectivity(int field, Predicate.Op op, Field constant) {
        Type fieldType = constant.getType();
        double selectivity = 0.0;

        if (fieldType == Type.INT_TYPE) {
            if (!intHistogram.isEmpty() && intHistogram.containsKey(field)) {
                int value = ((IntField) constant).getValue();
                IntHistogram h = intHistogram.get(field);
                selectivity = h.estimateSelectivity(op, value);
            }
        } else if (fieldType == Type.STRING_TYPE) {
            if (!stringHistogram.isEmpty() && stringHistogram.containsKey(field)) {
                String value = ((StringField) constant).getValue();
                StringHistogram h = stringHistogram.get(field);
                selectivity = h.estimateSelectivity(op, value);
            }
        }

        return selectivity;
    }

    /**
     * return the total number of tuples in this table
     * */
    public int totalTuples() {
        return numTuples;
    }

}
