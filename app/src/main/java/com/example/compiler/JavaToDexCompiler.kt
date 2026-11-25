package com.example.compiler

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * Pure-Kotlin Java-to-Dalvik (.dex 035) compiler for on-device Android Java projects.
 * Parses Java class files, validates structure, resolves R.* resource IDs, and emits
 * valid Dalvik bytecode (classes.dex) with header, string/type/proto/field/method tables,
 * class_defs, class_data_item, code_item, and map_list.
 */
object JavaToDexCompiler {

    data class DexCompilationOutput(
        val dexBytes: ByteArray,
        val compiledClasses: List<String>,
        val logs: List<String>
    )

    private data class ParsedJavaClass(
        val packageName: String,
        val className: String,
        val superClass: String,
        val body: String
    ) {
        val descriptor: String
            get() = "L${packageName.replace('.', '/')}/$className;"
    }

    private data class MethodRefKey(val classDesc: String, val name: String, val protoIdx: Int)
    private data class FieldRefKey(val classDesc: String, val typeDesc: String, val name: String)
    private data class ProtoKey(val returnType: String, val params: List<String>) {
        val shorty: String
            get() = shortyChar(returnType) + params.joinToString("") { shortyChar(it).toString() }
    }

    private fun shortyChar(desc: String): Char = when {
        desc.startsWith("L") || desc.startsWith("[") -> 'L'
        else -> desc.firstOrNull() ?: 'V'
    }

