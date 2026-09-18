package com.stesholdings.snagreport.util

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.stesholdings.snagreport.data.Rating
import com.stesholdings.snagreport.data.SnagEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a minimal but valid .docx (Office Open XML) report directly, with no third-party
 * library, so the app has no dependency that needs the internet to fetch document templates.
 */
object DocxExporter {

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private const val MAX_WIDTH_EMU = 5_486_400L // 6 inches
    private const val MAX_HEIGHT_EMU = 7_315_200L // 8 inches

    private data class ImageRef(val relId: String, val fileName: String, val bytes: ByteArray)

    fun export(context: Context, entries: List<SnagEntity>): Uri {
        val fileName = "SnaggingReport_" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".docx"
        val (documentXml, images) = buildDocument(entries)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, fileName, documentXml, images)
        } else {
            exportViaLegacyFile(context, fileName, documentXml, images)
        }
    }

    private fun exportViaMediaStore(
        context: Context,
        fileName: String,
        documentXml: String,
        images: List<ImageRef>
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create output file in Downloads")
        resolver.openOutputStream(uri)?.use { out -> writeZip(out, documentXml, images) }
            ?: error("Could not open output stream for $uri")
        return uri
    }

    private fun exportViaLegacyFile(
        context: Context,
        fileName: String,
        documentXml: String,
        images: List<ImageRef>
    ): Uri {
        @Suppress("DEPRECATION")
        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SnaggingReports"
        ).apply { mkdirs() }
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { out -> writeZip(out, documentXml, images) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ---- OOXML generation -------------------------------------------------------------------

    private fun buildDocument(entries: List<SnagEntity>): Pair<String, List<ImageRef>> {
        val images = mutableListOf<ImageRef>()
        val body = StringBuilder()

        body.append(titleParagraphs(entries.size))

        entries.forEachIndexed { index, entry ->
            if (index > 0) body.append(PAGE_BREAK_PARAGRAPH)
            body.append(headingParagraph(entry))
            body.append(fieldParagraph("Rating", entry.rating.label, colorForRating(entry.rating)))

            val imageFile = entry.imagePath?.let { File(it) }
            if (imageFile != null && imageFile.exists()) {
                val bytes = imageFile.readBytes()
                val (w, h) = decodeBounds(bytes)
                val (cx, cy) = scaledExtent(w, h)
                val relId = "rId${images.size + 1}"
                images.add(ImageRef(relId, "image${images.size + 1}.jpg", bytes))
                body.append(imageParagraph(relId, images.size, cx, cy))
            } else {
                body.append(plainParagraph("Photo: none"))
            }

            body.append(descriptionParagraph(entry.description))
        }

        body.append(SECT_PR)

        val document = DOCUMENT_HEADER + body.toString() + DOCUMENT_FOOTER
        return document to images
    }

    private fun writeZip(out: OutputStream, documentXml: String, images: List<ImageRef>) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
            entry("_rels/.rels", ROOT_RELS_XML.toByteArray(Charsets.UTF_8))
            entry("word/document.xml", documentXml.toByteArray(Charsets.UTF_8))
            entry("word/_rels/document.xml.rels", documentRelsXml(images).toByteArray(Charsets.UTF_8))
            images.forEach { entry("word/media/${it.fileName}", it.bytes) }
        }
    }

    private fun decodeBounds(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth.coerceAtLeast(1)
        val height = options.outHeight.coerceAtLeast(1)
        return width to height
    }

    private fun scaledExtent(width: Int, height: Int): Pair<Long, Long> {
        var cx = MAX_WIDTH_EMU
        var cy = cx * height / width
        if (cy > MAX_HEIGHT_EMU) {
            cy = MAX_HEIGHT_EMU
            cx = cy * width / height
        }
        return cx to cy
    }

    private fun colorForRating(rating: Rating): String = when (rating) {
        Rating.RED -> "C00000"
        Rating.AMBER -> "E2A100"
        Rating.GREEN -> "1E7B34"
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun titleParagraphs(count: Int): String {
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
        return """<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t xml:space="preserve">Snagging Report</w:t></w:r></w:p>""" +
            """<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="240"/></w:pPr><w:r><w:rPr><w:sz w:val="20"/><w:color w:val="666666"/></w:rPr><w:t xml:space="preserve">Generated ${escapeXml(generated)} &#8226; $count item(s)</w:t></w:r></w:p>"""
    }

    private fun headingParagraph(entry: SnagEntity): String {
        val heading = listOf(entry.buildingName, entry.level, entry.roomLocation)
            .filter { it.isNotBlank() }
            .joinToString("  –  ")
        return """<w:p><w:pPr><w:spacing w:before="240" w:after="60"/><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="1" w:color="CCCCCC"/></w:pBdr></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="26"/></w:rPr><w:t xml:space="preserve">${escapeXml(heading)}</w:t></w:r></w:p>"""
    }

    private fun fieldParagraph(label: String, value: String, colorHex: String): String {
        return """<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">$label: </w:t></w:r><w:r><w:rPr><w:b/><w:color w:val="$colorHex"/></w:rPr><w:t xml:space="preserve">${escapeXml(value)}</w:t></w:r></w:p>"""
    }

    private fun plainParagraph(text: String): String =
        """<w:p><w:r><w:t xml:space="preserve">${escapeXml(text)}</w:t></w:r></w:p>"""

    private fun descriptionParagraph(text: String): String {
        val label = """<w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">Description: </w:t></w:r>"""
        val lines = text.ifBlank { "-" }.split("\n")
        val runs = lines.mapIndexed { i, line ->
            val br = if (i > 0) "<w:br/>" else ""
            "<w:r>$br<w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r>"
        }.joinToString("")
        return """<w:p><w:pPr><w:spacing w:after="240"/></w:pPr>$label$runs</w:p>"""
    }

    private fun imageParagraph(relId: String, index: Int, cx: Long, cy: Long): String {
        return """<w:p><w:pPr><w:spacing w:before="120" w:after="120"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:docPr id="$index" name="Picture $index"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$index" name="Picture $index"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
    }

    private fun documentRelsXml(images: List<ImageRef>): String {
        val rels = images.joinToString("") {
            """<Relationship Id="${it.relId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${it.fileName}"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>"""
    }

    private const val PAGE_BREAK_PARAGRAPH = """<w:p><w:r><w:br w:type="page"/></w:r></w:p>"""

    private const val SECT_PR = """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1417" w:right="1417" w:bottom="1417" w:left="1417" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>"""

    private const val DOCUMENT_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>"""

    private const val DOCUMENT_FOOTER = """</w:body></w:document>"""

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
}
