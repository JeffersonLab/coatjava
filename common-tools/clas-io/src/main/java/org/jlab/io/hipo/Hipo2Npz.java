package org.jlab.io.hipo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        converter.convert(options.input, options.output);
    }

    // ------------------------------------------------------------------------
    // CLI
    // ------------------------------------------------------------------------

    private static final class CliOptions {
        final File input;
        final File output;
        final File schemaDir;
        final Set<String> selectedBanks; // null means all banks

        private CliOptions(File input, File output, File schemaDir, Set<String> selectedBanks) {
            this.input = input;
            this.output = output;
            this.schemaDir = schemaDir;
            this.selectedBanks = selectedBanks;
        }

        static CliOptions parse(String[] args) throws Exception {
            if (args.length < 2) {
                printUsageAndExit();
            }

            File input = new File(args[0]);
            File output = new File(args[1]);

            File schemaDir = null;
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

            return new CliOptions(input, output, schemaDir, selectAll ? null : selectedBanks);
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
        BYTE("<i1"),
        SHORT("<i2"),
        INT("<i4"),
        LONG("<i8"),
        FLOAT("<f4"),
        DOUBLE("<f8");

        final String npyDescr;

        ColumnType(String npyDescr) {
            this.npyDescr = npyDescr;
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

        Converter(Set<String> selectedBanks, Map<String, ColumnType> schemaTypes) {
            this.selectedBanks = selectedBanks;
            this.schemaTypes = schemaTypes;
        }

        void convert(File input, File output) throws Exception {
            if (selectedBanks == null) {
                System.out.println("Including all banks");
            } else {
                System.out.println("Including selected banks only");
            }

            HipoDataSource reader = new HipoDataSource();
            reader.open(input);

            long nEvents = 0;
            while (reader.hasEvent()) {
                DataEvent event = reader.getNextEvent();
                nEvents++;
                ingestEvent(event);

                if ((nEvents % 10000) == 0) {
                    System.out.printf("Processed %,d events%n", nEvents);
                }
            }
            reader.close();

            writeNpz(output);
            System.out.printf("Wrote %s with %,d events and %,d banks%n",
                    output.getAbsolutePath(), nEvents, banks.size());
        }

        private boolean keepBank(String bankName) {
            return selectedBanks == null || selectedBanks.contains(bankName);
        }

        private void ingestEvent(DataEvent event) {
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

                BankStore store = banks.computeIfAbsent(bankName, BankStore::new);
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

        private void ensureColumns(BankStore store, DataBank bank) {
            String[] cols = bank.getColumnList();
            if (cols == null) {
                return;
            }

            for (String col : cols) {
                if (store.columns.containsKey(col)) {
                    continue;
                }
                ColumnType type = discoverColumnType(bank, col);
                store.columns.put(col, new ColumnStore(store.bankName, col, type));
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
                    String bankBase = sanitize(bank.bankName);

                    addEntry(zos, bankBase + "__rows_per_event.npy",
                            Npy.writeIntArray(bank.rowsPerEvent.toArray(), "<i4"));
                    addEntry(zos, bankBase + "__offsets.npy",
                            Npy.writeLongArray(bank.offsets.toArray(), "<i8"));

                    for (ColumnStore col : bank.columns.values()) {
                        String entryName = bankBase + "__" + sanitize(col.columnName) + ".npy";
                        addEntry(zos, entryName, col.toNpyBytes());
                    }
                }
            }
        }

        private void addEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
            ZipEntry entry = new ZipEntry(name);
            entry.setSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
        }

        private String sanitize(String s) {
            return s.replace("::", "__").replace('/', '_').replace(' ', '_');
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
                Object root = Json.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                if (!(root instanceof List<?> banks)) {
                    continue;
                }

                for (Object bankObj : banks) {
                    if (!(bankObj instanceof Map<?, ?> bankMap)) {
                        continue;
                    }
                    Object nameObj = bankMap.get("name");
                    if (nameObj instanceof String bankName && !bankName.isBlank()) {
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
                Object root = Json.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                if (!(root instanceof List<?> banks)) {
                    continue;
                }

                for (Object bankObj : banks) {
                    if (!(bankObj instanceof Map<?, ?> bankMap)) {
                        continue;
                    }

                    Object nameObj = bankMap.get("name");
                    Object entriesObj = bankMap.get("entries");
                    if (!(nameObj instanceof String bankName) || !(entriesObj instanceof List<?> entries)) {
                        continue;
                    }

                    for (Object entryObj : entries) {
                        if (!(entryObj instanceof Map<?, ?> entryMap)) {
                            continue;
                        }

                        Object entryNameObj = entryMap.get("name");
                        Object entryTypeObj = entryMap.get("type");
                        if (!(entryNameObj instanceof String colName) || !(entryTypeObj instanceof String typeCode)) {
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
    // Stores
    // ------------------------------------------------------------------------

    private static final class BankStore {
        final String bankName;
        final Map<String, ColumnStore> columns = new LinkedHashMap<>();
        final IntList rowsPerEvent = new IntList();
        final LongList offsets = new LongList();
        long totalRows = 0;

        BankStore(String bankName) {
            this.bankName = bankName;
            this.offsets.add(0L);
        }

        void appendEventRows(int rows) {
            rowsPerEvent.add(rows);
            totalRows += rows;
            offsets.add(totalRows);
        }
    }

    private static final class ColumnStore {
        final String bankName;
        final String columnName;
        final ColumnType type;
        final ByteList bytes;
        final ShortList shorts;
        final IntList ints;
        final LongList longs;
        final FloatList floats;
        final DoubleList doubles;

        ColumnStore(String bankName, String columnName, ColumnType type) {
            this.bankName = bankName;
            this.columnName = columnName;
            this.type = type;
            this.bytes = type == ColumnType.BYTE ? new ByteList() : null;
            this.shorts = type == ColumnType.SHORT ? new ShortList() : null;
            this.ints = type == ColumnType.INT ? new IntList() : null;
            this.longs = type == ColumnType.LONG ? new LongList() : null;
            this.floats = type == ColumnType.FLOAT ? new FloatList() : null;
            this.doubles = type == ColumnType.DOUBLE ? new DoubleList() : null;
        }

        void append(DataBank bank, String col, int row) {
            switch (type) {
                case BYTE -> bytes.add(bank.getByte(col, row));
                case SHORT -> shorts.add(bank.getShort(col, row));
                case INT -> ints.add(bank.getInt(col, row));
                case LONG -> longs.add(bank.getLong(col, row));
                case FLOAT -> floats.add(bank.getFloat(col, row));
                case DOUBLE -> doubles.add(bank.getDouble(col, row));
            }
        }

        byte[] toNpyBytes() throws IOException {
            return switch (type) {
                case BYTE -> Npy.writeByteArray(bytes.toArray(), type.npyDescr);
                case SHORT -> Npy.writeShortArray(shorts.toArray(), type.npyDescr);
                case INT -> Npy.writeIntArray(ints.toArray(), type.npyDescr);
                case LONG -> Npy.writeLongArray(longs.toArray(), type.npyDescr);
                case FLOAT -> Npy.writeFloatArray(floats.toArray(), type.npyDescr);
                case DOUBLE -> Npy.writeDoubleArray(doubles.toArray(), type.npyDescr);
            };
        }
    }

    // ------------------------------------------------------------------------
    // Minimal JSON parser
    // ------------------------------------------------------------------------

    private static final class Json {
        static Object parse(String text) {
            return new Parser(text).parseValue();
        }

        private static final class Parser {
            private final String s;
            private int i = 0;

            Parser(String s) {
                this.s = s;
            }

            Object parseValue() {
                skipWs();
                if (i >= s.length()) {
                    throw new IllegalStateException("Unexpected end of JSON");
                }

                char c = s.charAt(i);
                return switch (c) {
                    case '{' -> parseObject();
                    case '[' -> parseArray();
                    case '"' -> parseString();
                    case 't' -> parseTrue();
                    case 'f' -> parseFalse();
                    case 'n' -> parseNull();
                    default -> parseNumber();
                };
            }

            private Map<String, Object> parseObject() {
                expect('{');
                Map<String, Object> obj = new LinkedHashMap<>();
                skipWs();
                if (peek('}')) {
                    expect('}');
                    return obj;
                }

                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    expect(':');
                    Object value = parseValue();
                    obj.put(key, value);
                    skipWs();
                    if (peek('}')) {
                        expect('}');
                        break;
                    }
                    expect(',');
                }
                return obj;
            }

            private List<Object> parseArray() {
                expect('[');
                List<Object> arr = new ArrayList<>();
                skipWs();
                if (peek(']')) {
                    expect(']');
                    return arr;
                }

                while (true) {
                    arr.add(parseValue());
                    skipWs();
                    if (peek(']')) {
                        expect(']');
                        break;
                    }
                    expect(',');
                }
                return arr;
            }

            private String parseString() {
                expect('"');
                StringBuilder sb = new StringBuilder();
                while (i < s.length()) {
                    char c = s.charAt(i++);
                    if (c == '"') {
                        return sb.toString();
                    }
                    if (c == '\\') {
                        if (i >= s.length()) {
                            throw new IllegalStateException("Bad escape");
                        }
                        char e = s.charAt(i++);
                        switch (e) {
                            case '"', '\\', '/' -> sb.append(e);
                            case 'b' -> sb.append('\b');
                            case 'f' -> sb.append('\f');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'u' -> {
                                if (i + 4 > s.length()) {
                                    throw new IllegalStateException("Bad unicode escape");
                                }
                                String hex = s.substring(i, i + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            }
                            default -> throw new IllegalStateException("Bad escape: \\" + e);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                throw new IllegalStateException("Unterminated string");
            }

            private Object parseNumber() {
                int start = i;
                if (s.charAt(i) == '-') {
                    i++;
                }
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
                boolean isFloat = false;
                if (i < s.length() && s.charAt(i) == '.') {
                    isFloat = true;
                    i++;
                    while (i < s.length() && Character.isDigit(s.charAt(i))) {
                        i++;
                    }
                }
                if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    isFloat = true;
                    i++;
                    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                        i++;
                    }
                    while (i < s.length() && Character.isDigit(s.charAt(i))) {
                        i++;
                    }
                }

                String num = s.substring(start, i);
                return isFloat ? Double.parseDouble(num) : Long.parseLong(num);
            }

            private Boolean parseTrue() {
                expect('t'); expect('r'); expect('u'); expect('e');
                return Boolean.TRUE;
            }

            private Boolean parseFalse() {
                expect('f'); expect('a'); expect('l'); expect('s'); expect('e');
                return Boolean.FALSE;
            }

            private Object parseNull() {
                expect('n'); expect('u'); expect('l'); expect('l');
                return null;
            }

            private void skipWs() {
                while (i < s.length()) {
                    char c = s.charAt(i);
                    if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                        i++;
                    } else {
                        break;
                    }
                }
            }

            private boolean peek(char c) {
                skipWs();
                return i < s.length() && s.charAt(i) == c;
            }

            private void expect(char c) {
                skipWs();
                if (i >= s.length() || s.charAt(i) != c) {
                    throw new IllegalStateException("Expected '" + c + "' at position " + i);
                }
                i++;
            }
        }
    }

    // ------------------------------------------------------------------------
    // NPY writer
    // ------------------------------------------------------------------------

    private static final class Npy {
        private static final byte[] MAGIC = {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'};

        static byte[] writeByteArray(byte[] values, String descr) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeader(out, descr, values.length);
            out.write(values);
            return out.toByteArray();
        }

        static byte[] writeShortArray(short[] values, String descr) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeader(out, descr, values.length);
            ByteBuffer bb = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short v : values) bb.putShort(v);
            out.write(bb.array());
            return out.toByteArray();
        }

        static byte[] writeIntArray(int[] values, String descr) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeader(out, descr, values.length);
            ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int v : values) bb.putInt(v);
            out.write(bb.array());
            return out.toByteArray();
        }

        static byte[] writeLongArray(long[] values, String descr) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeader(out, descr, values.length);
            ByteBuffer bb = ByteBuffer.allocate(values.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long v : values) bb.putLong(v);
            out.write(bb.array());
            return out.toByteArray();
        }

        static byte[] writeFloatArray(float[] values, String descr) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeader(out, descr, values.length);
            ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float v : values) bb.putFloat(v);
            out.write(bb.array());
            return out.toByteArray();
        }

        static byte[] writeDoubleArray(double[] values, String descr) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeader(out, descr, values.length);
            ByteBuffer bb = ByteBuffer.allocate(values.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (double v : values) bb.putDouble(v);
            out.write(bb.array());
            return out.toByteArray();
        }

        private static void writeHeader(ByteArrayOutputStream out, String descr, int length) throws IOException {
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
        }
    }

    // ------------------------------------------------------------------------
    // Primitive dynamic arrays
    // ------------------------------------------------------------------------

    private static final class ByteList {
        private byte[] data = new byte[1024];
        private int size = 0;
        void add(byte v) { ensure(size + 1); data[size++] = v; }
        byte[] toArray() { return Arrays.copyOf(data, size); }
        private void ensure(int n) { if (n > data.length) data = Arrays.copyOf(data, Math.max(n, data.length * 2)); }
    }

    private static final class ShortList {
        private short[] data = new short[1024];
        private int size = 0;
        void add(short v) { ensure(size + 1); data[size++] = v; }
        short[] toArray() { return Arrays.copyOf(data, size); }
        private void ensure(int n) { if (n > data.length) data = Arrays.copyOf(data, Math.max(n, data.length * 2)); }
    }

    private static final class IntList {
        private int[] data = new int[1024];
        private int size = 0;
        void add(int v) { ensure(size + 1); data[size++] = v; }
        int[] toArray() { return Arrays.copyOf(data, size); }
        private void ensure(int n) { if (n > data.length) data = Arrays.copyOf(data, Math.max(n, data.length * 2)); }
    }

    private static final class LongList {
        private long[] data = new long[1024];
        private int size = 0;
        void add(long v) { ensure(size + 1); data[size++] = v; }
        long[] toArray() { return Arrays.copyOf(data, size); }
        private void ensure(int n) { if (n > data.length) data = Arrays.copyOf(data, Math.max(n, data.length * 2)); }
    }

    private static final class FloatList {
        private float[] data = new float[1024];
        private int size = 0;
        void add(float v) { ensure(size + 1); data[size++] = v; }
        float[] toArray() { return Arrays.copyOf(data, size); }
        private void ensure(int n) { if (n > data.length) data = Arrays.copyOf(data, Math.max(n, data.length * 2)); }
    }

    private static final class DoubleList {
        private double[] data = new double[1024];
        private int size = 0;
        void add(double v) { ensure(size + 1); data[size++] = v; }
        double[] toArray() { return Arrays.copyOf(data, size); }
        private void ensure(int n) { if (n > data.length) data = Arrays.copyOf(data, Math.max(n, data.length * 2)); }
    }
}
