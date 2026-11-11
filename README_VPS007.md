# VPS 007 — Android-клиент

Форк [v2rayNG](https://github.com/2dust/v2rayNG) (GPL-3.0) под сервис VPS 007 (Bedolaga + Remnawave).

## Что изменено относительно v2rayNG

- Название «VPS 007», ID пакета `com.club007.vpn`, версия 1.0.0, иконки в фирменных цветах (#0a0f1a / золото #ddbd70).
- Deep link `vps007://install-sub?url=<ссылка на подписку>` для импорта подписки одним нажатием из бота (вместо `v2rayng://`, чтобы не пересекаться с оригинальным v2rayNG).
- Для запросов подписки добавлены HWID-заголовки Remnawave (`x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`), чтобы работал лимит устройств.
- User-Agent подписки остаётся `v2rayNG/2.3.15`, так что Remnawave отдаёт привычный формат.
- Из меню убраны реклама автора v2rayNG и проверка обновлений с его GitHub.
- Ссылки «О программе»: поддержка — https://t.me/biggob, бот — https://t.me/club_007_bot, политика — https://club-007.com/privacy.html.
- GitHub Actions собирает APK; при наличии ключа подписи — ещё release-APK и AAB для Google Play.

## Что поменять перед публикацией

- `V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt` → `APP_URL`: адрес вашего публичного репозитория (GPL требует открытых исходников).

## Сборка через GitHub Actions

1. Сделайте форк https://github.com/2dust/v2rayNG.
2. В форке откройте вкладку **Actions** и нажмите «I understand my workflows, go ahead and enable them».
3. Примените патч и отправьте изменения:
   ```bash
   git clone --recurse-submodules https://github.com/<ваш-логин>/v2rayNG.git vps007-android
   cd vps007-android
   git reset --hard cc074f0          # версия v2rayNG, на которой сделан патч
   git submodule update --init --recursive
   git am /путь/к/0001-VPS-007.patch
   git push -f origin master
   ```
4. Через 10–15 минут сборка появится в **Actions → Build VPS 007 APK → Artifacts → VPS007-apk**.

### Подпись для Google Play (один раз)

```bash
keytool -genkeypair -v -keystore vps007.jks -alias vps007 -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 vps007.jks > vps007.jks.b64
```

В репозитории: **Settings → Secrets and variables → Actions → New repository secret**:

| Секрет | Значение |
|---|---|
| `APP_KEYSTORE_BASE64` | содержимое `vps007.jks.b64` |
| `APP_KEYSTORE_PASSWORD` | пароль хранилища |
| `APP_KEYSTORE_ALIAS` | `vps007` |
| `APP_KEY_PASSWORD` | пароль ключа |

Файл `vps007.jks` и пароли храните отдельно: без них нельзя выпускать обновления приложения.

## Кнопка в боте Bedolaga

Deep link для кнопки «Открыть в приложении»:

```
vps007://install-sub?url={subscription_url}#VPS%20007
```

`{subscription_url}` — ссылка на подписку Remnawave (URL-encoded). Часть после `#` задаёт название подписки в приложении.
