# Wildlands

Мод для Minecraft Java Edition: генерация природы и ландшафта.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Forge | 61.1.5 |
| Java | 21 |
| ID мода | `wildlands` |

## Как собрать одной кнопкой

1. Откройте вкладку **Actions** в репозитории.
2. Слева выберите **Build**.
3. Нажмите **Run workflow**, затем зелёную кнопку **Run workflow** ещё раз.
4. Через несколько минут откройте завершённый запуск и скачайте **wildlands-jar** из блока **Artifacts**.
5. Распакуйте архив и положите `.jar` в папку `mods` вашей игры с Forge.

Сборка запускается только вручную, на каждый коммит она не стартует.

## Структура проекта

```
.github/workflows/build.yml     сборка по кнопке
build.gradle                    описание сборки
settings.gradle
gradle.properties
src/main/java/com/wildlands/
    Wildlands.java              точка входа мода
    registry/                   блоки, предметы, вкладка
    config/                     настройки
    worldgen/                   всё про генерацию мира
        feature/                собственные признаки
    client/                     только клиентский код
    util/                       общие помощники
src/main/resources/
    META-INF/mods.toml          описание мода
    pack.mcmeta
    assets/wildlands/lang/      переводы
    data/wildlands/worldgen/    JSON генерации мира
        biome/
        density_function/
        noise_settings/
        configured_feature/
        placed_feature/
        configured_carver/
        world_preset/
```

Порядок разработки описан в [ROADMAP.md](ROADMAP.md).

## Если сборка не проходит

Откройте неудавшийся запуск, шаг **Build**, и скопируйте последние строки с ошибкой.
