package me.anno.io.files

import me.anno.cache.instances.LastModifiedCache
import me.anno.io.windows.WindowsShortcut
import me.anno.io.zip.ZipCache
import me.anno.studio.StudioBase
import me.anno.utils.Tabs
import me.anno.utils.files.Files.openInExplorer
import me.anno.utils.files.LocalFile.toLocalPath
import me.anno.utils.types.Strings.isBlank2
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.apache.logging.log4j.LogManager
import java.io.*
import java.lang.ref.WeakReference
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

// todo when a file is changed, all inner files based on that need to be invalidated (editor only)
// todo when a file is changed, the meta data of it needs to be invalidated
// todo only allocate each inner file once: create a static store of weak references

/**
 * doesn't call toLowerCase() for each comparison,
 * so it's hopefully a lot faster
 *
 * we don't modify files a lot, but we do use them for comparisons a lot
 * because of that, this "performance-wrapper" exists
 *
 * also this can be used to navigate to "pseudo"-files, like files inside zip containers,
 * files on the web, or local resources
 * */
abstract class FileReference(val absolutePath: String) {

    // done if there is a !!, it's into a zip file -> it only needs to be a slash;
    // all zip files should be detected automatically
    // done if res:// at the start, then it's a local resource
    // todo other protocols as well, so like an URI replacement?

    companion object {

        private val LOGGER = LogManager.getLogger(FileReference::class)

        private val staticReferences = HashMap<String, FileReference>()

        private val allReferences = ConcurrentHashMap<String, WeakReference<FileReference>>()

        /**
         * removes old references
         * needs to be called regularly
         * */
        fun updateCache() {
            //allReferences.values.removeIf { it.get() == null }
        }

        fun registerStatic(ref: FileReference): FileReference {
            staticReferences[ref.absolutePath] = ref
            return ref
        }

        fun invalidate(file: String, fileInstance: File = File(file)) {
            allReferences.remove(
                file.replace('\\', '/')
            )?.get()?.invalidate()
            LastModifiedCache.invalidate(fileInstance)
        }

        fun invalidate(file: File) {
            invalidate(file.absolutePath, file)
        }

        fun register(file: FileReference): FileReference {
            // println("registered $file")
            synchronized(allReferences) {
                val str = file.absolutePath
                val cached = allReferences[str]?.get()
                if (cached != null) return cached
                allReferences[str] = WeakReference(file)
                return file
            }
        }

        fun getReference(str: String?): FileReference {
            // invalid
            if (str == null || str.isBlank2()) return InvalidRef
            // root
            if (str == "null") return FileRootRef
            synchronized(allReferences) {
                val cached = allReferences[str]?.get()
                if (cached != null) return cached
                val value = createReference(str)
                allReferences[str] = WeakReference(value)
                return value
            }
        }

        private fun createReference(str: String): FileReference {
            // internal resource
            if (str.startsWith(BundledRef.prefix)) return BundledRef.parse(str)
            // static references
            val static = staticReferences[str]
            if (static != null) return static
            // real or compressed files
            // check whether it exists -> easy then :)
            val file0 = File(str)
            if (file0.exists()) return FileFileRef(file0)
            // split by /, and check when we need to enter a zip file
            val parts = str.trim()
                .replace('\\', '/')
                .split('/')
            // binary search? let's do linear first
            for (i in parts.lastIndex downTo 0) {
                val substr = parts.subList(0, i).joinToString("/")
                val fileI = File(substr)
                if (fileI.exists()) {
                    // great :), now go into that file
                    return appendPath(fileI, i, parts)
                }
            }
            // somehow, we could not find the correct file
            // it probably just is new
            return FileFileRef(file0)
        }

        fun appendPath(parent: String, name: String): String {
            return if (parent.isBlank2()) name
            else "$parent/$name"
        }

        fun appendPath(fileI: File, i: Int, parts: List<String>): FileReference {
            var ref: FileReference = FileFileRef(fileI)
            for (j in i until parts.size) {
                ref = ref.getChild(parts[j])
                if (ref == InvalidRef) return ref
            }
            return ref
        }

        fun getReference(file: File?): FileReference {
            return getReference(file?.toString())
        }

        fun getReference(parent: File, name: String): FileReference {
            return getReference(getReference(parent), name)
        }

        fun getReference(parent: FileReference?, name: String): FileReference {
            var result = parent ?: return InvalidRef
            val name1 = name.replace('\\', '/')
            if ('/' !in name1) {
                return result.getChild(name)
            } else {
                val parts = name1.split('/')
                for (partialName in parts) {
                    if (!partialName.isBlank2()) {
                        result = if (partialName == "..") {
                            result.getParent()
                        } else {
                            result.getChild(partialName)
                        } ?: return InvalidRef
                    }
                }
                return result
            }
        }

        fun createZipFile(file: FileReference): ZipFile {
            return if (file is FileFileRef) ZipFile(file.file) else
                ZipFile(SeekableInMemoryByteChannel(file.inputStream().readBytes()))
        }

    }

