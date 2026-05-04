# Apothic Staff Rarities

Datapack-driven extension that brings Fallen Gems and Affixes' staff affixes to Apotheotic Additions' post-mythic rarities (Heirloom, Artifact, Esoteric).

FG&A ships AA-rarity tables for the 17 attribute affixes on staffs, but stops short of the other staff affix categories. This mod fills in the gap. The 30 added affix entries cover:

- 9 autocast affixes (acupuncture, arrow_volley, burning_dash, ice_spikes, shadow_slash, sonic_boom, stomp, sunbeam, volt_strike)
- 14 spell_effect / mob_effect affixes (acidic, bloodletting, bolstering, bursting, elusive, ensnaring, grievous, ivy_laced, revitalizing, satanic, sophisticated, swift, weakening, withering)
- 4 spell_cast affixes (bastion, hemospike, radiant, stormlash)
- 3 unique staff affixes (concentration, cooldown_reset, mana_shield)

Each entry only declares the three AA rarity buckets. Apotheosis merges affix definitions across datapacks by id, so FG&A keeps owning common through ancient and this mod adds the AA tier rolls on top.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Apotheosis 7.4.x
- Fallen Gems and Affixes
- Apotheotic Additions
- Iron's Spellbooks (every entry currently gates on `irons_spellbooks` being loaded)

All dependencies are declared as optional in `mods.toml`. The mod will load even if a dep is missing; individual JSON entries are skipped at datapack load time when their `forge:mod_loaded` conditions fail.

## Configuration

Tuning will live in `config/apothic_staff_rarities-common.toml` once the config layer lands. It will let you scale the heirloom/artifact/esoteric values up or down per-affix without editing the JSON, and disable individual affix entries entirely.

## License

MIT.
