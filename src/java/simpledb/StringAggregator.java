package simpledb;

import java.util.*;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private final int gbField;
    private final Type gbFieldType;
    private final int aField;
    private final Op aggregatorOp;
    private final HashMap<Field, Integer> aggMap;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        if (what != Op.COUNT) {
            throw new IllegalArgumentException();
        }

        this.gbField = gbfield;
        this.gbFieldType = gbfieldtype;
        this.aField = afield;
        this.aggregatorOp = what;
        this.aggMap = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        Field gbField = this.gbField == Aggregator.NO_GROUPING ? null : tup.getField(this.gbField);
        boolean containsKey = aggMap.containsKey(gbField);

        // we only support count so if key doesnt exist, we start with count = 1
        if (!containsKey) {
            aggMap.put(gbField, 1);
        } else {
            aggMap.put(gbField, aggMap.get(gbField) + 1); // if key already exists, we increment count
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
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
