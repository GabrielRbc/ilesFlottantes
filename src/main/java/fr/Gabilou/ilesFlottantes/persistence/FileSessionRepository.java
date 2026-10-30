package fr.Gabilou.ilesFlottantes.persistence;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Fichier versionné avec contrôle SHA-256, remplacement atomique et copie précédente.
 * Une corruption est signalée sans retour silencieux à un état plus ancien.
 * Le marqueur de reset permet de terminer une remise à zéro interrompue.
 */
public final class FileSessionRepository {
    /** Enveloppe vérifiable avant désérialisation des données métier. */
    private record Envelope(int version, String checksum, String payload) { }
    private final Gson gson = new Gson();
    private final Path file;
    public FileSessionRepository(Path file) { this.file = file; }
    public Path backup() { return file.resolveSibling(file.getFileName() + ".bak"); }
    private Path marker() { return file.resolveSibling("reset.pending"); }
    public boolean resetting() { return Files.exists(marker()); }

    public Optional<SessionSnapshot> load() throws IOException {
        if (!Files.exists(file)) {
            if (Files.exists(backup())) throw new IOException();
            return Optional.empty();
        }
        return Optional.of(decode(read(file)));
    }

    private byte[] read(Path path) throws IOException {
        if (Files.size(path) > 32 * 1024 * 1024) throw new IOException();
        return Files.readAllBytes(path);
    }

    private SessionSnapshot decode(byte[] bytes) throws IOException {
        try {
            Envelope envelope = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), Envelope.class);
            if (envelope == null || envelope.version() != 1 || envelope.payload() == null
                    || !digest(envelope.payload()).equals(envelope.checksum())) throw new IllegalArgumentException();
            return Objects.requireNonNull(gson.fromJson(envelope.payload(), SessionSnapshot.class));
        } catch (RuntimeException e) { throw new IOException(e); }
    }

    public void save(SessionSnapshot snapshot) throws IOException {
        String payload = gson.toJson(snapshot);
        byte[] updated = gson.toJson(new Envelope(1, digest(payload), payload)).getBytes(StandardCharsets.UTF_8);
        if (Files.exists(file)) {
            byte[] previous = read(file); decode(previous);
            atomicWrite(backup(), previous);
        }
        atomicWrite(file, updated);
    }

    public void beginReset() throws IOException { atomicWrite(marker(), new byte[]{1}); }
    public void finishReset() throws IOException { Files.deleteIfExists(marker()); }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void atomicWrite(Path file, byte[] data) throws IOException {
        Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "session-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
