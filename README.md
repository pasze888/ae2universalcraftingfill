# AE2 Universal Crafting Fill

[English](README.md) | [简体中文](README.zh-CN.md)

Click the **+** button on any JEI recipe to fill the AE2 (Applied Energistics 2) **Crafting Terminal / Wireless Crafting Terminal** 3×3 grid with its ingredients, on **Minecraft 1.21.1 (NeoForge)**.

Install by dropping this mod into `mods/` alongside AE2 and JEI (JEI is required on the client only, so dedicated servers can run without it).

Item sourcing reuses AE2's own server-side fill logic: return grid contents to the network, extract from ME storage sorted by availability, fall back to the player inventory, and Ctrl+click additionally schedules autocrafting for missing items. Ingredient amounts shown by JEI are respected when the recipe slots can be matched up; otherwise one item per slot is filled with a notice. Display-only categories that are not backed by a recipe (anvil, brewing, fuel, composting, villager trades) get no transfer button, and neither do recipes without item inputs (fluid-only recipes, etc.). It does not depend on AE2-JEI-Integration but coexists with it cleanly.

## Config

Client config `config/ae2universalcraftingfill-client.toml`:

```toml
# Recipe types / recipe serializers that get no transfer button
blacklisted_recipe_types = []
```

Anything listed there gets **no transfer button**; it can also be edited graphically through the **Config** button in the mod list. Both kinds of id are accepted, and matching either one is enough:

- the recipe type (`RecipeType`) id, e.g. `"minecraft:smelting"`, `"ae2cs:crystal_aggregator_recipe"`;
- the recipe serializer id, i.e. the `"type"` field in a datapack JSON, e.g. `"ae2cs:crystal_aggregator_recipe_serializer"`.

Mods do not always give both the same name (vanilla happens to, AE2CS does not), so either spelling works. Meant only for recipes that have a real recipe object but still should not be filled into the 3×3 grid; display-only categories and recipes without item inputs are excluded automatically and need no config.

## Documentation

- [Behavior and compatibility](docs/reference/behavior-and-compatibility.md)
- [Design notes](docs/design/universal-transfer-handler.md)
- [AE2 / JEI / NeoForge API facts](docs/reference/ae2-api.md)
- [Environment and build issues](docs/troubleshooting.md)

## License

LGPL-3.0-or-later.
