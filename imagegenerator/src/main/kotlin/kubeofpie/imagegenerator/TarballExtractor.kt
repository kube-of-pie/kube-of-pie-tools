package kubeofpie.imagegenerator

import jakarta.inject.Singleton
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Streams a `.tar.gz` into a target directory. Validates each entry's path stays
 * inside the target (zip-slip guard), creates intermediate directories, copies
 * regular files, and applies POSIX permissions when the filesystem supports
 * them — on Windows the permission step is silently skipped, which is fine for
 * the kube-of-pie flow since Alpine's `setup-disk` rewrites ownership at first
 * boot on the Pi.
 *
 * Hard / symbolic links present in the archive are skipped: the use cases here
 * (Alpine rpi tarball, headless overlay) do not rely on them, and supporting
 * them across Windows / non-POSIX filesystems would add risk without payoff.
 */
@Singleton
class TarballExtractor {

    fun extract(tarball: Path, outDir: Path) {
        Files.createDirectories(outDir)
        val outRoot = outDir.toAbsolutePath().normalize()

        BufferedInputStream(Files.newInputStream(tarball)).use { buffered ->
            GzipCompressorInputStream(buffered).use { gz ->
                TarArchiveInputStream(gz).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        write(entry, tar, outDir, outRoot)
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }

    private fun write(
        entry: TarArchiveEntry,
        tar: TarArchiveInputStream,
        outDir: Path,
        outRoot: Path,
    ) {
        if (entry.isSymbolicLink || entry.isLink) {
            return
        }
        val name = entry.name.trim('/')
        if (name.isEmpty()) return
        val target = outDir.resolve(name).normalize()
        val targetAbs = target.toAbsolutePath().normalize()
        require(targetAbs.startsWith(outRoot)) {
            "refusing to extract entry escaping target directory: ${entry.name}"
        }

        if (entry.isDirectory) {
            Files.createDirectories(target)
        } else {
            Files.createDirectories(target.parent)
            Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING)
        }
        applyMode(target, entry.mode)
    }

    private fun applyMode(target: Path, mode: Int) {
        val view = Files.getFileAttributeView(
            target,
            java.nio.file.attribute.PosixFileAttributeView::class.java,
            LinkOption.NOFOLLOW_LINKS,
        ) ?: return
        val perms = PosixFilePermissions.fromString(posixString(mode))
        view.setPermissions(perms)
    }

    private fun posixString(mode: Int): String = buildString {
        append(if (mode and 0b100_000_000 != 0) 'r' else '-')
        append(if (mode and 0b010_000_000 != 0) 'w' else '-')
        append(if (mode and 0b001_000_000 != 0) 'x' else '-')
        append(if (mode and 0b000_100_000 != 0) 'r' else '-')
        append(if (mode and 0b000_010_000 != 0) 'w' else '-')
        append(if (mode and 0b000_001_000 != 0) 'x' else '-')
        append(if (mode and 0b000_000_100 != 0) 'r' else '-')
        append(if (mode and 0b000_000_010 != 0) 'w' else '-')
        append(if (mode and 0b000_000_001 != 0) 'x' else '-')
    }
}