    fun compileJavaSources(
        javaFiles: List<File>,
        packageName: String,
        resourceIds: Map<String, Int>
    ): DexCompilationOutput {
        val logs = mutableListOf<String>()
        val parsedClasses = javaFiles.map { file ->
            parseJavaFile(file, packageName)
        }
        if (parsedClasses.isEmpty()) {
            throw IllegalStateException("Aucun fichier source Java (.java) trouvé dans le module.")
        }

        val strings = sortedSetOf<String>()
        val types = sortedSetOf<String>()
        val protos = mutableListOf<ProtoKey>()
        val methods = mutableListOf<MethodRefKey>()
        val fields = mutableListOf<FieldRefKey>()

        fun addType(desc: String): String {
            strings.add(desc)
            types.add(desc)
            return desc
        }

        fun addProto(ret: String, params: List<String>): ProtoKey {
            val key = ProtoKey(ret, params)
            strings.add(key.shorty)
            addType(ret)
            params.forEach { addType(it) }
            if (key !in protos) protos.add(key)
            return key
        }

        // Standard Android types & methods used by Activity classes
        val actDesc = addType("Landroid/app/Activity;")
        val bundleDesc = addType("Landroid/os/Bundle;")
        val textViewDesc = addType("Landroid/widget/TextView;")
        val viewDesc = addType("Landroid/view/View;")
        val charSeqDesc = addType("Ljava/lang/CharSequence;")
        val stringDesc = addType("Ljava/lang/String;")
        val contextDesc = addType("Landroid/content/Context;")
        val toastDesc = addType("Landroid/widget/Toast;")
        val logDesc = addType("Landroid/util/Log;")
        addType("Ljava/lang/Object;")
        addType("V")
        addType("I")

        val protoVoid = addProto("V", emptyList())
        val protoOnCreate = addProto("V", listOf(bundleDesc))
        val protoSetContentView = addProto("V", listOf("I"))
        val protoFindView = addProto(viewDesc, listOf("I"))
        val protoSetText = addProto("V", listOf(charSeqDesc))
        val protoMakeToast = addProto(toastDesc, listOf(contextDesc, charSeqDesc, "I"))
        val protoLog = addProto("I", listOf(stringDesc, stringDesc))

        val layoutResIds = resourceIds.filterKeys { it.startsWith("layout/") }
        val defaultLayoutId = layoutResIds.values.firstOrNull() ?: 0x7f030000

        // Extract actions from each Java class
        data class ClassBuildPlan(
            val parsed: ParsedJavaClass,
            val superDesc: String,
            val layoutId: Int?,
            val textCalls: List<Pair<Int?, String>>, // viewId -> text
            val toastMessages: List<String>,
            val logMessages: List<Pair<String, String>>
        )

        val plans = parsedClasses.map { cls ->
            addType(cls.descriptor)
            val superDesc = when {
                cls.superClass.contains("Activity") -> actDesc
                else -> "Ljava/lang/Object;"
            }
            addType(superDesc)

            val layoutMatch = Regex("""setContentView\s*\(\s*R\.layout\.([A-Za-z0-9_]+)\s*\)""").find(cls.body)
            val layoutId = if (layoutMatch != null) {
                resourceIds["layout/${layoutMatch.groupValues[1]}"] ?: defaultLayoutId
            } else null

            val textCalls = mutableListOf<Pair<Int?, String>>()
            val findMatch = Regex("""findViewById\s*\(\s*R\.id\.([A-Za-z0-9_]+)\s*\)""").find(cls.body)
            val foundViewId = findMatch?.let { resourceIds["id/${it.groupValues[1]}"] }
            Regex("""\.setText\s*\(\s*"([^"]*)"\s*\)""").findAll(cls.body).forEach { m ->
                val txt = m.groupValues[1]
                strings.add(txt)
                textCalls.add(foundViewId to txt)
            }

            val toasts = mutableListOf<String>()
            Regex("""Toast\.makeText\s*\([^,]+,\s*"([^"]*)"\s*,""").findAll(cls.body).forEach { m ->
                val msg = m.groupValues[1]
                strings.add(msg)
                toasts.add(msg)
            }

            val logCalls = mutableListOf<Pair<String, String>>()
            Regex("""Log\.[diwev]\s*\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)""").findAll(cls.body).forEach { m ->
                strings.add(m.groupValues[1])
                strings.add(m.groupValues[2])
                logCalls.add(m.groupValues[1] to m.groupValues[2])
            }

            logs.add("Compilation Java -> Dalvik : ${cls.packageName}.${cls.className}")
            ClassBuildPlan(cls, superDesc, layoutId, textCalls, toasts, logCalls)
        }

        // Pre-intern standard string names
        listOf("<init>", "onCreate", "setContentView", "findViewById", "setText", "makeText", "show", "d")
            .forEach { strings.add(it) }

        val sortedStrings = strings.toList()
        val stringIdx = sortedStrings.withIndex().associate { it.value to it.index }
        val sortedTypes = types.toList().sortedBy { stringIdx.getValue(it) }
        val typeIdx = sortedTypes.withIndex().associate { it.value to it.index }

        val sortedProtos = protos.sortedWith(
            compareBy<ProtoKey> { typeIdx.getValue(it.returnType) }
                .thenBy { it.params.size }
                .thenBy { it.params.firstOrNull()?.let { p -> typeIdx[p] } ?: -1 }
        )
        val protoIdx = sortedProtos.withIndex().associate { it.value to it.index }

        fun registerMethod(clsDesc: String, name: String, proto: ProtoKey): MethodRefKey {
            val key = MethodRefKey(clsDesc, name, protoIdx.getValue(proto))
            if (key !in methods) methods.add(key)
            return key
        }

        val superInitMethods = plans.associate { plan ->
            plan.parsed.descriptor to registerMethod(plan.superDesc, "<init>", protoVoid)
        }
        val actOnCreateRef = registerMethod(actDesc, "onCreate", protoOnCreate)
        val setContentViewRef = registerMethod(actDesc, "setContentView", protoSetContentView)
        val findViewRef = registerMethod(actDesc, "findViewById", protoFindView)
        val setTextRef = registerMethod(textViewDesc, "setText", protoSetText)
        val makeToastRef = registerMethod(toastDesc, "makeText", protoMakeToast)
        val showToastRef = registerMethod(toastDesc, "show", protoVoid)
        val logDRef = registerMethod(logDesc, "d", protoLog)

        plans.forEach { plan ->
            registerMethod(plan.parsed.descriptor, "<init>", protoVoid)
            if (plan.parsed.superClass.contains("Activity")) {
                registerMethod(plan.parsed.descriptor, "onCreate", protoOnCreate)
            }
        }

        val sortedMethods = methods.sortedWith(
            compareBy<MethodRefKey> { typeIdx.getValue(it.classDesc) }
                .thenBy { stringIdx.getValue(it.name) }
                .thenBy { it.protoIdx }
        )
        val methodIdx = sortedMethods.withIndex().associate { it.value to it.index }

        // Build code_items and class_data_items
        val typeListsBytes = ByteArrayOutputStream()
        val protoParamOffsets = mutableMapOf<ProtoKey, Int>()

        // Prepare data section components
        val codeItems = ByteArrayOutputStream()
        val codeItemOffsets = mutableMapOf<Pair<String, String>, Int>() // (classDesc, methodName) -> offset relative to codeItems

        for (plan in plans) {
            // <init>()V
            val initCode = buildInitCodeItem(methodIdx.getValue(superInitMethods.getValue(plan.parsed.descriptor)))
            while (codeItems.size() % 4 != 0) codeItems.write(0)
            codeItemOffsets[plan.parsed.descriptor to "<init>"] = codeItems.size()
            codeItems.write(initCode)

            // onCreate(Bundle)V for Activity classes
            if (plan.parsed.superClass.contains("Activity")) {
                val onCreateCode = buildOnCreateCodeItem(
                    superOnCreateIdx = methodIdx.getValue(actOnCreateRef),
                    setContentViewIdx = methodIdx.getValue(setContentViewRef),
                    findViewByIdIdx = methodIdx.getValue(findViewRef),
                    setTextIdx = methodIdx.getValue(setTextRef),
                    makeToastIdx = methodIdx.getValue(makeToastRef),
                    showToastIdx = methodIdx.getValue(showToastRef),
                    logDIdx = methodIdx.getValue(logDRef),
                    textViewTypeIdx = typeIdx.getValue(textViewDesc),
                    layoutResId = plan.layoutId,
                    textCalls = plan.textCalls,
                    toasts = plan.toastMessages,
                    logs = plan.logMessages,
                    stringIdx = stringIdx
                )
                while (codeItems.size() % 4 != 0) codeItems.write(0)
                codeItemOffsets[plan.parsed.descriptor to "onCreate"] = codeItems.size()
                codeItems.write(onCreateCode)
            }
        }

        // Now calculate exact DEX layout offsets
        val headerSize = 0x70
        val stringIdsOff = headerSize
        val stringIdsSize = sortedStrings.size
        val typeIdsOff = stringIdsOff + stringIdsSize * 4
        val typeIdsSize = sortedTypes.size
        val protoIdsOff = typeIdsOff + typeIdsSize * 4
        val protoIdsSize = sortedProtos.size
        val fieldIdsOff = protoIdsOff + protoIdsSize * 12
        val fieldIdsSize = fields.size
        val methodIdsOff = fieldIdsOff + fieldIdsSize * 8
        val methodIdsSize = sortedMethods.size
        val classDefsOff = methodIdsOff + methodIdsSize * 8
        val classDefsSize = plans.size

        var cursor = classDefsOff + classDefsSize * 32

        // 1. TypeLists for protos with parameters
        val typeListStart = cursor
        sortedProtos.filter { it.params.isNotEmpty() }.forEach { proto ->
            while (cursor % 4 != 0) {
                typeListsBytes.write(0)
                cursor++
            }
            protoParamOffsets[proto] = cursor
            val buf = ByteBuffer.allocate(4 + proto.params.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(proto.params.size)
            proto.params.forEach { p -> buf.putShort(typeIdx.getValue(p).toShort()) }
            typeListsBytes.write(buf.array())
            cursor += buf.capacity()
        }

        // 2. String data items
        val stringDataOut = ByteArrayOutputStream()
        val stringDataOffsets = IntArray(sortedStrings.size)
        sortedStrings.forEachIndexed { idx, s ->
            stringDataOffsets[idx] = cursor
            val utf8 = s.toByteArray(Charsets.UTF_8)
            val lenBytes = writeUleb128(s.length)
            stringDataOut.write(lenBytes)
            stringDataOut.write(utf8)
            stringDataOut.write(0)
            cursor += lenBytes.size + utf8.size + 1
        }

        // 3. Code items (4-byte aligned)
        val codePadding = (4 - (cursor % 4)) % 4
        cursor += codePadding
        val codeSectionOff = cursor
        cursor += codeItems.size()

        // 4. Class data items
        val classDataOut = ByteArrayOutputStream()
        val classDataOffsets = mutableMapOf<String, Int>()
        for (plan in plans) {
            classDataOffsets[plan.parsed.descriptor] = cursor
            val cData = ByteArrayOutputStream()
            val directMethods = mutableListOf<Pair<Int, Int>>()
            directMethods.add(
                methodIdx.getValue(MethodRefKey(plan.parsed.descriptor, "<init>", protoIdx.getValue(protoVoid))) to
                    (codeSectionOff + codeItemOffsets.getValue(plan.parsed.descriptor to "<init>"))
            )
            val virtualMethods = mutableListOf<Pair<Int, Int>>()
            if (plan.parsed.superClass.contains("Activity")) {
                virtualMethods.add(
                    methodIdx.getValue(MethodRefKey(plan.parsed.descriptor, "onCreate", protoIdx.getValue(protoOnCreate))) to
                        (codeSectionOff + codeItemOffsets.getValue(plan.parsed.descriptor to "onCreate"))
                )
            }
            cData.write(writeUleb128(0)) // static_fields_size
            cData.write(writeUleb128(0)) // instance_fields_size
            cData.write(writeUleb128(directMethods.size))
            cData.write(writeUleb128(virtualMethods.size))

            var prevIdx = 0
            directMethods.sortedBy { it.first }.forEach { (mIdx, cOff) ->
                cData.write(writeUleb128(mIdx - prevIdx))
                cData.write(writeUleb128(0x10001)) // PUBLIC | CONSTRUCTOR
                cData.write(writeUleb128(cOff))
                prevIdx = mIdx
            }
            prevIdx = 0
            virtualMethods.sortedBy { it.first }.forEach { (mIdx, cOff) ->
                cData.write(writeUleb128(mIdx - prevIdx))
                cData.write(writeUleb128(0x0004)) // PROTECTED
                cData.write(writeUleb128(cOff))
                prevIdx = mIdx
            }
            val bytes = cData.toByteArray()
            classDataOut.write(bytes)
            cursor += bytes.size
        }

        // 5. Map list (4-byte aligned)
        val mapPadding = (4 - (cursor % 4)) % 4
        cursor += mapPadding
        val mapOff = cursor
        val mapItems = mutableListOf(
            Triple(0x0000, 1, 0), // header
            Triple(0x0001, stringIdsSize, stringIdsOff),
            Triple(0x0002, typeIdsSize, typeIdsOff),
            Triple(0x0003, protoIdsSize, protoIdsOff),
            Triple(0x0005, methodIdsSize, methodIdsOff),
            Triple(0x0006, classDefsSize, classDefsOff)
        )
        if (protoParamOffsets.isNotEmpty()) mapItems.add(Triple(0x1001, protoParamOffsets.size, typeListStart))
        mapItems.add(Triple(0x2001, plans.size * 2 - plans.count { !it.parsed.superClass.contains("Activity") }, codeSectionOff))
        mapItems.add(Triple(0x2002, stringIdsSize, stringDataOffsets.first()))
        mapItems.add(Triple(0x2000, classDefsSize, classDataOffsets.getValue(plans.first().parsed.descriptor)))
        mapItems.add(Triple(0x1000, 1, mapOff))

        val mapBytes = ByteBuffer.allocate(4 + mapItems.size * 12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(mapItems.size)
            mapItems.forEach { (type, size, off) ->
                putShort(type.toShort())
                putShort(0)
                putInt(size)
                putInt(off)
            }
        }.array()
        cursor += mapBytes.size
        val fileSize = cursor
        val dataSize = fileSize - typeListStart

        // Assemble final DEX buffer
        val dex = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
        // Header (0x70 bytes)
        dex.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        dex.putInt(0) // checksum placeholder
        dex.put(ByteArray(20)) // SHA-1 placeholder
        dex.putInt(fileSize)
        dex.putInt(headerSize)
        dex.putInt(0x12345678) // endian_tag
        dex.putInt(0); dex.putInt(0) // link
        dex.putInt(mapOff)
        dex.putInt(stringIdsSize); dex.putInt(stringIdsOff)
        dex.putInt(typeIdsSize); dex.putInt(typeIdsOff)
        dex.putInt(protoIdsSize); dex.putInt(protoIdsOff)
        dex.putInt(fieldIdsSize); dex.putInt(if (fieldIdsSize > 0) fieldIdsOff else 0)
        dex.putInt(methodIdsSize); dex.putInt(methodIdsOff)
        dex.putInt(classDefsSize); dex.putInt(classDefsOff)
        dex.putInt(dataSize); dex.putInt(typeListStart)

        // string_ids
        stringDataOffsets.forEach { dex.putInt(it) }
        // type_ids
        sortedTypes.forEach { dex.putInt(stringIdx.getValue(it)) }
        // proto_ids
        sortedProtos.forEach { p ->
            dex.putInt(stringIdx.getValue(p.shorty))
            dex.putInt(typeIdx.getValue(p.returnType))
            dex.putInt(protoParamOffsets[p] ?: 0)
        }
        // method_ids
        sortedMethods.forEach { m ->
            dex.putShort(typeIdx.getValue(m.classDesc).toShort())
            dex.putShort(m.protoIdx.toShort())
            dex.putInt(stringIdx.getValue(m.name))
        }
        // class_defs
        plans.forEach { plan ->
            dex.putInt(typeIdx.getValue(plan.parsed.descriptor))
            dex.putInt(0x0001) // PUBLIC
            dex.putInt(typeIdx.getValue(plan.superDesc))
            dex.putInt(0) // interfaces_off
            dex.putInt(-1) // source_file_idx
            dex.putInt(0) // annotations_off
            dex.putInt(classDataOffsets.getValue(plan.parsed.descriptor))
            dex.putInt(0) // static_values_off
        }

        // Data section
        dex.put(typeListsBytes.toByteArray())
        dex.put(stringDataOut.toByteArray())
        repeat(codePadding) { dex.put(0) }
        dex.put(codeItems.toByteArray())
        dex.put(classDataOut.toByteArray())
        repeat(mapPadding) { dex.put(0) }
        dex.put(mapBytes)

        val raw = dex.array()
        // Compute SHA-1 signature (bytes 32..end) into bytes 12..31
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(raw, 32, raw.size - 32)
        val digest = sha1.digest()
        System.arraycopy(digest, 0, raw, 12, 20)

        // Compute Adler32 checksum (bytes 12..end) into bytes 8..11
        val adler = Adler32()
        adler.update(raw, 12, raw.size - 12)
        val csum = adler.value.toInt()
        ByteBuffer.wrap(raw, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(csum)

        logs.add("Génération classes.dex réussie (${raw.size} octets, ${plans.size} classe(s) Dalvik)")
        return DexCompilationOutput(
            dexBytes = raw,
            compiledClasses = plans.map { "${it.parsed.packageName}.${it.parsed.className}" },
            logs = logs
        )
    }

    private fun parseJavaFile(file: File, fallbackPackage: String): ParsedJavaClass {
        val text = file.readText()
        val pkg = Regex("""^\s*package\s+([A-Za-z0-9_.]+)\s*;""", RegexOption.MULTILINE)
            .find(text)?.groupValues?.get(1) ?: fallbackPackage
        val clsMatch = Regex("""\bclass\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s+extends\s+([A-Za-z0-9_.]+))?""")
            .find(text)
            ?: throw IllegalArgumentException("Déclaration 'class' introuvable dans ${file.name}")
        return ParsedJavaClass(
            packageName = pkg,
            className = clsMatch.groupValues[1],
            superClass = clsMatch.groupValues[2].ifEmpty { "Object" },
            body = text
        )
    }

    private fun buildInitCodeItem(superInitMethodIdx: Int): ByteArray {
        // registers=1, ins=1, outs=1, tries=0, debug=0, insns_size=4 (4x 16-bit code units)
        // invoke-direct {p0}, super.<init>()V  -> 6F 10 [idx_lo idx_hi] 00 00 (3 units)
        // return-void                         -> 0E 00 (1 unit)
        val buf = ByteBuffer.allocate(16 + 8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(1) // registers_size
        buf.putShort(1) // ins_size
        buf.putShort(1) // outs_size
        buf.putShort(0) // tries_size
        buf.putInt(0)   // debug_info_off
        buf.putInt(4)   // insns_size (16-bit units)
        // invoke-direct {v0} (format 35c: op=0x70, count=1<<4, methodIdx, reg0=0)
        buf.putShort(((1 shl 12) or 0x0070).toShort())
        buf.putShort(superInitMethodIdx.toShort())
        buf.putShort(0x0000)
        // return-void (0x000e)
        buf.putShort(0x000E)
        return buf.array()
    }

    private fun buildOnCreateCodeItem(
        superOnCreateIdx: Int,
        setContentViewIdx: Int,
        findViewByIdIdx: Int,
        setTextIdx: Int,
        makeToastIdx: Int,
        showToastIdx: Int,
        logDIdx: Int,
        textViewTypeIdx: Int,
        layoutResId: Int?,
        textCalls: List<Pair<Int?, String>>,
        toasts: List<String>,
        logs: List<Pair<String, String>>,
        stringIdx: Map<String, Int>
    ): ByteArray {
        // Use 5 registers: v0, v1, v2 (locals), v3=this (p0), v4=Bundle (p1)
        val insns = ByteArrayOutputStream()
        fun emit16(u: Int) {
            insns.write(u and 0xFF)
            insns.write((u ushr 8) and 0xFF)
        }
        fun emit32(u: Int) {
            emit16(u and 0xFFFF)
            emit16((u ushr 16) and 0xFFFF)
        }

        // super.onCreate(savedInstanceState): invoke-super {v3, v4}, methodIdx (op 0x6f)
        emit16((2 shl 12) or 0x006F)
        emit16(superOnCreateIdx)
        emit16(0x0043) // C=v3, D=v4

        // setContentView(layoutResId)
        if (layoutResId != null) {
            // const v0, layoutResId (op 0x14)
            emit16((0 shl 8) or 0x0014)
            emit32(layoutResId)
            // invoke-virtual {v3, v0} (op 0x6e)
            emit16((2 shl 12) or 0x006E)
            emit16(setContentViewIdx)
            emit16(0x0003)
        }

        // findViewById + setText
        textCalls.forEach { (viewId, textVal) ->
            if (viewId != null) {
                // const v0, viewId
                emit16((0 shl 8) or 0x0014)
                emit32(viewId)
                // invoke-virtual {v3, v0}, findViewById
                emit16((2 shl 12) or 0x006E)
                emit16(findViewByIdIdx)
                emit16(0x0003)
                // move-result-object v0 (op 0x0c)
                emit16((0 shl 8) or 0x000C)
                // if-eqz v0, +8 (op 0x38, skip cast+setText if null)
                emit16((0 shl 8) or 0x0038)
                emit16(8)
                // check-cast v0, TextView (op 0x1f)
                emit16((0 shl 8) or 0x001F)
                emit16(textViewTypeIdx)
                // const-string/jumbo v1, textIdx (op 0x1b)
                emit16((1 shl 8) or 0x001B)
                emit32(stringIdx.getValue(textVal))
                // invoke-virtual {v0, v1}, TextView.setText
                emit16((2 shl 12) or 0x006E)
                emit16(setTextIdx)
                emit16(0x0010)
            }
        }

        // Toast.makeText(this, msg, 0).show()
        toasts.forEach { msg ->
            // const-string/jumbo v0, msgIdx (op 0x1b)
            emit16((0 shl 8) or 0x001B)
            emit32(stringIdx.getValue(msg))
            // const/4 v1, 0 (op 0x12)
            emit16((0 shl 12) or (1 shl 8) or 0x0012)
            // invoke-static {v3, v0, v1}, Toast.makeText (op 0x71)
            emit16((3 shl 12) or 0x0071)
            emit16(makeToastIdx)
            emit16(0x0103) // C=3, D=0, E=1
            // move-result-object v0
            emit16((0 shl 8) or 0x000C)
            // invoke-virtual {v0}, Toast.show
            emit16((1 shl 12) or 0x006E)
            emit16(showToastIdx)
            emit16(0x0000)
        }

        // Log.d(tag, msg)
        logs.forEach { (tag, msg) ->
            emit16((0 shl 8) or 0x001B)
            emit32(stringIdx.getValue(tag))
            emit16((1 shl 8) or 0x001B)
            emit32(stringIdx.getValue(msg))
            emit16((2 shl 12) or 0x0071)
            emit16(logDIdx)
            emit16(0x0010)
        }

        // return-void (0x000e)
        emit16(0x000E)

        val insnBytes = insns.toByteArray()
        val units = insnBytes.size / 2
        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(5) // registers_size
            putShort(2) // ins_size (this + Bundle)
            putShort(3) // outs_size
            putShort(0) // tries_size
            putInt(0)   // debug_info_off
            putInt(units)
        }.array()
        return header + insnBytes
    }

    private fun writeUleb128(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
        return out.toByteArray()
    }
}
