# Contributing to DeltaCalc

Thank you for your interest in contributing to DeltaCalc! We welcome bug reports, feature suggestions, documentation improvements, and code contributions from the community.

---

## Code of Conduct

All contributors and maintainers are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md). Please be respectful and collaborative in all interactions.

---

## How Can I Contribute?

### 1. Reporting Bugs
Before submitting a new issue, please check existing open issues to avoid duplicates.

When submitting a bug report:
- Use the [Bug Report Template](https://github.com/nickmatteo/DeltaCalc/issues/new?template=bug_report.yml).
- Include your Minecraft version, Fabric Loader version, Cobblemon version, and DeltaCalc version.
- Describe steps to reproduce the issue clearly.
- Provide crash logs or debug dumps (e.g. from `.minecraft/latest-calc.json` if debug dumping is enabled) where relevant.

### 2. Requesting Features or Improvements
- Open a feature request issue using the [Feature Request Template](https://github.com/nickmatteo/DeltaCalc/issues/new?template=feature_request.yml).
- Explain the motivation and expected behavior.

### 3. Adding or Updating Delta Pokémon & Custom Mechanics
Cobblemon Delta introduces custom species, regional variants, and custom mechanics. You can contribute new data or fixes by:
- Using the [New Delta Pokémon Issue Template](https://github.com/nickmatteo/DeltaCalc/issues/new?template=new_delta_pokemon.yml).
- Or updating `tools/database/build_battle_database.py` and running the build script to regenerate `src/main/resources/data/deltacalc/database/battle-database.generated.json`.

---

## Development Setup

### Prerequisites
- **JDK 21** installed and configured in your environment (`JAVA_HOME`).
- **Git**

### Getting the Code
```bash
git clone https://github.com/nickmatteo/DeltaCalc.git
cd DeltaCalc
```

### Useful Gradle Tasks
- `./gradlew check` — Run linter and verification checks.
- `./gradlew test` — Execute the automated test suite.
- `./gradlew build` — Compile the mod jar into `build/libs/`.
- `./gradlew runClient` — Launch a Fabric development client with Cobblemon and DeltaCalc loaded for manual testing.

---

## Code Guidelines

- **Kotlin Best Practices**: Follow standard Kotlin coding conventions. Prefer immutable data structures and explicit types where helpful.
- **Mixins**: Ensure Fabric Mixins are scoped tightly, cleanly documented, and handle nullable values safely to avoid breaking compatibility with other Cobblemon client mods.
- **Damage Calculations**: The core calculation logic resides in `com.cobblemonextendedbattleui.calc.DamageEngine`. All damage modifiers and edge cases should be backed by tests or verified against Pokémon Showdown / Cobblemon battle mechanics.

---

## Submitting Pull Requests

1. **Fork the repository** and create a feature branch off `main`:
   ```bash
   git checkout -b feat/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```
2. **Make your changes** with clear, descriptive commit messages (following [Conventional Commits](https://www.conventionalcommits.org/), e.g., `feat(calc): ...`, `fix(ui): ...`, `chore: ...`).
3. **Verify the build**:
   ```bash
   ./gradlew check
   ./gradlew build
   ```
4. **Push your branch** to your fork and submit a Pull Request to `main`.
5. Fill out the [Pull Request Template](.github/pull_request_template.md) completely.

---

Thank you for helping make DeltaCalc better for the entire Cobblemon community!
