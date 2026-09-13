# InstallClicker — план доводки до работающего APK

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** превратить уже написанные, но ни разу не собранные исходники InstallClicker в подписанный APK, проверенный на головном устройстве Jaecoo J7 (прошивка DesaySV).

**Architecture:** приложение готово целиком — сервис доступности жмёт кнопку подтверждения установки через `ACTION_CLICK`, обходя `filterTouchesWhenObscured`; логика поиска кнопки вынесена в чистый `NodeMatcher` и покрыта JVM-тестами. Работа исполнителя — тулчейн, компиляция, прогон тестов, сборка, проверка на устройстве и, если понадобится, донастройка `NodeMatcher` под конкретную прошивку по дампу дерева окна.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17, Android SDK (compileSdk 34), JUnit 4.13.2, adb.

Полное описание задачи, причины проблемы и логики поиска — в [SPEC.md](SPEC.md). Читать до начала работы: без раздела «Логика поиска» нельзя судить, ошибка в коде или в тесте.

## Global Constraints

- Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17. Версии не поднимать.
- `minSdk 24`, `compileSdk 34`, `targetSdk 30`. `targetSdk` низкий намеренно — не поднимать.
- Внешних зависимостей нет, кроме `junit:junit:4.13.2` в `testImplementation`. AndroidX не подключать (`android.useAndroidX=false`, тема `Theme.DeviceDefault.Light`).
- `applicationId` и `namespace` — `ru.installclicker`. Имя компонента сервиса `ru.installclicker/ru.installclicker.InstallClickerService` зашито в `README.md` и в строку `adb_hint` — при переименовании обновить оба места.
- Разрешений в `AndroidManifest.xml` нет и не добавлять.
- Строки UI — только в `app/src/main/res/values/strings.xml`, на русском.
- Предохранители из раздела «Безопасность» SPEC.md не ослаблять: стоп-проверка на опасные диалоги, чёрный список меток, дебаунсы.
- Область клика не расширять: только кнопка подтверждения установки/обновления.
- Задачи 4-7 требуют ГУ, подключённого по adb. Если устройства нет — выполнить задачи 1-3 и остановиться на задаче 8, отметив, что проверка на устройстве не проводилась.

---

### Task 1: Репозиторий, тулчейн, Gradle wrapper

Проект не под git и без `gradle-wrapper.jar` (бинарник нельзя было создать в исходном окружении).

**Files:**
- Create: `.git/` (git init), `local.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
- Read: `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`

**Interfaces:**
- Consumes: ничего.
- Produces: работающая команда `./gradlew`, git-репозиторий с исходным состоянием в первом коммите.

- [ ] **Step 1: Зафиксировать исходное состояние в git**

```bash
cd <корень проекта>
git init
git add -A
git commit -m "chore: исходники InstallClicker до первой сборки"
```

- [ ] **Step 2: Проверить JDK 17**

Run: `java -version`
Expected: строка вида `openjdk version "17.0.x"`. Если версия не 17 — поставить JDK 17 (`brew install openjdk@17` на macOS, `apt install openjdk-17-jdk` на Debian) и выставить `JAVA_HOME` на него. AGP 8.5.2 на JDK 11 и ниже падает, на JDK 21 возможны предупреждения.

- [ ] **Step 3: Проверить Android SDK и прописать путь**

Run: `echo $ANDROID_HOME && ls $ANDROID_HOME/platforms`
Expected: непустой путь и наличие `android-34`. Если `android-34` нет:

```bash
sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

Если `sdkmanager` требует лицензии: `yes | sdkmanager --licenses`.

Затем создать `local.properties` в корне проекта (файл в `.gitignore`, коммитить не нужно):

```properties
sdk.dir=/абсолютный/путь/к/android/sdk
```

- [ ] **Step 4: Создать Gradle wrapper**

Вариант А, если Gradle установлен локально:

```bash
gradle wrapper --gradle-version 8.7
```

