# Teammate Setup — from zero to a working demo

For Windows 10/11. Mac notes are at the end. It takes about 45–60 minutes if nothing is installed yet.

**How to use this:** each step starts with a **CHECK**. If the check passes, skip to the next step. If it fails, do the **INSTALL** part, then run the check again.

Run the checks in a **PowerShell** window (Start → type *PowerShell* → open it). To paste into PowerShell, right-click.

---

## Step 1 — Java (JDK 8 or newer)

**CHECK:**
```powershell
java -version
javac -version
```
✅ Passes if **both** print a version, e.g. `17.0.x` or `21.0.x`, or `1.8.0_xxx` for Java 8.
❌ Fails if:
- either one says *"not recognized"*, or
- `java` works but `javac` doesn't. That means only a JRE is installed, and a JDK is needed.

**INSTALL:**
1. Go to **adoptium.net** → download **Temurin 21 (LTS)**, Windows x64, `.msi` installer.
2. Run it. On the *Custom Setup* screen, set **"Set JAVA_HOME variable"** and **"Add to PATH"** to *"Will be installed"*.
3. **Close and reopen PowerShell**, then run the check again.

---

## Step 2 — MySQL 8 Server + MySQL Workbench

**CHECK:**
```powershell
Get-Service *mysql*
```
✅ Passes if you see a service such as **MySQL80** with Status **Running**, **and** you know the MySQL *root* password.
✅ Workbench check: Start menu → type *MySQL Workbench*. It should be listed.

❌ If nothing is listed, go to INSTALL.
- ⚠️ If the service exists but is **Stopped**, run `Start-Service MySQL80` in PowerShell (as Administrator).
- ⚠️ If MySQL is installed but **you don't know the root password**, tell the team. Resetting it is fiddly, and on a non-work laptop reinstalling is often quicker.

**INSTALL:**
1. Go to **dev.mysql.com/downloads/installer** → download **MySQL Installer for Windows** (the larger "full" file is simplest).
2. Choose **Custom** and add **MySQL Server 8.0.x** and **MySQL Workbench 8.0.x**.
3. On the authentication screen, keep **"Use Strong Password Encryption"**.
4. Set a **root password** and write it down.
5. Keep **"Configure MySQL Server as a Windows Service"** and **"Start at System Startup"** ticked.
6. Finish, then run the check again.

---

## Step 3 — Git (needed for the shared repo)

**CHECK:**
```powershell
git --version
```
✅ Passes if it prints a version such as `git version 2.4x`.

**INSTALL:** go to **git-scm.com/download/win** → run the installer. The default options are fine. Close and reopen PowerShell, then run the check again.

---

## Step 4 — An editor (Antigravity or VS Code)

Either works; the team uses **Antigravity**. You only need its built-in terminal. **No Java extension is required**, because we build with scripts.

---

## Step 5 — Get the project

1. Check your email (or github.com → the bell icon) for the **repository invitation** and click **Accept**. You need a GitHub account first (github.com → Sign up, free).
2. Open PowerShell and run (paste the repo URL the team sent you):
   ```powershell
   mkdir C:\projects
   cd C:\projects
   git clone https://github.com/<owner>/realtime-db-monitor.git
   cd realtime-db-monitor
   git checkout builder-b
   ```
   The first time, a GitHub sign-in window opens. Sign in, and the download continues.
   `git checkout builder-b` switches to **your own branch**. Everything you build goes there, never directly to `main`.
3. Open that folder in Antigravity: *File → Open Folder* → `C:\projects\realtime-db-monitor`.

✅ Passes if the folder contains `README.md`, `HANDOFF.md`, `src`, `sql`, and so on, and `git branch` shows `* builder-b`.

---

## Step 6 — MySQL driver (Connector/J 8.x)

**CHECK:** open the project's `lib\` folder. ✅ Passes if it contains exactly **one** file named like `mysql-connector-j-8.x.x.jar`. It's not included in the repo on purpose.

**INSTALL:**
1. Go to **dev.mysql.com/downloads/connector/j**. The page shows version 9.x by default: click **"Archives"** (or *"Looking for previous GA versions?"*) and choose an **8.x** version, e.g. 8.4.0.
2. Operating System: **Platform Independent** → download the **ZIP**.
3. Unzip it, find `mysql-connector-j-8.x.x.jar` inside, and copy **only that jar** into the project's `lib\` folder.

(Or ask a teammate to send you the same jar file.)

---

## Step 7 — Create the database (one script)

**CHECK:** open Workbench → connect to *Local instance* **as root** (with your root password). In the left panel (*Schemas*), press the refresh icon.
✅ Passes if **alert_monitor** is listed, its *Tables* include **alerts**, **data_changes**, **data_change_values**, **alert_broadcast_log** and **products**, and *products* has 3 *Triggers*.
If anything is missing or looks wrong, just do the INSTALL below. It starts from scratch every time.

**INSTALL:** in the **root** connection: *File → Open SQL Script* → the project's `sql\setup.sql` → click the ⚡ lightning bolt.
✅ The last result tab shows **SETUP OK | 20 | 3 | 0** (20 products, 3 triggers, 0 changes).

- If you see **Error 1419** ("SUPER privilege ... binary logging"), you ran it from a non-root connection. Run it again from the root connection.
- The script **deletes and rebuilds** the `alert_monitor` database each time, and resets the `alertapp` password. Nothing else in MySQL is touched.

