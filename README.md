# InstallClicker

Автоклик кнопки «Установить» для головного устройства Jaecoo J7 (прошивка DesaySV),
где кнопка видима, но не реагирует на касание.

**Статус:** логика поиска кнопки и весь сборочный тулчейн проверены —
17 юнит-тестов зелёные, release APK собирается и подтверждён `aapt2 dump`
(манифест, сервис, отсутствие разрешений). Сервис доступности и автоклик
проверены живьём на обычном Android-телефоне — включается, находит кнопку,
жмёт её через `ACTION_CLICK`. Проверка непосредственно на ГУ Jaecoo J7 —
по чеклисту в [PLAN.md](PLAN.md), задачи 4–7.

## Почему кнопка не нажимается

Это не отключённая кнопка — это **фильтрация касаний по признаку перекрытого окна**.

Диалог `PackageInstaller` использует стиль с `android:filterTouchesWhenObscured="true"`
(защита от tapjacking). Пока поверх окна установщика присутствует чужое окно-оверлей
(`TYPE_APPLICATION_OVERLAY`, toast, системный слой прошивки ГУ), система помечает
`MotionEvent` флагами `FLAG_WINDOW_IS_OBSCURED` / `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`,
и `View` молча отбрасывает касание. Кнопка при этом не переводится в состояние
`enabled=false`, поэтому визуально выглядит рабочей, а «Отмена» продолжает нажиматься.

`AccessibilityNodeInfo.performAction(ACTION_CLICK)` — не `MotionEvent`, а
accessibility-действие, диспетчеризуемое самой системой, и под этот фильтр не попадает.
На этом и построено приложение.

## Что делает

- Сервис доступности следит за окнами установщика пакетов.
- Находит кнопку подтверждения установки: по `viewIdResourceName`
  (`ok_button`, `continue_button`, `install_button`, …) и по тексту
  (`Установить`, `Обновить`, `Install`, `Update`, `安装`, `更新`).
- Жмёт её через `ACTION_CLICK`; если узел действие не принял — пробует жест по центру кнопки.
- Всё это только когда переключатель в приложении включён. Флаг читается на каждом
  событии, поэтому вкл/выкл срабатывает мгновенно, без переактивации сервиса.

**Предохранители.** Если в окне есть кнопка удаления, оплаты или подписки —
автоклик не жмёт ничего вообще (диалог удаления в AOSP использует тот же `ok_button`).
Кнопки «Отмена», «Готово», «Открыть», «Закрыть» целями не являются никогда.
Дебаунс 1500 мс не даёт зациклиться на потоке `typeWindowContentChanged`.

## Сборка

Нужен JDK 17 и Android SDK (`platforms;android-34`, `build-tools;34.0.0`).
Gradle wrapper (8.7) в репозитории есть, системный Gradle не нужен.

Проще всего — открыть проект в Android Studio (Ladybug или новее, любая ОС):
она сама поставит JDK и SDK при первой синхронизации, и `local.properties`
создавать не придётся. Ниже — вариант через терминал, для каждой ОС отдельно.

### macOS (Homebrew)

Без правки `~/.zshrc` — переменные только на один проект:

```bash
brew install openjdk@17
brew install --cask android-commandlinetools
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Linux (Debian/Ubuntu)

```bash
sudo apt install openjdk-17-jdk-headless unzip
```

На других дистрибутивах — тот же пакет через свой менеджер
(`sudo dnf install java-17-openjdk-devel`, `sudo pacman -S jdk17-openjdk`).

Дальше — Android SDK command-line tools: скачать «Command line tools only»
для Linux со страницы
[developer.android.com/studio#command-tools](https://developer.android.com/studio#command-tools)
и распаковать так, чтобы вложенная папка `cmdline-tools` лежала на один
уровень глубже (`~/android-sdk/cmdline-tools/latest/...`, а не
`~/android-sdk/cmdline-tools/...`) — иначе `sdkmanager` её не найдёт:

```bash
mkdir -p ~/android-sdk/cmdline-tools
unzip -q ~/Downloads/commandlinetools-linux-*.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Windows (PowerShell)

