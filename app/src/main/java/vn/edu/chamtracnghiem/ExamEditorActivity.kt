package vn.edu.chamtracnghiem
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import vn.edu.chamtracnghiem.databinding.ActivityEditorBinding
class ExamEditorActivity:AppCompatActivity() { override fun onCreate(s:Bundle?) { super.onCreate(s); val b=ActivityEditorBinding.inflate(layoutInflater);setContentView(b.root); b.save.setOnClickListener { val title=b.title.text.toString().trim(); if(title.isBlank()){b.title.error="Nhập tên bài";return@setOnClickListener}; ExamStore(this).add(title,b.questions.text.toString().toIntOrNull()?:40);Toast.makeText(this,"Đã tạo bài kiểm tra",Toast.LENGTH_SHORT).show();finish() } } }
