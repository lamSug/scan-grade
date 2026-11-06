package vn.edu.chamtracnghiem

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Transparent ZipGrade-style positioning guide for the four black registration squares. */
class SheetGuideView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dim = Paint().apply { color = 0x66000000; style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF35D07F.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    override fun onDraw(c: Canvas) { super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat(); val sheetW = min(w * .88f, h * .72f); val sheetH = sheetW * 1.414f
        val l = (w-sheetW)/2; val t = (h-sheetH)/2 - h*.05f; val r=l+sheetW; val b=t+sheetH
        c.drawRect(0f,0f,w,t,dim); c.drawRect(0f,b,w,h,dim); c.drawRect(0f,t,l,b,dim); c.drawRect(r,t,w,b,dim)
        c.drawRoundRect(l,t,r,b,10f,10f,line)
        // Generous target boxes mirror the large finders in ZipGrade and remain usable while handheld.
        val s=sheetW*.145f; listOf(l to t,r-s to t,l to b-s,r-s to b-s).forEach { (x,y) -> c.drawRect(x,y,x+s,y+s,line) }
        c.drawText("Đưa 4 ô vuông đen vào khung",w/2,t-16f,text)
    }
}
