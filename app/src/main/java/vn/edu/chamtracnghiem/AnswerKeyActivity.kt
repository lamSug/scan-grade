package vn.edu.chamtracnghiem
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import vn.edu.chamtracnghiem.databinding.ActivityAnswerKeyBinding
class AnswerKeyActivity:AppCompatActivity() { override fun onCreate(s:Bundle?) { super.onCreate(s); val b=ActivityAnswerKeyBinding.inflate(layoutInflater);setContentView(b.root); val e=ExamStore(this).exams().find { it.id==intent.getLongExtra("exam",-1) }?:run{finish();return}; val store=ExamStore(this);b.examName.text=e.title
 fun refresh(){ b.saved.text = store.codes(e).joinToString("\n") { c -> val a=store.key(e,c)?.answers?.joinToString("") ?: ""; "Mã đề $c: $a" }.ifBlank { "Chưa có đáp án" } }
 refresh()
 b.saveKey.setOnClickListener { val c=b.code.text.toString().padStart(3,'0'); val a=b.answers.text.toString().uppercase().filter { it in "ABCD" }.toList(); if(c.length!=3||a.size!=e.questionCount){Toast.makeText(this,"Mã đề gồm 3 số và cần đủ ${e.questionCount} đáp án A–D",Toast.LENGTH_LONG).show();return@setOnClickListener};store.saveKey(e,c,a);refresh();Toast.makeText(this,"Đã lưu mã đề $c",Toast.LENGTH_SHORT).show() }
} }
