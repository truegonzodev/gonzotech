# Инструкция по работе с Git и веткой проекта (от А до Я)

Эта инструкция создана специально для быстрой работы с веткой **`arena/01a08b48-gonzotech`**, обновления текстур и решения всех типичных ошибок Git.

---

## 🚀 Быстрый старт: Как накатить свои текстуры и запушить

### Шаг 1. Переключиться на актуальную ветку
Открой терминал в папке проекта и выполни:

```bash
# 1. Загрузить все актуальные ветки с GitHub
git fetch origin --prune

# 2. Переключиться на актуальную рабочую ветку (сбросив указатель на серверный)
git checkout -B arena/01a08b48-gonzotech origin/arena/01a08b48-gonzotech
```

### Шаг 2. Заменить файлы текстур
Скопируй свои файлы/папки в папку:
`src/main/resources/assets/gonzotech/textures/`

### Шаг 3. Закоммитить и отправить на GitHub
```bash
# 1. Проверить, какие текстуры изменились
git status

# 2. Добавить изменённые файлы в коммит
git add src/main/resources/assets/gonzotech/textures

# 3. Создать коммит
git commit -m "Текстуры: обновление ассетов"

# 4. Запушить в рабочую ветку
git push origin arena/01a08b48-gonzotech
```

---

## 🛠 Разбор частых ошибок и как их мгновенно решить

### Ошибка 1: `Your local changes to the following files would be overwritten by checkout`
**Причина:** У тебя локально изменены какие-то файлы, и Git боится их стереть при переключении ветки.

**Решение А (если локальные правки НЕ нужны / текстуры уже скопированы отдельно):**
```bash
git reset --hard
git checkout arena/01a08b48-gonzotech
git pull
```

**Решение Б (если хочешь сохранить текущие локальные файлы во временный карман):**
```bash
git stash
git checkout arena/01a08b48-gonzotech
git pull
git stash pop
```

---

### Ошибка 2: `error: pathspec 'arena/01a08b48-gonzotech' did not match any file(s) known to git`
**Причина:** Твой локальный Git ещё не знает о существовании новой ветки на сервере.

**Решение:**
```bash
git fetch origin
git checkout -b arena/01a08b48-gonzotech origin/arena/01a08b48-gonzotech
```

---

### Ошибка 3: `[rejected - non-fast-forward] / Updates were rejected because the remote contains work`
**Причина:** На GitHub появился новый коммит от агента/бота, пока ты готовил свои изменения.

**Решение:**
```bash
# Стянуть свежие коммиты поверх своих
git pull --rebase origin arena/01a08b48-gonzotech

# Отправить снова
git push origin arena/01a08b48-gonzotech
```

---

### Ошибка 4: Ветка перепутана, локальные ветки отличаются, куча старых удалённых веток
**Причина:** В локальном репозитории накопились хвосты от старых сессий (`01a0686a`, `pc-update` и т.д.).

**Решение (полная синхронизация со свежим состоянием):**
```bash
# Очистить список удалённых веток
git remote prune origin

# Принудительно выставить рабочую ветку в точное состояние с сервера
git fetch origin
git checkout -B arena/01a08b48-gonzotech origin/arena/01a08b48-gonzotech
git branch -u origin/arena/01a08b48-gonzotech
```

---

## 📌 Памятка по полезным командам

| Действие | Команда |
|---|---|
| Узнать текущую ветку и статус файлов | `git status` |
| Посмотреть последние 5 коммитов | `git log -n 5 --oneline` |
| Отменить все локальные незакоммиченные правки | `git reset --hard` |
| Стянуть последние обновления с сервера | `git pull` |
