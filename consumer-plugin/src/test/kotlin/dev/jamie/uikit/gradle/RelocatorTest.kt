package dev.jamie.uikit.gradle

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class RelocatorTest {
    /** A class in [name] whose one field has type [fieldType] and which carries a Kotlin @Metadata naming it. */
    private fun classBytes(name: String, fieldType: String): ByteArray {
        val w = ClassWriter(0)
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        w.visitAnnotation("Lkotlin/Metadata;", true).apply {
            visit("pn", "dev.jamie.uikit.core")
            visitArray("d1").apply { visit(null, "dev/jamie/uikit/binary"); visitEnd() }
            visitArray("d2").apply { visit(null, "L$fieldType;"); visit(null, "plain"); visitEnd() }
            visitEnd()
        }
        w.visitField(Opcodes.ACC_PUBLIC, "f", "L$fieldType;", null, null).visitEnd()
        w.visitEnd()
        return w.toByteArray()
    }

    private fun ascii(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    @Test fun renamesClassesAndReferencesAndMetadata(@TempDir dir: File) {
        val nested = byteArrayOf(1, 2, 3, 4)
        val jar = File(dir, "mod.jar")
        ZipOutputStream(jar.outputStream()).use { z ->
            fun put(name: String, b: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(b); z.closeEntry() }
            put("dev/jamie/uikit/ui/Canvas.class", classBytes("dev/jamie/uikit/ui/Canvas", "java/lang/String"))
            put("dev/me/Mod.class", classBytes("dev/me/Mod", "dev/jamie/uikit/ui/Canvas"))
            put("META-INF/jars/lib.jar", nested)
            put("fabric.mod.json", "{}".toByteArray())
        }

        Relocator.relocate(jar, "dev.jamie.uikit", "dev.me.uikit")

        ZipFile(jar).use { z ->
            assertEquals(
                setOf("dev/me/uikit/ui/Canvas.class", "dev/me/Mod.class", "META-INF/jars/lib.jar", "fabric.mod.json"),
                z.entries().asSequence().map { it.name }.toSet(),
            )
            assertArrayEquals(nested, z.getInputStream(z.getEntry("META-INF/jars/lib.jar")).readBytes())
            val mod = ascii(z.getInputStream(z.getEntry("dev/me/Mod.class")).readBytes())
            assert("Ldev/me/uikit/ui/Canvas;" in mod) { "field type and @Metadata d2 are renamed" }
            assert("dev.me.uikit.core" in mod) { "@Metadata pn is renamed" }
            assert("dev/jamie/uikit/binary" in mod) { "@Metadata d1 is binary and stays" }
            assertFalse("Ldev/jamie/uikit/" in mod)
        }
    }

    @Test fun refusesATargetInsideTheKitsOwnPackage(@TempDir dir: File) {
        val jar = File(dir, "m.jar")
        assertThrows(IllegalArgumentException::class.java) { Relocator.relocate(jar, "dev.jamie.uikit", "dev.jamie.uikit.mine") }
    }
}
