# Apothic Staff Rarities

Datapack-driven extension that brings Fallen Gems and Affixes' staff affixes to Apotheotic Additions' rarities (Heirloom, Artifact, Esoteric).

FG&A's staff affixes define values only through the base rarities, up to ancient; none of them declare the Apotheotic Additions tiers. Apothic Staff Rarities adds those tiers. The 30 added affix entries cover:

- 9 autocast affixes (acupuncture, arrow_volley, burning_dash, ice_spikes, shadow_slash, sonic_boom, stomp, sunbeam, volt_strike)
- 14 spell_effect / mob_effect affixes (acidic, bloodletting, bolstering, bursting, elusive, ensnaring, grievous, ivy_laced, revitalizing, satanic, sophisticated, swift, weakening, withering)
- 4 spell_cast affixes (bastion, hemospike, radiant, stormlash)
- 3 unique staff affixes (concentration, cooldown_reset, mana_shield)

Each entry only declares the three AA rarity buckets. Apotheosis loads each JSON as its own affix id, so FG&A keeps owning common through ancient, and Apothic Staff Rarities adds parallel AA tier rolls on top.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Apotheosis 7.4.x
- Fallen Gems and Affixes
- Apotheotic Additions
- Iron's Spellbooks (every entry currently gates on `irons_spellbooks` being loaded)

All dependencies are declared as optional in `mods.toml`. The mod will load even if a dep is missing; individual JSON entries are skipped at datapack load time when their `forge:mod_loaded` conditions fail.

## Configuration

Tuning lives in `config/apothic_staff_rarities-common.toml`. The file is created on first launch with all defaults set, plus a header comment that walks through the layout.

The config is two-tier so both casual and power users have a clean way in:

### Tier 1: rarity-wide multipliers

`[scaling]` holds three multipliers, one per AA rarity, clamped to the range 0.01 to 100.0. What a multiplier changes depends on the value:

- Autocast and spell affixes: the level range and the cooldown, rounded to whole numbers.
- Step-function values (durations, the `cooldown_reset` and `mana_shield` chances, and amplifiers written as a `min`/`steps`/`step` block): the per-level step term, so the level-dependent part scales in proportion to the multiplier. The base floor and the step count stay as written in the JSON.
- Integer amplifiers (a bare number): the amplifier, rounded to the nearest whole level, so a multiplier close to 1.0 may leave it unchanged.
- Concentration is a yes/no flag, so the multiplier leaves it alone.

```toml
[scaling]
heirloom_multiplier = 1.0
artifact_multiplier = 1.0
esoteric_multiplier = 1.25   # scale every esoteric staff affix up
```

At high multipliers a mob-effect amplifier can climb past the levels Minecraft has display names for, so a potion effect may show a raw key such as `potion.potency.8` instead of a roman numeral. This is a vanilla display limit for high potion levels, not a fault in Apothic Staff Rarities, and does not change the actual effect strength.

### Tier 2: per-affix, per-rarity, per-field absolute values

`[overrides]` has one subsection per affix per rarity. Leave a field out to keep the tier 1 scaled default; set a field to any value to override it absolutely, and the multiplier no longer touches that field. Old config files that still set a field to `-1` (or `-1.0`) keep working for backward compatibility, but omitting the field is the current way.

```toml
[overrides.autocast.acupuncture.esoteric]
cooldown = 90      # absolute override; omitted fields stay tier 1 scaled
```

Mix the tiers freely. A common setup is everything at 1.25x with one cooldown pinned to an exact value, which is the example above plus an `esoteric_multiplier = 1.25` in `[scaling]`.

### Disabling a category

`[disable]` skips an entire affix category at AA rarities. Each entry there sets the affected affix's rarity-values map to empty at load, so the affix cannot roll on heirloom/artifact/esoteric. FG&A's own common-through-ancient entries are untouched.

```toml
[disable]
autocast = true        # no AA-rarity autocast affixes will roll
mob_effect = false
spell = false
concentration = false
cooldown_reset = false
mana_shield = false
```

### Resolution order

Each affix value is computed at load as:

1. Read the default value from the JSON.
2. If `[disable.<category>]` is true, skip the affix entirely.
3. Scale the value by the tier 1 multiplier for the affix's rarity.
4. If a tier 2 override for this field is set, replace the scaled value with the override.
5. Apply the final value to the live affix.

### Reloading without restarting

`/apothicstaffrarities reload` (alias `/asr reload`, both requiring op level 2) re-reads the config from disk and re-applies it against snapshots taken at the last datapack load. The reply is one short line: `reloaded, applied to N affixes` when the config changed since the last apply, or `config unchanged` when it did not.

A regular `/reload` also picks up config changes since the override pass re-runs at the end of each datapack reload.

## License

MIT.
