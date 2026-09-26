# Кастомные звуки Gonzo Tech

Сюда добавляются `.ogg` (Vorbis; для позиционного звука — mono).
Пока реальных звуков нет, `../sounds.json` намеренно пуст, фиктивных событий нет.

Для нового звука `example.ogg`:
1. В `ModSounds` объявить статическое поле `EXAMPLE = sound("example")` (тип `DeferredHolder<SoundEvent, SoundEvent>`).
2. В `assets/gonzotech/sounds.json` добавить:
   `"example": {"subtitle": "subtitles.gonzotech.example", "sounds": ["gonzotech:example"]}`.
3. Добавить перевод `subtitles.gonzotech.example` в обе локализации.
4. Использовать `ModSounds.EXAMPLE.get()` в `playSound`.

Регистратор уже подключён к шине мода; `sounds.json` регистрирует аудиофайлы на клиенте.
