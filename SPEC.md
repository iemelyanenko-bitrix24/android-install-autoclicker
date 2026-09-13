# InstallClicker — спецификация

**Статус:** код написан целиком, ни разу не компилировался и не запускался.
В окружении, где он писался, не было ни JDK, ни Android SDK, ни ГУ на adb.
Задача исполнителя — довести до работающего APK и проверить на устройстве.
Пошаговый порядок — в [PLAN.md](PLAN.md).

## Задача

Головное устройство Jaecoo J7 с заводской прошивкой DesaySV: при установке
любого APK кнопка «Установить» в системном диалоге видима и выглядит активной,
но тап по ней не даёт эффекта. «Отмена» при этом работает.

Нужно приложение, которое умеет включать и отключать автоклик по этой кнопке.
APK ставится на ГУ вручную через adb.

## Причина

Фильтрация касаний по признаку перекрытого окна.

Диалог `PackageInstaller` использует стиль с
`android:filterTouchesWhenObscured="true"` — защита от tapjacking. Когда поверх
окна установщика присутствует чужое окно-оверлей (`TYPE_APPLICATION_OVERLAY`,
toast, системный слой прошивки ГУ), система помечает `MotionEvent` флагами
`FLAG_WINDOW_IS_OBSCURED` / `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`, и `View` молча
отбрасывает касание. Кнопка при этом **не** переводится в `enabled=false`,
поэтому визуально остаётся рабочей, а «Отмена» (вне защищённого стиля) нажимается.

На прошивке ГУ системные слои висят поверх интерфейса постоянно, поэтому эффект
воспроизводится всегда, а не разово.

Второй, более редкий сценарий на Jaecoo — не выданный appop
`REQUEST_INSTALL_PACKAGES` источнику установки. Симптом другой («установка
заблокирована»), лечится `adb shell appops set <pkg> REQUEST_INSTALL_PACKAGES allow`.
Настоящая спека его не покрывает.

## Механизм обхода

`AccessibilityNodeInfo.performAction(ACTION_CLICK)` — не `MotionEvent`, а
accessibility-действие, диспетчеризуемое самой системой. `filterTouchesWhenObscured`
на него не распространяется, поэтому клик проходит.

`dispatchGesture()` формирует синтетическое касание и на части прошивок
отбрасывается тем же фильтром — только fallback, не основа.

Практическое следствие, которое легко упустить: диалог подтверждения сервиса
доступности в системных настройках на этих прошивках защищён тем же фильтром.
Если он тоже не нажимается, сервис включается через `adb shell settings put secure`.

## Область

Автоклик жмёт только кнопку подтверждения установки или обновления.

Вне области: «Готово», «Открыть», «Всё равно установить», диалоги Play Protect,
переключатель «разрешить установку из этого источника», root, Shizuku,
собственный установщик пакетов, тайл быстрых настроек.

## Ограничения (действуют на все задачи плана)

- Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17.
- `minSdk 24`, `compileSdk 34`, `targetSdk 30`. Низкий `targetSdk` намеренный:
  версия Android на ГУ неизвестна, а на `targetSdk <= 30` нет ужесточений
  видимости пакетов из Android 13+. Не поднимать.
- Внешних зависимостей нет, кроме `junit:junit:4.13.2` в `testImplementation`.
  AndroidX не подключать: `android.useAndroidX=false`, тема — `Theme.DeviceDefault.Light`.
- `applicationId` и `namespace` — `ru.installclicker`. Имя компонента сервиса
  (`ru.installclicker/ru.installclicker.InstallClickerService`) зашито в README
  и в строку `adb_hint`: при переименовании обновить оба места.
- Разрешений в манифесте нет и не добавлять.
- Строки UI — на русском, в `res/values/strings.xml`, без хардкода в коде.
- Предохранители из раздела «Безопасность» не ослаблять.

## Структура файлов

```
app/src/main/java/ru/installclicker/
  NodeLike.kt                — абстракция узла UI-дерева
  NodeMatcher.kt             — чистая логика поиска кнопки (без Android API)
  ClickThrottle.kt           — дебаунс по ключу
  A11yNode.kt                — адаптер AccessibilityNodeInfo -> NodeLike + NodeScope
  InstallClickerService.kt    — AccessibilityService: события -> поиск -> клик
  Prefs.kt                   — SharedPreferences
  EventLog.kt                — кольцевой буфер событий + logcat
  ToggleReceiver.kt          — вкл/выкл по adb-broadcast
  MainActivity.kt            — экран: переключатель, статус, диагностика, лог
app/src/main/res/
  layout/activity_main.xml
  values/strings.xml, values/themes.xml
  xml/accessibility_service_config.xml
  mipmap-xhdpi/ic_launcher.png
app/src/test/java/ru/installclicker/
  FakeNode.kt                — NodeLike для тестов + хелперы button/label/container
  NodeMatcherTest.kt         — 14 тестов
  ClickThrottleTest.kt       — 3 теста
```

