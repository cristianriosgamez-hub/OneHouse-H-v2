package com.onehouse.app.feature.consumption.transfer

import android.content.Context
import android.net.Uri
import com.onehouse.app.data.energy.MeterType
import com.onehouse.app.data.local.EnergyReadingEntity
import com.onehouse.app.data.local.OneHouseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class ConsumptionExcelManager(private val context: Context) {
    private val dao = OneHouseDatabase.getInstance(context).energyReadingDao()
    private val exportDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val importDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
    )

    suspend fun export(uri: Uri, selectedTypes: Set<MeterType>): Int = withContext(Dispatchers.IO) {
        require(selectedTypes.isNotEmpty()) { "Selecciona al menos un contador para exportar" }
        val readings = dao.getAllOnce().filter { reading ->
            selectedTypes.any { it.storageValue == reading.meterType }
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip -> writeWorkbook(zip, readings, selectedTypes) }
        } ?: error("No se pudo abrir el archivo de destino")
        readings.size
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo abrir el Excel")
        val entries = unzip(bytes)
        val workbook = entries["xl/workbook.xml"] ?: error("Excel no válido: falta workbook.xml")
        val rels = entries["xl/_rels/workbook.xml.rels"] ?: error("Excel no válido: faltan relaciones")
        val sheets = workbookSheets(workbook, rels)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val parsed = mutableListOf<EnergyReadingEntity>()
        var invalid = 0
        for ((name, path) in sheets) {
            val type = typeForSheet(name) ?: continue
            val xml = entries[path] ?: continue
            val result = parseSheet(xml, type, sharedStrings)
            parsed += result.first
            invalid += result.second
        }
        if (parsed.isEmpty() && invalid == 0) error("No se encontraron hojas de consumos compatibles")
        val before = dao.count()
        dao.insertAll(parsed)
        val after = dao.count()
        ImportResult(newReadings = after - before, duplicates = parsed.size - (after - before), invalid = invalid)
    }

    private fun writeWorkbook(
        zip: ZipOutputStream,
        readings: List<EnergyReadingEntity>,
        selectedTypes: Set<MeterType>
    ) {
        val types = listOf(MeterType.ENDESA, MeterType.AGBAR, MeterType.CLIMATIZATION, MeterType.ACS)
            .filter { it in selectedTypes }
        put(zip, "[Content_Types].xml", contentTypes(types.size))
        put(zip, "_rels/.rels", rootRels())
        put(zip, "xl/workbook.xml", workbook(types))
        put(zip, "xl/_rels/workbook.xml.rels", workbookRels(types.size))
        types.forEachIndexed { index, type ->
            put(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(readings.filter { it.meterType == type.storageValue }))
        }
    }

    private fun sheetXml(rows: List<EnergyReadingEntity>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        row(1, listOf("Fecha", "Lectura", "Consumo", "Coste", "Unidad", "Origen", "Nota"))
        rows.sortedBy { it.timestamp }.forEachIndexed { i, r ->
            row(i + 2, listOf(exportDateFormat.format(Date(r.timestamp)), r.meterValue?.let(::excelNumber).orEmpty(), r.consumption?.let(::excelNumber).orEmpty(), r.cost?.let(::excelNumber).orEmpty(), r.unit, r.source, r.note.orEmpty()))
        }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.row(number: Int, values: List<String>) {
        append("<row r=\"").append(number).append("\">")
        values.forEachIndexed { i, value ->
            val ref = "${('A'.code + i).toChar()}$number"
            append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>")
        }
        append("</row>")
    }

    private fun parseSheet(xml: ByteArray, type: MeterType, sharedStrings: List<String>): Pair<List<EnergyReadingEntity>, Int> {
        val doc = parse(xml)
        val rows = doc.getElementsByTagNameNS("*", "row")
        val result = mutableListOf<EnergyReadingEntity>()
        var invalid = 0
        for (i in 1 until rows.length) {
            try {
                val row = rows.item(i) as Element
                val cells = row.getElementsByTagNameNS("*", "c")
                val values = MutableList(7) { "" }
                for (j in 0 until cells.length) {
                    val c = cells.item(j) as Element
                    val ref = c.getAttribute("r")
                    val col = ref.firstOrNull()?.uppercaseChar()?.code?.minus('A'.code) ?: -1
                    if (col in values.indices) values[col] = cellText(c, sharedStrings)
                }
                if (values[0].isBlank()) continue
                result += EnergyReadingEntity(
                    meterType = type.storageValue,
                    timestamp = parseImportDate(values[0]) ?: error("Fecha inválida"),
                    meterValue = values[1].toDoubleOrNull(),
                    consumption = values[2].toDoubleOrNull(),
                    cost = values[3].toDoubleOrNull(),
                    unit = values[4].ifBlank { type.unit },
                    source = values[5].ifBlank { "EXCEL" },
                    note = values[6].ifBlank { null }
                )
            } catch (_: Exception) { invalid++ }
        }
        return result to invalid
    }

    private fun parseImportDate(value: String): Long? =
        importDateFormats.firstNotNullOfOrNull { format ->
            runCatching { format.parse(value.trim())?.time }.getOrNull()
        }

    private fun excelNumber(value: Double): String =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun cellText(c: Element, sharedStrings: List<String>): String {
        val t = c.getElementsByTagNameNS("*", "t")
        if (t.length > 0) return t.item(0).textContent.orEmpty()
        val v = c.getElementsByTagNameNS("*", "v")
        val raw = if (v.length > 0) v.item(0).textContent.orEmpty() else ""
        return if (c.getAttribute("t") == "s") raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty() else raw
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val doc = parse(xml)
        val nodes = doc.getElementsByTagNameNS("*", "si")
        return (0 until nodes.length).map { i ->
            val si = nodes.item(i) as Element
            val texts = si.getElementsByTagNameNS("*", "t")
            buildString { for (j in 0 until texts.length) append(texts.item(j).textContent.orEmpty()) }
        }
    }

    private fun workbookSheets(workbook: ByteArray, rels: ByteArray): List<Pair<String, String>> {
        val relDoc = parse(rels)
        val targets = mutableMapOf<String, String>()
        val relNodes = relDoc.getElementsByTagNameNS("*", "Relationship")
        for (i in 0 until relNodes.length) {
            val e = relNodes.item(i) as Element
            targets[e.getAttribute("Id")] = "xl/" + e.getAttribute("Target").removePrefix("/").removePrefix("xl/")
        }
        val doc = parse(workbook)
        val nodes = doc.getElementsByTagNameNS("*", "sheet")
        return (0 until nodes.length).mapNotNull { i ->
            val e = nodes.item(i) as Element
            val id = e.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            targets[id]?.let { e.getAttribute("name") to it }
        }
    }

    private fun typeForSheet(name: String): MeterType? = when (name.uppercase(Locale.ROOT)) {
        "ENDESA" -> MeterType.ENDESA; "AGBAR" -> MeterType.AGBAR; "CLIMATIZACION", "CLIMATIZACIÓN" -> MeterType.CLIMATIZATION; "ACS" -> MeterType.ACS; else -> null
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) { val out = ByteArrayOutputStream(); zin.copyTo(out); map[e.name] = out.toByteArray() }
                e = zin.nextEntry
            }
        }
        return map
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun put(zip: ZipOutputStream, name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun workbook(types: List<MeterType>) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" + types.mapIndexed { i,t -> "<sheet name=\"${if(t==MeterType.CLIMATIZATION) "CLIMATIZACION" else t.storageValue}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("") + "</sheets></workbook>"
    private fun workbookRels(n: Int) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + (1..n).joinToString("") { "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>" } + "</Relationships>"
    private fun rootRels() = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
    private fun contentTypes(n: Int) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" + (1..n).joinToString("") { "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>"
}

data class ImportResult(val newReadings: Int, val duplicates: Int, val invalid: Int)