JDK 17 — например,
[Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/download#openjdk-17)
(`.msi`, путь установки — он и есть `JAVA_HOME` ниже).

Android SDK command-line tools — «Command line tools only» для Windows со
страницы
[developer.android.com/studio#command-tools](https://developer.android.com/studio#command-tools),
распаковать так же, как на Linux: вложенная папка `cmdline-tools` должна
лежать на один уровень глубже, `%ANDROID_HOME%\cmdline-tools\latest\...`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.x.x.x-hotspot"
$env:ANDROID_HOME = "$HOME\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
"sdk.dir=$($env:ANDROID_HOME -replace '\\','/')" | Out-File -Encoding ascii local.properties
```

### Сборка и тесты (одинаково на всех ОС)

```bash
./gradlew assembleRelease
```

На Windows — `gradlew.bat assembleRelease` (или тот же `./gradlew` из Git Bash).

Готовый APK: `app/build/outputs/apk/release/app-release.apk`
(release подписан debug-ключом, чтобы сразу ставился через adb).

Юнит-тесты логики поиска кнопки, без устройства:

```bash
./gradlew test
```

17 тестов (`NodeMatcherTest` — 14, `ClickThrottleTest` — 3), проверено на
Gradle 8.7 / JDK 17.0.20 / compileSdk 34 (macOS).

## Включение сервиса доступности

Штатный путь — «Настройки → Специальные возможности → InstallClicker → включить».

Тут возможны две независимые проблемы:

**1. Restricted settings (Android 13+).** Тумблер серый либо при тапе
показывается «Из соображений безопасности этот параметр сейчас недоступен» /
«For your security, this setting is currently unavailable». Так система
блокирует включение accessibility-сервиса у приложений, установленных не из
Google Play (в том числе через `adb install`). Снимается на самом устройстве:

Настройки → Приложения → InstallClicker → меню (⋮) → **«Разрешить ограниченные
настройки»** / **«Allow restricted settings»** → вернуться в Специальные
возможности, тумблер станет активным.

**2. Фильтрация касаний на ГУ.** На части прошивок диалог подтверждения
сервиса доступности защищён тем же фильтром касаний, что и кнопка
«Установить» (см. «Почему кнопка не нажимается» выше) — тап по «Разрешить» в
этом диалоге не срабатывает. Если пункт меню из способа 1 не помог или его
нет — включаем сервис через adb напрямую, минуя оба защищённых экрана:

```bash
adb shell settings put secure enabled_accessibility_services ru.installclicker/ru.installclicker.InstallClickerService
adb shell settings put secure accessibility_enabled 1
```

Проверка:

```bash
adb shell settings get secure enabled_accessibility_services
```

Если в системе уже включены другие сервисы доступности, первую команду надо дописывать,
а не перезатирать: значение — список компонентов через `:`.

Выключить сервис полностью:

```bash
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0
```

## Использование

В приложении один главный переключатель — **«Автоклик кнопки „Установить“»**.
Включил → открыл APK → диалог установки подтверждается сам. Выключил → всё как раньше.

То же самое через adb, без касаний экрана ГУ:

```bash
adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled true
adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled false
adb shell am broadcast -a ru.installclicker.TOGGLE
```

### Параметры

| Параметр | По умолчанию | Зачем |
|---|---|---|
| Работать в любом приложении | выкл | Если установщик на прошивке ГУ называется не `*packageinstaller*`. В этом режиме совпадения только по id недостаточно — требуется ещё и совпадение текста. |
| Пробовать жест по координатам | вкл | Fallback, когда узел не принял `ACTION_CLICK`. |
| Подробный лог | выкл | Дампит дерево окна в logcat, когда кнопка не найдена. Нужен для донастройки под конкретную прошивку. |

## Диагностика

На главном экране — статус сервиса, версия Android ГУ и лог последних 40 событий
(кнопки «Скопировать» / «Очистить»).

Полный лог:

```bash
adb logcat -s InstallClicker
```

Что означают записи:

| Запись | Что делать |
|---|---|
| `«установить» (text=установить) → ACTION_CLICK выполнен` | Всё работает. |
| `кнопка «…» найдена, но isEnabled=false` | Кнопка отключена самой прошивкой — дело не в фильтрации касаний, автоклик тут не поможет. |
| `ACTION_CLICK не сработал, отправлен жест` | Узел действие не принял; проверь, поставилось ли приложение. |
| `кнопка установки в окне не найдена` (при подробном логе) | Пришли дамп дерева из logcat — по нему добавим id/текст этой прошивки в `NodeMatcher`. |
| `пропуск: в окне есть опасное действие` | Сработал предохранитель, окно с удалением/оплатой. |
| Пусто, событий нет | Включи «Работать в любом приложении»: установщик прошивки называется иначе. |

## Проверка на ГУ

1. Поставить APK, включить сервис доступности, убедиться, что статус — «Сервис работает, автоклик включён».
2. Открыть любой APK с флешки → диалог установки подтверждается сам, приложение ставится.
3. Выключить переключатель → открыть APK снова → диалог ждёт, автоклик молчит.
4. Открыть диалог удаления приложения при включённом автоклике → ничего не нажимается,
   в логе «пропуск: в окне есть опасное действие».
5. Перезагрузить ГУ → сервис поднимается сам, состояние переключателя сохранено.

## Ограничения

- Сервис доступности имеет доступ к содержимому окон — это плата за обход фильтрации.
  Фильтр по пакетам, чёрный список текстов и стоп-проверка на опасные диалоги ограничивают,
  что именно приложение может нажать, но сам доступ к дереву окон никуда не девается.
- `ToggleReceiver` экспортирован, чтобы принимать broadcast от `adb shell`. Любое
  приложение на ГУ может переключить флаг — больше ничего сделать оно не может.
- «Готово», «Открыть», «Всё равно установить» и переключатель «разрешить установку
  из этого источника» автоклик не жмёт: область сознательно ограничена подтверждением установки.

## Если автоклик не помог

Признак того, что причина другая (не фильтрация касаний): в логе `isEnabled=false`,
либо система пишет «установка заблокирована».

Тогда проверить appop источника установки:

```bash
adb shell appops set ru.vk.store REQUEST_INSTALL_PACKAGES allow
```

Либо ставить приложения напрямую, минуя диалог:

```bash
adb install -r -g приложение.apk
```

## См. также

- [SPEC.md](SPEC.md) — спецификация: причина проблемы, интерфейсы, логика поиска кнопки, критерии готовности.
- [PLAN.md](PLAN.md) — пошаговый план доводки: тулчейн, тесты, сборка, проверка на ГУ, донастройка под конкретную прошивку.
- [CLEANUP.md](CLEANUP.md) — как откатить установку JDK/SDK, если ставили по инструкции выше.
