package org.jlab.io.hipo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;

/**
 *
 * @author veronique
 */

/**
 * Convert selected banks from a HIPO file into a single NPZ archive.
 *
 * Bank selection:
 *   - no bank selection specified: include all banks found in the HIPO file
 *   - comma-separated bank names: include only those banks
 *   - --bank-file <file>: one bank name per line, '#' comments allowed
 *   - both comma list and --bank-file may be used together
 *
 * Schema JSON selection:
 *   - --schema-dir <dir> is required
 *   - if banks are selected, only JSON files containing at least one selected bank are parsed
 *   - if no banks are selected, all JSON files in the folder are parsed
 *
 * Example: after compiling coatjava, cd coatjava, run the script:
 *   ./bin/hipo2npz input.hipo output.npz --schema-dir /path/to/json
 *
 *   ./bin/hipo2npz input.hipo output.npz --schema-dir /path/to/json BST::adc,RUN::config
 *
 *   ./bin/hipo2npz input.hipo output.npz --schema-dir /path/to/json --bank-file banks.txt
 */
public class Hipo2Npz {

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);

        Map<String, File> schemaIndex = SchemaLoader.indexSchemaFiles(options.schemaDir);
        Set<File> schemaFilesToLoad = SchemaLoader.chooseSchemaFiles(schemaIndex, options.selectedBanks);

        if (schemaFilesToLoad.isEmpty()) {
            throw new IllegalStateException("No schema JSON files selected from " + options.schemaDir.getAbsolutePath());
        }

        Map<String, ColumnType> schemaTypes = SchemaLoader.loadSchemaTypes(schemaFilesToLoad);

        System.out.println("Schema dir      : " + options.schemaDir.getAbsolutePath());
        System.out.println("Schema files    : " + schemaFilesToLoad.size());
        if (options.selectedBanks == null) {
            System.out.println("Selected banks  : ALL");
        } else {
            System.out.println("Selected banks  : " + options.selectedBanks.size());
            for (String b : options.selectedBanks) {
                System.out.println("  " + b);
            }
        }

        Converter converter = new Converter(options.selectedBanks, schemaTypes);
        converter.convert(options.input, options.output, options.numEvents, options.firstEvent);
    }

    // ------------------------------------------------------------------------
    // CLI
    // ------------------------------------------------------------------------

    private static final class CliOptions {
        final File input;
        final File output;
        final File schemaDir;
        final long numEvents;
        final long firstEvent;
        final Set<String> selectedBanks; // null means all banks

        private CliOptions(File input, File output, File schemaDir, long numEvents, long firstEvent, Set<String> selectedBanks) {
            this.input         = input;
            this.output        = output;
            this.schemaDir     = schemaDir;
            this.numEvents     = numEvents;
            this.firstEvent    = firstEvent;
            this.selectedBanks = selectedBanks;
        }

        static CliOptions parse(String[] args) throws Exception {
            if (args.length < 2) {
                printUsageAndExit();
            }

            File input                = new File(args[0]);
            File output               = new File(args[1]);
            File schemaDir            = null;
            long numEvents            = 0;
            long firstEvent           = 0;
            Set<String> selectedBanks = new LinkedHashSet<>();
            boolean selectAll = true;

            for (int i = 2; i < args.length; i++) {
                String arg = args[i];
                if (arg == null || arg.isBlank()) {
                    continue;
                }

                if ("--schema-dir".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--schema-dir requires a directory path");
                    }
                    schemaDir = new File(args[++i]);
                    continue;
                }

                if ("--bank-file".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--bank-file requires a file path");
                    }
                    File bankFile = new File(args[++i]);
                    selectedBanks.addAll(readBankNames(bankFile));
                    selectAll = false;
                    continue;
                }

                if ("--num-events".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--num-events requires a number");
                    }
                    numEvents = Long.parseLong(args[++i]);
                    continue;
                }

                if ("--first-event".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--first-event requires a number");
                    }
                    firstEvent = Long.parseLong(args[++i]);
                    continue;
                }

                if ("*".equals(arg)) {
                    selectedBanks.clear();
                    selectAll = true;
                    continue;
                }

                for (String s : arg.split(",")) {
                    String bank = s.trim();
                    if (!bank.isEmpty()) {
                        selectedBanks.add(bank);
                        selectAll = false;
                    }
                }
            }

            if (schemaDir == null) {
                schemaDir = new File(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
            }
            if (!schemaDir.isDirectory()) {
                throw new IllegalArgumentException("Schema directory not found: " + schemaDir.getAbsolutePath());
            }

            if (!input.exists()) {
                throw new IllegalArgumentException("Input HIPO file not found: " + input.getAbsolutePath());
            }

            return new CliOptions(input, output, schemaDir, numEvents, firstEvent, selectAll ? null : selectedBanks);
        }

        private static Set<String> readBankNames(File bankFile) throws IOException {
            if (!bankFile.exists()) {
                throw new IOException("Bank file not found: " + bankFile.getAbsolutePath());
            }

            Set<String> names = new LinkedHashSet<>();
            for (String line : Files.readAllLines(bankFile.toPath(), StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                names.add(s);
            }
            return names;
        }

        private static void printUsageAndExit() {
            System.err.println("USAGE:");
            System.err.println("  hipo2npz <input.hipo> <output.npz> [OPTIONS...] [bank1,bank2,...|*]");
            System.err.println("");
            System.err.println("Specify a comma-delimited list of banks, otherwise it will use all banks found in the HIPO file");
            System.err.println("");
            System.err.println("OPTIONS:");
            System.err.println("  --bank-file FILE      a file with one bank name per line, '#' comments allowed;");
            System.err.println("                        both comma list and `--bank-file` may be used together");
            System.err.println("  --schema-dir DIR      use a custom schema directory");
            System.err.println("                        default: the one included with this coatjava installation");
            System.err.println("  --num-events NUM      process this many events (default: all)");
            System.err.println("  --first-event NUM     start from this event (default: 0)");
            System.err.println("");
            System.err.println("EXAMPLES:");
            System.err.println("*   hipo2npz input.hipo output.npz");
            System.err.println("*   hipo2npz input.hipo output.npz BST::adc,RUN::config");
            System.err.println("*   hipo2npz input.hipo output.npz --bank-file banks.txt");
            System.exit(2);
        }
    }

    // ------------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------------

    private enum ColumnType {
        BYTE("<i1", 1),
        SHORT("<i2", 2),
        INT("<i4", 4),
        LONG("<i8", 8),
        FLOAT("<f4", 4),
        DOUBLE("<f8", 8);

        final String npyDescr;
        final int byteWidth;

        ColumnType(String npyDescr, int byteWidth) {
            this.npyDescr = npyDescr;
            this.byteWidth = byteWidth;
        }

        static ColumnType fromSchemaCode(String code, String fullName) {
            String s = code.trim().toUpperCase();
            return switch (s) {
                case "B" -> BYTE;
                case "S" -> SHORT;
                case "I" -> INT;
                case "L" -> LONG;
                case "F" -> FLOAT;
                case "D" -> DOUBLE;
                default -> throw new IllegalStateException("Unknown schema type '" + code + "' for " + fullName);
            };
        }
    }

    // ------------------------------------------------------------------------
    // Converter
    // ------------------------------------------------------------------------

    private static final class Converter {
        private final Map<String, BankStore> banks = new LinkedHashMap<>();
        private final Set<String> selectedBanks; // null means all banks
        private final Map<String, ColumnType> schemaTypes; // BANK/COLUMN -> type
        private Path tmpDir;
        private Thread cleanupHook;

        Converter(Set<String> selectedBanks, Map<String, ColumnType> schemaTypes) {
            this.selectedBanks = selectedBanks;
            this.schemaTypes   = schemaTypes;
        }

        void convert(File input, File output, long numEvents, long firstEvent) throws Exception {
            System.out.println(selectedBanks==null ? "Including all banks" : "Including selected banks only");

            File tmpDirFile = new File(output.getPath() + ".tmp");
            tmpDir = createRunDir(tmpDirFile);
            cleanupHook = new Thread(() -> deleteRecursively(tmpDir));
            Runtime.getRuntime().addShutdownHook(cleanupHook);

            HipoDataSource reader = new HipoDataSource();
            reader.open(input);

            long nevRead = 0;
            long nevProc = 0;
            try {
                try {
                    while (reader.hasEvent()) {
                        DataEvent event = reader.getNextEvent();
                        nevRead++;
                        if (nevRead <= firstEvent) continue;
                        ingestEvent(event);
                        nevProc++;
                        if ((nevProc % 10000) == 0) System.out.printf("Processed %,d events%n", nevProc);
                        if (numEvents > 0 && nevProc >= numEvents) break;
                    }
                } finally {
                    reader.close();
                }
                System.out.println("Writing NPZ file...");
                writeNpz(output);
            } finally {
                closeAllQuietly();
                deleteRecursively(tmpDir);
                try {
                    Runtime.getRuntime().removeShutdownHook(cleanupHook);
                } catch (IllegalStateException ignored) {
                    // JVM is already shutting down — the hook itself will run deleteRecursively
                }
            }

            System.out.printf("Wrote %s with %,d events and %,d banks%n", output.getAbsolutePath(), nevProc, banks.size());
        }

        private boolean keepBank(String bankName) {
            return selectedBanks == null || selectedBanks.contains(bankName);
        }

        private void ingestEvent(DataEvent event) throws IOException {
            String[] bankNames = event.getBankList();
            if (bankNames == null) {
                return;
            }

            Map<String, Integer> presentRows = new HashMap<>();

            for (String bankName : bankNames) {
                if (bankName == null || bankName.isBlank()) {
                    continue;
                }
                if (!keepBank(bankName)) {
                    continue;
                }

                DataBank bank = event.getBank(bankName);
                if (bank == null) {
                    continue;
                }

                int rows = bank.rows();
                presentRows.put(bankName, rows);

                BankStore store = banks.computeIfAbsent(bankName, name -> new BankStore(name, tmpDir.toFile()));
                ensureColumns(store, bank);

                String[] cols = bank.getColumnList();
                if (cols != null) {
                    for (String col : cols) {
                        ColumnStore cstore = store.columns.get(col);
                        if (cstore == null) {
                            continue;
                        }
                        for (int r = 0; r < rows; r++) {
                            cstore.append(bank, col, r);
                        }
                    }
                }
            }

            for (BankStore store : banks.values()) {
                int rows = presentRows.getOrDefault(store.bankName, 0);
                store.appendEventRows(rows);
            }
        }

        private void ensureColumns(BankStore store, DataBank bank) throws IOException {
            String[] cols = bank.getColumnList();
            if (cols == null) {
                return;
            }

            for (String col : cols) {
                if (store.columns.containsKey(col)) {
                    continue;
                }
                ColumnType type = discoverColumnType(bank, col);
                store.columns.put(col, new ColumnStore(col, type, tmpDir.toFile()));
            }
        }

        private ColumnType discoverColumnType(DataBank bank, String col) {
            String fullName = bank.getDescriptor().getName() + "/" + col;

            ColumnType schemaType = schemaTypes.get(fullName);
            if (schemaType != null) {
                return schemaType;
            }

            // Fallback to descriptor only if schema map lacks the bank/column.
            Object desc = bank.getDescriptor();
            if (desc == null) {
                throw new IllegalStateException("No descriptor and no schema entry for " + fullName);
            }

            String typeName = tryGetTypeName(desc, col);
            if (typeName != null) {
                String s = typeName.trim();
                if (!s.isEmpty() && !s.equalsIgnoreCase("undefined")) {
                    return mapTypeName(s, fullName);
                }
            }

            Integer typeCode = tryGetTypeCode(desc, col);
            if (typeCode != null) {
                return mapTypeCode(typeCode, fullName);
            }

            throw new IllegalStateException("Could not determine type for " + fullName);
        }

        private Integer tryGetTypeCode(Object desc, String col) {
            try {
                Object v = desc.getClass()
                        .getMethod("getProperty", String.class, String.class)
                        .invoke(desc, "type", col);
                if (v instanceof Number n) {
                    return n.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }

            try {
                Object v = desc.getClass()
                        .getMethod("getProperty", String.class)
                        .invoke(desc, col + ".type");
                if (v instanceof Number n) {
                    return n.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }

            return null;
        }

        private String tryGetTypeName(Object desc, String col) {
            try {
                Object v = desc.getClass()
                        .getMethod("getPropertyString", String.class, String.class)
                        .invoke(desc, "type", col);
                if (v != null) {
                    return v.toString();
                }
            } catch (ReflectiveOperationException ignored) {
            }

            try {
                Object v = desc.getClass()
                        .getMethod("getPropertyString", String.class)
                        .invoke(desc, col + ".type");
                if (v != null) {
                    return v.toString();
                }
            } catch (ReflectiveOperationException ignored) {
            }

            return null;
        }

        private ColumnType mapTypeCode(int t, String fullName) {
            return switch (t) {
                case 1 -> ColumnType.INT;
                case 2 -> ColumnType.FLOAT;
                case 3 -> ColumnType.DOUBLE;
                case 4 -> ColumnType.SHORT;
                case 5 -> ColumnType.BYTE;
                case 6 -> ColumnType.LONG;
                case 8 -> ColumnType.BYTE;
                default -> throw new IllegalStateException("Unknown type code " + t + " for " + fullName);
            };
        }

        private ColumnType mapTypeName(String typeName, String fullName) {
            String s = typeName.trim().toLowerCase();
            return switch (s) {
                case "int", "int32", "i4" -> ColumnType.INT;
                case "float", "float32", "f4" -> ColumnType.FLOAT;
                case "double", "float64", "f8" -> ColumnType.DOUBLE;
                case "short", "int16", "i2" -> ColumnType.SHORT;
                case "byte", "int8", "i1" -> ColumnType.BYTE;
                case "long", "int64", "i8" -> ColumnType.LONG;
                default -> throw new IllegalStateException("Unknown type name '" + typeName + "' for " + fullName);
            };
        }

        private void writeNpz(File output) throws IOException {
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
                zos.setLevel(Deflater.BEST_SPEED);

                for (BankStore bank : banks.values()) {
                    bank.close(); // flush + close the rowsPerEvent/offsets temp-file streams

                    String bankBase = sanitize(bank.bankName);

                    streamEntry(zos, bankBase + "__rows_per_event.npy", "<i4", bank.nEvents,     bank.rowsPerEventFile);
                    streamEntry(zos, bankBase + "__offsets.npy",        "<i8", bank.nEvents + 1, bank.offsetsFile);

                    for (ColumnStore col : bank.columns.values()) {
                        col.close(); // flush + close the column's temp-file stream

                        String entryName = bankBase + "__" + sanitize(col.columnName) + ".npy";
                        streamEntry(zos, entryName, col.type.npyDescr, col.count, col.tempFile);
                    }
                }
            }
        }

        /**
         * Writes one NPY entry into the zip by writing a small header (built once the final
         * element count is known) followed by a streamed copy of the temp file's raw bytes.
         * Never materializes the full column/index array in memory.
         */
        private void streamEntry(ZipOutputStream zos, String name, String descr, long count, File dataFile) throws IOException {
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);

            zos.write(Npy.buildHeader(descr, count));

            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(dataFile), 1 << 16)) {
                in.transferTo(zos);
            }

            zos.closeEntry();
        }

        /**
         * Safety net for any stores that weren't already closed by writeNpz (e.g. because an
         * earlier bank threw partway through). Closing an already-closed stream is a no-op.
         */
        private void closeAllQuietly() {
            for (BankStore bank : banks.values()) {
                closeQuietly(bank);
                for (ColumnStore col : bank.columns.values()) {
                    closeQuietly(col);
                }
            }
        }

        private void closeQuietly(Closeable c) {
            if (c == null) {
                return;
            }
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }

        private String sanitize(String s) {
            return s.replace("::", "__").replace('/', '_').replace(' ', '_');
        }

        private static Path createRunDir(File dir) throws IOException {
            if (dir.exists()) {
                throw new RuntimeException("tmp directory still exists, possibly from a failed previous run: " + dir.getAbsolutePath());
            }
            return Files.createDirectory(dir.toPath());
        }

        private static void deleteRecursively(Path dir) {
            if (dir == null || !Files.exists(dir)) {
                return;
            }
            try (var files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                     .forEach(p -> p.toFile().delete());
            } catch (IOException | UncheckedIOException ignored) {
                // best-effort; nothing more we can do here
            }
        }
    }

    // ------------------------------------------------------------------------
    // Schema loading
    // ------------------------------------------------------------------------

    private static final class SchemaLoader {

        static Map<String, File> indexSchemaFiles(File schemaDir) throws IOException {
            File[] files = schemaDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                throw new IOException("No .json schema files found in " + schemaDir.getAbsolutePath());
            }

            Map<String, File> bankToFile = new HashMap<>();
            for (File file : files) {
                JSONArray root = new JSONArray(Files.readString(file.toPath(), StandardCharsets.UTF_8));

                for (int i = 0; i < root.length(); i++) {
                    JSONObject bankObj = root.optJSONObject(i);
                    if (bankObj == null) {
                        continue;
                    }
                    String bankName = bankObj.optString("name", null);
                    if (bankName != null && !bankName.isBlank()) {
                        bankToFile.put(bankName, file);
                    }
                }
            }
            return bankToFile;
        }

        static Set<File> chooseSchemaFiles(Map<String, File> bankToFile, Set<String> selectedBanks) {
            Set<File> files = new LinkedHashSet<>();

            if (selectedBanks == null) {
                files.addAll(bankToFile.values());
                return files;
            }

            List<String> missing = new ArrayList<>();
            for (String bank : selectedBanks) {
                File file = bankToFile.get(bank);
                if (file != null) {
                    files.add(file);
                } else {
                    missing.add(bank);
                }
            }

            if (!missing.isEmpty()) {
                System.err.println("Warning: no schema JSON file found for selected banks:");
                for (String b : missing) {
                    System.err.println("  " + b);
                }
            }

            return files;
        }

        static Map<String, ColumnType> loadSchemaTypes(Set<File> schemaFiles) throws IOException {
            Map<String, ColumnType> types = new HashMap<>();

            List<File> sorted = new ArrayList<>(schemaFiles);
            sorted.sort(Comparator.comparing(File::getName));

            for (File file : sorted) {
                JSONArray root = new JSONArray(Files.readString(file.toPath(), StandardCharsets.UTF_8));

                for (int i = 0; i < root.length(); i++) {
                    JSONObject bankObj = root.optJSONObject(i);
                    if (bankObj == null) {
                        continue;
                    }

                    String bankName = bankObj.optString("name", null);
                    JSONArray entries = bankObj.optJSONArray("entries");
                    if (bankName == null || entries == null) {
                        continue;
                    }

                    for (int j = 0; j < entries.length(); j++) {
                        JSONObject entryObj = entries.optJSONObject(j);
                        if (entryObj == null) {
                            continue;
                        }

                        String colName = entryObj.optString("name", null);
                        String typeCode = entryObj.optString("type", null);
                        if (colName == null || typeCode == null) {
                            continue;
                        }

                        String fullName = bankName + "/" + colName;
                        types.put(fullName, ColumnType.fromSchemaCode(typeCode, fullName));
                    }
                }
            }

            return types;
        }
    }

    // ------------------------------------------------------------------------
    // BankStore
    // ------------------------------------------------------------------------

    private static final class BankStore implements Closeable {
        final String bankName;
        final Map<String, ColumnStore> columns = new LinkedHashMap<>();
        final File rowsPerEventFile;
        final File offsetsFile;
        private final BufferedOutputStream rowsPerEventOut;
        private final BufferedOutputStream offsetsOut;
        private final ByteBuffer scratch4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        private final ByteBuffer scratch8 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        long nEvents = 0;
        long totalRows = 0;

        BankStore(String bankName, File tmpDir) {
            this.bankName = bankName;
            try {
                // create rows per event temp file
                this.rowsPerEventFile = File.createTempFile("hipo2npz_rpe_", ".bin", tmpDir);
                this.rowsPerEventOut = new BufferedOutputStream(new FileOutputStream(rowsPerEventFile), 1 << 16);
                // create offsets file
                this.offsetsFile = File.createTempFile("hipo2npz_off_", ".bin", tmpDir);
                this.offsetsOut = new BufferedOutputStream(new FileOutputStream(offsetsFile), 1 << 16);
                writeOffset(0L);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void appendEventRows(int rows) throws IOException {
            scratch4.clear();
            scratch4.putInt(rows);
            rowsPerEventOut.write(scratch4.array(), 0, 4);
            nEvents++;
            totalRows += rows;
            writeOffset(totalRows);
        }

        private void writeOffset(long value) throws IOException {
            scratch8.clear();
            scratch8.putLong(value);
            offsetsOut.write(scratch8.array(), 0, 8);
        }

        @Override
        public void close() throws IOException {
            rowsPerEventOut.close();
            offsetsOut.close();
        }
    }

    // ------------------------------------------------------------------------
    // ColumnStore
    // ------------------------------------------------------------------------

    private static final class ColumnStore implements Closeable {
        final String columnName;
        final ColumnType type;
        final File tempFile;
        private final BufferedOutputStream out;
        private final ByteBuffer scratch = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        long count = 0;

        ColumnStore(String columnName, ColumnType type, File tmpDir) throws IOException {
            this.columnName = columnName;
            this.type = type;
            this.tempFile = File.createTempFile("hipo2npz_col_", ".bin", tmpDir);
            this.out = new BufferedOutputStream(new FileOutputStream(tempFile), 1 << 16);
        }

        void append(DataBank bank, String col, int row) throws IOException {
            scratch.clear();
            switch (type) {
                case BYTE   -> scratch.put(bank.getByte(col, row));
                case SHORT  -> scratch.putShort(bank.getShort(col, row));
                case INT    -> scratch.putInt(bank.getInt(col, row));
                case LONG   -> scratch.putLong(bank.getLong(col, row));
                case FLOAT  -> scratch.putFloat(bank.getFloat(col, row));
                case DOUBLE -> scratch.putDouble(bank.getDouble(col, row));
            }
            out.write(scratch.array(), 0, type.byteWidth);
            count++;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    // ------------------------------------------------------------------------
    // NPY writer
    // ------------------------------------------------------------------------

    private static final class Npy {
        private static final byte[] MAGIC = {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'};

        /**
         * Builds just the NPY header bytes for an array of the given dtype and length.
         * The actual array data is streamed separately from a temp file.
         */
        static byte[] buildHeader(String descr, long length) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC);
            out.write(1);
            out.write(0);

            String dict = "{'descr': '" + descr + "', 'fortran_order': False, 'shape': (" + length + ",), }";
            int preamble = MAGIC.length + 2 + 2;
            int padLen = 16 - ((preamble + dict.length() + 1) % 16);
            if (padLen == 16) {
                padLen = 0;
            }

            String fullHeader = dict + " ".repeat(padLen) + "\n";
            byte[] fullHeaderBytes = fullHeader.getBytes(StandardCharsets.US_ASCII);

            ByteBuffer hlen = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
            hlen.putShort((short) fullHeaderBytes.length);
            out.write(hlen.array());
            out.write(fullHeaderBytes);

            return out.toByteArray();
        }
    }
}