    abstract fun getChild(name: String): FileReference

    open fun hasChildren(): Boolean = listChildren()?.isNotEmpty() == true

    private var isValid = true

    open fun invalidate() {
        println("invalidated $absolutePath")
        isValid = false
        // if this has inner folders, replace all of their children as well
        ZipCache.getMetaMaybe(this)?.invalidate()
    }

    fun validate(): FileReference {
        return if (isValid) this else getReference(absolutePath)
    }

    /*constructor() : this("")
    constructor(parent: File, name: String) : this(File(parent, name))
    constructor(parent: FileReference, name: String) : this(File(parent.file, name))

    constructor(str: String) : this(File(str))*/

    // val nameWithoutExtension = file.nameWithoutExtension
    // val extension = file.extension

    val name: String
    val nameWithoutExtension: String
    val extension: String
    val lcExtension: String // the extension is often required in lowercase, so we cache it here

    init {
        val lastIndex = absolutePath.lastIndexOf('/')
        name = if (lastIndex < 0) {
            absolutePath
        } else absolutePath.substring(lastIndex + 1)
        val extIndex = name.lastIndexOf('.')
        if (extIndex < 0) {
            extension = ""
            lcExtension = ""
            nameWithoutExtension = name
        } else {
            extension = name.substring(extIndex + 1)
            lcExtension = extension.lowercase()
            nameWithoutExtension = name.substring(0, extIndex)
        }
    }

    val hashCode = absolutePath.hashCode()

    val hasValidName = !absolutePath.isBlank2()
    fun hasValidName() = hasValidName

    /**
     * give an access to an input stream
     * should be buffered for better performance
     * */
    abstract fun inputStream(): InputStream

    /**
     * give an access to an output stream
     * should be buffered for better performance
     * */
    abstract fun outputStream(): OutputStream

    open fun readText() = String(readBytes())
    open fun readText(charset: Charset) = String(readBytes(), charset)

    open fun readBytes() = inputStream().readBytes()
    fun readByteBuffer(): ByteBuffer {
        return ByteBuffer.wrap(readBytes())
    }

    open fun writeText(text: String) {
        val os = outputStream()
        val wr = OutputStreamWriter(os)
        wr.write(text)
        wr.close()
        os.close()
    }

    open fun writeText(text: String, charset: Charset) {
        val os = outputStream()
        val wr = OutputStreamWriter(os, charset)
        wr.write(text)
        wr.close()
        os.close()
    }

    open fun writeBytes(bytes: ByteArray) {
        val os = outputStream()
        os.write(bytes)
        os.close()
    }

    open fun writeBytes(bytes: ByteBuffer) {
        val byte2 = ByteArray(bytes.remaining())
        bytes.get(byte2)
        if (!exists || length() != byte2.size.toLong() || !readBytes().contentEquals(byte2)) {
            writeBytes(byte2)
        }
    }

    abstract fun length(): Long

    // fun length() = if (isInsideCompressed) zipFile?.size ?: 0L else file.length()
    fun openInExplorer() = File(absolutePath.replace("!!", "/")).openInExplorer()

    abstract fun delete(): Boolean
    abstract fun mkdirs(): Boolean

    open fun deleteOnExit() {
        deleteRecursively()
    }

    open fun deleteRecursively(): Boolean {
        return delete()
    }

    val zipFileForDirectory
        get(): FileReference? {
            var zipFile = zipFile ?: return null
            if (!zipFile.isDirectory) {
                zipFile = ZipCache.getMeta(zipFile, false) ?: return null
            }
            return zipFile
        }

    private val zipFile get() = ZipCache.getMeta(this, false)