Вариант Б, если есть Android Studio: открыть проект, дождаться первой синхронизации — Studio создаст wrapper сама.

Проверить, что `gradle/wrapper/gradle-wrapper.properties` после этого по-прежнему ссылается на 8.7:

Run: `grep distributionUrl gradle/wrapper/gradle-wrapper.properties`
Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip`

- [ ] **Step 5: Убедиться, что wrapper поднимает проект**

Run: `./gradlew --version`
Expected: `Gradle 8.7` и `JVM: 17.x`.

Run: `./gradlew :app:tasks --all | head -30`
Expected: список задач без ошибок конфигурации; среди них `assembleRelease` и `testDebugUnitTest`.

Если конфигурация падает — ошибка в `build.gradle.kts` или `settings.gradle.kts`, читать сообщение Gradle и править минимально, не меняя версии из Global Constraints.

- [ ] **Step 6: Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties
git commit -m "build: добавить gradle wrapper 8.7"
```

---

### Task 2: Юнит-тесты и исправление ошибок компиляции

Первая в истории проекта компиляция. Ожидать синтаксические и типовые ошибки — это нормально, их и надо вычистить.

**Files:**
- Test: `app/src/test/java/ru/installclicker/NodeMatcherTest.kt`, `app/src/test/java/ru/installclicker/ClickThrottleTest.kt`, `app/src/test/java/ru/installclicker/FakeNode.kt`
- Modify (по факту ошибок): любые файлы в `app/src/main/java/ru/installclicker/`

**Interfaces:**
- Consumes: `./gradlew` из задачи 1.
- Produces: зелёный `:app:testDebugUnitTest`, 17 тестов; неизменная семантика `NodeMatcher.findInstallButton(root, requireLabel)` и `isInstallLabel(raw)`.

- [ ] **Step 1: Прогнать тесты и увидеть первый результат**

Run: `./gradlew :app:testDebugUnitTest`
Expected: либо `BUILD SUCCESSFUL` с 17 пройденными тестами, либо ошибки компиляции Kotlin — тогда переходить к шагу 2.

Отчёт о прогоне: `app/build/reports/tests/testDebugUnitTest/index.html`.

- [ ] **Step 2: Починить ошибки компиляции**

Править минимально: цель — компиляция, а не переписывание. Места, где ошибки вероятнее всего, и что с ними делать:

| Файл | Место | Если компилятор ругается |
|---|---|---|
| `InstallClickerService.kt` | `return@use` внутри `when` внутри `NodeScope().use { … }` | заменить `NodeScope().use { scope -> … }` на явный `val scope = NodeScope()` + `try { … } finally { scope.close() }`, а `return@use` — на `return` |
| `InstallClickerService.kt` | `@Volatile var isRunning` с `private set` в `companion object` | оставить `@Volatile`, при отказе — убрать `private set` и пометить сеттер `@JvmStatic`-независимо; семантику не менять |
| `A11yNode.kt` | `runCatching { @Suppress("DEPRECATION") it.recycle() }` | вынести `@Suppress("DEPRECATION")` на функцию `close()` |
| `MainActivity.kt` | `findViewById<Button>(...)` при `minSdk 24` | это предупреждение lint, не ошибка; erasure совпадает, ничего не менять |
| `NodeMatcherTest.kt` | имена тестов в обратных кавычках с кириллицей | допустимо в Kotlin/JVM; если сборка ругается — переименовать в латиницу, смысл тестов сохранить |

Чего **не** делать: не менять пороги скоринга, списки `INSTALL_LABELS` / `INSTALL_PREFIXES` / `ABORT_MARKERS` / `LABEL_DENY`, лимиты `MAX_LABEL_LENGTH`, `MAX_LABEL_WORDS`, `MAX_ASCEND` и значения дебаунсов. Всё это — спека, а не деталь реализации.

- [ ] **Step 3: Прогнать тесты до зелёного**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, в отчёте 17 тестов, 0 failures.

