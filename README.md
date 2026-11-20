# Maid Clinic (车万女仆：女仆诊所)

An unofficial health-care and clinic add-on for **Touhou Little Maid**, built for **Minecraft 1.21.1 / NeoForge 21.1.x**.

This is a game mod, not a medical product. All illnesses, examinations, medicines, and treatments are fictional game mechanics. Nothing in this project is real medical advice, diagnosis, or medication guidance.

![Preview](docs/preview.png)

---

## What it does

Maid Clinic gives maids a health and clinic layer beyond feeding, clothing, and work.

It adds:

- **Health records** — in-game vital signs, constitution, illnesses, symptoms, trust, care points, and long-term health status.
- **Behaviour-driven illness** — rain, cold, heat, fatigue, injuries, poor food, allergens, sleep loss, and nearby sick maids can affect a maid's health.
- **Examinations** — in-game checks for temperature, heart rate, blood pressure, breathing, oxygen level, and blood glucose.
- **Fictional treatment flow** — in-game care items and treatment categories such as fever relief, hydration, wound care, and recovery support.
- **Recovery and long-term care** — recovery periods, possible sequelae, optional chronic conditions, care rewards, and work-efficiency effects.
- **Doctor system** — a family-doctor relationship, doctor badges, support items, and a maid doctor that can assist other maids within game rules.
- **Hospital beds** — beds that follow vanilla occupancy and sleeping behaviour and let sick maids rest.
- **Smart Terminal** — a GUI terminal that can bind up to 16 hospital beds. Click a bed to view that maid's full record. One bed can hold only one maid. The interface updates only when the displayed state actually changes.
- **Supporting items** — first-aid kit, care manual, medical mask, diagnosis notes, family-doctor contract, support kit, and related items.
- **Configuration and commands** — options for illness severity, chronic conditions, work penalties, death rules, and player-specific overrides.

---

## Why download it

If you already use Touhou Little Maid and want more depth for the maids outside of work and combat, this mod offers:

- A trackable health-record and illness system.
- A loop from examination and diagnosis to treatment, recovery, and long-term care.
- Doctor maids and clinic management.
- Hospital beds and a terminal GUI for checking several maids.
- Adjustable rules for different modpacks and difficulty preferences.

It is aimed at players who enjoy simulation, character care, and light strategy. It is not primarily a combat-item mod.

---

## Requirements

- **Minecraft 1.21.1**
- **NeoForge 21.1.x**
- **Touhou Little Maid 1.5.x (NeoForge)** — optional dependency

Touhou Little Maid is a **soft dependency**. The mod can load without it, but the maid-related health gameplay will not work. Install TLM to use the full feature set.

Only one jar with the same mod ID should be placed in `mods/`. Do not install multiple versions at the same time.

---

## Installation

1. Install Minecraft 1.21.1 and NeoForge 21.1.x.
2. Install Touhou Little Maid 1.5.x for NeoForge.
3. Place `maidclinic-1.1.0.jar` in the `mods/` folder.
4. Launch the game.

---

## Quick start

1. Use an examination or care item on your maid to create or view her health record.
2. Place a hospital bed and let a sick maid rest nearby.
3. Place a Smart Terminal.
4. Sneak-right-click the terminal to bind the nearest hospital bed.
5. Right-click the terminal to open its interface.
6. Click a bed in the left-hand list to view that maid's information.
7. Follow the in-game prompts to diagnose, treat, and provide long-term care.

The Smart Terminal supports up to 16 bound beds per terminal. A bed can be occupied by only one maid at a time.

---

## Version and compatibility

### 1.1.0 stable

The current release is **1.1.0** and contains **no Mixin**. It is the recommended version for normal play and modpacks that need maximum compatibility.

- Soft dependency on Touhou Little Maid.
- Health data is stored in world save data, not injected into maid entity NBT.
- No remote servers, telemetry, or data-upload features.
- No custom world generation and no replacement of vanilla or TLM recipes.

