# MK15 — первичная оценка Android-платформы

Дата: 2026-11-10

## 1. Исходная точка

Данные взяты из диагностического отчёта, предоставленного владельцем устройства:
- модель: SIYI MK15;
- SoC/плата: Qualcomm MSM8953;
- ОС: Android 9, API 28;
- версия ядра: Linux 4.9.112-perf;
- ABI: arm64-v8a (также заявлены 32-битные ABI);
- дисплей: 1776×1080, 480 dpi;
- память: примерно 1.9 ГБ RAM.

Это инвентаризация, а не подтверждение, что все необходимые для AOSP исходники и бинарные компоненты доступны.

## 2. Что означает «собственная система»

Полноценная системная платформа состоит не из одного APK. В общих чертах это:
- базовая Android-платформа (framework и системные службы);
- аппаратно-зависимая совместимая основа, предоставленная производителем или открытым проектом;
- системный интерфейс и предустановленные приложения;
- проверенная модель обновления, резервного копирования и восстановления.

Без совместимых аппаратных компонентов универсальный AOSP не превращается автоматически в работающую систему для конкретного устройства.

## 3. Предварительный вывод по базе

Android 9 — логичная версия для изучения, поскольку устройство уже работает на Android 9. Однако совпадение версии Android и семейства Qualcomm не гарантирует совместимости. Официальный список тегов AOSP Android 9 указывает конкретные устройства Pixel; это не готовая сборка для MK15. (Источники: AOSP build numbers и объявление Android 9.)

Следовательно, пока нельзя честно обещать ни загрузочную сборку, ни её установку на устройство.

## 4. Ресурсы компьютера

Ранее сообщённая конфигурация рабочего ноутбука: Intel Core i3-3110M, около 8 ГБ RAM и 365.6 ГБ свободного места на диске.

Актуальная документация AOSP для современных веток указывает 64 ГБ RAM и 400 ГБ свободного места как рекомендуемый минимум рабочего окружения. Эти цифры относятся к современным веткам и не должны механически переноситься на Android 9, но показывают, что полный современный AOSP build на данном ноутбуке нецелесообразен. Можно использовать ноутбук для документации, исходников небольших компонентов и управления проектом; полноценную сборку следует оценивать отдельно.

## 5. Вариант GSI — новая исследовательская зацепка

Android Generic System Image (GSI) — универсальная системная часть Android. Это потенциальный вариант для изучения, но не готовая прошивка для MK15.

По документации Android Developers, GSI применимы только при выполнении условий, включая полную совместимость устройства с Project Treble, поддержку со стороны загрузчика и исходный запуск устройства с Android 9/API 28 или новее. Текущая версия Android 9 сама по себе не доказывает, что устройство изначально запускалось с Android 9.

Для MK15 на данный момент подтверждено только то, что установлен Android 9/API 28. Не подтверждены:
- полная совместимость с Project Treble;
- исходная версия Android при выпуске устройства;
- доступность официального и безопасного способа восстановления;
- совместимость конкретного системного образа с аппаратной ревизией.

Поэтому GSI пока остаётся гипотезой для исследования, а не кандидатом на установку. Никакие образы не загружаются и не записываются на пульт.

Источник: https://developer.android.com/topic/generic-system-image/

## 6. Что нужно выяснить до любых решений об установке

- Есть ли публично доступная документация именно для аппаратной ревизии MK15.
- Какие системные компоненты являются закрытыми и какие лицензии/условия применяются.
- Можно ли независимо проверить совместимость платформы без записи чего-либо на пульт.
- Как производитель предусматривает восстановление штатной системы и доступен ли проверенный официальный пакет восстановления.

## 7. Безопасный план работ

1. Зафиксировать аппаратные сведения и источники.
2. Составить список подтверждённых и неподтверждённых компонентов.
3. Спроектировать пользовательский интерфейс и общую архитектуру системного ПО.
4. Исследовать сборку в изолированной тестовой среде, не на MK15.
5. Отдельно оценить резервное копирование/восстановление по официальной документации.

На этом этапе не выполняются операции с загрузчиком, разделами, прошивкой, закрытыми службами производителя, MCU, радиоканалом или аппаратным трактом управления.


## 8. Read-only compatibility checks

Official Android documentation identifies these read-only properties as the first checks for GSI eligibility:
- `ro.treble.enabled` — whether Treble is enabled;
- `ro.product.first_api_level` — Android API level the device originally launched with;
- `ro.product.cpu.abi` — primary CPU ABI.

The current diagnostic report confirms Android API 28 and ARM64 ABI for the running system, but it did not include the first-launch API level or Treble property. These values must not be guessed from the current Android version.

### Windows PowerShell / ADB

With the MK15 connected and USB debugging already authorized, these commands only read Android properties:

```powershell
.\adb.exe shell getprop ro.treble.enabled
.\adb.exe shell getprop ro.product.first_api_level
.\adb.exe shell getprop ro.product.cpu.abi
```

No settings are changed and no data is written. Do not run flashing, bootloader, partition, or erase commands as part of this check.

Interpretation:
- Treble must report `true` for GSI eligibility.
- First API level should be `28` or higher for the standard Android 9+ GSI path.
- ABI should be checked against the image architecture; the existing report indicates `arm64-v8a`.

Even if these checks pass, that is not approval to install an image. Recovery availability and manufacturer-specific compatibility remain unverified.

Reference: https://developer.android.com/topic/generic-system-image/
