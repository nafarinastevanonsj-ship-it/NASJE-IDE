package com.example.compiler

import com.example.model.ProjectInfo
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BinaryXmlCompiler {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private val ANDROID_ATTR_IDS = mapOf(
        "theme" to 0x01010000,
        "label" to 0x01010001,
        "icon" to 0x01010002,
        "name" to 0x01010003,
        "exported" to 0x01010010,
        "textSize" to 0x01010095,
        "textColor" to 0x01010098,
        "gravity" to 0x010100af,
        "orientation" to 0x010100c4,
        "id" to 0x010100d0,
        "background" to 0x010100d4,
        "padding" to 0x010100d5,
        "layout_marginTop" to 0x010100f8,
        "layout_width" to 0x010100f4,
        "layout_height" to 0x010100f5,
        "text" to 0x0101014f,
        "minSdkVersion" to 0x0101020c,
        "versionCode" to 0x0101021b,
        "versionName" to 0x0101021c,
        "targetSdkVersion" to 0x01010270,
        "allowBackup" to 0x01010280,
        "supportsRtl" to 0x010103af,
        "width" to 0x01010155,
        "height" to 0x01010159,
        "viewportWidth" to 0x01010402,
        "viewportHeight" to 0x01010403,
        "fillColor" to 0x01010404,
        "pathData" to 0x01010405
    )

    private sealed class XmlEvent {
        data class StartNs(val prefix: String, val uri: String, val line: Int) : XmlEvent()
        data class EndNs(val prefix: String, val uri: String, val line: Int) : XmlEvent()
        data class StartTag(
            val ns: String?,
            val name: String,
            val attrs: List<Attr>,
            val line: Int
        ) : XmlEvent()
        data class EndTag(val ns: String?, val name: String, val line: Int) : XmlEvent()
    }

    private data class Attr(
        val ns: String?,
        val name: String,
        val rawValue: String
    )

    fun compileManifest(
        manifestXml: String,
        project: ProjectInfo,
        resourceIds: Map<String, Int>
    ): ByteArray {
        val enriched = injectManifestBuildMetadata(manifestXml, project)
        return compileXml(enriched, resourceIds)
    }

    fun compileXml(xmlContent: String, resourceIds: Map<String, Int>): ByteArray {
        val events = mutableListOf<XmlEvent>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(xmlContent.reader())

        var nsDepthStarted = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val nsCount = parser.getNamespaceCount(parser.depth)
                    val prevNsCount = if (parser.depth > 1) parser.getNamespaceCount(parser.depth - 1) else 0
                    for (i in prevNsCount until nsCount) {
                        val prefix = parser.getNamespacePrefix(i) ?: ""
                        val uri = parser.getNamespaceUri(i) ?: ""
                        events.add(XmlEvent.StartNs(prefix, uri, parser.lineNumber))
                        nsDepthStarted = true
                    }

                    val attrs = (0 until parser.attributeCount).map { i ->
                        Attr(
                            ns = parser.getAttributeNamespace(i)?.ifEmpty { null },
                            name = parser.getAttributeName(i),
                            rawValue = parser.getAttributeValue(i)
                        )
                    }.sortedBy { attr ->
                        if (attr.ns == ANDROID_NS) ANDROID_ATTR_IDS[attr.name] ?: Int.MAX_VALUE else Int.MAX_VALUE
                    }

                    events.add(
                        XmlEvent.StartTag(
                            ns = parser.namespace?.ifEmpty { null },
                            name = parser.name,
                            attrs = attrs,
                            line = parser.lineNumber
                        )
                    )
                }
                XmlPullParser.END_TAG -> {
                    events.add(
                        XmlEvent.EndTag(
                            ns = parser.namespace?.ifEmpty { null },
                            name = parser.name,
                            line = parser.lineNumber
                        )
                    )
                    if (parser.depth == 1 && nsDepthStarted) {
                        val nsCount = parser.getNamespaceCount(parser.depth)
                        for (i in nsCount - 1 downTo 0) {
                            val prefix = parser.getNamespacePrefix(i) ?: ""
                            val uri = parser.getNamespaceUri(i) ?: ""
                            events.add(XmlEvent.EndNs(prefix, uri, parser.lineNumber))
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Build string pool: Android attribute names with resource IDs MUST come first!
        val poolStrings = mutableListOf<String>()
        val resIdMap = mutableListOf<Int>()

        // Collect android attribute names first
        val androidAttrNames = events.filterIsInstance<XmlEvent.StartTag>()
            .flatMap { it.attrs }
            .filter { it.ns == ANDROID_NS && ANDROID_ATTR_IDS.containsKey(it.name) }
            .map { it.name }
            .distinct()
            .sortedBy { ANDROID_ATTR_IDS[it] }

        androidAttrNames.forEach { attrName ->
            poolStrings.add(attrName)
            resIdMap.add(ANDROID_ATTR_IDS.getValue(attrName))
        }

        fun intern(str: String?): Int {
            if (str == null) return -1
            val idx = poolStrings.indexOf(str)
            if (idx >= 0) return idx
            poolStrings.add(str)
            return poolStrings.size - 1
        }

        // Intern all strings in order
        events.forEach { ev ->
            when (ev) {
                is XmlEvent.StartNs -> {
                    intern(ev.prefix); intern(ev.uri)
                }
                is XmlEvent.EndNs -> {
                    intern(ev.prefix); intern(ev.uri)
                }
                is XmlEvent.StartTag -> {
                    intern(ev.ns); intern(ev.name)
                    ev.attrs.forEach { a ->
                        intern(a.ns); intern(a.name); intern(a.rawValue)
                    }
                }
                is XmlEvent.EndTag -> {
                    intern(ev.ns); intern(ev.name)
                }
            }
        }

        val poolBytes = ResourcesArscCompiler.encodeStringPoolUtf16(poolStrings)
        val resMapChunk = encodeResourceMapChunk(resIdMap)

        val bodyOut = ByteArrayOutputStream()
        events.forEach { ev ->
            when (ev) {
                is XmlEvent.StartNs -> {
                    bodyOut.write(encodeNsChunk(0x0100, ev.line, intern(ev.prefix), intern(ev.uri)))
                }
                is XmlEvent.EndNs -> {
                    bodyOut.write(encodeNsChunk(0x0101, ev.line, intern(ev.prefix), intern(ev.uri)))
                }
                is XmlEvent.StartTag -> {
                    val attrData = ev.attrs.map { a ->
                        val (vType, vData) = resolveTypedValue(a, resourceIds, ::intern)
                        EncodedAttr(
                            nsIdx = intern(a.ns),
                            nameIdx = intern(a.name),
                            rawValueIdx = if (vType == 0x03.toByte()) intern(a.rawValue) else -1,
                            valueType = vType,
                            valueData = vData
                        )
                    }
                    val idAttrIndex = ev.attrs.indexOfFirst { it.name == "id" } + 1
                    bodyOut.write(
                        encodeStartElement(
                            line = ev.line,
                            nsIdx = intern(ev.ns),
                            nameIdx = intern(ev.name),
                            idIdx = idAttrIndex,
                            attrs = attrData
                        )
                    )
                }
                is XmlEvent.EndTag -> {
                    bodyOut.write(encodeEndElement(ev.line, intern(ev.ns), intern(ev.name)))
                }
            }
        }

        val bodyBytes = bodyOut.toByteArray()
        val headerSize = 8
        val totalSize = headerSize + poolBytes.size + resMapChunk.size + bodyBytes.size
        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0003) // RES_XML_TYPE
            putShort(headerSize.toShort())
            putInt(totalSize)
        }.array()

        return ByteArrayOutputStream(totalSize).apply {
            write(header)
            write(poolBytes)
            write(resMapChunk)
            write(bodyBytes)
        }.toByteArray()
    }

    private fun injectManifestBuildMetadata(xml: String, project: ProjectInfo): String {
        var result = xml
        if (!result.contains("android:versionCode")) {
            result = result.replace(
                "<manifest ",
                "<manifest android:versionCode=\"${project.versionCode}\" android:versionName=\"${project.versionName}\" "
            )
        }
        if (!result.contains("<uses-sdk")) {
            result = result.replace(
                "<application",
                "<uses-sdk android:minSdkVersion=\"${project.minSdk}\" android:targetSdkVersion=\"${project.targetSdk}\" />\n    <application"
            )
        }
        return result
    }

    private fun resolveTypedValue(
        attr: Attr,
        resourceIds: Map<String, Int>,
        intern: (String) -> Int
    ): Pair<Byte, Int> {
        val v = attr.rawValue.trim()
        // Resource references @string/..., @drawable/..., @color/..., @+id/..., @id/...
        if (v.startsWith("@+id/")) {
            val key = "id/" + v.removePrefix("@+id/")
            val resId = resourceIds[key]
            if (resId != null) return 0x01.toByte() to resId // TYPE_REFERENCE
        } else if (v.startsWith("@")) {
            val key = v.removePrefix("@")
            val resId = resourceIds[key]
            if (resId != null) return 0x01.toByte() to resId
        }
        // Booleans
        if (v == "true") return 0x12.toByte() to -1
        if (v == "false") return 0x12.toByte() to 0
        // Layout constants
        if (attr.name == "layout_width" || attr.name == "layout_height") {
            if (v == "match_parent" || v == "fill_parent") return 0x10.toByte() to -1
            if (v == "wrap_content") return 0x10.toByte() to -2
        }
        if (attr.name == "orientation") {
            if (v == "vertical") return 0x10.toByte() to 1
            if (v == "horizontal") return 0x10.toByte() to 0
        }
        if (attr.name == "gravity") {
            if (v == "center") return 0x11.toByte() to 0x11
        }
        // Colors #RRGGBB or #AARRGGBB
        if (v.startsWith("#")) {
            val hex = v.removePrefix("#")
            val argb = when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                else -> null
            }
            if (argb != null) return 0x1C.toByte() to argb
        }
        // Dimensions dp / sp
        if (v.endsWith("dp") || v.endsWith("dip")) {
            val num = v.removeSuffix("dip").removeSuffix("dp").toFloatOrNull()
            if (num != null) return 0x05.toByte() to encodeDimension(num, 1)
        }
        if (v.endsWith("sp")) {
            val num = v.removeSuffix("sp").toFloatOrNull()
            if (num != null) return 0x05.toByte() to encodeDimension(num, 2)
        }
        // Plain integers for numeric attributes
        if (attr.name in setOf("minSdkVersion", "targetSdkVersion", "versionCode")) {
            val i = v.toIntOrNull()
            if (i != null) return 0x10.toByte() to i
        }
        // Fallback string
        return 0x03.toByte() to intern(attr.rawValue)
    }

    private fun encodeDimension(value: Float, unit: Int): Int {
        val intVal = value.toInt().coerceIn(-8388608, 8388607)
        return (intVal shl 8) or (0 shl 4) or (unit and 0xF)
    }

    private fun encodeResourceMapChunk(ids: List<Int>): ByteArray {
        val headerSize = 8
        val totalSize = headerSize + ids.size * 4
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0180) // RES_XML_RESOURCE_MAP_TYPE
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        ids.forEach { buf.putInt(it) }
        return buf.array()
    }

    private fun encodeNsChunk(type: Int, line: Int, prefixIdx: Int, uriIdx: Int): ByteArray {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(type.toShort())
        buf.putShort(16)
        buf.putInt(24)
        buf.putInt(line)
        buf.putInt(-1)
        buf.putInt(prefixIdx)
        buf.putInt(uriIdx)
        return buf.array()
    }

    private data class EncodedAttr(
        val nsIdx: Int,
        val nameIdx: Int,
        val rawValueIdx: Int,
        val valueType: Byte,
        val valueData: Int
    )

    private fun encodeStartElement(
        line: Int,
        nsIdx: Int,
        nameIdx: Int,
        idIdx: Int,
        attrs: List<EncodedAttr>
    ): ByteArray {
        val attrSize = 20
        val totalSize = 36 + attrs.size * attrSize
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0102) // RES_XML_START_ELEMENT_TYPE
        buf.putShort(16)
        buf.putInt(totalSize)
        buf.putInt(line)
        buf.putInt(-1) // comment
        buf.putInt(nsIdx)
        buf.putInt(nameIdx)
        buf.putShort(20) // attributeStart
        buf.putShort(attrSize.toShort()) // attributeSize
        buf.putShort(attrs.size.toShort())
        buf.putShort(idIdx.toShort()) // idIndex (1-based)
        buf.putShort(0) // classIndex
        buf.putShort(0) // styleIndex
        attrs.forEach { a ->
            buf.putInt(a.nsIdx)
            buf.putInt(a.nameIdx)
            buf.putInt(a.rawValueIdx)
            buf.putShort(8) // Res_value size
            buf.put(0) // res0
            buf.put(a.valueType)
            buf.putInt(a.valueData)
        }
        return buf.array()
    }

    private fun encodeEndElement(line: Int, nsIdx: Int, nameIdx: Int): ByteArray {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0103) // RES_XML_END_ELEMENT_TYPE
        buf.putShort(16)
        buf.putInt(24)
        buf.putInt(line)
        buf.putInt(-1)
        buf.putInt(nsIdx)
        buf.putInt(nameIdx)
        return buf.array()
    }
}
