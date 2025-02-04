package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;
    private final TransactionId tid;
    private OpIterator child;
    private final TupleDesc tupleDesc;
    private final BufferPool bufferPool = Database.getBufferPool();
    private boolean called;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     * 
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    public Delete(TransactionId t, OpIterator child) {
        this.tid = t;
        this.child = child;
        this.tupleDesc = new TupleDesc(new Type[]{Type.INT_TYPE}); // returned Tuple of Delete is always count of deleted tuples
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
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     * 
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (called) {
            return null;
        }
        called = true;

        // tracking number of deleted tuples
        int count = 0;

        // loops through all tuples in OpIterator child and deletes them
        while (child.hasNext()) {
            Tuple tuple = child.next();
            try {
                bufferPool.deleteTuple(tid, tuple);
            } catch (IOException e) {
                throw new DbException(e.getMessage());
            }
            count++;
        }

        // building the 1-field tuple with count of deleted tuples
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
