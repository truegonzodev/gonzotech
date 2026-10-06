# Кастомные звуки Gonzo Tech

Звуковые файлы `.ogg` (Vorbis) и события регистрируются отдельно:

- `sword_ready.ogg` — сигналы достижения 10% и 100% заряда большого меча;
- `sword_impact.ogg` — выполненная заряженная атака;
- `videoplaybak.ogg` — звук видео на доске учёного.

Для нового звука `example.ogg`:
1. В `ModSounds` объявить статическое поле `EXAMPLE = sound("example")` (тип `DeferredHolder<SoundEvent, SoundEvent>`).
2. В `assets/gonzotech/sounds.json` добавить событие и путь `gonzotech:example`.
3. При использовании добавить при необходимости ключ субтитров в обе локализации.
4. Использовать `ModSounds.EXAMPLE.get()` в `playSound`.

Регистратор подключён к шине мода; `sounds.json` сопоставляет события с аудиофайлами.
