package com.example.data.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import kotlin.math.max
import kotlin.math.min

object HealthReportPdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42
    private const val FOOTER_TOP = 748

    fun createPdf(context: Context, content: HealthReportContent): File {
        val reportDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportDir, "fitplan-resumen-salud.pdf")
        val document = PdfDocument()
        try {
            drawCover(document, content)
            drawDetailPage(document, content)
            document.writeTo(file.outputStream())
        } finally {
            document.close()
        }
        return file
    }

    private fun drawCover(document: PdfDocument, content: HealthReportContent) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val titlePaint = paint(26f, Color.rgb(20, 28, 45), true)
        val subtitlePaint = paint(13f, Color.rgb(55, 65, 81), false)
        val labelPaint = paint(12f, Color.rgb(15, 118, 110), true)
        val disclaimerPaint = paint(10f, Color.rgb(127, 29, 29), false)

        canvas.drawColor(Color.WHITE)
        canvas.drawRoundRect(RectF(MARGIN.toFloat(), 44f, (PAGE_WIDTH - MARGIN).toFloat(), 150f), 18f, 18f, fill(Color.rgb(236, 253, 245)))
        canvas.drawText(content.coverTitle, MARGIN + 18f, 88f, titlePaint)
        canvas.drawText(content.patientLine, MARGIN + 18f, 116f, subtitlePaint)
        canvas.drawText(content.rangeLine, MARGIN + 18f, 136f, subtitlePaint)

        canvas.drawText("Documento para consulta con profesional de salud", MARGIN.toFloat(), 208f, labelPaint)
        drawWrapped(canvas, content.planLine, MARGIN.toFloat(), 234f, PAGE_WIDTH - (MARGIN * 2), subtitlePaint, 18f)

        canvas.drawRoundRect(RectF(MARGIN.toFloat(), 300f, (PAGE_WIDTH - MARGIN).toFloat(), 462f), 14f, 14f, stroke(Color.rgb(127, 29, 29), 2f))
        canvas.drawText("Aviso legal importante", MARGIN + 16f, 330f, paint(15f, Color.rgb(127, 29, 29), true))
        drawWrapped(canvas, content.legalDisclaimer, MARGIN + 16f, 354f, PAGE_WIDTH - (MARGIN * 2) - 32, disclaimerPaint, 15f)

        drawSectionList(canvas, "Contenido del informe", content.sections.map { it.title }, 520f)
        drawFooter(canvas, content.legalDisclaimer, 1)
        document.finishPage(page)
    }

    private fun drawDetailPage(document: PdfDocument, content: HealthReportContent) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        canvas.drawText("Resumen de seguimiento", MARGIN.toFloat(), 54f, paint(20f, Color.rgb(20, 28, 45), true))
        canvas.drawText(content.patientLine, MARGIN.toFloat(), 78f, paint(11f, Color.rgb(75, 85, 99), false))

        drawWeightChart(canvas, content.weightPoints, 108f)
        drawNutritionTable(canvas, content.weeklyNutrition, 300f)
        drawSectionList(canvas, "Actividad fisica", content.activityItems.take(8), 506f)

        drawFooter(canvas, content.legalDisclaimer, 2)
        document.finishPage(page)
    }

    private fun drawWeightChart(canvas: android.graphics.Canvas, points: List<ReportPoint>, top: Float) {
        canvas.drawText("Progreso de peso / medidas", MARGIN.toFloat(), top, paint(14f, Color.rgb(20, 28, 45), true))
        val chart = RectF(MARGIN.toFloat(), top + 18f, (PAGE_WIDTH - MARGIN).toFloat(), top + 142f)
        canvas.drawRoundRect(chart, 10f, 10f, stroke(Color.rgb(203, 213, 225), 1.2f))
        if (points.size < 2) {
            canvas.drawText("Se requieren al menos 2 registros para graficar tendencia.", MARGIN + 16f, top + 82f, paint(11f, Color.rgb(100, 116, 139), false))
            return
        }
        val values = points.map { it.value }
        val minValue = values.minOrNull() ?: 0f
        val maxValue = values.maxOrNull() ?: 1f
        val delta = max(1f, maxValue - minValue)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = chart.left + 18f + ((chart.width() - 36f) * index / (points.size - 1))
            val y = chart.bottom - 20f - ((point.value - minValue) / delta * (chart.height() - 40f))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            canvas.drawCircle(x, y, 3.5f, fill(Color.rgb(14, 165, 233)))
        }
        canvas.drawPath(path, stroke(Color.rgb(14, 165, 233), 2.5f))
        canvas.drawText("Min ${minValue} kg", chart.left + 12f, chart.bottom - 8f, paint(9f, Color.rgb(100, 116, 139), false))
        canvas.drawText("Max ${maxValue} kg", chart.right - 70f, chart.bottom - 8f, paint(9f, Color.rgb(100, 116, 139), false))
    }

    private fun drawNutritionTable(canvas: android.graphics.Canvas, rows: List<WeeklyNutritionSummary>, top: Float) {
        canvas.drawText("Resumen semanal de macronutrientes y calorias", MARGIN.toFloat(), top, paint(14f, Color.rgb(20, 28, 45), true))
        var y = top + 26f
        val headers = listOf("Semana", "Kcal", "Proteina", "Carbs", "Grasa")
        val xs = listOf(MARGIN.toFloat(), 150f, 230f, 330f, 430f)
        headers.forEachIndexed { index, h -> canvas.drawText(h, xs[index], y, paint(10f, Color.rgb(15, 118, 110), true)) }
        y += 18f
        if (rows.isEmpty()) {
            canvas.drawText("Sin registros de nutricion en el rango.", MARGIN.toFloat(), y, paint(10f, Color.rgb(100, 116, 139), false))
            return
        }
        rows.take(6).forEach { row ->
            canvas.drawLine(MARGIN.toFloat(), y - 12f, (PAGE_WIDTH - MARGIN).toFloat(), y - 12f, stroke(Color.rgb(226, 232, 240), 1f))
            canvas.drawText(row.label, xs[0], y, paint(10f, Color.rgb(31, 41, 55), false))
            canvas.drawText(row.calories.toString(), xs[1], y, paint(10f, Color.rgb(31, 41, 55), false))
            canvas.drawText("${row.proteinG.toInt()} g", xs[2], y, paint(10f, Color.rgb(31, 41, 55), false))
            canvas.drawText("${row.carbsG.toInt()} g", xs[3], y, paint(10f, Color.rgb(31, 41, 55), false))
            canvas.drawText("${row.fatG.toInt()} g", xs[4], y, paint(10f, Color.rgb(31, 41, 55), false))
            y += 20f
        }
    }

    private fun drawSectionList(canvas: android.graphics.Canvas, title: String, lines: List<String>, top: Float) {
        canvas.drawText(title, MARGIN.toFloat(), top, paint(14f, Color.rgb(20, 28, 45), true))
        var y = top + 24f
        val textPaint = paint(10.5f, Color.rgb(55, 65, 81), false)
        if (lines.isEmpty()) {
            canvas.drawText("Sin datos disponibles.", MARGIN.toFloat(), y, textPaint)
            return
        }
        lines.forEach { line ->
            val used = drawWrapped(canvas, "- $line", MARGIN.toFloat(), y, PAGE_WIDTH - (MARGIN * 2), textPaint, 15f)
            y += used + 5f
            if (y > FOOTER_TOP - 28f) return
        }
    }

    private fun drawFooter(canvas: android.graphics.Canvas, disclaimer: String, page: Int) {
        canvas.drawLine(MARGIN.toFloat(), FOOTER_TOP.toFloat(), (PAGE_WIDTH - MARGIN).toFloat(), FOOTER_TOP.toFloat(), stroke(Color.rgb(203, 213, 225), 1f))
        drawWrapped(canvas, disclaimer, MARGIN.toFloat(), FOOTER_TOP + 18f, PAGE_WIDTH - (MARGIN * 2), paint(7.6f, Color.rgb(127, 29, 29), false), 10.5f)
        canvas.drawText("Pagina $page", (PAGE_WIDTH - MARGIN - 44).toFloat(), (PAGE_HEIGHT - 22).toFloat(), paint(8f, Color.rgb(100, 116, 139), false))
    }

    private fun drawWrapped(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, width: Int, paint: Paint, lineHeight: Float): Float {
        var cursorY = y
        val words = text.split(" ")
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) > width && line.isNotBlank()) {
                canvas.drawText(line, x, cursorY, paint)
                cursorY += lineHeight
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotBlank()) canvas.drawText(line, x, cursorY, paint)
        return cursorY - y + lineHeight
    }

    private fun paint(size: Float, color: Int, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }
}
