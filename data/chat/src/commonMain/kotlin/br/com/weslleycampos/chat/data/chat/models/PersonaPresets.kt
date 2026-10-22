package br.com.weslleycampos.chat.data.chat.models

/**
 * The three personas of the design prototype's `PERSONAS`, in its order, which every install starts
 * with. They are seeded into the database once and read back from it, as the user's own personas
 * are: observe them through `ModelRepository.observePersonas`, not from here.
 */
object PersonaPresets {
    val personas: List<Persona> = listOf(
        Persona(
            id = "reviewer",
            name = "Code reviewer",
            model = ModelCatalogue["sonnet"],
            prompt = "Correctness first, style last. Cite line numbers. No praise.",
        ),
        Persona(
            id = "terse",
            name = "Terse",
            model = ModelCatalogue["gpt5"],
            prompt = "One paragraph max. Code over prose.",
        ),
        Persona(
            id = "digest",
            name = "Paper digest",
            model = ModelCatalogue["gemini"],
            prompt = "Claim, method, evidence, caveats. Bullets.",
        ),
    )
}
