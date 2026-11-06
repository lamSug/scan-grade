package vn.edu.chamtracnghiem

import android.graphics.*
import kotlin.math.*

/** OMR reader for the single TN Team 40-question form supplied by the user. */
object OmrReader {
    private data class Pt(val x: Float, val y: Float)
    private const val W = 1000f
    private const val H = 1414f
    private fun gray(c: Int) = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
    private fun darkness(b: Bitmap, p: Pt, radius: Int): Float { var total=0f; var n=0; for(y in (p.y-radius).toInt()..(p.y+radius).toInt() step 2) for(x in (p.x-radius).toInt()..(p.x+radius).toInt() step 2) if(x in 0 until b.width && y in 0 until b.height){total+=255-gray(b.getPixel(x,y));n++}; return if(n==0) 0f else total/n }
    private fun marker(b: Bitmap, cx: Float, cy: Float): Pt { val span=(min(b.width,b.height)*.13f).toInt(); val step=max(3,min(b.width,b.height)/180); val radius=max(5,min(b.width,b.height)/110); var best=Pt(cx,cy);var score=-1f; for(y in (cy-span).toInt()..(cy+span).toInt() step step) for(x in (cx-span).toInt()..(cx+span).toInt() step step){val s=darkness(b,Pt(x.toFloat(),y.toFloat()),radius);if(s>score){score=s;best=Pt(x.toFloat(),y.toFloat())}};return best }
    /** Solves template destination -> source image homography from the four printed squares. */
    private fun homography(src: Array<Pt>): (Float,Float)->Pt { val dst=arrayOf(Pt(0f,0f),Pt(W,0f),Pt(0f,H),Pt(W,H));val m=Array(8){FloatArray(9)};for(i in 0..3){val X=dst[i].x;val Y=dst[i].y;val x=src[i].x;val y=src[i].y;val r=i*2;m[r]=floatArrayOf(X,Y,1f,0f,0f,0f,-x*X,-x*Y,x);m[r+1]=floatArrayOf(0f,0f,0f,X,Y,1f,-y*X,-y*Y,y)};for(col in 0 until 8){var p=col;for(r in col+1 until 8)if(abs(m[r][col])>abs(m[p][col]))p=r;val t=m[col];m[col]=m[p];m[p]=t;val d=m[col][col];for(j in col..8)m[col][j]/=d;for(r in 0 until 8)if(r!=col){val q=m[r][col];for(j in col..8)m[r][j]-=q*m[col][j]}};val a=FloatArray(8){m[it][8]};return{X,Y->val den=a[6]*X+a[7]*Y+1f;Pt((a[0]*X+a[1]*Y+a[2])/den,(a[3]*X+a[4]*Y+a[5])/den)} }
    private fun slot(q:Int,o:Int):Pt { val x=when{q<10->650f;q<20->165f;q<30->418f;else->645f};val y=if(q<10)381f+q*44.5f else 872f+(q%10)*49f;return Pt(x+o*42f,y) }
    private fun codeSlot(d:Int,c:Int)=Pt(410f+c*43f,381f+d*44.5f)
    fun read(source:Bitmap,count:Int,key:AnswerKey?):Pair<ScanResult,Bitmap>{
        val b = source.copy(Bitmap.Config.ARGB_8888, true)
        val tl = marker(b, b.width * .165f, b.height * .10f)
        val tr = marker(b, b.width * .80f, b.height * .085f)
        val bl = marker(b, b.width * .15f, b.height * .80f)
        val br = marker(b, b.width * .80f, b.height * .80f)
        if (tr.x - tl.x < b.width * .35f || bl.y - tl.y < b.height * .45f) throw IllegalArgumentException("Không nhận được đủ 4 ô định vị")
        val map = homography(arrayOf(tl, tr, bl, br))
        fun score(p: Pt): Float = darkness(b, map(p.x, p.y), max(5, min(b.width, b.height) / 145))
        fun selected(points: List<Pt>): Int? {
            val values = points.map(::score); val sorted = values.sortedDescending()
            return if (sorted[0] > 55f && sorted[0] - sorted.getOrElse(1) { 0f } > 9f) values.indexOf(sorted[0]) else null
        }
        val code = (0..2).map { c -> selected((0..9).map { d -> codeSlot(d, c) })?.toString() ?: "?" }.joinToString("")
        val choices = MutableList<Char?>(count) { null }; val positions = MutableList<Pt?>(count) { null }
        for (q in 0 until count) {
            val answer = selected((0..3).map { slot(q, it) })
            if (answer != null) { choices[q] = ('A'.code + answer).toChar(); positions[q] = map(slot(q, answer).x, slot(q, answer).y) }
        }
        val correct = if (key == null) 0 else choices.indices.count { choices[it] != null && it < key.answers.size && choices[it] == key.answers[it] }
        val canvas = Canvas(b); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(4f, b.width * .004f) }; val radius = max(10f, b.width * .018f)
        choices.indices.forEach { i -> positions[i]?.let { p -> val ok = key != null && i < key.answers.size && choices[i] == key.answers[i]; paint.color = if (ok) Color.rgb(0, 190, 80) else Color.rgb(230, 50, 45); canvas.drawCircle(p.x, p.y, radius, paint) } }
        return ScanResult(code, choices, correct, count) to b
    }
}
