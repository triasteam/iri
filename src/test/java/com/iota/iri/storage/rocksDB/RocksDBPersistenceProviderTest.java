package com.iota.iri.storage.rocksDB;

import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.utils.Pair;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RocksDBPersistenceProviderTest {

    private static RocksDBPersistenceProvider rocksDBPersistenceProvider;
    private static String dbPath = "tmpdb", dbLogPath = "tmplogs";

    @BeforeClass
    public static void setUpDb() throws Exception {
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
               dbPath, dbLogPath,1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        rocksDBPersistenceProvider.init();
    }

    @AfterClass
    public static void destroyDb() {
        rocksDBPersistenceProvider.shutdown();
        FileUtils.deleteQuietly(new File(dbPath));
        FileUtils.deleteQuietly(new File(dbLogPath));
        rocksDBPersistenceProvider = null;
    }

    @Before
    public void setUp() throws Exception {
        rocksDBPersistenceProvider.clear(Transaction.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDeleteBatch() throws Exception {
        Persistable tx = new Transaction();
        byte[] bytes = new byte[Transaction.SIZE];
        Arrays.fill(bytes, (byte) 1);
        tx.read(bytes);
        tx.readMetadata(bytes);
        List<Pair<Indexable, Persistable>> models = IntStream.range(1, 1000)
                .mapToObj(i -> new Pair<>((Indexable) new IntegerIndex(i), tx))
                .collect(Collectors.toList());

        rocksDBPersistenceProvider.saveBatch(models);

        List<Pair<Indexable, ? extends Class<? extends Persistable>>> modelsToDelete = models.stream()
                .filter(entry -> ((IntegerIndex) entry.low).getValue() < 900)
                .map(entry -> new Pair<>(entry.low, entry.hi.getClass()))
                .collect(Collectors.toList());

        rocksDBPersistenceProvider.deleteBatch(modelsToDelete);

        for (Pair<Indexable, ? extends Class<? extends Persistable>> model : modelsToDelete) {
            Assert.assertNull("value at index " + ((IntegerIndex) model.low).getValue() + " should be deleted",
                    rocksDBPersistenceProvider.get(model.hi, model.low).bytes());
        }

        List<IntegerIndex> indexes = IntStream.range(900, 1000)
                .mapToObj(i -> new IntegerIndex(i))
                .collect(Collectors.toList());

        for (IntegerIndex index : indexes) {
            Assert.assertArrayEquals("saved bytes are not as expected in index " + index.getValue(), tx.bytes(),
                    rocksDBPersistenceProvider.get(Transaction.class, index).bytes());
        }
    }

    @Test
    public void testTxnCount() {
        long oldCount = rocksDBPersistenceProvider.getTotalTxns();
        long number = 100;
        rocksDBPersistenceProvider.addTxnCount(number);
        long newCount = rocksDBPersistenceProvider.getTotalTxns();
        Assert.assertEquals(oldCount + number, newCount);
    }

    @Test
    public void testStore(){
        Hash h1 = IotaUtils.getRandomTransactionHash();
        Hash h2 = IotaUtils.getRandomTransactionHash();
        Hash h3 = IotaUtils.getRandomTransactionHash();
        Hash h4 = IotaUtils.getRandomTransactionHash();
        Stack<Hash> stack = new Stack<>();
        stack.push(h1);
        stack.push(h2);
        stack.push(h3);
        stack.push(h4);

        rocksDBPersistenceProvider.storeAncestors(stack);
        Stack<Hash> obj = rocksDBPersistenceProvider.getAncestors();
        assert obj.equals(stack);
    }
}