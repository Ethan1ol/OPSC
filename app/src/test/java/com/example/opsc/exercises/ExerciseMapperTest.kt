package com.example.opsc.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for turning wger API data into cache rows, including missing and odd data. */
class ExerciseMapperTest {
    private val categories = listOf(CategoryDto(10, "Abs"), CategoryDto(9, "Legs"))
    private val muscles = listOf(
        MuscleDto(11, "Biceps femoris", "Hamstrings"),
        MuscleDto(8, "Gluteus maximus", "Glutes"),
        MuscleDto(5, "Soleus", null)
    )
    private val equipment = listOf(EquipmentDto(10, "Kettlebell"), EquipmentDto(1, "Barbell"))

    private fun translation(exercise: Int?, name: String?, language: Int? = 2, description: String? = "<p>Do it.</p>") =
        TranslationDto(id = 1, exercise = exercise, name = name, description = description, language = language)

    private fun merge(exercises: List<ExerciseDto>, translations: List<TranslationDto>) =
        ExerciseMapper.merge(exercises, translations, categories, muscles, equipment)

    @Test fun joinsExerciseWithItsEnglishTranslation() {
        val result = merge(
            listOf(ExerciseDto(9, 10, listOf(11, 8), listOf(5), listOf(10))),
            listOf(translation(9, "Kettlebell Swing"))
        )
        assertEquals(1, result.size)
        val e = result.first()
        assertEquals("Kettlebell Swing", e.name)
        assertEquals("Abs", e.categoryName)
        assertEquals("Hamstrings, Glutes", e.musclesText)
        assertEquals("Soleus", e.secondaryMusclesText) // falls back to the anatomical name
        assertEquals("Kettlebell", e.equipmentText)
    }

    @Test fun ignoresTranslationsInOtherLanguages() {
        val result = merge(
            listOf(ExerciseDto(9, 10, null, null, null)),
            listOf(translation(9, "Swing com kettlebell", language = 7), translation(9, "Kettlebell Swing", language = 2))
        )
        assertEquals("Kettlebell Swing", result.single().name)
    }

    @Test fun skipsExercisesWithNoEnglishTranslation() {
        val result = merge(listOf(ExerciseDto(9, 10, null, null, null)), listOf(translation(9, "Swing", language = 7)))
        assertTrue(result.isEmpty())
    }

    @Test fun skipsExercisesWithBlankOrMissingNames() {
        val result = merge(
            listOf(ExerciseDto(1, 10, null, null, null), ExerciseDto(2, 10, null, null, null)),
            listOf(translation(1, "   "), translation(2, null))
        )
        assertTrue(result.isEmpty())
    }

    @Test fun survivesMissingFieldsEverywhere() {
        val result = merge(
            listOf(ExerciseDto(null, null, null, null, null), ExerciseDto(3, null, null, null, null)),
            listOf(translation(3, "Plank", description = null), translation(null, "Orphan"))
        )
        val plank = result.single()
        assertEquals("Plank", plank.name)
        assertEquals("", plank.description)
        assertEquals(0, plank.categoryId)
        assertEquals("", plank.categoryName)
        assertEquals("", plank.musclesText)
    }

    @Test fun unknownIdsInLookupsAreIgnored() {
        val result = merge(
            listOf(ExerciseDto(3, 999, listOf(404), listOf(405), listOf(406))),
            listOf(translation(3, "Plank"))
        )
        assertEquals("", result.single().categoryName)
        assertEquals("", result.single().musclesText)
        assertEquals("", result.single().equipmentText)
    }

    @Test fun duplicateExercisesAreKeptOnce() {
        val dupes = listOf(ExerciseDto(3, 10, null, null, null), ExerciseDto(3, 10, null, null, null))
        assertEquals(1, merge(dupes, listOf(translation(3, "Plank"))).size)
    }

    @Test fun resultIsSortedByNameIgnoringCase() {
        val result = merge(
            listOf(ExerciseDto(1, 10, null, null, null), ExerciseDto(2, 10, null, null, null), ExerciseDto(3, 10, null, null, null)),
            listOf(translation(1, "squat"), translation(2, "Bench press"), translation(3, "Deadlift"))
        )
        assertEquals(listOf("Bench press", "Deadlift", "squat"), result.map { it.name })
    }

    @Test fun readsTheNextOffsetFromAWgerLink() {
        assertEquals(100, ExerciseMapper.nextOffset("https://wger.de/api/v2/exercise/?format=json&language=2&limit=100&offset=100"))
        assertEquals(2, ExerciseMapper.nextOffset("https://wger.de/api/v2/exerciseinfo/?language=2&limit=2&offset=2&search=x"))
    }

    @Test fun noNextLinkMeansLastPage() {
        assertNull(ExerciseMapper.nextOffset(null))
        assertNull(ExerciseMapper.nextOffset(""))
        assertNull(ExerciseMapper.nextOffset("https://wger.de/api/v2/exercise/?limit=100"))
    }
}
