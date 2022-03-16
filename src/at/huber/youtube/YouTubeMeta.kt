package at.huber.youtube

import at.huber.youtube.Format.Companion.findFormat
import me.anno.io.Streams.readText
import me.anno.io.config.ConfigBasics.cacheFolder
import me.anno.io.json.JsonArray
import me.anno.io.json.JsonNode
import me.anno.io.json.JsonReader
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * youtube sources extractor from https://github.com/HaarigerHarald/android-youtubeExtractor,
 * adjusted to our needs, and a little beautified, where possible
 * @param youtubeLink the youtube page link or video id
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class YouTubeMeta(youtubeLink: String?) {

    companion object {

        var CACHING = true
        var LOGGING = false

        private const val CACHE_FILE_NAME = "decipherJSFunction"
        private val cacheDirPath = cacheFolder
        private var decipherJsFileName: String? = null
        private var decipherFunctions: String? = null
        private var decipherFunctionName: String? = null

        // somebody had issues and changed chrome version to 99.0.4844.51, so if we encounter some, we might try that
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.98 Safari/537.36"
        private const val IMAGE_BASE_URL = "http://i.ytimg.com/vi/"
        private const val WATCH_BASE_URL = "https://youtube.com/watch?v="
        private val patYouTubePageLink =
            Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)")
        private val patYouTubeShortLink = Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)")
        private val patPlayerResponse = Pattern.compile("var ytInitialPlayerResponse\\s*=\\s*(\\{.+?})\\s*;")
        private val patSigEncUrl = Pattern.compile("url=(.+?)(\\u0026|$)")
        private val patSignature = Pattern.compile("s=(.+?)(\\u0026|$)")
        private val patVariableFunction =
            Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(")
        private val patFunction = Pattern.compile("([{; =])([a-zA-Z\$_][a-zA-Z0-9$]{0,2})\\(")
        private val patDecryptionJsFile = Pattern.compile("\\\\/s\\\\/player\\\\/([^\"]+?)\\.js")
        private val patDecryptionJsFileWithoutSlash = Pattern.compile("/s/player/([^\"]+?).js")
        private val patSignatureDecFunction =
            Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{1,4})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)")

        private val LOGGER = LogManager.getLogger(YouTubeMeta::class.java)

        private fun readDecipherFunctionFromCache() {
            val cacheFile = cacheDirPath.getChild(CACHE_FILE_NAME)
            // The cached functions are valid for 2 weeks
            val timeoutMillis = 2 * 7 * 24L * 3600L * 1000L
            if (cacheFile.exists && System.currentTimeMillis() - cacheFile.lastModified < timeoutMillis) {
                try {
                    val lines = cacheFile.readText().split("\n")
                    decipherJsFileName = lines[0]
                    decipherFunctionName = lines[1]
                    decipherFunctions = lines[2]
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun writeDecipherFunctionToCache() {
            try {
                val cacheFile = cacheDirPath.getChild(CACHE_FILE_NAME)
                val parent = cacheFile.getParent()
                parent?.tryMkdirs()
                cacheFile.writeText(
                    listOf(
                        decipherJsFileName,
                        decipherFunctionName,
                        decipherFunctions
                    ).joinToString("\n")
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun hasKey(node: JsonNode, key: String): Boolean {
            return node.get(key) != null
        }

        @Throws(IOException::class)
        private fun loadDataFromURL(urlString: String): String {
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                return urlConnection.inputStream.readText()
                    .replace('\n', ' ')
                    .replace("\r", "")
            } finally {
                urlConnection.disconnect()
            }
        }

        /**
         * the signatures are decoded using decipherFunctions
         * the result is returned as String with \n as a separator
         */
        private fun decipherJavaScript(encSignatures: HashMap<Format, String>, decipherFunctions: String?): String? {

            val functionHead = " function decipher(){return "
            val functionFoot = "};decipher();"
            val separator = "+\"\\n\"+"

            // calculate the length to prevent unnecessary allocations
            var requiredSize = decipherFunctions!!.length + functionHead.length + functionFoot.length
            requiredSize += encSignatures.size * (decipherFunctionName!!.length + 4 + separator.length)
            for ((_, value) in encSignatures) {
                requiredSize += value.length
            }

            // build JavaScript function
            val stb = StringBuilder(requiredSize)
            stb.append(decipherFunctions)
            stb.append(functionHead)
            var i = 0
            for ((_, value) in encSignatures) {
                stb.append(decipherFunctionName)
                stb.append("('")
                stb.append(value)
                stb.append("')")
                if (i++ < encSignatures.size - 1) {
                    stb.append("+\"\\n\"+")
                }
            }
            stb.append(functionFoot)
            val stbString = stb.toString()
            if (LOGGING) LOGGER.debug(stbString)

            // todo it hurts a little to include a while JS engine for such a small thing
            // todo if the functions are simple, like currently, we could implement our own small js engine
            val engine = ScriptEngineManager().getEngineByName("nashorn")
            return try {
                engine.eval(stbString).toString()
            } catch (e: ScriptException) {
                e.printStackTrace()
                null
            }
        }

        @Throws(IOException::class)
        private fun decipherSources(encodedSources: HashMap<Format, String>): String? {
            // Assume the functions don't change that much
            return if (decipherFunctionName == null || decipherFunctions == null) {
                val decipherFunctionUrl = "https://youtube.com$decipherJsFileName"
                if (LOGGING) LOGGER.debug("Decipher FunctionURL: $decipherFunctionUrl")
                val javascriptFile = loadDataFromURL(decipherFunctionUrl)
                var mat = patSignatureDecFunction.matcher(javascriptFile)
                if (mat.find()) {
                    val decipherFunctionName = mat.group(1)
                    val dfn2 = decipherFunctionName.replace("$", "\\$")
                    Companion.decipherFunctionName = decipherFunctionName
                    if (LOGGING) LOGGER.debug("Decipher FunctionName: $decipherFunctionName")
                    val patMainVariable = Pattern.compile(
                        "(var |\\s|,|;)$dfn2(=function\\((.{1,3})\\)\\{)"
                    )
                    val mainDecipherFunction = StringBuilder()
                    mat = patMainVariable.matcher(javascriptFile)
                    if (mat.find()) {
                        mainDecipherFunction.append("var ")
                        mainDecipherFunction.append(decipherFunctionName)
                        mainDecipherFunction.append(mat.group(2))
                    } else {
                        val patMainFunction = Pattern.compile("function $dfn2(\\((.{1,3})\\)\\{)")
                        mat = patMainFunction.matcher(javascriptFile)
                        if (!mat.find()) return null
                        mainDecipherFunction.append("function ")
                        mainDecipherFunction.append(decipherFunctionName)
                        mainDecipherFunction.append(mat.group(2))
                    }
                    var startIndex = mat.end()
                    var braces = 1
                    for (i in startIndex until javascriptFile.length) {
                        if (braces == 0 && i > startIndex + 5) {
                            mainDecipherFunction.append(javascriptFile, startIndex, i)
                            mainDecipherFunction.append(";")
                            break
                        }
                        val charAt = javascriptFile[i]
                        if (charAt == '{') braces++ else if (charAt == '}') braces--
                    }
                    val functionBuilder = StringBuilder(mainDecipherFunction)
                    // Search the main function for extra functions and variables
                    // needed for deciphering
                    // Search for variables
                    mat = patVariableFunction.matcher(mainDecipherFunction)
                    while (mat.find()) {
                        val variableDef = "var ${mat.group(2)}={"
                        if (functionBuilder.indexOf(variableDef) >= 0) continue
                        startIndex = javascriptFile.indexOf(variableDef) + variableDef.length
                        braces = 1
                        // find end of function
                        for (i in startIndex until javascriptFile.length) {
                            if (braces == 0) {
                                functionBuilder.append(variableDef)
                                functionBuilder.append(javascriptFile, startIndex, i)
                                functionBuilder.append(";")
                                break
                            }
                            val charAt = javascriptFile[i]
                            if (charAt == '{') braces++ else if (charAt == '}') braces--
                        }
                    }
                    // Search for functions
                    mat = patFunction.matcher(mainDecipherFunction)
                    while (mat.find()) {
                        val functionDef = "function ${mat.group(2)}("
                        if (functionBuilder.indexOf(functionDef) >= 0) continue
                        startIndex = javascriptFile.indexOf(functionDef) + functionDef.length
                        braces = 0
                        for (i in startIndex until javascriptFile.length) {
                            if (braces == 0 && i > startIndex + 5) {
                                functionBuilder.append(functionDef)
                                functionBuilder.append(javascriptFile, startIndex, i)
                                functionBuilder.append(";")
                                break
                            }
                            val charAt = javascriptFile[i]
                            if (charAt == '{') braces++ else if (charAt == '}') braces--
                        }
                    }
                    decipherFunctions = functionBuilder.toString()
                    if (LOGGING) LOGGER.debug("Decipher Function: $decipherFunctions")
                    val answer = decipherJavaScript(encodedSources, decipherFunctions)
                    if (CACHING) writeDecipherFunctionToCache()
                    answer
                } else null
            } else decipherJavaScript(encodedSources, decipherFunctions)
        }
    }

    var videoId: String? = null
        private set
    var title: String? = null
        private set
    var shortDescription: String? = null
        private set
    var author: String? = null
        private set
    var channelId: String? = null
        private set
    var videoLengthSeconds = 0L
        private set
    var viewCount = 0L
        private set
    var isLiveStream = false
        private set

    /** 120 x 90 */
    val thumbnailUrl
        get() = "$IMAGE_BASE_URL$videoId/default.jpg"

    /** 320 x 180 */
    val mediumQualityImageUrl
        get() = "$IMAGE_BASE_URL$videoId/mqdefault.jpg"

    /** 480 x 360 */
    val highQualityImageUrl
        get() = "$IMAGE_BASE_URL$videoId/hqdefault.jpg"

    /** 640 x 480 */
    val higherQualityImageUrl
        get() = "$IMAGE_BASE_URL$videoId/sddefault.jpg"

    /** maximum resolution */
    val maximumQualityImageUrl
        get() = "$IMAGE_BASE_URL$videoId/maxresdefault.jpg"

    init {
        if (youtubeLink != null) {
            var mat = patYouTubePageLink.matcher(youtubeLink)
            if (mat.find()) {
                videoId = mat.group(3)
            } else {
                mat = patYouTubeShortLink.matcher(youtubeLink)
                if (mat.find()) {
                    videoId = mat.group(3)
                } else if (youtubeLink.matches(Regex("\\p{Graph}+?"))) {
                    videoId = youtubeLink
                }
            }
            if (videoId == null) {
                LOGGER.error("Wrong YouTube link format")
            }
        }
    }

    val links = try {
        loadMetadata(videoId) ?: emptyMap()
    } catch (e: Exception) {
        if (videoId != null) LOGGER.error("Extraction failed", e)
        emptyMap()
    }

    override fun toString(): String {
        return "YouTubeMeta{" +
                "videoId='" + videoId + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", channelId='" + channelId + '\'' +
                ", videoLength=" + videoLengthSeconds +
                ", viewCount=" + viewCount +
                ", isLiveStream=" + isLiveStream +
                ", links=" + links + "}"
    }

    @Throws(UnsupportedEncodingException::class)
    private fun decodeFormats(
        formats: JsonArray,
        sources: HashMap<Format, String>,
        encodedSources: HashMap<Format, String>
    ) {
        val content: List<Any> = formats.content
        for (o in content) {
            val format = o as JsonNode
            // FORMAT_STREAM_TYPE_OTF(otf=1) requires downloading the init fragment (adding `&sq=0` to the URL)
            // and parsing emsg box to determine the number of fragments that
            // would subsequently requested with (`&sq=N`) (cf. youtube-dl)
            val type = format.getText("type")
            if ("FORMAT_STREAM_TYPE_OTF" != type) {
                decodeSource(format, sources, encodedSources)
            }
        }
    }

    @Throws(UnsupportedEncodingException::class)
    private fun decodeSource(
        sourceJson: JsonNode,
        sources: HashMap<Format, String>,
        encodedSources: HashMap<Format, String>
    ) {
        val itag = sourceJson.getInt("itag")
        val format = findFormat(itag)
        if (format != null) {
            val url = sourceJson.getText("url")
            if (url != null) {
                sources[format] = url.replace("\\u0026", "&")
            } else {
                val cipher = sourceJson.getText("signatureCipher")
                if (cipher != null) {
                    val mat = patSigEncUrl.matcher(cipher)
                    val matSig = patSignature.matcher(cipher)
                    if (mat.find() && matSig.find()) {
                        val url1 = URLDecoder.decode(mat.group(1), "UTF-8")
                        val signature = URLDecoder.decode(matSig.group(1), "UTF-8")
                        sources[format] = url1
                        encodedSources[format] = signature
                    }
                }
            }
        } else LOGGER.warn("Unknown format type $itag")
    }

    fun loadMetadata(videoId: String?): HashMap<Format, String>? {
        videoId ?: return null
        val pageHtml = loadDataFromURL(WATCH_BASE_URL + videoId)
        var mat = patPlayerResponse.matcher(pageHtml)
        if (mat.find()) {

            val encodedSources = HashMap<Format, String>()
            val sources = HashMap<Format, String>()

            val jsonSource = mat.group(1)
            val jsonData = JsonReader(jsonSource).readObject()

            val streamingData = jsonData.get("streamingData")
            if (streamingData != null) {
                val formats = streamingData.get("formats")
                if (formats is JsonArray) {
                    decodeFormats(formats, sources, encodedSources)
                } else LOGGER.warn("Missing formats")
                val adaptiveFormats = streamingData.get("adaptiveFormats")
                if (adaptiveFormats is JsonArray) {
                    decodeFormats(adaptiveFormats, sources, encodedSources)
                } else LOGGER.warn("Missing adaptive formats")
            }

            val videoDetails = jsonData.get("videoDetails")
            if (videoDetails != null) {
                // why would we need it again?
                // videoId = getString(videoDetails, "videoId")
                title = videoDetails.getText("title")
                author = videoDetails.getText("author")
                channelId = videoDetails.getText("channelId")
                videoLengthSeconds = videoDetails.getLong("lengthSeconds")
                viewCount = videoDetails.getLong("viewCount")
                isLiveStream = videoDetails.getBool("isLiveContent")
                shortDescription = videoDetails.getText("shortDescription")
            } else LOGGER.warn("Video details is null")

            if (encodedSources.isNotEmpty()) {
                if (CACHING && (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null)) {
                    readDecipherFunctionFromCache()
                }
                mat = patDecryptionJsFile.matcher(pageHtml)
                if (!mat.find()) mat = patDecryptionJsFileWithoutSlash.matcher(pageHtml)
                if (mat.find()) {
                    val curJsFileName = mat.group(0).replace("\\/", "/")
                    if (decipherJsFileName == null || decipherJsFileName != curJsFileName) {
                        decipherFunctions = null
                        decipherFunctionName = null
                    }
                    decipherJsFileName = curJsFileName
                }
                if (LOGGING) LOGGER.debug("Decipher signatures: ${encodedSources.size}, videos: ${sources.size}")
                val signature = decipherSources(encodedSources)
                if (signature != null) {
                    val signatures = signature.split("\n")
                    var index = 0
                    for ((format) in encodedSources) {
                        val url = sources[format].toString() + "&sig=" + signatures[index++]
                        sources[format] = url
                    }
                }
            }
            if (sources.isNotEmpty()) return sources
            else if (LOGGING) LOGGER.debug(pageHtml)
        } else {
            LOGGER.debug("ytPlayerResponse was not found")
            if (LOGGING) LOGGER.debug(pageHtml)
        }
        return null
    }
}