## Интерфейсы

```kotlin
interface NodeLike {
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val className: String?
    val isClickable: Boolean
    val isEnabled: Boolean
    val isVisibleToUser: Boolean
    val childCount: Int
    fun child(index: Int): NodeLike?
    fun parentNode(): NodeLike?
}

sealed class MatchOutcome {
    object NoCandidate : MatchOutcome()
    data class Blocked(val marker: String) : MatchOutcome()
    data class NoClickable(val label: String) : MatchOutcome()
    data class Found(
        val node: NodeLike,
        val label: String,   // нормализованная метка: trim + lowercase
        val via: String,     // "text=установить" либо "id=<viewIdResourceName>"
        val enabled: Boolean // false = кнопка реально отключена системой
    ) : MatchOutcome()
}

object NodeMatcher {
    fun findInstallButton(root: NodeLike, requireLabel: Boolean): MatchOutcome
    fun isInstallLabel(raw: String?): Boolean
}

class ClickThrottle(windowMs: Long = 1500L) {
    fun allow(key: String, now: Long): Boolean
    fun reset()
}

class NodeScope : java.io.Closeable {
    fun wrap(info: AccessibilityNodeInfo): A11yNode
    fun wrapOrNull(info: AccessibilityNodeInfo?): A11yNode?
    override fun close()
}

class A11yNode(val info: AccessibilityNodeInfo, scope: NodeScope) : NodeLike

class Prefs(context: Context) {
    var enabled: Boolean          // главный переключатель, default false
    var anyPackage: Boolean       // работать в любом приложении, default false
    var verboseLog: Boolean       // дамп дерева в logcat, default false
    var gestureFallback: Boolean  // жест по координатам, default true
}

object EventLog {
    const val TAG = "InstallClicker"
    fun add(message: String)
    fun snapshot(): List<String>   // последние 40 записей
    fun clear()
}

class InstallClickerService : AccessibilityService() {
    companion object {
        val isRunning: Boolean
        fun isEnabledInSettings(context: Context): Boolean
    }
}

class ToggleReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE = "ru.installclicker.TOGGLE"
        const val EXTRA_ENABLED = "enabled"
    }
}
```

`MainActivity` и `InstallClickerService` живут в одном процессе, поэтому
`EventLog`, `InstallClickerService.isRunning` и `Prefs` для них общие.

## Поток данных

`typeWindowStateChanged` / `typeWindowContentChanged`
→ `prefs.enabled`
→ фильтр пакета (имя содержит `packageinstaller` или `installer`; при
`prefs.anyPackage` фильтр снят)
→ дебаунс обхода дерева 250 мс (только для `typeWindowContentChanged`)
→ `rootInActiveWindow` → `NodeScope.wrap` → `NodeLike`
→ `NodeMatcher.findInstallButton(root, requireLabel = prefs.anyPackage)`
→ дебаунс клика 1500 мс по ключу `«пакет|метка»`
→ `ACTION_CLICK`; при `false` и `prefs.gestureFallback` — `dispatchGesture`
   по центру `boundsInScreen`
→ запись в `EventLog`.

## Логика поиска

1. **Стоп-проверка.** Если в дереве есть узел с короткой меткой (≤3 слов,
   ≤30 символов), который кликабелен или имеет класс `*Button*`, и метка содержит
   один из `ABORT_MARKERS` (`удал`, `uninstall`, `delete`, `remove`, `оплат`,
   `купить`, `buy`, `pay`, `подписк`, `subscribe`) → `Blocked`, ни одного клика.
   Требование кликабельности отсекает прозу вида «данные не будут удалены».
2. **Сбор кандидатов** обходом в глубину, лимит 40 уровней. Кандидат — узел, у
   которого:
   - `viewIdResourceName` после последнего `/` равен одному из `ok_button`,
     `continue_button`, `install_button`, `btn_install`, `button_install`,
     `install_confirm_ok` — принимается только при `requireLabel == false`; либо
   - метка (`text`, иначе `contentDescription`) проходит `isInstallLabel`.
