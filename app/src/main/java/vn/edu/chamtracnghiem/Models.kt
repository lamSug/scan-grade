package vn.edu.chamtracnghiem

data class Exam(val id: Long, val title: String, val questionCount: Int)
data class AnswerKey(val code: String, val answers: List<Char>)
data class ScanResult(val code: String, val choices: List<Char?>, val correct: Int, val total: Int)
