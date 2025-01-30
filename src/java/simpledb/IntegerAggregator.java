package simpledb;

import java.util.*;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private final int gbField;
    private final Type gbFieldType;
    private final int aField;
    private final Op aggregatorOp;
    private final HashMap<Field, Integer> aggMap;
    private final HashMap<Field, List<Integer>> avgMap;

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbField = gbfield;
        this.gbFieldType = gbfieldtype;
        this.aField = afield;
        this.aggregatorOp = what;
        this.aggMap = new HashMap<>();
        this.avgMap = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        Field gbField = this.gbField == Aggregator.NO_GROUPING ? null : tup.getField(this.gbField);
        int tupValue = ((IntField) tup.getField(this.aField)).getValue();

        boolean containsKey = aggMap.containsKey(gbField);

        // if the map doesn't contain the current gbField, we can just add it into the map and return early
        // since we dont need to enter the case switch
        if (!containsKey) {
            int val = (aggregatorOp == Op.COUNT) ? 1 : tupValue;
            aggMap.put(gbField, val);

            // if operator is avg, we're keeping track of additional info so we can avg
            if (aggregatorOp == Op.AVG) {
                avgMap.put(gbField, new ArrayList<>(){{add(tupValue);}});
            }
            return;
        }

        // if we reach this point, gbField exists in aggMap and we can .get() safely
        switch (this.aggregatorOp) {
            case MIN:
                aggMap.put(gbField, Math.min(aggMap.get(gbField), tupValue));
                break;
            case MAX:
                aggMap.put(gbField, Math.max(aggMap.get(gbField), tupValue));
                break;
            case SUM:
                aggMap.put(gbField, tupValue + aggMap.get(gbField));
                break;
            case COUNT:
                aggMap.put(gbField, aggMap.get(gbField) + 1);
                break;
            case AVG:
                // adds the current tuple value into the avg list
                List<Integer> list = avgMap.get(gbField);
                list.add(tupValue);

                // streams to get avg
                int avgInt = (int) list.stream().mapToInt(i -> i).average().orElse(0);

                aggMap.put(gbField, avgInt);
                break;
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        return new OpIterator() {
            int currentIndex = 0;
            TupleDesc tupleDesc;
            List<Tuple> tuples;

            @Override
            public void open() throws DbException, TransactionAbortedException {
                tuples = new ArrayList<>();

                if (gbField == Aggregator.NO_GROUPING) {
                    this.tupleDesc = new TupleDesc(new Type[] { Type.INT_TYPE });

                    // we only need access to the values of the map since the key of the map stores groupValue
                    for (Integer i : aggMap.values()) {
                        Tuple tuple = new Tuple(this.tupleDesc);
                        tuple.setField(0, new IntField(i));
                        tuples.add(tuple);
                    }
                } else {
                    this.tupleDesc = new TupleDesc(new Type[] { gbFieldType, Type.INT_TYPE });

                    // entrySet so we can get key aka groupValue for the first field, and the aggValue for second field
                    for (Map.Entry<Field, Integer> entry : aggMap.entrySet()) {
                        Tuple tuple = new Tuple(this.tupleDesc);
                        tuple.setField(0, entry.getKey());
                        tuple.setField(1, new IntField(entry.getValue()));
                        tuples.add(tuple);
                    }
                }
            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                return currentIndex < tuples.size();
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (tuples == null || !hasNext()) {
                    throw new NoSuchElementException();
                }
                return tuples.get(currentIndex++);
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                close();
                open();
            }

            @Override
            public TupleDesc getTupleDesc() {
                return this.tupleDesc;
            }

            @Override
            public void close() {
                currentIndex = 0;
                tupleDesc = null;
                tuples = null;
            }
        };
    }

}
