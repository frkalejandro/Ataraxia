`3.db` es una instantánea vacía del esquema anterior a Ejercicio. Incluye las
definiciones de Habit, Agenda, Sleep, Journal y State de la versión 3, sin datos
personales. SQLDelight aplica `3.sqm` a esta instantánea y compara el resultado
con el esquema actual. `4.sqm` añade el historial de bloques de concentración;
la verificación aplica la cadena 3 → 4 → 5 sin modificar los registros existentes.
