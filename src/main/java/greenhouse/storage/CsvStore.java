package greenhouse.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** A small disk-backed CSV store: one record per file, not an in-memory repository. */
public final class CsvStore {
    private final Path directory;

    public CsvStore(Path directory) {
        this.directory = directory;
        try { Files.createDirectories(directory); }
        catch (IOException e) { throw new GreenhouseException("Cannot create " + directory, e); }
    }

    public static void checkId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}"))
            throw new GreenhouseException("ID must use 1-40 letters, numbers, _ or -");
    }

    private Path path(String id) { checkId(id); return directory.resolve(id + ".csv"); }

    public Optional<String[]> read(String id) {
        Path file = path(id);
        if (!Files.exists(file)) return Optional.empty();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) throw new GreenhouseException("Empty CSV: " + file);
            return Optional.of(text.split(",", -1));
        } catch (IOException e) { throw new GreenhouseException("Cannot read " + file, e); }
    }

    public void write(String id, String... fields) {
        Path file = path(id);
        for (String field : fields)
            if (field.contains(",") || field.contains("\n") || field.contains("\r"))
                throw new GreenhouseException("CSV fields must not contain commas or newlines");
        try {
            Path tmp = Files.createTempFile(directory, id + "-", ".tmp");
            try {
                Files.writeString(tmp, String.join(",", fields) + "\n", StandardCharsets.UTF_8);
                try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
        } catch (IOException e) { throw new GreenhouseException("Cannot save " + file, e); }
    }

    public List<String> ids() {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".csv"))
                .map(p -> p.getFileName().toString().replaceFirst("\\.csv$", ""))
                .sorted().toList();
        } catch (IOException e) { throw new GreenhouseException("Cannot list " + directory, e); }
    }
}
