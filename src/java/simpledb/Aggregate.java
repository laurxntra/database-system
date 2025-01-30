package simpledb;

import java.util.*;

/**
 * The Aggregation operator that computes an aggregate (e.g., sum, avg, max,
 * min). Note that we only support aggregates over a single column, grouped by a
 * single column.
 */
public class Aggregate extends Operator {

    private static final long serialVersionUID = 1L;
    private OpIterator child;
    private final int aField;
    private final int gField;
    private final Aggregator.Op aggOp;
    private Aggregator aggregator;
    private final TupleDesc aggTupleDesc;
    private OpIterator aggIterator;

    /**
     * Constructor.
     * 
     * Implementation hint: depending on the type of afield, you will want to
     * construct an {@link IntegerAggregator} or {@link StringAggregator} to help
     * you with your implementation of readNext().
     * 
     * 
     * @param child
     *            The OpIterator that is feeding us tuples.
     * @param afield
     *            The column over which we are computing an aggregate.
     * @param gfield
     *            The column over which we are grouping the result, or -1 if
     *            there is no grouping
     * @param aop
     *            The aggregation operator to use
     */
    public Aggregate(OpIterator child, int afield, int gfield, Aggregator.Op aop) {
	    this.child = child;
        this.aField = afield;
        this.gField = gfield;
        this.aggOp = aop;
        TupleDesc tupleDesc = child.getTupleDesc();

        boolean isNoGrouping = gField == Aggregator.NO_GROUPING;
        // if gField is NO_GROUPING, we don't have a type for the group by field
        Type gbFieldType = isNoGrouping ? null : tupleDesc.getFieldType(gfield);
        this.aggTupleDesc = isNoGrouping ? new TupleDesc(new Type[] { Type.INT_TYPE }) : new TupleDesc(new Type[] { gbFieldType, Type.INT_TYPE });

        if (tupleDesc.getFieldType(afield) == Type.INT_TYPE) {
            aggregator = new IntegerAggregator(gfield, gbFieldType, afield, aggOp);
        } else if (tupleDesc.getFieldType(afield) == Type.STRING_TYPE) {
            aggregator = new StringAggregator(gfield, gbFieldType, afield, aggOp);
        }
    }

    /**
     * @return If this aggregate is accompanied by a groupby, return the groupby
     *         field index in the <b>INPUT</b> tuples. If not, return
     *         {@link simpledb.Aggregator#NO_GROUPING}
     * */
    public int groupField() {
	    return this.gField;
    }

    /**
     * @return If this aggregate is accompanied by a group by, return the name
     *         of the groupby field in the <b>OUTPUT</b> tuples. If not, return
     *         null;
     * */
    public String groupFieldName() {
        // not grouped by anything so return null
	    if (gField == Aggregator.NO_GROUPING) {
            return null;
        }

        // aggregated TupleDesc is always (groupBy, aggregated) so we should access index = 0 if we want groupBy's fieldName
        return this.aggTupleDesc.getFieldName(0);
    }

    /**
     * @return the aggregate field
     * */
    public int aggregateField() {
	    return aField;
    }

    /**
     * @return return the name of the aggregate field in the <b>OUTPUT</b>
     *         tuples
     * */
    public String aggregateFieldName() {
        // when there's no grouping, the aggregated's tupleDesc only has 1 field which is the aggregated field so we
        // access index = 0, if it does have grouping the aggregated field comes second so we access index = 1
	    return gField == Aggregator.NO_GROUPING ? this.aggTupleDesc.getFieldName(0) : this.aggTupleDesc.getFieldName(1);
    }

    /**
     * @return return the aggregate operator
     * */
    public Aggregator.Op aggregateOp() {
	return aggOp;
    }

    public static String nameOfAggregatorOp(Aggregator.Op aop) {
	return aop.toString();
    }

    public void open() throws NoSuchElementException, DbException,
	    TransactionAbortedException {
	    child.open();
        super.open();

        // aggregating all of the children and then assigning aggIterator the iterator and opening
        while (child.hasNext()) {
            aggregator.mergeTupleIntoGroup(child.next());
        }
        child.close();

        aggIterator = aggregator.iterator();
        aggIterator.open();
    }

    /**
     * Returns the next tuple. If there is a group by field, then the first
     * field is the field by which we are grouping, and the second field is the
     * result of computing the aggregate. If there is no group by field, then
     * the result tuple should contain one field representing the result of the
     * aggregate. Should return null if there are no more tuples.
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
	    if (aggIterator != null && aggIterator.hasNext()) {
            return aggIterator.next();
        }
        return null;
    }

    public void rewind() throws DbException, TransactionAbortedException {
        aggIterator.rewind();
    }

    /**
     * Returns the TupleDesc of this Aggregate. If there is no group by field,
     * this will have one field - the aggregate column. If there is a group by
     * field, the first field will be the group by field, and the second will be
     * the aggregate value column.
     * 
     * The name of an aggregate column should be informative. For example:
     * "aggName(aop) (child_td.getFieldName(afield))" where aop and afield are
     * given in the constructor, and child_td is the TupleDesc of the child
     * iterator.
     */
    public TupleDesc getTupleDesc() {
	    return aggTupleDesc;
    }

    public void close() {
        aggIterator.close();
    }

    @Override
    public OpIterator[] getChildren() { return new OpIterator[]{child}; }

    @Override
    public void setChildren(OpIterator[] children) { child = children[0]; }
}