Если тест падает, а не компиляция: сверить ожидание теста с разделом «Логика поиска» SPEC.md. Тест — источник истины, пока он не противоречит спеке. Если противоречит именно тест — исправлять тест и явно написать об этом в отчёте задачи 8.

- [ ] **Step 4: Проверить, что список тестов полный**

Run: `grep -c "@Test" app/src/test/java/ru/installclicker/NodeMatcherTest.kt app/src/test/java/ru/installclicker/ClickThrottleTest.kt`
Expected: `NodeMatcherTest.kt:14` и `ClickThrottleTest.kt:3`.

Если меньше — тест потерян при правках, восстановить по списку в разделе «Тесты» SPEC.md.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "fix: исправить ошибки компиляции, юнит-тесты зелёные"
```

---

### Task 3: Сборка release APK и фикстуры для проверки установки

Для проверки автоклика на ГУ нужен второй APK, который можно ставить и удалять сколько угодно. Отдельный модуль ради этого не нужен: достаточно дать debug-сборке другой `applicationId`, и она встанет рядом с release как отдельное приложение.

**Files:**
- Modify: `app/build.gradle.kts`
- Produces: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: зелёные тесты из задачи 2.
- Produces: два подписанных APK; `ru.installclicker` (основной) и `ru.installclicker.debug` (фикстура для установки/удаления).

- [ ] **Step 1: Развести applicationId debug- и release-сборок**

В `app/build.gradle.kts` в блок `buildTypes` добавить `debug`, рядом с существующим `release`:

```kotlin
    buildTypes {
        debug {
            // Отдельный applicationId, чтобы debug-сборку можно было ставить и
            // удалять на ГУ как подопытное приложение, не задевая основное.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Подписываем debug-ключом, чтобы assembleRelease давал APK,
            // который сразу ставится через adb install.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
```

- [ ] **Step 2: Собрать оба APK**

Run: `./gradlew assembleRelease assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Run: `ls -l app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/debug/app-debug.apk`
Expected: два существующих файла.

- [ ] **Step 3: Проверить, что в манифест APK попало всё нужное**

Run: `$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -20`
Expected: `package: name='ru.installclicker'`, `sdkVersion:'24'`, `targetSdkVersion:'30'`, `launchable-activity: name='ru.installclicker.MainActivity'`, и ни одной строки `uses-permission`.

Run: `$ANDROID_HOME/build-tools/34.0.0/aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/release/app-release.apk | grep -A2 -E "service|receiver"`
Expected: сервис `ru.installclicker.InstallClickerService` с `permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` и meta-data `android.accessibilityservice`; ресивер `ru.installclicker.ToggleReceiver` с `exported=true`.

Если сервиса или meta-data нет — ошибка в `app/src/main/AndroidManifest.xml` или в `app/src/main/res/xml/accessibility_service_config.xml`.

- [ ] **Step 4: Проверить applicationId фикстуры**

Run: `$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -2`
Expected: `package: name='ru.installclicker.debug'`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: отдельный applicationId для debug-сборки как фикстуры"
```

---

### Task 4: Установка на ГУ и включение сервиса доступности

Требует ГУ, подключённого по adb. На Jaecoo ADB включается в инженерном меню (многократный тап по «Версия», затем `USB2.0` → `DEVICE`).

**Files:**
- Read: `README.md` (раздел «Включение сервиса доступности»)

**Interfaces:**
- Consumes: `app-release.apk` из задачи 3.
- Produces: установленное приложение с работающим сервисом доступности; в logcat запись `Сервис доступности подключён`.

- [ ] **Step 1: Убедиться, что ГУ видно**

Run: `adb devices -l`
Expected: одна строка с устройством в состоянии `device`. Если `unauthorized` — подтвердить отпечаток на экране ГУ. Если список пуст — ADB на ГУ не включён, дальше задачи 4-7 невыполнимы.

- [ ] **Step 2: Записать версию Android ГУ**

Run: `adb shell getprop ro.build.version.release && adb shell getprop ro.build.version.sdk && adb shell getprop ro.product.model`
Expected: три непустые строки. Записать их — они пойдут в отчёт задачи 8, а если SDK окажется 33+, отметить это отдельно: на `targetSdk 30` приложение работает, но диагностику имён пакетов надо будет читать внимательнее.

- [ ] **Step 3: Установить APK**

Run: `adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`.

При `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — на ГУ уже стоит сборка с другой подписью: `adb uninstall ru.installclicker`, затем повторить.

- [ ] **Step 4: Открыть логи в отдельном терминале и держать их до конца задачи 7**

Run: `adb logcat -c && adb logcat -s InstallClicker`
Expected: пустой вывод, ожидание событий.

- [ ] **Step 5: Включить сервис доступности через adb**

Штатный путь через «Настройки → Специальные возможности» может не сработать: диалог подтверждения сервиса на этих прошивках защищён тем же фильтром касаний, что и кнопка «Установить». Поэтому сразу через adb.

Сначала посмотреть, что там уже включено, чтобы не перезатереть чужие сервисы:

```bash
adb shell settings get secure enabled_accessibility_services
```

Если вывод `null` или пустой:

```bash
adb shell settings put secure enabled_accessibility_services ru.installclicker/ru.installclicker.InstallClickerService
adb shell settings put secure accessibility_enabled 1
```

Если там уже есть другие компоненты — дописать свой через `:`, сохранив прежние:

```bash
adb shell settings put secure enabled_accessibility_services "<прежнее значение>:ru.installclicker/ru.installclicker.InstallClickerService"
adb shell settings put secure accessibility_enabled 1
```

- [ ] **Step 6: Проверить, что сервис поднялся**

Run: `adb shell settings get secure enabled_accessibility_services`
Expected: строка содержит `ru.installclicker/ru.installclicker.InstallClickerService`.

В окне logcat Expected: `Сервис доступности подключён, автоклик выключен`.

Если записи нет — сервис не запущен. Открыть приложение на ГУ (`adb shell am start -n ru.installclicker/.MainActivity`) и посмотреть строку статуса: она различает «сервис выключен» и «включён в настройках, ожидает запуска системой».

---

### Task 5: Проверка автоклика на установке APK

**Interfaces:**
- Consumes: работающий сервис из задачи 4, `app-debug.apk` из задачи 3.
- Produces: подтверждённый факт, что кнопка «Установить» нажимается автокликом и не нажимается при выключенном переключателе.

- [ ] **Step 1: Убедиться, что фикстура ещё не установлена**

Run: `adb uninstall ru.installclicker.debug`
Expected: `Success` либо `Failure [DELETE_FAILED_INTERNAL_ERROR]` / `Unknown package` — оба варианта годятся, важно лишь отсутствие пакета.

- [ ] **Step 2: Положить фикстуру в память ГУ**

Run: `adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/test-install.apk`
Expected: строка вида `1 file pushed`.

- [ ] **Step 3: Проверить базовый симптом при выключенном автоклике**

Автоклик по умолчанию выключен. Открыть диалог установки:

```bash
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test-install.apk -t application/vnd.android.package-archive
```

Если команда не открывает диалог (`Error: Activity not started` или исключение про file:// URI) — открыть файл с экрана ГУ штатным файловым менеджером. Это ровно тот путь, которым пользуется владелец машины.

Expected: диалог установки на экране; тап по «Установить» пальцем не даёт эффекта — исходный симптом воспроизведён. Записать этот факт: он подтверждает диагноз из SPEC.md. Закрыть диалог кнопкой «Отмена» (она нажимается).

Run: `adb shell pm list packages ru.installclicker.debug`
Expected: пустой вывод — приложение не установилось.

- [ ] **Step 4: Включить автоклик**

Run: `adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled true`
Expected: `Broadcast completed: result=0`.

В окне logcat Expected: `Автоклик включён через broadcast`.

- [ ] **Step 5: Повторить установку и убедиться, что кнопка нажалась сама**

```bash
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test-install.apk -t application/vnd.android.package-archive
```

Expected в logcat: строка вида `[com.android.packageinstaller] «установить» (text=установить) → ACTION_CLICK выполнен`.

Run: `adb shell pm list packages ru.installclicker.debug`
Expected: `package:ru.installclicker.debug`.

Если в логе `ACTION_CLICK не сработал, отправлен жест` — приложение всё равно должно установиться; отметить это в отчёте, значит на этой прошивке узел действие не принимает и работает fallback.

Если в логе `кнопка установки в окне не найдена` или лог пуст — переходить к задаче 7.

- [ ] **Step 6: Проверить путь обновления**

Приложение уже стоит, поэтому тот же APK откроет диалог обновления.

```bash
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test-install.apk -t application/vnd.android.package-archive
```

Expected в logcat: ещё одна запись с `→ ACTION_CLICK выполнен`; на экране установка завершается сама.

- [ ] **Step 7: Проверить, что выключение действует мгновенно**

```bash
adb shell pm uninstall ru.installclicker.debug
adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled false
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test-install.apk -t application/vnd.android.package-archive
```

Expected: диалог установки ждёт пользователя, в logcat новых записей о клике нет. Сервис при этом не переактивировался — проверяется именно чтение флага на каждом событии.

Закрыть диалог «Отмена».

---

### Task 6: Предохранитель на диалоге удаления и живучесть после перезагрузки

Самая важная проверка безопасности: диалог удаления в AOSP использует тот же `ok_button`, что и диалог установки.

**Interfaces:**
- Consumes: работающий автоклик из задачи 5.
- Produces: подтверждение, что стоп-проверка срабатывает и что сервис поднимается после перезагрузки ГУ.

- [ ] **Step 1: Поставить фикстуру обратно**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 2: Включить автоклик**

Run: `adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled true`
Expected: `Broadcast completed: result=0`.

- [ ] **Step 3: Открыть диалог удаления и убедиться, что автоклик молчит**

Run: `adb shell am start -a android.intent.action.DELETE -d package:ru.installclicker.debug`
Expected: диалог «Удалить это приложение?» остаётся на экране, ничего не подтверждается.

Expected в logcat: `пропуск: в окне есть опасное действие «удалить»`.

Run: `adb shell pm list packages ru.installclicker.debug`
Expected: `package:ru.installclicker.debug` — приложение на месте.

Если приложение удалилось — критическая ошибка предохранителя. Остановиться, не продолжать проверки, разобрать причину в `NodeMatcher.unsafeActionMarker` (метка кнопки удаления на этой прошивке не попала в `ABORT_MARKERS` либо не кликабельна и не имеет класса `*Button*`), добавить регрессионный тест в `NodeMatcherTest.kt` по образцу теста `не жмёт ничего в диалоге удаления` и починить.

Закрыть диалог «Отмена».

- [ ] **Step 4: Перезагрузить ГУ и проверить, что состояние цело**

```bash
adb reboot
```

Дождаться загрузки (`adb wait-for-device`), затем:

Run: `adb shell settings get secure enabled_accessibility_services`
Expected: строка по-прежнему содержит `ru.installclicker/ru.installclicker.InstallClickerService`.

Run: `adb logcat -c && adb logcat -s InstallClicker -m 1`
Expected: `Сервис доступности подключён, автоклик включён` — переключатель сохранился во `SharedPreferences`.

- [ ] **Step 5: Убрать фикстуру**

Run: `adb uninstall ru.installclicker.debug`
Expected: `Success`.

Удаление через adb, а не через диалог: предохранитель специально не даст подтвердить его автокликом.

---

### Task 7: Донастройка NodeMatcher под прошивку (только если кнопка не найдена)

Выполнять, только если в задаче 5 автоклик не сработал: в логе `кнопка установки в окне не найдена`, `«…» найдена, но кликабельного узла нет`, или лог пуст.

**Files:**
- Modify: `app/src/main/java/ru/installclicker/NodeMatcher.kt`
- Test: `app/src/test/java/ru/installclicker/NodeMatcherTest.kt`

**Interfaces:**
- Consumes: дамп дерева окна из logcat.
- Produces: расширенные `INSTALL_ID_SUFFIXES` / `INSTALL_LABELS` под конкретную прошивку + регрессионный тест на каждое добавление.

- [ ] **Step 1: Если лог пуст — снять фильтр по пакету**

Пустой лог означает, что установщик прошивки называется не `*installer*`, и события отфильтровываются до обхода дерева.

Открыть приложение на ГУ, включить галку «Работать в любом приложении», повторить установку из задачи 5, шаг 5.

Заодно узнать реальное имя пакета установщика:

Run: `adb shell dumpsys window | grep -i -E "mCurrentFocus|mFocusedApp"`
Expected: строка с именем пакета, который держит фокус в момент открытого диалога установки. Записать его — оно пойдёт в отчёт задачи 8.

- [ ] **Step 2: Включить подробный лог и снять дамп дерева**

На экране приложения включить «Подробный лог», затем:

```bash
adb logcat -c
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test-install.apk -t application/vnd.android.package-archive
adb logcat -d -s InstallClicker > /tmp/installclicker-tree.txt
```

Expected: в файле дамп дерева окна — строки вида
`android.widget.Button id=com.android.packageinstaller:id/ok_button text=Установить desc=null clickable=true enabled=true visible=true`.

- [ ] **Step 3: Найти в дампе настоящую кнопку подтверждения**

Найти строку, у которой `clickable=true` и текст или id намекают на подтверждение установки. Записать её `id` и `text` дословно.

Три типичных случая и что они означают:

| Что в дампе | Причина | Что менять |
|---|---|---|
| id вида `com.desaysv.…:id/btn_ok`, текст «Установить» | нестандартный id, текст стандартный | ничего, должно было сработать по тексту — проверить, не длиннее ли текст 30 символов и не больше ли 3 слов |
| текст вида «Установить сейчас» из 2 слов, `clickable=true` | попадает под префикс `установ` | ничего, разобраться, почему не выбрался: посмотреть, нет ли в окне `Blocked` |
| id `…:id/confirm_install`, текст пустой | опознание только по id, а такого суффикса в списке нет | добавить суффикс в `INSTALL_ID_SUFFIXES` |
| текст на китайском, отличный от `安装` / `更新` | локаль прошивки | добавить точную метку в `INSTALL_LABELS` |

- [ ] **Step 4: Написать падающий тест под найденный узел**

Пример для случая «новый суффикс id» — добавить в `NodeMatcherTest.kt`, подставив реальные значения из дампа:

```kotlin
    @Test
    fun `находит кнопку установщика прошивки ГУ по id confirm_install`() {
        val root = container(
            button("Отмена", viewId = "com.desaysv.packageinstaller:id/cancel"),
            button("", viewId = "com.desaysv.packageinstaller:id/confirm_install"),
        )

        val outcome = find(root) as MatchOutcome.Found

        assertTrue(outcome.via.contains("confirm_install"))
    }
```

- [ ] **Step 5: Убедиться, что тест падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.installclicker.NodeMatcherTest"`
Expected: FAIL на новом тесте, `ClassCastException` либо `MatchOutcome.NoCandidate is not MatchOutcome.Found`.

- [ ] **Step 6: Добавить найденное в NodeMatcher**

В `app/src/main/java/ru/installclicker/NodeMatcher.kt` дописать значение в нужный список, не убирая существующие:

```kotlin
    private val INSTALL_ID_SUFFIXES = listOf(
        "ok_button",
        "continue_button",
        "install_button",
        "btn_install",
        "button_install",
        "install_confirm_ok",
        "confirm_install",
    )
```

Для новой текстовой метки — в `INSTALL_LABELS`. Метка обязана оставаться короткой (≤3 слов, ≤30 символов) и не пересекаться с `LABEL_DENY`, иначе `isInstallLabel` её отбросит.

- [ ] **Step 7: Прогнать все тесты**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 18 тестов (17 исходных + новый), 0 failures. Важно, что старые тесты не поехали: `не жмёт ничего в диалоге удаления` и `в режиме любого приложения совпадения по id недостаточно` — главные из них.

- [ ] **Step 8: Пересобрать, переустановить, перепроверить**

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled true
adb uninstall ru.installclicker.debug
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test-install.apk -t application/vnd.android.package-archive
```

Expected в logcat: `→ ACTION_CLICK выполнен`, приложение `ru.installclicker.debug` установилось.

Переустановка сервиса доступности не сбрасывает его включённость, но если после `adb install -r` записи в логе исчезли — повторить шаги 5-6 задачи 4.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ru/installclicker/NodeMatcher.kt app/src/test/java/ru/installclicker/NodeMatcherTest.kt
git commit -m "fix: опознавать кнопку установки прошивки ГУ"
```

---

### Task 8: Итоговый отчёт и обновление README

**Files:**
- Modify: `README.md`
- Create: `REPORT.md`

**Interfaces:**
- Consumes: результаты задач 1-7.
- Produces: отчёт с фактами проверки и README, приведённый в соответствие с тем, что реально наблюдалось на ГУ.

- [ ] **Step 1: Написать REPORT.md**

Строго по факту: что проверено, что нет, что пришлось поменять. Никаких «должно работать».

```markdown
# Отчёт о доводке InstallClicker

## Окружение
- JDK: <версия>
- Android SDK: <версия compileSdk / build-tools>
- ГУ: <модель>, Android <версия> (SDK <n>)
- Пакет установщика прошивки: <имя из задачи 7 шаг 1, либо «стандартный com.android.packageinstaller»>

## Сборка
- `./gradlew :app:testDebugUnitTest`: <кол-во тестов>, <failures>
- `./gradlew assembleRelease`: <успех/ошибка>
- Исправления ошибок компиляции: <список файлов и что менялось, либо «не потребовались»>

## Проверка на ГУ
| Критерий готовности из SPEC.md | Результат |
|---|---|
| Симптом воспроизведён: тап по «Установить» не работает | <да/нет> |
| Автоклик включён → APK ставится без касания экрана | <да/нет, запись из лога> |
| Автоклик выключен → диалог ждёт пользователя | <да/нет> |
| Диалог удаления не подтверждается | <да/нет, запись из лога> |
| После перезагрузки ГУ сервис поднялся, флаг цел | <да/нет> |
| Сработал ACTION_CLICK или fallback-жест | <что именно> |

## Донастройка под прошивку
<что добавлено в NodeMatcher и почему, либо «не потребовалась»>

## Не проверено
<честный список>
```

- [ ] **Step 2: Привести README в соответствие с фактами**

Проверить и поправить только то, что расходится с наблюдениями:

- если установщик прошивки называется не `*installer*` — дописать его имя в раздел «Параметры», в строку про «Работать в любом приложении», и указать, что галку надо включить сразу;
- если штатный путь включения сервиса через «Настройки → Специальные возможности» на ГУ сработал — убрать из раздела оговорку, что он может не сработать;
- если сработал только fallback-жест — отметить это в разделе «Диагностика», чтобы владелец не считал такую запись в логе ошибкой;
- если что-то из README на устройстве не подтвердилось — исправить текст, а не отчёт.

- [ ] **Step 3: Финальный прогон перед сдачей**

Run: `./gradlew clean :app:testDebugUnitTest assembleRelease`
Expected: `BUILD SUCCESSFUL`, тесты зелёные, APK на месте.

Run: `ls -l app/build/outputs/apk/release/app-release.apk`
Expected: существующий файл.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: отчёт о доводке и актуализация README"
```
