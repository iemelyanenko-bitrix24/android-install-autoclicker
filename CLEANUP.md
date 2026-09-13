# Откат установки тулчейна

Этой сессией на Mac были поставлены только три Homebrew-пакета — специально
так, чтобы ничего не трогало систему за пределами Cellar/Caskroom, ничего не
дописывалось в `~/.zshrc`, и всё снималось стандартным `brew uninstall`.

## Что было поставлено

| Пакет | Тип | Команда установки |
|---|---|---|
| `openjdk@17` | formula, keg-only (не линкован в `/opt/homebrew`, не трогает системный `java`) | `brew install openjdk@17` |
| `android-commandlinetools` | cask (SDK: `platforms;android-34`, `build-tools;34.0.0`, `platform-tools` — докачаны отдельно через `sdkmanager` внутрь того же каталога) | `brew install --cask android-commandlinetools` |
| `gradle` (временно, только чтобы сгенерировать `gradle-wrapper.jar`) | formula | `brew install gradle` → уже удалён этой же сессией (`brew uninstall gradle`, заодно снёс свои зависимости `gradle-completion` и generic `openjdk`) |

`adb` в `/opt/homebrew/bin/adb` (формула `android-platform-tools`) стоял на
машине ещё до этой сессии — этой установкой не создан, в откат не включён.

## Полный откат

```bash
brew uninstall --cask android-commandlinetools
brew uninstall openjdk@17
brew autoremove
```

`brew autoremove` уберёт транзитивные зависимости (`libtiff`, `little-cms2` —
подтянулись как зависимости `openjdk@17`), если их не использует что-то ещё
на машине. Если `brew autoremove` перечислит что-то неожиданное — сначала
посмотреть `brew autoremove --dry-run`, а не выполнять вслепую.

## Файлы проекта, которые перестанут быть нужны

```bash
rm -f .env.local.sh local.properties
rm -f gradlew gradlew.bat
rm -rf gradle/wrapper/gradle-wrapper.jar
rm -rf app/build build .gradle
```

Ничего из этого не закоммичено (все в `.gitignore`), кроме
`gradle/wrapper/gradle-wrapper.properties` — она была в репозитории с самого
начала и описывает версию Gradle, а не факт установки, её удалять не нужно.

## Что не откатывается автоматически

- Диски, занятые скачанными пакетами SDK (`platforms;android-34`,
  `build-tools;34.0.0`, `platform-tools`) физически лежат внутри
  `/opt/homebrew/share/android-commandlinetools` — это каталог, которым
  управляет cask, `brew uninstall --cask` уберёт его целиком.
- Кеш Gradle (`~/.gradle`) — не проектный файл, общий для всех Gradle-проектов
  на машине. `brew uninstall --cask android-commandlinetools` его не трогает.
  Удалить вручную, если нужно: `rm -rf ~/.gradle/wrapper/dists/gradle-8.7-bin`.
