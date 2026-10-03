# Ataraxia

Con mucho orgullo presentamos la última versión de Ataraxia, una aplicación diseñada para ayudarte a organizar tus actividades, construir hábitos saludables y mantener un mayor equilibrio en tu vida diaria.

Esta versión cuenta con las siguientes funcionalidades:

- Creación y seguimiento de hábitos organizados en mañana, durante el día y noche.
- Registro diario del cumplimiento de hábitos y seguimiento de rachas.
- Recordatorios a las 08:00, 14:00 y 22:00, según la categoría de cada hábito. En Android se pueden activar o desactivar individualmente desde la campana de Hábitos; la elección se conserva al cerrar o reiniciar la app.
- Agenda para crear y administrar tareas y eventos.
- Ejercicio: biblioteca manual por cuello, hombros, pecho, tríceps, bíceps, antebrazos, espalda, abdominales y piernas. Rutinas editables por series/repeticiones o por un tiempo total compartido entre varios ejercicios (máximas repeticiones), con contador y pausa. Las sesiones se guardan al confirmar que se realizaron y se muestran todas las del último día de entrenamiento. La sesión activa se conserva al reiniciar la app; en Android las rutinas por tiempo muestran una cuenta regresiva en notificaciones y emiten un aviso con sonido al terminar.
- Espacio de concentración (Enfoque) con bloques de 15, 25 o 50 minutos, pausa y reinicio; descansos de 5 minutos y una pausa de 15 cada cuatro sesiones. El contador continúa al navegar entre secciones y se actualiza al volver del segundo plano; cada bloque se inicia manualmente. Android conserva el temporizador al cerrar la app, muestra la fase y la cuenta regresiva en notificaciones, y avisa con sonido al terminar la concentración o el descanso.
- En Android, avisos de agenda a las 08:00 (hora local) con eventos y tareas pendientes del día. Los lunes se agregan resúmenes de esta semana y de la próxima, de lunes a domingo, leídos desde la agenda al emitir el aviso.
- Registro y seguimiento de las horas de sueño.
- Calculadora de ciclos de sueño basada en la hora de despertar y la cantidad de ciclos deseados.
- Notificaciones una hora, treinta minutos y diez minutos antes de la hora recomendada para dormir.
- Frases estoicas de Marco Aurelio, del gran Epicteto y Séneca mostradas aleatoriamente en la pantalla principal.
- Panel de inicio con un resumen de hábitos, tareas, eventos y descanso, más tarjetas de Ejercicio, Enfoque, Diario y Estado que abren sus respectivas pantallas. Ejercicio invita a entrenar o muestra «Descansa» si ya hay una sesión confirmada hoy. Enfoque muestra el tiempo registrado en bloques de concentración completados hoy, sin sumar descansos ni bloques incompletos.
- Historial persistente de concentración: cada bloque completado se registra una sola vez, incluso al recuperar el temporizador o recibir su alarma en segundo plano. Se asigna al día local en que terminó. El registro comienza con esta versión; los contadores antiguos no tienen fecha y no se convierten en historial.
- En Android, recordatorio diario a las 21:00 (hora local) para rellenar Estado. Tocar el aviso abre Estado. El canal «Recordatorio de Estado» permite silenciarlo desde los ajustes de Android.
- Interfaz adaptable a diferentes tamaños de pantalla.
Con Ataraxia buscamos entregar una herramienta sencilla y accesible que permita mejorar la organización personal, fortalecer la constancia y promover un estilo de vida más consciente.

_Proximos pasos... Atarax(IA)?_

Los avisos de agenda requieren permitir notificaciones; se pueden silenciar desde el canal «Agenda diaria y semanal» en los ajustes de Android. El sistema puede retrasarlos si no permite alarmas exactas. Se reprograman tras reiniciar o cambiar la zona horaria. Los avisos programados y las notificaciones de temporizadores no están implementados en escritorio ni iOS.

En Android, los temporizadores usan los canales «Temporizadores activos» (silencioso) y «Temporizadores terminados» (sonido y vibración). Al pausar, reiniciar o descartar se retira la cuenta regresiva y se cancela su alarma; al continuar se programa el nuevo final. Tocar una notificación abre Enfoque o Ejercicio. Ambos temporizadores pueden funcionar a la vez. Para avisos puntuales en segundo plano, usa «Permitir alarmas exactas» en Enfoque o en una rutina por tiempo. Sin ese permiso, Android puede retrasar el aviso; el volumen, el modo No molestar y los ajustes del canal controlan el sonido. Forzar la detención de la app desde Android impide recibir alarmas hasta volver a abrirla. Véase la [documentación de alarmas de Android](https://developer.android.com/develop/background-work/services/alarms).
