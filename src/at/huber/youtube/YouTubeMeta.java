package at.huber.youtube;

import me.anno.io.config.ConfigBasics;
import me.anno.io.files.FileReference;
import me.anno.io.json.JsonArray;
import me.anno.io.json.JsonNode;
import me.anno.io.json.JsonReader;
import me.anno.io.json.JsonValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * youtube sources extractor from https://github.com/HaarigerHarald/android-youtubeExtractor,
 * adjusted to our needs, and a little beautified, where possible
 */
@SuppressWarnings("unused")
public class YouTubeMeta {

    static boolean CACHING = true;
    static boolean LOGGING = false;

    private final static String CACHE_FILE_NAME = "decipherJSFunction";

    private String videoID;
    private static final FileReference cacheDirPath = ConfigBasics.INSTANCE.getCacheFolder();

    private static String decipherJsFileName;
    private static String decipherFunctions;
    private static String decipherFunctionName;

    // somebody had issues and changed chrome version to 99.0.4844.51, so if we encounter some, we might try that
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.98 Safari/537.36";

    private static final Pattern patYouTubePageLink = Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)");
    private static final Pattern patYouTubeShortLink = Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)");

    private static final Pattern patPlayerResponse = Pattern.compile("var ytInitialPlayerResponse\\s*=\\s*(\\{.+?})\\s*;");
    private static final Pattern patSigEncUrl = Pattern.compile("url=(.+?)(\\u0026|$)");
    private static final Pattern patSignature = Pattern.compile("s=(.+?)(\\u0026|$)");

    private static final Pattern patVariableFunction = Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(");
    private static final Pattern patFunction = Pattern.compile("([{; =])([a-zA-Z$_][a-zA-Z0-9$]{0,2})\\(");

    private static final Pattern patDecryptionJsFile = Pattern.compile("\\\\/s\\\\/player\\\\/([^\"]+?)\\.js");
    private static final Pattern patDecryptionJsFileWithoutSlash = Pattern.compile("/s/player/([^\"]+?).js");
    private static final Pattern patSignatureDecFunction = Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{1,4})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)");

    private static final Format[] FORMATS = new Format[316];

    static {

        // http://en.wikipedia.org/wiki/YouTube#Quality_and_formats

        // Video and Audio
        FORMATS[17] = new Format("3gp", 144, VideoCodec.MPEG4, AudioCodec.AAC, 24, false);
        FORMATS[36] = new Format("3gp", 240, VideoCodec.MPEG4, AudioCodec.AAC, 32, false);
        FORMATS[5] = new Format("flv", 240, VideoCodec.H263, AudioCodec.MP3, 64, false);
        FORMATS[43] = new Format("webm", 360, VideoCodec.VP8, AudioCodec.VORBIS, 128, false);
        FORMATS[18] = new Format("mp4", 360, VideoCodec.H264, AudioCodec.AAC, 96, false);
        FORMATS[22] = new Format("mp4", 720, VideoCodec.H264, AudioCodec.AAC, 192, false);

        // Dash Video
        FORMATS[160] = new Format("mp4", 144, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[133] = new Format("mp4", 240, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[134] = new Format("mp4", 360, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[135] = new Format("mp4", 480, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[136] = new Format("mp4", 720, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[137] = new Format("mp4", 1080, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[264] = new Format("mp4", 1440, VideoCodec.H264, AudioCodec.NONE, true);
        FORMATS[266] = new Format("mp4", 2160, VideoCodec.H264, AudioCodec.NONE, true);

        FORMATS[298] = new Format("mp4", 720, VideoCodec.H264, 60, AudioCodec.NONE, true);
        FORMATS[299] = new Format("mp4", 1080, VideoCodec.H264, 60, AudioCodec.NONE, true);

        // Dash Audio
        FORMATS[140] = new Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 128, true);
        FORMATS[141] = new Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 256, true);
        FORMATS[256] = new Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 192, true);
        FORMATS[258] = new Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 384, true);

        // WEBM Dash Video
        FORMATS[278] = new Format("webm", 144, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[242] = new Format("webm", 240, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[243] = new Format("webm", 360, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[244] = new Format("webm", 480, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[247] = new Format("webm", 720, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[248] = new Format("webm", 1080, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[271] = new Format("webm", 1440, VideoCodec.VP9, AudioCodec.NONE, true);
        FORMATS[313] = new Format("webm", 2160, VideoCodec.VP9, AudioCodec.NONE, true);

        FORMATS[302] = new Format("webm", 720, VideoCodec.VP9, 60, AudioCodec.NONE, true);
        FORMATS[308] = new Format("webm", 1440, VideoCodec.VP9, 60, AudioCodec.NONE, true);
        FORMATS[303] = new Format("webm", 1080, VideoCodec.VP9, 60, AudioCodec.NONE, true);
        FORMATS[315] = new Format("webm", 2160, VideoCodec.VP9, 60, AudioCodec.NONE, true);

        // WEBM Dash Audio
        FORMATS[171] = new Format("webm", VideoCodec.NONE, AudioCodec.VORBIS, 128, true);

        FORMATS[249] = new Format("webm", VideoCodec.NONE, AudioCodec.OPUS, 48, true);
        FORMATS[250] = new Format("webm", VideoCodec.NONE, AudioCodec.OPUS, 64, true);
        FORMATS[251] = new Format("webm", VideoCodec.NONE, AudioCodec.OPUS, 160, true);

        // HLS Live Stream
        FORMATS[91] = new Format("mp4", 144, VideoCodec.H264, AudioCodec.AAC, 48, false, true);
        FORMATS[92] = new Format("mp4", 240, VideoCodec.H264, AudioCodec.AAC, 48, false, true);
        FORMATS[93] = new Format("mp4", 360, VideoCodec.H264, AudioCodec.AAC, 128, false, true);
        FORMATS[94] = new Format("mp4", 480, VideoCodec.H264, AudioCodec.AAC, 128, false, true);
        FORMATS[95] = new Format("mp4", 720, VideoCodec.H264, AudioCodec.AAC, 256, false, true);
        FORMATS[96] = new Format("mp4", 1080, VideoCodec.H264, AudioCodec.AAC, 256, false, true);
    }

    private static final String IMAGE_BASE_URL = "http://i.ytimg.com/vi/";

    private String title;
    private String shortDescription;

    private String author;
    private String channelId;

    private long videoLength;
    private long viewCount;

    private boolean isLiveStream;

    // 120 x 90
    public String getThumbnailUrl() {
        return IMAGE_BASE_URL + videoID + "/default.jpg";
    }

    // 320 x 180
    public String getMediumQualityImageUrl() {
        return IMAGE_BASE_URL + videoID + "/mqdefault.jpg";
    }

    // 480 x 360
    public String getHighQualityImageUrl() {
        return IMAGE_BASE_URL + videoID + "/hqdefault.jpg";
    }

    // 640 x 480
    public String getHigherQualityImageUrl() {
        return IMAGE_BASE_URL + videoID + "/sddefault.jpg";
    }

    // Max Res
    public String getMaximumQualityImageUrl() {
        return IMAGE_BASE_URL + videoID + "/maxresdefault.jpg";
    }

    public String getVideoId() {
        return videoID;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getChannelId() {
        return channelId;
    }

    public boolean isLiveStream() {
        return isLiveStream;
    }

    /**
     * The video length in seconds.
     */
    public long getVideoLength() {
        return videoLength;
    }

    public long getViewCount() {
        return viewCount;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    @Override
    public String toString() {
        return "YouTubeMeta{" +
                "videoId='" + videoID + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", channelId='" + channelId + '\'' +
                ", videoLength=" + videoLength +
                ", viewCount=" + viewCount +
                ", isLiveStream=" + isLiveStream +
                ", links=" + links + "}";
    }

    private HashMap<Format, String> links;

    public HashMap<Format, String> getLinks() {
        return links;
    }

    /**
     * Start the extraction.
     *
     * @param youtubeLink the youtube page link or video id
     */
    public YouTubeMeta(String youtubeLink) {

        videoID = null;

        if (youtubeLink != null) {

            Matcher mat = patYouTubePageLink.matcher(youtubeLink);
            if (mat.find()) {
                videoID = mat.group(3);
            } else {
                mat = patYouTubeShortLink.matcher(youtubeLink);
                if (mat.find()) {
                    videoID = mat.group(3);
                } else if (youtubeLink.matches("\\p{Graph}+?")) {
                    videoID = youtubeLink;
                }
            }

            if (videoID != null) {
                try {
                    links = getStreamUrls();
                } catch (Exception e) {
                    LOGGER.error("Extraction failed", e);
                }
            } else {
                LOGGER.error("Wrong YouTube link format");
            }

        }
    }

    private boolean hasKey(JsonNode node, String key) {
        return node.get(key) != null;
    }

    private String getString(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (!(child instanceof JsonValue)) {
            return null;
        }
        JsonValue value = (JsonValue) child;
        Object something = value.getValue();
        return something == null ? null : something.toString();
    }

    private boolean getBoolean(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (!(child instanceof JsonValue)) {
            return false;
        }
        JsonValue value = (JsonValue) child;
        Object something = value.getValue();
        if (something == Boolean.TRUE) return true;
        return something instanceof String && "true".equalsIgnoreCase((String) something);
    }

    private int getInt(JsonNode node, String key) {
        JsonNode child = node.get(key);
        if (!(child instanceof JsonValue)) {
            return 0;
        }
        JsonValue value = (JsonValue) child;
        Object something = value.getValue();
        if (something instanceof Integer) return (Integer) something;
        if (something instanceof Long) return ((Long) something).intValue();
        if (something instanceof Float) return ((Float) something).intValue();
        if (something instanceof Double) return ((Double) something).intValue();
        if (something instanceof String) return Integer.parseInt((String) something);
        return 0;
    }

    private HashMap<Format, String> getStreamUrls() throws IOException {

        String pageHtml = readFile("https://youtube.com/watch?v=" + videoID);


        Matcher mat = patPlayerResponse.matcher(pageHtml);
        if (mat.find()) {

            HashMap<Format, String> encSignatures = new HashMap<>();
            HashMap<Format, String> ytFiles = new HashMap<>();

            String jsonSource = mat.group(1);

            JsonNode ytPlayerResponse = new JsonReader(jsonSource).readObject();
            JsonNode streamingData = ytPlayerResponse.get("streamingData");

            if (streamingData != null) {

                JsonNode formats = streamingData.get("formats");
                if (formats instanceof JsonArray) {
                    decodeFormats((JsonArray) formats, ytFiles, encSignatures);
                } else LOGGER.warn("Formats is null");

                JsonNode adaptiveFormats = streamingData.get("adaptiveFormats");
                if (adaptiveFormats instanceof JsonArray) {
                    decodeFormats((JsonArray) adaptiveFormats, ytFiles, encSignatures);
                } else LOGGER.warn("Adaptive formats is null");

            }

            JsonNode videoDetails = ytPlayerResponse.get("videoDetails");
            if (videoDetails != null) {
                videoID = getString(videoDetails, "videoId");
                title = getString(videoDetails, "title");
                author = getString(videoDetails, "author");
                channelId = getString(videoDetails, "channelId");
                String lengthSeconds = getString(videoDetails, "lengthSeconds");
                if (lengthSeconds != null) videoLength = Long.parseLong(lengthSeconds);
                String viewCount2 = getString(videoDetails, "viewCount");
                if (viewCount2 != null) viewCount = Long.parseLong(viewCount2);
                isLiveStream = getBoolean(videoDetails, "isLiveContent");
                shortDescription = getString(videoDetails, "shortDescription");
            } else LOGGER.warn("video details is null");


            if (encSignatures.size() > 0) {

                if (CACHING && (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null)) {
                    readDecipherFunctionFromCache();
                }

                mat = patDecryptionJsFile.matcher(pageHtml);
                if (!mat.find())
                    mat = patDecryptionJsFileWithoutSlash.matcher(pageHtml);
                if (mat.find()) {
                    final String curJsFileName = mat.group(0).replace("\\/", "/");
                    if (decipherJsFileName == null || !decipherJsFileName.equals(curJsFileName)) {
                        decipherFunctions = null;
                        decipherFunctionName = null;
                    }
                    decipherJsFileName = curJsFileName;
                }

                if (LOGGING) {
                    LOGGER.debug("Decipher signatures: " + encSignatures.size() + ", videos: " + ytFiles.size());
                }

                final String signature = decipherSignature(encSignatures);
                if (signature == null) {
                    return null;
                } else {
                    String[] signatures = signature.split("\n");
                    int index = 0;
                    for (Map.Entry<Format, String> entry : encSignatures.entrySet()) {
                        Format format = entry.getKey();
                        String url = ytFiles.get(format) + "&sig=" + signatures[index++];
                        ytFiles.put(format, url);
                    }
                }
            }

            if (ytFiles.size() == 0) {
                if (LOGGING) LOGGER.debug(pageHtml);
                return null;
            }

            return ytFiles;

        } else {
            LOGGER.debug("ytPlayerResponse was not found");
            if (LOGGING) LOGGER.debug(pageHtml);
            return null;
        }
    }

    private void decodeFormats(JsonArray formats, HashMap<Format, String> ytFiles, HashMap<Format, String> encSignatures) throws UnsupportedEncodingException {
        List<Object> content = formats.getContent();
        for (Object o : content) {
            JsonNode format = (JsonNode) o;
            // FORMAT_STREAM_TYPE_OTF(otf=1) requires downloading the init fragment (adding
            // `&sq=0` to the URL) and parsing emsg box to determine the number of fragments that
            // would subsequently requested with (`&sq=N`) (cf. youtube-dl)
            String type = getString(format, "type");
            if (!"FORMAT_STREAM_TYPE_OTF".equals(type)) {
                decodeFormat(format, ytFiles, encSignatures);
            }
        }
    }

    private void decodeFormat(JsonNode format, HashMap<Format, String> ytFiles, HashMap<Format, String> encSignatures) throws UnsupportedEncodingException {
        final int itag = getInt(format, "itag");
        if (itag >= 0 && itag < FORMATS.length) {
            final Format format1 = FORMATS[itag];
            if (hasKey(format, "url")) {
                String url = getString(format, "url");
                assert url != null;
                url = url.replace("\\u0026", "&");
                ytFiles.put(format1, url);
            } else if (hasKey(format, "signatureCipher")) {
                String cipher = getString(format, "signatureCipher");
                if (cipher != null) {
                    Matcher mat = patSigEncUrl.matcher(cipher);
                    Matcher matSig = patSignature.matcher(cipher);
                    if (mat.find() && matSig.find()) {
                        String url = URLDecoder.decode(mat.group(1), "UTF-8");
                        String signature = URLDecoder.decode(matSig.group(1), "UTF-8");
                        ytFiles.put(format1, url);
                        encSignatures.put(format1, signature);
                    }
                }
            }
        }
    }

    private String readFile(String url0) throws IOException {
        BufferedReader reader = null;
        String textBlock;
        URL url = new URL(url0);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append(' ');
            }
            textBlock = sb.toString();
        } finally {
            if (reader != null)
                reader.close();
            urlConnection.disconnect();
        }
        return textBlock;
    }

    private String decipherSignature(final HashMap<Format, String> encSignatures) throws IOException {
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {

            String decipherFunctionUrl = "https://youtube.com" + decipherJsFileName;
            if (LOGGING) LOGGER.debug("Decipher FunctionURL: " + decipherFunctionUrl);

            String javascriptFile = readFile(decipherFunctionUrl);
            Matcher mat = patSignatureDecFunction.matcher(javascriptFile);
            if (mat.find()) {
                decipherFunctionName = mat.group(1);
                if (LOGGING) LOGGER.debug("Decipher FunctionName: " + decipherFunctionName);

                Pattern patMainVariable = Pattern.compile(
                        "(var |\\s|,|;)" + decipherFunctionName.replace("$", "\\$") + "(=function\\((.{1,3})\\)\\{)");

                StringBuilder mainDecipherFunct = new StringBuilder();

                mat = patMainVariable.matcher(javascriptFile);
                if (mat.find()) {
                    mainDecipherFunct.append("var ");
                    mainDecipherFunct.append(decipherFunctionName);
                    mainDecipherFunct.append(mat.group(2));
                } else {
                    Pattern patMainFunction = Pattern.compile("function " + decipherFunctionName.replace("$", "\\$") + "(\\((.{1,3})\\)\\{)");
                    mat = patMainFunction.matcher(javascriptFile);
                    if (!mat.find()) return null;
                    mainDecipherFunct.append("function ");
                    mainDecipherFunct.append(decipherFunctionName);
                    mainDecipherFunct.append(mat.group(2));
                }

                int startIndex = mat.end();

                for (int braces = 1, i = startIndex; i < javascriptFile.length(); i++) {
                    if (braces == 0 && startIndex + 5 < i) {
                        mainDecipherFunct.append(javascriptFile, startIndex, i);
                        mainDecipherFunct.append(";");
                        break;
                    }
                    char charAt = javascriptFile.charAt(i);
                    if (charAt == '{') braces++;
                    else if (charAt == '}') braces--;
                }
                StringBuilder decipherFunctions = new StringBuilder(mainDecipherFunct);
                // Search the main function for extra functions and variables
                // needed for deciphering
                // Search for variables
                mat = patVariableFunction.matcher(mainDecipherFunct);
                while (mat.find()) {
                    final String variableDef = "var " + mat.group(2) + "={";
                    if (decipherFunctions.indexOf(variableDef) >= 0) continue;
                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length();
                    for (int braces = 1, i = startIndex; i < javascriptFile.length(); i++) {
                        if (braces == 0) {
                            decipherFunctions.append(variableDef);
                            decipherFunctions.append(javascriptFile, startIndex, i);
                            decipherFunctions.append(";");
                            break;
                        }
                        char charAt = javascriptFile.charAt(i);
                        if (charAt == '{') braces++;
                        else if (charAt == '}') braces--;
                    }
                }
                // Search for functions
                mat = patFunction.matcher(mainDecipherFunct);
                while (mat.find()) {
                    final String functionDef = "function " + mat.group(2) + "(";
                    if (decipherFunctions.indexOf(functionDef) >= 0) continue;
                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length();
                    for (int braces = 0, i = startIndex; i < javascriptFile.length(); i++) {
                        if (braces == 0 && startIndex + 5 < i) {
                            decipherFunctions.append(functionDef);
                            decipherFunctions.append(javascriptFile, startIndex, i);
                            decipherFunctions.append(";");
                            break;
                        }
                        char charAt = javascriptFile.charAt(i);
                        if (charAt == '{') braces++;
                        else if (charAt == '}') braces--;
                    }
                }
                YouTubeMeta.decipherFunctions = decipherFunctions.toString();
                if (LOGGING) LOGGER.debug("Decipher Function: " + YouTubeMeta.decipherFunctions);
                String answer = decipherJavaScript(encSignatures, YouTubeMeta.decipherFunctions);
                if (CACHING) writeDecipherFunctionToCache();
                return answer;
            } else return null;
        } else return decipherJavaScript(encSignatures, decipherFunctions);
    }

    private void readDecipherFunctionFromCache() {
        FileReference cacheFile = cacheDirPath.getChild(CACHE_FILE_NAME);
        // The cached functions are valid for 2 weeks
        long timeoutMillis = 2 * 7 * 24L * 3600L * 1000L;
        if (cacheFile.getExists() && (System.currentTimeMillis() - cacheFile.getLastModified()) < timeoutMillis) {
            try {
                String[] lines = cacheFile.readText().split("\n");
                decipherJsFileName = lines[0];
                decipherFunctionName = lines[1];
                decipherFunctions = lines[2];
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void writeDecipherFunctionToCache() {
        try {
            FileReference cacheFile = cacheDirPath.getChild(CACHE_FILE_NAME);
            FileReference parent = cacheFile.getParent();
            if (parent != null) parent.tryMkdirs();
            cacheFile.writeText(decipherJsFileName + "\n" + decipherFunctionName + "\n" + decipherFunctions);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * the signatures are decoded using decipherFunctions
     * the result is returned as String with \n as a separator
     */
    private String decipherJavaScript(final HashMap<Format, String> encSignatures, String decipherFunctions) {

        final String functionHead = " function decipher(){return ";
        final String functionFoot = "};decipher();";
        final String separator = "+\"\\n\"+";

        // calculate the length to prevent unnecessary allocations
        int requiredSize = decipherFunctions.length() + functionHead.length() + functionFoot.length();
        requiredSize += encSignatures.size() * (decipherFunctionName.length() + 4 + separator.length());
        for (Map.Entry<Format, String> entry : encSignatures.entrySet()) {
            requiredSize += entry.getValue().length();
        }

        // build JavaScript function
        final StringBuilder stb = new StringBuilder(requiredSize);
        stb.append(decipherFunctions);
        stb.append(functionHead);

        int i = 0;
        for (Map.Entry<Format, String> entry : encSignatures.entrySet()) {
            stb.append(decipherFunctionName);
            stb.append("('");
            stb.append(entry.getValue());
            stb.append("')");
            if (i++ < encSignatures.size() - 1) {
                stb.append("+\"\\n\"+");
            }
        }
        stb.append(functionFoot);

        String stbString = stb.toString();
        if (LOGGING) LOGGER.debug(stbString);

        // todo it hurts a little to include a while JS engine for such a small thing
        // todo if the functions are simple, like currently, we could implement our own small js engine

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            return engine.eval(stbString).toString();
        } catch (ScriptException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger(YouTubeMeta.class);

}