package simpledb;

import javax.xml.crypto.Data;
import java.io.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;
    private int numPages;

    private HashMap<PageId, Page> idToPg;
    private LockManager lockManager;

    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        this.numPages = numPages;
        this.idToPg = new HashMap<>();
        this.lockManager = new LockManager();
    }

    public static int getPageSize() {
      return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        try {
            // lockManager.acquireLock checks to see if the lock can be given based on the current
            // lock status and requested permissions
            // if our lock can't be acquired right now, then it has to wait until it is notified (in LockManager.java)
            // so it can proceed.
            while (!lockManager.acquireLock(tid, pid, perm)) {
                wait();
            }
        } catch (InterruptedException e) {
            throw new TransactionAbortedException();
        }

        if (idToPg.get(pid) == null) {

            // If not enough space evict page!
            if (idToPg.size() >= numPages) {
                evictPage();
            }

            // buffer pool has space, so we can add our new pages to the buffer pool!
            // we can access the DbFile by using the DbFile.readPage method!
            idToPg.put(pid, Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid));
        }
        return idToPg.get(pid);
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        lockManager.releasePage(tid, pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        return lockManager.holdsLock(tid, p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        // we're committing so we want to flush dirty pages associated with the transaction
        if (commit) {
            flushPages(tid);
        // we're aborting so we want to revert any changes made by the transaction
        } else {
            // we check every page in bufferPool, if it is dirty due to this transaction we replace it in bufferPool
            // with the on-disk page to restore it
            for (PageId pid : idToPg.keySet()) {
                HeapPage page = (HeapPage) idToPg.get(pid);
                if (page == null) continue;
                if (page.isDirty() == tid) {
                    idToPg.put(pid, Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid));
                }
            }
        }

        // regardless of commit or abort, we want to release any locks BufferPool was managing regarding
        // this transaction so we check if a page holds a lock relating to this tid, and if it does, release it
        for (PageId pid : idToPg.keySet()) {
            if (holdsLock(tid, pid)) {
                releasePage(tid, pid);
            }
        }
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // Helps us perform insertion of our tuple into the table with tableId
        DbFile file = Database.getCatalog().getDatabaseFile(tableId);
        // Keeps track of dirty pages that were modified in our HeapPage
        ArrayList<Page> dirtyPgs = file.insertTuple(tid, t);

        // Looping through our dirty pages
        for (Page pg : dirtyPgs) {
            // We need to mark the dirty pages that has been modified back to our disk with its tid
            pg.markDirty(true, tid);
            // Now we need to add our dirty pages back to our hashmap so that our cache
            idToPg.put(pg.getId(), pg);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        int tableId = t.getRecordId().getPageId().getTableId();
        DbFile file = Database.getCatalog().getDatabaseFile(tableId);
        ArrayList<Page> dirtyPgs = file.deleteTuple(tid, t);

        // Looping through dirty pages
        for (Page pg: dirtyPgs) {
            // We need to check first if our buffer pool is full and if the page
            // we're currently looking at is not already in our pool. If so,
            // we need to evict the page from the buffer pool to make space for
            // the new page. This is purely for optimization!!
            if (idToPg.size() == numPages && !idToPg.containsKey(pg.getId())) {
                evictPage();
            }

            // similar scenario as our insertTuple() previously
            pg.markDirty(true, tid);
            idToPg.put(pg.getId(), pg);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        for (PageId pid : idToPg.keySet()) {
            HeapPage page = (HeapPage) idToPg.get(pid);
            if (page == null) continue;
            if (page.isDirty() != null) flushPage(pid);
        }

    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.

        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        Page pg = idToPg.get(pid);

        // Have to check if the page is dirty
        // If the page is not dirty, we don't need to flush it to the disk
        // so we can just return!
        if (pg.isDirty() == null) {
            return;
        }

        // We have to check if the page is null. This will help us know
        // that the page actually exists, so if it is null we can just return!
        if (pg == null) {
            return;
        }

        // We can now mark the page as not dirty since it has been flushed to disk
        // and is no longer modified in our memory.
        pg.markDirty(false, null);
        // Write the page back to the disk
        Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(pg);
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // for every page in bufferPool, we check if the page is dirty due to this transaction and if it is
        // we flush the page to disk
        for (PageId pid : idToPg.keySet()) {
            HeapPage page = (HeapPage) idToPg.get(pid);
            if (page == null) continue;
            if (page.isDirty() == tid ) flushPage(pid);
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        PageId pidToEvict = null;

        // Looping through our buffer pool to find a clean page to evict.
        for (PageId pid : idToPg.keySet()) {
            Page pg = idToPg.get(pid);

            // We need to check if the page is not dirty, as soon as we do
            // we're going to exit the loop and continue on!
            if (pg.isDirty() == null) {
                pidToEvict = pid;
                break;
            }
        }

        // if there are no clean pages, we need to throw an exception to tell us
        // that all pages in the buffer pool are dirty and needs to flush before
        // we evict
        if (pidToEvict == null) {
            throw new DbException("All pages are dirty, we cannot evict");
        }

        // Flushing the page selected to disk
        try {
            flushPage(pidToEvict);
        } catch (Exception e) {
            throw new DbException("Error flushing page during eviction!");
        }

        // Offically, remove the page from the buffer pool to
        // make space for our new pages!!
        idToPg.remove(pidToEvict);
    }

}