    abstract fun getParent(): FileReference?

    fun getSibling(name: String): FileReference {
        return getParent()?.getChild(name) ?: InvalidRef
    }

    fun renameTo(newName: File) = renameTo(getReference(newName))
    abstract fun renameTo(newName: FileReference): Boolean

    abstract val isDirectory: Boolean

    open fun isSerializedFolder(): Boolean {
        // only read the first bytes
        return when (val signature = Signature.findName(this)) {
            "zip", "rar", "7z", "bz2", "lz4", "xar", "oar", "gzip", "tar" -> {
                LOGGER.info("Checking $absolutePath for zip file, matches signature")
                true
            }
            "pdf" -> true
            // todo all mesh extensions
            "fbx", "vox", "obj", "mtl", "gltf", "dae", "yaml", "blend", "draco",
            "md2", "md5mesh" -> {
                LOGGER.info("Checking $absolutePath for mesh file, matches signature")
                true
            }
            "png", "jpg", "bmp", "pds", "hdr", "webp", "ico", "tga", "dds" -> {
                LOGGER.info("Checking $absolutePath for image file, matches signature")
                true
            }
            null, "xml", "json", "media" -> return try {// maybe something unknown, that we understand anyways
                // dae is xml
                when (lcExtension) {
                    "fbx", "vox", "obj", "mtl", "gltf", "glb", "dae", "blend",
                    "mat", "prefab", "unity", "asset", "controller", "json" -> {
                        LOGGER.info("Checking $absolutePath for mesh file, matches extension")
                        true
                    }
                    "png", "jpg", "bmp", "pds", "hdr", "webp", "tga", "dds" -> {
                        LOGGER.info("Checking $absolutePath for image file, matches extension")
                        true
                    }
                    else -> {
                        val zis = createZipFile(this)
                        val result = zis.entries.hasMoreElements()
                        LOGGER.info("Checking $absolutePath for zip file, success")
                        result
                    }
                }
            } catch (e: IOException) {
                LOGGER.info("Checking $absolutePath for zip file, ${e.message}")
                false
            }
            else -> {
                LOGGER.info("Checking $absolutePath for zip file, other signature: $signature")
                false
            }
        }
    }

    abstract val exists: Boolean
    abstract val lastModified: Long
    abstract val lastAccessed: Long

    abstract fun toUri(): URI

    override fun equals(other: Any?): Boolean {
        return other is FileReference && other.hashCode == hashCode && other.absolutePath == absolutePath
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return absolutePath
    }

    @Deprecated(
        "This function only is defined, if the reference is an actual file",
        ReplaceWith("inputStream(),outputStream()")
    )

    val unsafeFile
        get() = (this as FileFileRef).file

    fun toLocalPath(workspace: FileReference? = StudioBase.workspace): String {
        return absolutePath.toLocalPath(workspace)
    }

    val windowsLnk: Lazy<WindowsShortcut?> = lazy {
        if (lcExtension == "lnk" && WindowsShortcut.isPotentialValidLink(this)) {
            try {
                WindowsShortcut(this)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }

    open val isSomeKindOfDirectory get() = isDirectory || windowsLnk.value != null || isPacked.value

    val isPacked = lazy {
        !isDirectory && isSerializedFolder()
    }

    open fun listChildren(): List<FileReference>? {
        // LOGGER.info("listing children of $this, lnk: ${windowsLnk.value}")
        val link = windowsLnk.value ?: return null
        // if the file is not a directory, then list the parent?
        // todo mark this child somehow?...
        val str = link.realFilename.replace('\\', '/')
        val ref = getReference(str)
        return listOf(
            if (link.isDirectory) {
                ref.getParent() ?: ref
            } else ref
        )
    }

    open fun nullIfUndefined(): FileReference? = this

    inline fun anyInHierarchy(run: (FileReference) -> Boolean): Boolean {
        var element = this
        while (element != InvalidRef) {
            if (run(this)) return true
            element = element.getParent() ?: return false
        }
        return false
    }

    private fun printTree(depth: Int = 0) {
        LOGGER.info("${Tabs.spaces(depth * 2)}$name")
        if (isDirectory) {
            for (child in listChildren() ?: return) {
                child.printTree(depth + 1)
            }
        }
    }

    // todo support for ffmpeg to read all zip files

}