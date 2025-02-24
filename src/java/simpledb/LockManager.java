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
    private Map<TransactionId, HashSet<TransactionId>> tidToWaitingForTid;

    public LockManager() {
        this.tidToPg = new HashMap<>();
        this.pgToTid = new HashMap<>();
        this.pgToPerm = new HashMap<>();
        this.acquireLock = new HashMap<>();
        this.tidToWaitingForTid = new HashMap<>();
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

        // the permissions that are already on the page
        Permissions currPerm = pgToPerm.get(pid);

        // we need to check if the current permissions on the page is READ_ONLY, if so, we need
        // to look deeper!
        if (currPerm == Permissions.READ_ONLY) {
            // Give the lock if the requested permissions are READ_ONLY or if the requesting
            // transaction already holds a lock on the page and there is no more than 1 transaction
            // holding a lock on it
            if (perm == Permissions.READ_ONLY ||
                    (pgToTid.containsKey(pid) && pgToTid.get(pid).contains(tid) && pgToTid.get(pid).size() < 2)) {
                return true;
            } else {
                // check who holds the lock to the requested pg and add it to waitingMap
                Set<TransactionId> tidsHoldingLock = pgToTid.get(pid);
                HashSet<TransactionId> tidsToWaitOn = tidToWaitingForTid.getOrDefault(tid, new HashSet<>());

                tidsToWaitOn.addAll(tidsHoldingLock);
                tidToWaitingForTid.put(tid, tidsToWaitOn);
                return false;
            }
        } else {
                // we give a lock if the requesting transaction already holds a lock on a page
                if (pgToTid.containsKey(pid) && pgToTid.get(pid).contains(tid) && pgToPerm.get(pid).equals(Permissions.READ_WRITE)) {
                    return true;
                } else {
                    // check who holds the lock to the requested pg and add it to waitingMap
                    Set<TransactionId> tidsHoldingLock = pgToTid.get(pid);
                    HashSet<TransactionId> tidsToWaitOn = tidToWaitingForTid.getOrDefault(tid, new HashSet<>());

                    tidsToWaitOn.addAll(tidsHoldingLock);
                    tidToWaitingForTid.put(tid, tidsToWaitOn);
                    return false;
                }
        }
    }

    /*
     * Acquires a lock for a transaction page
     */
    public synchronized boolean acquireLock(TransactionId tid, PageId pid, Permissions perm) throws TransactionAbortedException, InterruptedException {
        // loops until the lock can be acquired
        // Our lockStats method helps check if the lock can be granted based on lock status and
        // permission requests. If the lock can't be done, then it waits until it has been notified
        // that it can proceed!
        while (!lockStats(tid, pid, perm)) {
            if (isDeadlock()) {
                throw new TransactionAbortedException();
            }
            wait();
        }

        // Initializes our set of pages locked by the transaction if not already exists
        if (!tidToPg.containsKey(tid)) {
            tidToPg.put(tid, new HashSet<>());
        }
        // Adds the page to the set of pages locked by our transaction
        tidToPg.get(tid).add(pid);

        // Initializes our set of transactions holding locks on the page if not already exists
         if(!pgToTid.containsKey(pid)) {
             pgToTid.put(pid, new HashSet<>());
         }

        // Adds the transaction to the set of transactions holding locks on their page
        pgToTid.get(pid).add(tid);

        // Updates the permission for our page
        pgToPerm.put(pid, perm);

        // notifies all that they can continue
        notifyAll();
        return true;
    }

    /*
     * Helper function for BufferPool's releasePage() method
     */
    public synchronized boolean releasePage(TransactionId tid, PageId pid) {
        // the pages given tid has locks on
        Set<PageId> pages = tidToPg.get(tid);

        // Checks to see if the transaction has a set of pages it has locked
        // and if it holds the lock on the specific page
        if (pages == null || !pages.contains(pid)) {
            return false;
        }

        // the tids that have locks on this page
        Set<TransactionId> tids = pgToTid.get(pid);

        // Checks to see if the page has a set of transactions holding locks
        // and if the transaction is in that particular set
        if(tids == null || !tids.contains(tid)) {
            return false;
        }

        // we need to remove the page from the set of pages locked by our transaction
        pages.remove(pid);

        // if the transaction does not have any pages locked, then we can remove
        // our transaction
        if (pages.isEmpty()) {
            tidToPg.remove(tid);
        }

        // we need to remove the transaction from the set of transactions holding locks on our page
        tids.remove(tid);

        // if our transactions are holding a lock on the page, then we need
        // to remove the page and its permissions
        if(tids.isEmpty()) {
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

    // releases all locks a given tid has
    public synchronized void releaseAllLocks(TransactionId tid) {
        Set<PageId> pages = tidToPg.get(tid); // pages this tid is locking
        if (pages != null) {
            // making a copy bc in releasePage we remove from tidToPg which causes concurrent exceptions
            Set<PageId> pagesCopy = new HashSet<>(pages);
            for (PageId pid : pagesCopy) {
                // we release all the pages this tid is locking
                releasePage(tid, pid);
            }
        }


        // for all tids that are waiting on other tids before it can get its lock, we remove ourselves from that set
        // of tids
        for (TransactionId tidKey : tidToWaitingForTid.keySet()) {
            if (!tidKey.equals(tid)) {
                tidToWaitingForTid.get(tidKey).remove(tid);
            }
        }

        // we remove ourselves (this tid) from the set of keys bc we're no longer waiting on anything
        tidToWaitingForTid.remove(tid);
    }

    private synchronized boolean isDeadlock() {
        Set<TransactionId> visited = new HashSet<>();
        Set<TransactionId> recursionStack = new HashSet<>();

        for (TransactionId tid : tidToWaitingForTid.keySet()) {
            if (!visited.contains(tid)) {
                if (isCyclic(tid, visited, recursionStack)) {
                    // theres a deadlock
                    return true;
                }
            }
        }

        return false;
    }

    private synchronized boolean isCyclic(TransactionId tid, Set<TransactionId> visited, Set<TransactionId> recursionStack) {
            if (recursionStack.contains(tid)) {
                return true;
            }

            if (visited.contains(tid)) {
                return false;
            }

            recursionStack.add(tid);
            visited.add(tid);

            for (TransactionId tid2 : tidToWaitingForTid.getOrDefault(tid, new HashSet<>())) {
                if (isCyclic(tid2, visited, recursionStack)) {
                    return true;
                }
            }

            recursionStack.remove(tid);
            return false;
    }

}
