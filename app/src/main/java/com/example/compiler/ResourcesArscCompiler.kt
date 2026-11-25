package com.example.compiler

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CompiledResources(
    val arscBytes: ByteArray,
    val resourceIds: Map<String, Int>, // e.g. "string/app_name" -> 0x7f010000
    val strings: Map<String, String>,
    val colors: Map<String, Int>
)

object ResourcesArscCompiler {

    private const val TYPE_STRING_ID = 1
    private const val TYPE_COLOR_ID = 2
    private const val TYPE_LAYOUT_ID = 3
    private const val TYPE_DRAWABLE_ID = 4
    private const val TYPE_ID_ID = 5

    fun compile(moduleResDir: File, packageName: String): CompiledResources {
        val stringsMap = linkedMapOf<String, String>()
        val colorsMap = linkedMapOf<String, Int>()
        val layoutsList = mutableListOf<String>()
        val drawablesList = mutableListOf<String>()
        val idsList = linkedSetOf<String>()

        // 1. Parse strings.xml
        val stringsFile = File(moduleResDir, "values/strings.xml")
        if (stringsFile.exists()) {
            parseValuesXml(stringsFile.readText(), "string") { name, value ->
                stringsMap[name] = value
            }
        }
        if (!stringsMap.containsKey("app_name")) {
            stringsMap["app_name"] = "NASJE App"
        }

        // 2. Parse colors.xml
        val colorsFile = File(moduleResDir, "values/colors.xml")
        if (colorsFile.exists()) {
            parseValuesXml(colorsFile.readText(), "color") { name, value ->
                colorsMap[name] = parseColorHex(value)
            }
        }

        // 3. Collect layout XMLs & @+id/... references
        val layoutDir = File(moduleResDir, "layout")
        if (layoutDir.exists()) {
            layoutDir.listFiles()?.filter { it.isFile && it.extension == "xml" }?.sortedBy { it.name }?.forEach { file ->
                layoutsList.add(file.nameWithoutExtension)
                val idRegex = Regex("""@id/([a-zA-Z0-9_]+)|@\+id/([a-zA-Z0-9_]+)""")
                idRegex.findAll(file.readText()).forEach { m ->
                    val idName = m.groupValues[1].ifEmpty { m.groupValues[2] }
                    if (idName.isNotEmpty()) idsList.add(idName)
                }
            }
        }

        // 4. Collect drawables
        val drawableDir = File(moduleResDir, "drawable")
        if (drawableDir.exists()) {
            drawableDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { file ->
                drawablesList.add(file.nameWithoutExtension)
            }
        }

        val resourceIds = linkedMapOf<String, Int>()
        stringsMap.keys.forEachIndexed { i, k ->
            resourceIds["string/$k"] = (0x7f shl 24) or (TYPE_STRING_ID shl 16) or i
        }
        colorsMap.keys.forEachIndexed { i, k ->
            resourceIds["color/$k"] = (0x7f shl 24) or (TYPE_COLOR_ID shl 16) or i
        }
        layoutsList.forEachIndexed { i, k ->
            resourceIds["layout/$k"] = (0x7f shl 24) or (TYPE_LAYOUT_ID shl 16) or i
        }
        drawablesList.forEachIndexed { i, k ->
            resourceIds["drawable/$k"] = (0x7f shl 24) or (TYPE_DRAWABLE_ID shl 16) or i
        }
        idsList.forEachIndexed { i, k ->
            resourceIds["id/$k"] = (0x7f shl 24) or (TYPE_ID_ID shl 16) or i
        }

        val arsc = buildArscBytes(
            packageName = packageName,
            strings = stringsMap,
            colors = colorsMap,
            layouts = layoutsList,
            drawables = drawablesList,
            ids = idsList.toList()
        )
        return CompiledResources(arsc, resourceIds, stringsMap, colorsMap)
    }

