# Database System
## Overview
This project is a database management system written in Java. It implements fundamental database system components such as buffer pool management, transaction processing, locking management, and basic file storage. This project simulates how real world database systems manage memory and ensures transaction correctness

## Features
- Buffer Pool Caching: Caches database pages in memory to reduce disk I/O
- Page Eviction: Automatically evicts pages when the buffer pool is full and prioritizes clean pages.
- Transaction Management: Supports commiting and aborting transactions with logging for durability and rollback
- Lock Management: Ensures safe access to pages by using shared and exclusive locks
- Logging and Recovery: Logs changes to support transaction
- Tuple Insertion and Deletion: Allows transactions to add and remove tuples from tables safelt

## Project Structure
This project is organized into several main classes:
| File/Folder | Description |
|:---|:---|
| BufferPool.java | Manages reading, caching, and evicting pages from the database. |
| LockManager.java | Handles transaction locks to ensure database consistency. |
| Catalog.java | Manages tables and their schemas. |
| Page.java | Interface that represents a page stored in the database. |
| HeapFile.java | Implementation of a database file storing data as unordered pages. |
| TransactionId.java | Uniquely identifies each transaction. |
| HeapPage.java | Represents a page of data in a heap file, supporting tuple operations. |
| Insert.java | Inserts tuples from a child operator into a specified table. |
| Join.java | Implements the relational join operation using a nested loop join. |
| RecordId.java | Represents a reference to a specific tuple on a specific page in a table. |
| SeqScan.java | Implements a sequential scan to read tuples from a table in no particular order. |
| Tuple.java | Represents a tuple with a schema (TupleDesc) and fields (Field), providing methods for accessing and manipulating field values. |
| TupleDesc.java | Describes the schema of a tuple, including field types and names, and provides methods to access and manipulate field details. |
