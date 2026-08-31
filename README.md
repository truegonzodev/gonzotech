# Gonzo Tech — стартовый скелет мода (NeoForge)

Это не официальный MDK-архив, а собранный вручную минимальный проект
(build.gradle / gradle.properties / neoforge.mods.toml / стартовый класс
+ пустые пакеты под core/chalkboard/machines/space/mind/swag).
Чего тут **нет** — Gradle Wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`) — его нужно сгенерировать у себя
локально, у меня в среде нет доступа к серверам Gradle/NeoForge, чтобы
скачать бинарники.

## Шаг 1. Сверить версии (обязательно, до первой сборки)

В `gradle.properties` стоят версии-плейсхолдеры (Minecraft 1.21.8,
NeoForge 21.8.30, ModDevGradle 2.0.78). Их надо свериться с актуальными:

- NeoForge / Minecraft: https://projects.neoforged.net/neoforged/neoforge
- ModDevGradle (версия плагина в `build.gradle`):
  https://projects.neoforged.net/neoforged/ModDevGradle
- Parchment-маппинги: https://parchmentmc.org/docs/getting-started

Поправьте `neo_version`, `minecraft_version`,
`parchment_mappings_version` и версию плагина в `build.gradle` под то,
что видите на сайтах.

## Шаг 2. Сгенерировать Gradle Wrapper

Нужен локально установленный Gradle (8.8+) один раз, дальше проект
будет пользоваться wrapper'ом сам:

```bash
gradle wrapper --gradle-version 8.10
```

Это создаст `gradlew`, `gradlew.bat` и `gradle/wrapper/*`.

Альтернатива: скачать официальный MDK-архив для нужной версии с
https://github.com/NeoForgeMDKs (репозитории вида
`MDK-1.21.X-ModDevGradle`) — там wrapper уже есть, можно просто
скопировать `gradlew`/`gradlew.bat`/`gradle/` из него в эту папку,
ничего больше из того архива не нужно.

## Шаг 3. Собрать и запустить

```bash
./gradlew build          # первая сборка потянет NeoForge userdev, будет долго
./gradlew runClient      # запустит клиент с модом
```

Если в IntelliJ IDEA — просто открыть папку как Gradle-проект,
IDE сама предложит настроить run-конфигурации (`runClient`, `runServer`,
`runData`).

## Критерий готовности этого шага (Фаза 0 роадмапа)

Мод собирается, `GonzoTechMod` логирует в консоль при старте клиента —
на этом фундамент готов, дальше по роадмапу:

1. `core.SeedFormulaManager` — сид → SHA-256 → 16 детерминированных
   формул, сохранение в WorldSavedData.
2. `chalkboard` — блок доски + простейший GUI.
3. `machines` — первый мультиблок (Стирлинг).

## Структура пакетов

```
com.gonzotech
├── core        — сид-ключ, порт движка формул, WorldSavedData
├── chalkboard  — блок доски, GUI, свободная сборка каркаса
├── machines    — мультиблоки (Стирлинг, турбины, реакторы, токамаки...)
├── space       — Dimension-типы, терминал, криокамера, червоточины
├── mind        — стресс/психоз/глитчи
└── swag        — транспорт, плазменный динамик/вейп и т.д.
```
