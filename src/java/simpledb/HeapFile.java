package simpledb;

import java.io.*;
import java.sql.Array;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {
    private File file;
    private TupleDesc tupleDesc;

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.file = f;
        this.tupleDesc = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return file.getAbsoluteFile().hashCode(); // aka tableId
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return tupleDesc;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        int size = BufferPool.getPageSize();
        byte[] data = new byte[size];

        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            int offset = pid.getPageNumber() * size;

            raf.seek(offset);
            raf.read(data);
            raf.close();

            HeapPageId pageId = new HeapPageId(pid.getTableId(), pid.getPageNumber());
            return new HeapPage(pageId, data);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // Based off DbFile.java, we need to write a page to the disk!

        // raf allows us to open the file as a read write mode to allow us to
        // write the page data to our disk!
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        HeapPage pg = (HeapPage) page;

        // we need to grab the byte array of the page data so we're able to write
        // data to our disk!
        byte[] data = pg.getPageData();

        // We need to move the file pointer to the page's position
        // By multiplying the page # by the page size, this can give us the
        // correct location in the file
        raf.seek(pg.getId().getPageNumber() * BufferPool.getPageSize());

        // Now we're writing the page data to the file!
        raf.write(data);
        raf.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) Math.ceil((double) file.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // This method is for inserting a specific tuple into the database file
        // We're also trying to optimize spacing, so we need to find any empty
        // slots that we can insert tuples in, if not we're going to need to
        // create a new page for our tuple!

        // Keeps track of dirty pages
        ArrayList<Page> dirtyPg = new ArrayList<Page>();

        // Now, lets loop through all pages in the file
        for (int i = 0; i < numPages(); i++) {
            // tracking pid & page for the page we're looking at
            HeapPageId pid = new HeapPageId(getId(), i);
            // Grabbing pages from the bufferpool, marking it as a read then write
            HeapPage pg = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);

            // If the page has an empty slot, we can insert a tuple
            // we're doing this for optimization as mentioned previously!
            if (pg.getNumEmptySlots() > 0) {
                pg.insertTuple(t);
                pg.markDirty(true, tid);
                dirtyPg.add(pg);
                return dirtyPg;
            }

        }

        // If no pages with empty slots are being found, then we need to create
        // a new page for our tuple, then insert it in!
        HeapPageId pid = new HeapPageId(getId(), numPages());
        HeapPage pg = new HeapPage(pid, HeapPage.createEmptyPageData());
        pg.insertTuple(t);
        writePage(pg);
        return dirtyPg;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // This method is for deleting a specific tuple in the database file

        // Keeps track of dirty pages & pid
        ArrayList<Page> dirtyPgs = new ArrayList<>();
        PageId pid = t.getRecordId().getPageId();

        // Our pid has to refer to an existing page, so we need to check
        // if our page number is less than the total number of pages we have, then
        // this means our tuple is not within the range of pages in our file.
        if (pid.getPageNumber() >= 0 && pid.getPageNumber() < numPages()) {
            // we have to use permissions write only to modify our page for deletion
            HeapPage pg = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
            pg.deleteTuple(t);
            pg.markDirty(true, tid);
            dirtyPgs.add(pg);
        }
        return dirtyPgs;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {

        return new DbFileIterator() {
            private int pageNumber;
            private HeapPageId currentPageId;
            private HeapPage currentPage;
            private Iterator<Tuple> tupleIterator;
            private final BufferPool bufferPool = Database.getBufferPool();

            @Override
            public void open() throws DbException, TransactionAbortedException {
                this.pageNumber = 0;
                updateFields();
            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                if (tupleIterator == null) return false;
                if (tupleIterator.hasNext()) {
                    return true; // tupleIterator can still iterate with the current page
                } else {
                    // we dont want to return true for a page that is entirely empty even if a next page exists so we should check
                    // that within the page, the tupleIterator hasNext before returning true. If it doesn't haveNext()
                    // we continue to check the next page until we find a page with tuples or run out of pages
                    while (pageNumber < numPages() - 1) {
                        this.pageNumber++;
                        updateFields();
                        if (tupleIterator.hasNext()) {
                            return true;
                        }
                    }
                }

                return false;
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (tupleIterator == null || !tupleIterator.hasNext()) throw new NoSuchElementException();
                return tupleIterator.next();
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                // resetting back to 0 and calling updateFields which will reset all the other
                // fields to what it initially should be bc other fields will use pageNumber
                // to set its fields
                this.pageNumber = 0;
                updateFields();
            }

            @Override
            public void close() {
                this.pageNumber = 0;
                this.currentPageId = null;
                this.currentPage = null;
                this.tupleIterator = null;
            }

            private void updateFields() throws TransactionAbortedException, DbException {
                this.currentPageId = new HeapPageId(getId(), pageNumber);
                this.currentPage = (HeapPage) bufferPool.getPage(tid, currentPageId, Permissions.READ_ONLY);
                this.tupleIterator = currentPage.iterator();
            }
        };
    }

}

