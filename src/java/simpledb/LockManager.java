package simpledb;

import java.util.*;

/*
 * This is mainly a helper class for BufferPool for lock managing
 *
 * Descriptor of BufferPool's responsibility for locking:
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 */
public class LockManager {
    private Map<TransactionId, Set<PageId>> tidToPg;
    private Map<PageId, Set<TransactionId>> pgToTid;
    private Map<PageId, Permissions> pgToPerm;
    private Map<TransactionId, PageId> acquireLock;

    public LockManager() {
        this.tidToPg = new HashMap<>();
        this.pgToTid = new HashMap<>();
        this.pgToPerm = new HashMap<>();
        this.acquireLock = new HashMap<>();
    }

    /*
     * Checks if a transaction can grab the desired lock on a page
     */
    public synchronized boolean lockStats(TransactionId tid, PageId pid, Permissions perm) {
        // Checks to see if the page has a lock
        // If the page does not have any locks then the lock can be given to the
        // requesting transaction!
        if (!pgToPerm.containsKey(pid)) {
            return true;
        }

        // we need to check if the current permissions on the page is READ_ONLY, if so, we need
        // to look deeper!
        if (pgToPerm.get(pid).equals(Permissions.READ_ONLY)) {
            // If the requesting permissions are also READ_ONLY, then the lock can be done
            if(perm.equals(Permissions.READ_ONLY)) {
                return true;
            } else {
                // If our pgToTid has the pid and at least two transactions hold the lock, then
                // to upgrade would not be possible. We are also checking to see if the requesting
                // transaction holds the READ_ONLY lock as well
                return !(pgToTid.containsKey(pid) && pgToTid.get(pid).size() >= 2) && pgToTid.get(pid).contains(tid);
            }
        } else {
            // Our current lock is READ_WRITE
            // we need to check if the requesting transaction holds the lock,
            // if so the lock can be given if the requesting transaction already holds
            // the READ_ONLY lock on the page.
            return pgToTid.get(pid).contains(tid);
        }

    }

    /*
     * Acquires a lock for a transaction page
     */
    public synchronized boolean acquireLock(TransactionId tid, PageId pid, Permissions perm) throws TransactionAbortedException {
        // loops until the lock can be acquired
        // Our lockStats method helps check if the lock can be granted based on lock status and
        // permission requests. If the lock can't be done, then it waits until it has been notified
        // that it can proceed!
        while (!lockStats(tid, pid, perm)) {
            try {
                // waiting for lock to be acquired...
                wait();
            } catch (InterruptedException e) {
                throw new TransactionAbortedException();
            }
        }

        // Initializes our set of pages locked by the transaction if not already exists
        if (!tidToPg.containsKey(tid)) {
            tidToPg.put(tid, new HashSet<>());
        }
        // Initializes our set of transactions holding locks on the page if not already exists
         if(!pgToTid.containsKey(pid)) {
             pgToTid.put(pid, new HashSet<>());
         }

         // Updates the permission for our page
         pgToPerm.put(pid, perm);
         // Adds the transaction to the set of transactions holding locks on their page
         pgToTid.get(pid).add(tid);
         // Adds the page to the set of pages locked by our transaction
         tidToPg.get(tid).add(pid);
         // notifies all that they can continue
         notifyAll();
         return true;
    }

    /*
     * Helper function for BufferPool's releasePage() method
     */
    public synchronized boolean releasePage(TransactionId tid, PageId pid) {
        // Checks to see if the transaction has a set of pages it has locked
        // and if it holds the lock on the specific page
        if (!tidToPg.containsKey(tid) || !tidToPg.get(tid).contains(pid)) {
            return false;
        }

        // Checks to see if the page has a set of transactions holding locks
        // and if the transaction is in that particular set
        if(!pgToTid.containsKey(pid) || !pgToTid.get(pid).contains(tid)) {
            return false;
        }

        // we need to remove the page from the set of pages locked by our transaction
        tidToPg.get(tid).remove(pid);

        // if the transaction does not have any pages locked, then we can remove
        // our transaction
        if (tidToPg.get(tid).isEmpty()) {
            tidToPg.remove(tid);
        }
        // we need to remove the transaction from the set of transactions holding locks on our page
        pgToTid.get(pid).remove(tid);

        // if our transactions are holding a lock on the page, then we need
        // to remove the page and its permissions
        if(pgToTid.get(pid).isEmpty()) {
            pgToTid.remove(pid);
            pgToPerm.remove(pid);
        }
        // notifies all that they can continue!
        notifyAll();
        return true;
    }

    /*
     * Helper function for the BufferPool's holdsLock() method
     */
    public synchronized boolean holdsLock(TransactionId tid, PageId pid) {
        // we need to check if the page has a set of transactions holding locks AND if the
        // specific transaction is in that particular set
        boolean pgHasTransactionLock = pgToTid.containsKey(pid) && pgToTid.get(pid).contains(tid);

        // we also need to check if the transaction has a set of pages it has locked AND if it holds
        // the lock on a specific page
        boolean transactionHasPgLock = tidToPg.containsKey(tid) && tidToPg.get(tid).contains(pid);

        // returns true if the transaction holds a lock on the page!!
        return pgHasTransactionLock && transactionHasPgLock;
    }

}
