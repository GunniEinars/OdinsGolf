package com.odinsgolf.data

import android.content.Context
import com.odinsgolf.data.dto.CourseDto
import com.odinsgolf.data.model.Course
import kotlinx.serialization.json.Json

/**
 * Loads course definitions from the assets "courses" folder. Parsing is tolerant of
 * unknown keys so the JSON schema can grow without breaking older builds.
 */
class CourseRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Result of trying to load a course, with a friendly error for the UI. */
    sealed interface LoadResult {
        data class Success(val course: Course) : LoadResult
        data class Failure(val message: String) : LoadResult
    }

    /** Minimal course descriptor for the picker. */
    data class CourseSummary(val file: String, val id: String, val name: String, val clubName: String)

    fun listCourseFiles(): List<String> =
        runCatching { context.assets.list("courses")?.toList() ?: emptyList() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".json", ignoreCase = true) }

    /** Lightweight list of bundled courses for the picker (id/name per file). */
    fun listCourses(): List<CourseSummary> = listCourseFiles().mapNotNull { file ->
        runCatching {
            val text = context.assets.open("courses/$file").bufferedReader().use { it.readText() }
            val dto = json.decodeFromString<CourseDto>(text)
            CourseSummary(file = file, id = dto.courseId, name = dto.courseName, clubName = dto.clubName)
        }.getOrNull()
    }.sortedBy { it.name }

    fun loadCourse(fileName: String = DEFAULT_COURSE_FILE): LoadResult = try {
        val text = context.assets.open("courses/$fileName").bufferedReader().use { it.readText() }
        val dto = json.decodeFromString<CourseDto>(text)
        LoadResult.Success(dto.toDomain())
    } catch (e: Exception) {
        LoadResult.Failure("Could not load course '$fileName': ${e.message}")
    }

    companion object {
        const val DEFAULT_COURSE_FILE = "setbergsvollur.json"
    }
}
