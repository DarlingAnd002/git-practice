package shop;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** 同一数据目录不能同时启动两个程序。 */
final class StoreLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    StoreLock(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = channel.tryLock(); }
        catch (IOException | OverlappingFileLockException e) {
            channel.close();
            throw new IOException("商店已在运行或无法锁定数据目录。", e);
        }
        if (acquired == null) {
            channel.close();
            throw new IOException("商店已在运行，请先退出另一窗口中的商店程序。");
        }
        lock = acquired;
    }
    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
