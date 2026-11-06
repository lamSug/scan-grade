package vn.edu.chamtracnghiem

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.chamtracnghiem.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding; private lateinit var store: ExamStore
    override fun onCreate(s: Bundle?) { super.onCreate(s); b=ActivityMainBinding.inflate(layoutInflater); setContentView(b.root); store=ExamStore(this)
        b.create.setOnClickListener { startActivity(Intent(this, ExamEditorActivity::class.java)) }; b.scan.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)) }
        b.exams.layoutManager=LinearLayoutManager(this)
    }
    override fun onResume() { super.onResume(); b.exams.adapter=ExamAdapter(store.exams()) { e -> startActivity(Intent(this, AnswerKeyActivity::class.java).putExtra("exam",e.id)) } }
}
private class ExamAdapter(private val items: List<Exam>, private val click:(Exam)->Unit): RecyclerView.Adapter<ExamAdapter.H>() {
    class H(v: View):RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=H(TextView(p.context).apply { setPadding(20,24,20,24); textSize=17f; setBackgroundColor(0xFFFFFFFF.toInt()) })
    override fun getItemCount()=items.size
    override fun onBindViewHolder(h:H,i:Int) { h.itemView as TextView; (h.itemView as TextView).text="${items[i].title}\n${items[i].questionCount} câu  •  Chạm để quản lý đáp án"; h.itemView.setOnClickListener { click(items[i]) } }
}