This creates the shared local user `alertapp` / `alertpass`, which `config\app.properties` already uses. **You don't need to edit any config.** (These are throwaway local credentials. Never reuse them as a real password.)

To reset only the demo data (products back to the original 20, no alerts), run `sql\reset-demo.sql` the same way.

---

## Step 8 — Build and self-test

In Antigravity: *Terminal → New Terminal*. Make sure it's in the project folder (the prompt should end in `realtime-db-monitor>`), then run:
```powershell
.\build.cmd
.\run.cmd checks
```
✅ Expected:
- `BUILD OK`
- then four results, each with **0 failed**:
  - `RESULT: 41 passed` (config, model and database setup)
  - `RESULT: 24 passed` (alert database code)
  - `RESULT: 26 passed` (the product rules R1-R10)
  - `RESULT: 19 passed` (a test product is added, changed and deleted, and the right alerts are created; it cleans up after itself)

| If you see | Fix |
|---|---|
| `javac is not recognized` | Step 1: reinstall with "Add to PATH", then restart Antigravity |
| `.\build.cmd is not recognized` | The terminal isn't in the project folder. Run `cd C:\projects\realtime-db-monitor` |
| Part C says **BLOCKED** | Step 6: the jar is missing from `lib\`, or there is more than one jar there |
| `Access denied for user 'alertapp'` | Step 7: run `setup.sql` as root (it also resets the password) |
| `Communications link failure` | MySQL is stopped: `Start-Service MySQL80` (as Administrator) |
| `Unknown database 'alert_monitor'` | Step 7 |
| `table 'products' exists` or `3 change-capture triggers` FAILS | Step 7: run `setup.sql` from the root connection |
| DetectionCheck fails with "detectOnce -> 1 alert" | The server is running. Stop it (Ctrl+C in its terminal) and run the checks again |
| A time-zone error | In `config\app.properties`, add `&serverTimezone=Asia/Kolkata` to the end of `db.url` |

---

## Step 9 — Run the demo (the final check)

Open **four terminals** (click the **+** in the terminal panel), all in the project folder:

| Terminal | Command | What you should see |
|---|---|---|
| 1 | `.\run.cmd server` | `started with 10 product rules` and `running` |
| 2 | `.\run.cmd admin` | The operator console opens, with 4 tabs: Alerts, Changes, Rules, Broadcast Log |
| 3 | `.\run.cmd dashboard` | `[CONNECTED] ... waiting for alerts` |
| 4 | `.\run.cmd employee` | The *Product Catalogue - Employee* window opens, showing 20 products |

Now, in **Workbench** (root connection), run:
```sql
UPDATE alert_monitor.products SET price = 49 WHERE id = 3;
```
Within about 2 s, terminal 3 prints a **CRITICAL** `[CHG]` line (*price 15999.00 -> 49.00*), and the Admin's **Alerts** and **Changes** tabs show it.

In the Admin, select that alert and click **Resolve**. Terminal 3 prints the RESOLVED update.

To stop the server or dashboard, click into its terminal and press **Ctrl+C**. Afterwards, run `sql\reset-demo.sql` to put the data back.

✅ **If all of this works, your setup is complete.** Send the team a screenshot of the Step 8 results.

---

## Step 10 — Your Claude session (builders only)

Start a **new** Claude chat and attach these files from the project:
- `REALTIME_DB_MONITOR_MASTER_CONTEXT_v7.md` (the requirements)
- `HANDOFF.md` (current state, decisions, and your next task in §6)
- `CONTRACT_SNAPSHOT.txt` (exact method signatures)
- the source files for the task. For the dashboard task, that's all of `src\dbmonitor\common\` plus `test\ConsoleDashboard.java`.

HANDOFF §0 lists what changed from the v7 file (v8). Tell Claude that §0 wins where they differ.

Then paste the prompt from **HANDOFF.md §6**.

---

## Step 11 — Saving and sharing your work (every session)

Git keeps everyone's copy in sync. You work on **your branch `builder-b`**. Builder A merges it into `main` after a verifier has checked it.

**Start of each session:** get the latest shared code into your branch:
```powershell
cd C:\projects\realtime-db-monitor
git checkout builder-b
git pull origin main
```

**When something works** (for example, it builds and `.\run.cmd checks` passes), save it and upload it:
```powershell
git add .
git status
git commit -m "dashboard: AlertCard + DashboardFrame first version"
git push
```
`git status` shows what's about to be saved. Check that it lists only your files (e.g. `src/dbmonitor/client/...`, `build.cmd`, `HANDOFF.md`). Never commit `out\` or the jar. The repo's `.gitignore` already blocks them.

**End of each session:** ask your Claude to update **HANDOFF.md §2 and §9** (what you did, what's open), then commit and push as above, and tell the team on your group chat. Builder A merges your branch into `main` when it's verified.

**Rules:**
- Only work in `src\dbmonitor\client\` (plus the build/run scripts and HANDOFF). Other packages belong to Builder A, so ask first.
- If `git pull` reports a **conflict**, stop and ask Builder A. Don't guess.

---

## Mac notes
- **Java:** Temurin `.pkg` from adoptium.net. Check with `java -version` and `javac -version` in Terminal.
- **MySQL:** the "macOS DMG Archive" from dev.mysql.com, plus Workbench (a separate DMG). You can start and stop the server in *System Settings → MySQL*.
- **Build and run:** `sh build.sh`, then `sh run.sh checks | server | admin | employee | dashboard`.
