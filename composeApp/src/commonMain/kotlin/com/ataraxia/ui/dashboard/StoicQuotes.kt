package com.ataraxia.ui.dashboard

data class StoicQuote(
    val text: String,
    val author: String,
    val work: String,
)

object StoicQuotes {
    private val quotes = listOf(
        StoicQuote(
            text = "No actúes como si fueras a vivir diez mil años. Mientras puedas, conviértete en una buena persona.",
            author = "Marco Aurelio",
            work = "Meditaciones",
        ),
        StoicQuote(
            text = "La mejor venganza es no parecerte a quien te hizo daño.",
            author = "Marco Aurelio",
            work = "Meditaciones",
        ),
        StoicQuote(
            text = "Lo que se interpone en el camino puede convertirse en el camino.",
            author = "Marco Aurelio",
            work = "Meditaciones",
        ),
        StoicQuote(
            text = "Tu mente toma la forma de aquello en lo que piensas con frecuencia.",
            author = "Marco Aurelio",
            work = "Meditaciones",
        ),
        StoicQuote(
            text = "Acepta lo que llega como parte de la trama de tu vida.",
            author = "Marco Aurelio",
            work = "Meditaciones",
        ),
        StoicQuote(
            text = "Las dificultades muestran de qué están hechas las personas.",
            author = "Epicteto",
            work = "Disertaciones",
        ),
        StoicQuote(
            text = "Primero decide qué quieres ser; después actúa de acuerdo con ello.",
            author = "Epicteto",
            work = "Disertaciones",
        ),
        StoicQuote(
            text = "La enfermedad puede limitar al cuerpo, pero no necesariamente a la voluntad.",
            author = "Epicteto",
            work = "Disertaciones",
        ),
        StoicQuote(
            text = "Nadie es verdaderamente libre si no aprende a gobernarse a sí mismo.",
            author = "Epicteto",
            work = "Disertaciones",
        ),
        StoicQuote(
            text = "No intentes controlar lo que no depende de ti. Ocúpate de tus propias decisiones.",
            author = "Epicteto",
            work = "Disertaciones",
        ),
        StoicQuote(
            text = "No tenemos poco tiempo; lo que ocurre es que perdemos mucho.",
            author = "Séneca",
            work = "Sobre la brevedad de la vida",
        ),
        StoicQuote(
            text = "Sufrimos más a menudo en la imaginación que en la realidad.",
            author = "Séneca",
            work = "Cartas a Lucilio",
        ),
        StoicQuote(
            text = "Mientras aplazamos las cosas, la vida sigue pasando.",
            author = "Séneca",
            work = "Cartas a Lucilio",
        ),
        StoicQuote(
            text = "Rodéate de personas que puedan ayudarte a convertirte en alguien mejor.",
            author = "Séneca",
            work = "Cartas a Lucilio",
        ),
        StoicQuote(
            text = "Cada día debería vivirse como si fuera una vida completa.",
            author = "Séneca",
            work = "Cartas a Lucilio",
        ),
    )

    fun random(): StoicQuote = quotes.random()

    fun first(): StoicQuote = quotes.first()
}
