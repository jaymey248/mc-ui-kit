package dev.jamie.uikit.gradle

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Renames one package in every class of a jar, in place. Nested jars and other files are copied untouched. */
object Relocator {
    fun relocate(jar: File, fromPackage: String, toPackage: String) {
        require(toPackage != fromPackage && !toPackage.startsWith("$fromPackage.")) {
            "relocateTo must be a package of the mod's own, not under $fromPackage: $toPackage"
        }
        val tmp = File(jar.path + ".relocating")
        ZipFile(jar).use { zip ->
            ZipOutputStream(tmp.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val isClass = entry.name.endsWith(".class")
                    val fromPath = fromPackage.replace('.', '/') + "/"
                    val name = if (isClass && entry.name.startsWith(fromPath)) toPackage.replace('.', '/') + "/" + entry.name.removePrefix(fromPath) else entry.name
                    out.putNextEntry(ZipEntry(name).apply { time = entry.time })
                    out.write(if (isClass) relocateClass(bytes, fromPackage, toPackage) else bytes)
                    out.closeEntry()
                }
            }
        }
        Files.move(tmp.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun relocateClass(bytes: ByteArray, fromPackage: String, toPackage: String): ByteArray {
        val fromPath = fromPackage.replace('.', '/') + "/"
        val toPath = toPackage.replace('.', '/') + "/"
        val mapper = object : Remapper(Opcodes.ASM9) {
            override fun map(internalName: String): String =
                if (internalName.startsWith(fromPath)) toPath + internalName.removePrefix(fromPath) else internalName
        }
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(MetadataAware(writer, mapper, fromPackage, toPackage), 0)
        return writer.toByteArray()
    }

    /** Kotlin's @Metadata names classes in plain strings, which a bytecode remap does not see: its string table is rewritten too. */
    private class MetadataAware(next: ClassVisitor, mapper: Remapper, private val from: String, private val to: String) :
        ClassRemapper(Opcodes.ASM9, next, mapper) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            val visitor = super.visitAnnotation(descriptor, visible) ?: return null
            return if (descriptor == "Lkotlin/Metadata;") MetadataStrings(visitor, from, to) else visitor
        }
    }

    private class MetadataStrings(next: AnnotationVisitor, private val from: String, private val to: String) : AnnotationVisitor(Opcodes.ASM9, next) {
        override fun visit(name: String?, value: Any?) {
            super.visit(name, if (value is String && name != "d1") rewrite(value) else value)
        }

        override fun visitArray(name: String?): AnnotationVisitor? {
            val array = super.visitArray(name) ?: return null
            return if (name == "d2") MetadataStrings(array, from, to) else array // d1 is the binary part
        }

        private fun rewrite(s: String) = s
            .replace(from.replace('.', '/') + "/", to.replace('.', '/') + "/")
            .replace("$from.", "$to.")
            .let { if (it == from) to else it }
    }
}
