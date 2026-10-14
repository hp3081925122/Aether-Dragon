# Aether Dragon 天境巨龙

天境巨龙独立 Boss 模组，包含多阶段战斗、专属技能与高空结构。
Standalone Aether Dragon boss mod with multi-phase combat, dedicated skills and a sky structure.

当前分支：`main`；Minecraft 1.21.1 / NeoForge / Java 21；模组版本 1.2.0。

- `1201-forge`：Minecraft 1.20.1 Forge，Java 17
- `main`：Minecraft 1.21.1 NeoForge，Java 21
- `2612-neoforge`：Minecraft 26.1.2 NeoForge，Java 25

需要安装对应游戏版本与加载器的 GeckoLib。配置文件为 `config/aether_dragon-common.toml`。

本次更新修复 Boss 因玩家远离而自然消失的问题；新生成与旧存档重新加载的 Boss 都会启用持久化。
This release prevents distance-based natural despawning for newly spawned bosses and bosses loaded from existing saves.

构建 / Build：使用上述 Java 版本执行 `./gradlew build`。

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/aether-dragon)

原作 / Original work：[Aether bedrock mobs](https://modrinth.com/mod/aether-bedrock-mobs)，CC0-1.0。
