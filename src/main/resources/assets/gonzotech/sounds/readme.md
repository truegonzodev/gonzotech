# Кастомные звуки Gonzo Tech

Звуковые файлы `.ogg` (Vorbis) и события регистрируются отдельно:

- `sword_ready.ogg` — сигналы достижения 10% и 100% заряда большого меча;
- `sword_impact.ogg` — выполненная заряженная атака;
- `videoplaybak.ogg` — звук видео на доске учёного;
- `ost/gt_ost_40000ft.ogg`, `ost/gt_ost_bitter.ogg`, `ost/gt_ost_cradle.ogg`,
  `ost/gt_ost_ethereal.ogg`, `ost/gt_ost_raidin_bones.ogg` — стерео-OST, зарегистрированные как
  музыкальные события со стримингом (`stream: true`). Их расписание и пулы описаны в
  `GonzoMusicClient` и в `data/gonzotech/worldgen/biome/*.json`.

Для нового звука `example.ogg`:
1. В `ModSounds` объявить статическое поле `EXAMPLE = sound("example")` (тип `DeferredHolder<SoundEvent, SoundEvent>`).
2. В `assets/gonzotech/sounds.json` добавить событие и путь `gonzotech:example`.
3. При использовании добавить при необходимости ключ субтитров в обе локализации.
4. Использовать `ModSounds.EXAMPLE.get()` в `playSound`.

Регистратор подключён к шине мода; `sounds.json` сопоставляет события с аудиофайлами.
