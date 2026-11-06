package vn.edu.chamtracnghiem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ExamStore(context: Context) {
    private val prefs = context.getSharedPreferences("exam_store", Context.MODE_PRIVATE)
    init { seedSampleKeys() }
    fun exams(): MutableList<Exam> = try {
        val a = JSONArray(prefs.getString("exams", "[]")); MutableList(a.length()) { i ->
            a.getJSONObject(i).let { Exam(it.getLong("id"), it.getString("title"), it.getInt("count")) }
        }
    } catch (_: Exception) { mutableListOf() }
    fun add(title: String, count: Int): Exam {
        val e = Exam(System.currentTimeMillis(), title, count.coerceIn(1, 40)); val list = exams(); list.add(e); save(list); return e
    }
    private fun save(list: List<Exam>) { prefs.edit().putString("exams", JSONArray().also { a -> list.forEach { e -> a.put(JSONObject().put("id",e.id).put("title",e.title).put("count",e.questionCount)) } }.toString()).apply() }
    fun key(exam: Exam, code: String): AnswerKey? = try { JSONObject(prefs.getString("key_${exam.id}_$code", "")!!).let { AnswerKey(code, it.getString("answers").toList()) } } catch (_: Exception) { null }
    fun saveKey(exam: Exam, code: String, answers: List<Char>) { prefs.edit().putString("key_${exam.id}_$code", JSONObject().put("answers", answers.joinToString("")).toString()).apply() }
    fun codes(exam: Exam): List<String> = prefs.all.keys.filter { it.startsWith("key_${exam.id}_") }.map { it.substringAfterLast('_') }.sorted()

    /** Bundled answer keys transcribed from the supplied four answer-key photographs. */
    private fun seedSampleKeys() {
        val list = exams()
        val exam = if (list.isNotEmpty()) list[0] else add("GDCD 6 – Phiếu mẫu", 40)
        // Explicit lists avoid ambiguity from table line breaks in the photographed source.
        val exact = mapOf(
            "001" to listOf("B","C","C","C","A","B","A","C","D","C","C","A","B","B","C","A","C","D","B","B","C","C","A","C","D","D","C","A","B","B","C","C","A","B","A","D","D","C","D","D"),
            "002" to listOf("A","C","C","C","C","B","D","C","D","B","B","A","D","B","D","C","D","B","D","A","C","A","C","C","C","D","C","C","B","D","A","A","A","D","A","A","B","A","D","C"),
            "003" to listOf("B","D","A","B","C","B","B","C","C","B","D","D","C","C","B","B","A","C","B","B","B","B","C","D","D","D","B","A","A","A","B","D","B","B","A","A","C","C","A","C"),
            "004" to listOf("B","B","B","C","A","B","C","D","D","C","D","C","B","B","C","D","B","D","D","D","C","A","D","C","C","A","D","C","A","A","D","A","C","B","B","C","A","B","B","A")
        )
        exact.forEach { (code, answers) -> saveKey(exam, code, answers.map { it[0] }) }
        prefs.edit().putBoolean("sample_keys_v1", true).apply()
    }
}