### Known limitation

Touhou Little Maid has its own rest and wake-up logic. As a result, hospital-bed rest may end early in some situations. This is a known limitation of the current no-Mixin version. If a maid does not stay in bed, have her rest again or adjust her current task.

---

## Health and treatment model

The mod is a compressed game simulation, not a medical system.

- Illness can develop from in-game behaviour and environment.
- Some conditions resolve on their own; others may worsen, cause complications, or leave sequelae.
- Treatment items are in-game objects with fictional effects and costs.
- Incorrect or incomplete treatment can have in-game consequences.
- Chronic conditions are optional and can be enabled through configuration or commands.
- Default settings are intended to be forgiving. Optional hardcore settings can make severe untreated illness dangerous.

No real drug names, dosages, or treatment instructions are provided by this README. Always treat game content as fiction.

---

## Doctor and care system

- Repeated care and successful treatment increase in-game trust.
- A family-doctor contract becomes available after sufficient trust and successful treatments.
- Doctor badges and support items allow a maid doctor to assist nearby maids within game rules.
- The relationship is a master-servant / professional care relationship, not romantic content.
- The mod does not add adult content.

---

## Configuration and commands

Configuration is generated as `config/maidclinic-common.toml` on first launch. It controls illness frequency, chronic conditions, work-efficiency effects, critical damage, death rules, and related options.

Useful commands include:

```text
/maidclinic status
/maidclinic hardcore on|off|default
/maidclinic chronic on|off|default
/maidclinic efficiency on|off|default
/maidclinic sequela on|off|default
/maidclinic deathmode default|virulence|sleep
```

Player-specific choices override the common configuration for that player where supported.

---

## Content and privacy notice

- This is an unofficial add-on. It is not affiliated with, authorized by, or endorsed by Minecraft, Touhou Little Maid, or their rights holders. Names and trademarks belong to their respective owners.
- The mod contains no adult content and does not promote violence, hatred, discrimination, or illegal activity.
- The mod does not provide real drugs, illegal substances, or real-world medical instructions.
- The mod does not upload data to remote servers, collect personal information, or include telemetry. Health records and terminal bindings are stored only in the current world save.
- Game content is for entertainment only. It is not a medical device or health service.

---

## Building from source

The repository includes an offline build script:

```bash
python tools/build.py
```

Output:

```text
build/libs/maidclinic-1.1.0.jar
```

Requirements for the offline build:

- JDK 21
- Minecraft 1.21.1 Mojmap client jar
- NeoForge universal jar
- Touhou Little Maid jar for compile-time compatibility

The script reads the local toolchain from `../.toolchain` and the TLM jar from `libs/`.

Resource and texture generation scripts are also included under `tools/` for development.

A Gradle build is available for IDE users, but it requires access to the NeoForge Maven repository.

---

## Project layout

```text
maidclinic/
├── build.gradle / settings.gradle / gradle.properties
├── libs/                       # TLM jar for local builds (not committed)
├── docs/                       # preview and reference material
├── tools/                      # resource, texture, and offline build scripts
└── src/main/
    ├── java/com/maidclinic/
    │   ├── health/             # illness models, vitals, saved data, core logic
    │   ├── item/               # examination items, care items, records, manuals
    │   ├── block/              # first-aid kit, hospital bed, smart terminal
    │   ├── terminal/           # terminal state and server-side update hub
    │   ├── network/            # GUI payloads
    │   ├── client/             # terminal screen (client only)
    │   ├── compat/             # TLM-only integrations
    │   ├── registry/           # registries
    │   └── util/               # text utilities
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── assets/maidclinic/  # models, textures, language files
        └── data/maidclinic/    # recipes, advancements, loot tables
```

---

## License

Code is released under the MIT License.

Textures are generated by `tools/gen_textures.py` and can be replaced freely.

Touhou Little Maid and Minecraft are third-party projects; this repository does not claim ownership of their names, assets, or trademarks.