3. **`isInstallLabel`:** после `trim` + `lowercase` + сжатия пробелов — не пустая,
   без `?`, ≤30 символов, ≤3 слов, не содержит ничего из `LABEL_DENY`
   (`ABORT_MARKERS` + `отмен`, `cancel`, `назад`, `back`, `закрыть`, `close`,
   `готов`, `done`, `открыть`, `open`, `нет`, `no`), и либо равна одной из
   `установить`, `обновить`, `install`, `update`, `安装`, `更新`, либо начинается
   с `установ`, `обнов`, `install`, `update`, `安装`, `更新`.
4. **Скоринг:** совпадение по id +50, по метке +30, `isClickable` +20,
   класс `*Button*` +15, `isVisibleToUser` +5. Побеждает максимум; при равенстве —
   первый в порядке обхода. Скоринг нужен, чтобы заголовок «Установка» проиграл
   настоящей кнопке «Установить».
5. **Цель клика:** сам узел, если `isClickable && isEnabled`; иначе ближайший
   такой предок в пределах 3 уровней вверх; иначе кликабельный, но
   `enabled == false`, узел или предок — с `enabled = false` в результате, чтобы
   диагностика показала «кнопка реально отключена»; иначе `NoClickable`.

`requireLabel = true` (режим «работать в любом приложении») запрещает
срабатывание по одному id, без совпадения текста: `ok_button` встречается в
чужих диалогах.

## Безопасность

- Сервис доступности видит содержимое окон — это плата за обход фильтрации.
  Ограничивают его именно перечисленные предохранители, а не системные разрешения.
- Диалог удаления приложения в AOSP использует тот же `ok_button`, что и диалог
  установки. Поэтому стоп-проверка (пункт 1) обязательна: без неё включённый
  автоклик подтвердит удаление.
- Автоклик работает только при включённом переключателе. Флаг читается на каждом
  событии, поэтому выключение мгновенное и не требует переактивации сервиса.
- `ToggleReceiver` экспортирован, чтобы принимать broadcast от `adb shell` (он
  приходит из чужого uid). Умеет только переключать локальный флаг.

## Обработка ошибок

| Ситуация | Поведение |
|---|---|
| Сервис не включён в системных настройках | `MainActivity` показывает статус и кнопку перехода в настройки; на экране продублированы adb-команды |
| Кнопка не найдена | Запись в лог только при `verboseLog`, плюс дамп дерева окна в logcat (иначе `typeWindowContentChanged` зафлудит буфер) |
| Найдена, но `enabled == false` | Явная запись в лог + попытка жеста, если он включён |
| `ACTION_CLICK` вернул `false` | Жест по центру кнопки, если включён; иначе запись с причиной |
| Границы кнопки пустые | Запись «клик не прошёл, границы кнопки пустые» |
| Опасный диалог | `Blocked`, клика нет, запись в лог (дебаунс 3000 мс) |

## Тесты

JVM-юнит-тесты на `NodeMatcher` и `ClickThrottle` через `FakeNode` — без
устройства и без Robolectric.

`NodeMatcherTest` (14): находит кнопку по тексту; находит «Обновить»; находит по
id установщика без совпадения текста; в режиме `requireLabel` id недостаточно;
не жмёт ничего в диалоге удаления; проза про удаление данных не блокирует
установку; не выбирает «Отмена»; не выбирает «Готово»/«Открыть»; поднимается до
кликабельного предка; сообщает про отключённую кнопку вместо клика вслепую;
предпочитает кнопку заголовку с похожим текстом; нет кандидатов в обычном окне;
метка распознаётся без учёта регистра и пробелов; длинный текст и вопросы
метками не считаются.

`ClickThrottleTest` (3): второй клик в окне дебаунса не проходит; после окна
проходит; разные ключи не мешают друг другу.

Проверка на устройстве — вручную, чеклист в PLAN.md (задачи 4-7).

## Критерии готовности

1. `./gradlew test` — зелёный.
2. `./gradlew assembleRelease` — APK собран и подписан.
3. APK установлен на ГУ, сервис доступности включён, статус на экране —
   «Сервис работает, автоклик включён».
4. При включённом автоклике установка APK на ГУ проходит без касания экрана,
   в логе `ACTION_CLICK выполнен`.
5. При выключенном автоклике диалог установки ждёт пользователя.
6. При включённом автоклике диалог удаления приложения не подтверждается,
   в логе «пропуск: в окне есть опасное действие».
7. После перезагрузки ГУ сервис поднимается сам, состояние переключателя цело.
