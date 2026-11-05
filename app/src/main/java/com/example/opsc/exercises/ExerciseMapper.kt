package com.example.opsc.exercises

/**
 * Turns the raw wger lists into [ExerciseEntity] rows. Pure Kotlin so it can be unit tested,
 * and written to survive missing or odd data: anything that cannot be used is skipped, never thrown.
 */
object ExerciseMapper {

    /**
     * Joins exercises with their English translation (by exercise id) and the lookup tables.
     * Exercises with no usable name are dropped. The result is sorted by name.
     */
    fun merge(
        exercises: List<ExerciseDto>,
        translations: List<TranslationDto>,
        categories: List<CategoryDto>,
        muscles: List<MuscleDto>,
        equipment: List<EquipmentDto>
    ): List<ExerciseEntity> {
        val categoryNames = categories.mapNotNull { c -> c.id?.let { it to c.name.orEmpty().trim() } }.toMap()
        val muscleNames = muscles.mapNotNull { m -> m.id?.let { it to muscleLabel(m) } }.toMap()
        val equipmentNames = equipment.mapNotNull { e -> e.id?.let { it to e.name.orEmpty().trim() } }.toMap()

        // wger lists every language; keep English only, and the first translation if there are several.
        val english = translations
            .filter { (it.language ?: WgerApi.ENGLISH) == WgerApi.ENGLISH && !it.name.isNullOrBlank() && it.exercise != null }
            .groupBy { it.exercise!! }
            .mapValues { it.value.first() }

        return exercises.mapNotNull { exercise ->
            val id = exercise.id ?: return@mapNotNull null
            val translation = english[id] ?: return@mapNotNull null
            val categoryId = exercise.category ?: 0
            ExerciseEntity(
                id = id,
                name = translation.name.orEmpty().trim(),
                description = translation.description.orEmpty().trim(),
                categoryId = categoryId,
                categoryName = categoryNames[categoryId].orEmpty(),
                musclesText = names(exercise.muscles, muscleNames),
                secondaryMusclesText = names(exercise.musclesSecondary, muscleNames),
                equipmentText = names(exercise.equipment, equipmentNames)
            )
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    /** "Hamstrings" (the everyday name) if wger gives one, otherwise the anatomical name. */
    private fun muscleLabel(m: MuscleDto): String =
        m.nameEn?.trim().takeUnless { it.isNullOrEmpty() } ?: m.name.orEmpty().trim()

    private fun names(ids: List<Int>?, lookup: Map<Int, String>): String =
        ids.orEmpty().mapNotNull { lookup[it]?.takeIf { name -> name.isNotBlank() } }.distinct().joinToString(", ")

    /** Reads the `offset` from a wger "next" link, or null if there is no next page. */
    fun nextOffset(nextUrl: String?): Int? {
        if (nextUrl.isNullOrBlank()) return null
        return Regex("[?&]offset=(\\d+)").find(nextUrl)?.groupValues?.get(1)?.toIntOrNull()
    }
}