    private fun parseValuesXml(xml: String, targetTag: String, onEntry: (String, String) -> Unit) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == targetTag) {
                    val name = parser.getAttributeValue(null, "name")
                    val text = parser.nextText()
                    if (!name.isNullOrBlank()) {
                        onEntry(name, text ?: "")
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
    }

    private fun parseColorHex(raw: String): Int {
        val hex = raw.trim().removePrefix("#")
        return try {
            when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                else -> 0xFF3B82F6.toInt()
            }
        } catch (_: Exception) {
            0xFF3B82F6.toInt()
        }
    }

    private fun buildArscBytes(
        packageName: String,
        strings: Map<String, String>,
        colors: Map<String, Int>,
        layouts: List<String>,
        drawables: List<String>,
        ids: List<String>
    ): ByteArray {
        val globalStrings = mutableListOf<String>()
        val stringValIndices = strings.values.map { v ->
            val idx = globalStrings.size
            globalStrings.add(v)
            idx
        }
        val layoutPathIndices = layouts.map { name ->
            val idx = globalStrings.size
            globalStrings.add("res/layout/$name.xml")
            idx
        }
        val drawablePathIndices = drawables.map { name ->
            val idx = globalStrings.size
            globalStrings.add("res/drawable/$name.xml")
            idx
        }

        val typeStrings = listOf("string", "color", "layout", "drawable", "id")
        val keyStrings = mutableListOf<String>()
        fun internKey(k: String): Int {
            val existing = keyStrings.indexOf(k)
            if (existing >= 0) return existing
            keyStrings.add(k)
            return keyStrings.size - 1
        }

        val stringKeyIndices = strings.keys.map { internKey(it) }
        val colorKeyIndices = colors.keys.map { internKey(it) }
        val layoutKeyIndices = layouts.map { internKey(it) }
        val drawableKeyIndices = drawables.map { internKey(it) }
        val idKeyIndices = ids.map { internKey(it) }

        val globalPoolBytes = encodeStringPoolUtf16(globalStrings)
        val typePoolBytes = encodeStringPoolUtf16(typeStrings)
        val keyPoolBytes = encodeStringPoolUtf16(keyStrings)

        val typeChunks = mutableListOf<ByteArray>()
        // 1: string
        typeChunks.add(encodeTypeSpec(TYPE_STRING_ID, strings.size))
        typeChunks.add(
            encodeTypeEntries(
                typeId = TYPE_STRING_ID,
                entries = stringKeyIndices.zip(stringValIndices).map { (kIdx, vIdx) ->
                    Triple(kIdx, 0x03.toByte(), vIdx) // TYPE_STRING = 0x03
                }
            )
        )
        // 2: color
        typeChunks.add(encodeTypeSpec(TYPE_COLOR_ID, colors.size))
        typeChunks.add(
            encodeTypeEntries(
                typeId = TYPE_COLOR_ID,
                entries = colorKeyIndices.zip(colors.values.toList()).map { (kIdx, argb) ->
                    Triple(kIdx, 0x1C.toByte(), argb) // TYPE_INT_COLOR_ARGB8 = 0x1C
                }
            )
        )
        // 3: layout
        typeChunks.add(encodeTypeSpec(TYPE_LAYOUT_ID, layouts.size))
        typeChunks.add(
            encodeTypeEntries(
                typeId = TYPE_LAYOUT_ID,
                entries = layoutKeyIndices.zip(layoutPathIndices).map { (kIdx, vIdx) ->
                    Triple(kIdx, 0x03.toByte(), vIdx)
                }
            )
        )
        // 4: drawable
        typeChunks.add(encodeTypeSpec(TYPE_DRAWABLE_ID, drawables.size))
        typeChunks.add(
            encodeTypeEntries(
                typeId = TYPE_DRAWABLE_ID,
                entries = drawableKeyIndices.zip(drawablePathIndices).map { (kIdx, vIdx) ->
                    Triple(kIdx, 0x03.toByte(), vIdx)
                }
            )
        )
        // 5: id
        typeChunks.add(encodeTypeSpec(TYPE_ID_ID, ids.size))
        typeChunks.add(
            encodeTypeEntries(
                typeId = TYPE_ID_ID,
                entries = idKeyIndices.map { kIdx ->
                    Triple(kIdx, 0x12.toByte(), 0) // TYPE_INT_BOOLEAN = 0x12
                }
            )
        )

        val packageHeaderSize = 288
        val typeStringsOffset = packageHeaderSize
        val keyStringsOffset = typeStringsOffset + typePoolBytes.size
        val typesSize = typeChunks.sumOf { it.size }
        val packageTotalSize = packageHeaderSize + typePoolBytes.size + keyPoolBytes.size + typesSize

        val pkgBuf = ByteBuffer.allocate(packageHeaderSize).order(ByteOrder.LITTLE_ENDIAN)
        pkgBuf.putShort(0x0200) // RES_TABLE_PACKAGE_TYPE
        pkgBuf.putShort(packageHeaderSize.toShort())
        pkgBuf.putInt(packageTotalSize)
        pkgBuf.putInt(0x7F) // id = 0x7f
        val nameChars = ByteArray(256)
        val pkgUtf16 = packageName.take(127).toByteArray(Charsets.UTF_16LE)
        System.arraycopy(pkgUtf16, 0, nameChars, 0, minOf(pkgUtf16.size, 254))
        pkgBuf.put(nameChars)
        pkgBuf.putInt(typeStringsOffset)
        pkgBuf.putInt(typeStrings.size)
        pkgBuf.putInt(keyStringsOffset)
        pkgBuf.putInt(keyStrings.size)
        pkgBuf.putInt(0) // typeIdOffset

        val tableHeaderSize = 12
        val tableTotalSize = tableHeaderSize + globalPoolBytes.size + packageTotalSize
        val out = ByteArrayOutputStream(tableTotalSize)
        val tblBuf = ByteBuffer.allocate(tableHeaderSize).order(ByteOrder.LITTLE_ENDIAN)
        tblBuf.putShort(0x0002) // RES_TABLE_TYPE
        tblBuf.putShort(tableHeaderSize.toShort())
        tblBuf.putInt(tableTotalSize)
        tblBuf.putInt(1) // packageCount = 1

        out.write(tblBuf.array())
        out.write(globalPoolBytes)
        out.write(pkgBuf.array())
        out.write(typePoolBytes)
        out.write(keyPoolBytes)
        typeChunks.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun encodeTypeSpec(typeId: Int, entryCount: Int): ByteArray {
        val headerSize = 16
        val totalSize = headerSize + entryCount * 4
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0202) // RES_TABLE_TYPE_SPEC_TYPE
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        buf.put(typeId.toByte())
        buf.put(0)
        buf.putShort(0)
        buf.putInt(entryCount)
        repeat(entryCount) { buf.putInt(0) }
        return buf.array()
    }

    private fun encodeTypeEntries(
        typeId: Int,
        entries: List<Triple<Int, Byte, Int>>
    ): ByteArray {
        val configSize = 56 // standard ResTable_config
        val headerSize = 20 + configSize
        val count = entries.size
        val offsetsSize = count * 4
        val entryStructSize = 16 // 8 bytes ResTable_entry + 8 bytes Res_value
        val entriesStart = headerSize + offsetsSize
        val totalSize = entriesStart + count * entryStructSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0201) // RES_TABLE_TYPE_TYPE
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        buf.put(typeId.toByte())
        buf.put(0)
        buf.putShort(0)
        buf.putInt(count)
        buf.putInt(entriesStart)
        // ResTable_config (56 bytes: size=56 then zeros)
        buf.putInt(configSize)
        repeat(configSize - 4) { buf.put(0) }

        for (i in 0 until count) {
            buf.putInt(i * entryStructSize)
        }
        for ((keyIdx, valueType, data) in entries) {
            buf.putShort(8) // entry size = 8
            buf.putShort(0) // flags = 0
            buf.putInt(keyIdx)
            buf.putShort(8) // Res_value size = 8
            buf.put(0) // res0
            buf.put(valueType)
            buf.putInt(data)
        }
        return buf.array()
    }

    fun encodeStringPoolUtf16(strings: List<String>): ByteArray {
        val headerSize = 28
        val offsets = IntArray(strings.size)
        val stringsData = ByteArrayOutputStream()
        strings.forEachIndexed { idx, s ->
            offsets[idx] = stringsData.size()
            val utf16 = s.toByteArray(Charsets.UTF_16LE)
            val charLen = s.length
            if (charLen < 0x8000) {
                stringsData.write(charLen and 0xFF)
                stringsData.write((charLen ushr 8) and 0xFF)
            } else {
                val high = 0x8000 or (charLen ushr 16)
                val low = charLen and 0xFFFF
                stringsData.write(high and 0xFF)
                stringsData.write((high ushr 8) and 0xFF)
                stringsData.write(low and 0xFF)
                stringsData.write((low ushr 8) and 0xFF)
            }
            stringsData.write(utf16)
            stringsData.write(0)
            stringsData.write(0)
        }
        while (stringsData.size() % 4 != 0) stringsData.write(0)

        val stringsStart = headerSize + strings.size * 4
        val totalSize = stringsStart + stringsData.size()
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0001) // RES_STRING_POOL_TYPE
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        buf.putInt(strings.size)
        buf.putInt(0) // styleCount
        buf.putInt(0) // flags (UTF-16)
        buf.putInt(stringsStart)
        buf.putInt(0) // stylesStart
        offsets.forEach { buf.putInt(it) }
        buf.put(stringsData.toByteArray())
        return buf.array()
    }
}
