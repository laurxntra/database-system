package simpledb;

import java.io.IOException;

/**
 * Inserts tuples read from the child operator into the tableId specified in the
 * constructor
 */
public class Insert extends Operator {

    private static final long serialVersionUID = 1L;
    private final TransactionId tid;
    private OpIterator child;
    private final int tableId;
    private final TupleDesc tupleDesc;
    private final BufferPool bufferPool = Database.getBufferPool();
    private boolean called;

    /**
     * Constructor.
     *
     * @param t
     *            The transaction running the insert.
     * @param child
     *            The child operator from which to read tuples to be inserted.
     * @param tableId
     *            The table in which to insert tuples.
     * @throws DbException
     *             if TupleDesc of child differs from table into which we are to
     *             insert.
     */
    public Insert(TransactionId t, OpIterator child, int tableId)
            throws DbException {
        // if child's td and table's td differs we throw
        TupleDesc childTd = child.getTupleDesc();
        DbFile file = Database.getCatalog().getDatabaseFile(tableId);
        if (!(childTd.equals(file.getTupleDesc()))) {
            throw new DbException("Insertion into table " + tableId + " failed. Differing tuple descriptions.");
        }

        this.tid = t;
        this.tupleDesc = new TupleDesc(new Type[]{Type.INT_TYPE}); // returned Tuple of Insert is always count of inserted tuples
        this.child = child;
        this.tableId = tableId;
        this.called = false;
    }

    public TupleDesc getTupleDesc() {
        return this.tupleDesc;
    }

    public void open() throws DbException, TransactionAbortedException {
        this.child.open();
        super.open();
    }

    public void close() {
        this.child.close();
        super.close();
        this.called = false;
    }

    public void rewind() throws DbException, TransactionAbortedException {
        this.child.rewind();
        this.called = false;
    }

    /**
     * Inserts tuples read from child into the tableId specified by the
     * constructor. It returns a one field tuple containing the number of
     * inserted records. Inserts should be passed through BufferPool. An
     * instances of BufferPool is available via Database.getBufferPool(). Note
     * that insert DOES NOT need check to see if a particular tuple is a
     * duplicate before inserting it.
     *
     * @return A 1-field tuple containing the number of inserted records, or
     *         null if called more than once.
     * @see Database#getBufferPool
     * @see BufferPool#insertTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        // according to specs, we return null if called more than once
        if (called) {
            return null;
        }
        called = true;

        // tracks how many tuples we've inserted
        int count = 0;

        // loops through all of OpIterator and inserts tuples, increments count as needed
        while (child.hasNext()) {
            Tuple tuple = child.next();
            try {
                bufferPool.insertTuple(tid, tableId, tuple);
            } catch (IOException e) {
                throw new DbException(e.getMessage());
            }
            count++;
        }

        // building the 1-field tuple with count of inserted tuples
        Tuple tuple = new Tuple(tupleDesc);
        tuple.setField(0, new IntField(count));
        return tuple;
    }

    @Override
    public OpIterator[] getChildren() {
        return new OpIterator[]{ child };
    }

    @Override
    public void setChildren(OpIterator[] children) {
        this.child = children[0];
    }
}
