# Палитра tint для пользовательских сплавов

Эта таблица получена из актуальных `textures/item/*_ingot.png` 47
исходных Gonzo-материалов. Для каждого PNG суммируются RGB всех видимых
пикселей с весом по alpha (полностью прозрачный фон не вносит чёрный цвет),
после чего берётся среднее. Его HSL-насыщенность умножается на `1.20`
(не более 100%); hue и lightness не меняются.

Итоговый цвет `G` в `AlloyMaterialCatalog` — последняя колонка. Для
`custom_alloy` сервер складывает эти RGB напрямую с весом material units:
`Σ(RGB × MU) / Σ(MU)`. Поэтому одинаковые пропорции дают одинаковую
конечную палитру независимо от форм входа. Эта точная палитра хранится в
`gonzotech:alloy_tint` и показывается в tooltip. При рендере
`AlloyTintSource` нормализует только её HSV value (максимальный RGB-канал →
255), чтобы серый PNG задавал светлоту/тени и не затемнял палитру повторным
умножением; HSV hue и saturation палитры не меняются.

| Материал | Среднее видимых пикселей | G после saturation +20% |
|---|---|---|
| `calcium` | `#C4C7AF` | `#C6C9AD` |
| `aluminum` | `#BABCBE` | `#BABCBE` |
| `magnesium` | `#A2BCB6` | `#9FBFB7` |
| `sulfur` | `#B5AA40` | `#C1B334` |
| `manganese` | `#662F56` | `#6C2958` |
| `titanium` | `#5F7480` | `#5C7583` |
| `barium` | `#A19472` | `#A6966D` |
| `zinc` | `#B8B9BA` | `#B8B9BA` |
| `tin` | `#A8ADA5` | `#A8AEA4` |
| `boron` | `#313841` | `#2F3843` |
| `chromium` | `#C0C0C5` | `#C0C0C5` |
| `nickel` | `#C9C4BB` | `#CAC4BA` |
| `cobalt` | `#2A499C` | `#1F44A7` |
| `silver` | `#818E9B` | `#7E8E9E` |
| `iodine` | `#922572` | `#9D1A76` |
| `tungsten` | `#2F2634` | `#2F2535` |
| `mercury` | `#A5A5A5` | `#A5A5A5` |
| `uranium` | `#59A554` | `#52AD4C` |
| `zirconium` | `#9B9B9B` | `#9B9B9B` |
| `thorium` | `#343027` | `#353026` |
| `platinum` | `#62BED6` | `#56C5E2` |
| `tellurium` | `#88B280` | `#85B77B` |
| `palladium` | `#DDAA9A` | `#E4A693` |
| `cesium` | `#D1BB4F` | `#DEC442` |
| `iridium` | `#9B9EC0` | `#979BC4` |
| `osmium` | `#8BA8C0` | `#86A8C5` |
| `steel` | `#808080` | `#808080` |
| `stainless_steel` | `#8E9193` | `#8E9193` |
| `corten_steel` | `#A15235` | `#AC4D2A` |
| `cast_iron` | `#888988` | `#888988` |
| `plutonium` | `#9BABA4` | `#99ADA4` |
| `nitinol` | `#B3B8B0` | `#B3B9AF` |
| `invar` | `#A5A4A9` | `#A5A4AA` |
| `lead` | `#606771` | `#5E6773` |
| `neodymium` | `#74859D` | `#7084A1` |
| `ferromagnetic` | `#A896A8` | `#AA94AA` |
| `cantor` | `#6F646E` | `#70636F` |
| `vitreloy` | `#80ACA5` | `#7CB0A8` |
| `semiconductor` | `#6C644A` | `#6F6647` |
| `vr20` | `#5A515D` | `#5B505E` |
| `stellite` | `#4B5A7B` | `#465880` |
| `alnico` | `#CEB87E` | `#D6BC76` |
| `telluride` | `#84AC83` | `#80B07F` |
| `bismuth` | `#635F7E` | `#615C81` |
| `rhenium` | `#4E4E4E` | `#4E4E4E` |
| `radium` | `#509798` | `#499E9F` |
| `lithium` | `#968F8C` | `#978F8B` |
