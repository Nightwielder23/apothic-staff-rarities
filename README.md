# Apothic Staff Rarities

Datapack-driven extension that brings Fallen Gems and Affixes' staff affixes to Apotheotic Additions' post-mythic rarities (Heirloom, Artifact, Esoteric).

FG&A ships AA-rarity tables for the 17 attribute affixes on staffs, but stops short of the other staff affix categories. This mod fills in the gap. The 30 added affix entries cover:

- 9 autocast affixes (acupuncture, arrow_volley, burning_dash, ice_spikes, shadow_slash, sonic_boom, stomp, sunbeam, volt_strike)
- 14 spell_effect / mob_effect affixes (acidic, bloodletting, bolstering, bursting, elusive, ensnaring, grievous, ivy_laced, revitalizing, satanic, sophisticated, swift, weakening, withering)
- 4 spell_cast affixes (bastion, hemospike, radiant, stormlash)
- 3 unique staff affixes (concentration, cooldown_reset, mana_shield)

Each entry only declares the three AA rarity buckets. Apotheosis loads each JSON as its own affix id, so FG&A keeps owning common through ancient and this mod adds parallel AA tier rolls on top.

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

`[scaling]` holds three multipliers, one per AA rarity. Each one multiplies every numeric value (cooldowns, durations, amplifiers, level ranges, step-function `min`/`steps`/`step`) on every AA-tier affix entry of that rarity.

```toml
[scaling]
heirloom_multiplier = 1.0
artifact_multiplier = 1.0
esoteric_multiplier = 1.25   # bump every esoteric staff affix by 25%
```

### Tier 2: per-affix, per-rarity, per-field absolute values

`[overrides]` has one subsection per affix per rarity. Every numeric field defaults to the sentinel `-1` (or `-1.0` for floats), which means "use the shipped default scaled by the tier 1 multiplier". A non-sentinel value replaces that scaled default outright; the multiplier no longer touches that single field.

```toml
[overrides.autocast.acupuncture.esoteric]
level_min = -1     # use scaled default
level_max = -1     # use scaled default
cooldown = 90      # absolute override; multiplier no longer applies here
```

Mix the tiers freely. A common setup is "everything 1.25x except for one cooldown I want pinned exactly" - which is the example above plus an `esoteric_multiplier = 1.25` in `[scaling]`.

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

1. Read the default from the shipped JSON.
2. If `[disable.<category>]` is true, skip the affix entirely.
3. Multiply by the tier 1 multiplier for the affix's rarity.
4. If a tier 2 override for this exact field is not the sentinel, replace the scaled value with the override.
5. Apply the final value to the live affix.

### Reloading without restarting

`/apothicstaffrarities reload` (alias `/asr reload`, requires op level 2) re-reads the config from disk and re-applies the resolution above against snapshots taken at the last datapack load. The reply summarizes total entries affected, a per-category breakdown, and any `[disable]` toggles that fired.

A regular `/reload` also picks up config changes since the override pass re-runs at the end of each datapack reload.

## License

MIT.
