# Project Execution Blueprint (`AGENTS.md`)

> **Purpose**: Operational rules and execution permissions for KReader workspace.

---

## 1. Permanent Authorizations & Autonomous Execution
1. **Automated Git Operations**:
   - Permanent authorization is granted to run `git add`, `git commit`, and `git push origin main` autonomously without asking for user confirmation.
2. **Autonomous CI Monitoring & Fixing**:
   - **FORBIDDEN**: Never use interactive `gh run watch` because it hangs indefinitely in non-TTY agent shells.
   - **MANDATORY**: Monitor CI non-interactively by polling `gh run list --limit 1 --json databaseId,status,conclusion` until `status == "completed"`.
   - In case of failure (`conclusion == "failure"`), inspect failure logs using `gh run view <id> --log-failed`.
   - Autonomously iterate, resolve any compilation, typing, or build errors, commit the fixes, and push until the CI build succeeds (`conclusion == "success"` / `✓`).

---

## 2. Universal Build & Run Templates (`fish` Shell)
All shell commands must use `fish` syntax.

```fish
# Check project setup and validate tasks
./gradlew tasks

# Assemble the Android Debug APK locally
./gradlew :composeApp:assembleDebug --configuration-cache

# Run Desktop application
./gradlew :composeApp:run
```

---

## 3. Architecture & Hardware Guardrails
1. **Code Completeness**: Always produce complete, modular, and ready-to-compile production code without placeholders, ellipses, or empty `TODO` stubs.
2. **Resource Efficiency**:
   - Optimize for ARM64 Fedora PRoot and low-overhead mobile/desktop execution.
   - Avoid AVX2-dependent binaries on legacy x86_64 machines.
