package app.tok.speech

import java.io.File
import java.util.zip.ZipInputStream

object ZipUtil {
    /** Pakt [zip] uit in [destDir] (met pad-traversal-bescherming). */
    fun unzip(zip: File, destDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator) &&
                    outFile.canonicalPath != destDir.canonicalPath
                ) {
                    throw SecurityException("Ongeldige zip-entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
