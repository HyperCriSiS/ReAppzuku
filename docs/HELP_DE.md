# 📖 ReAppzuku — FAQ

> Vollständiger Leitfaden zur Einrichtung und Nutzung von ReAppzuku

---

## Inhaltsverzeichnis

- [Was ist ReAppzuku?](#was-ist-reappzuku)
- [Voraussetzungen](#voraussetzungen)
- [Einrichtung für den Hintergrundbetrieb](#einrichtung-für-den-hintergrundbetrieb)
- [Erste Schritte](#erste-schritte)
- [Manuelle Steuerung](#manuelle-steuerung)
- [Hauptbildschirm](#hauptbildschirm)
  - [Symbolleiste](#symbolleiste)
  - [App-Trigger](#app-trigger)
- [Einstellungen](#einstellungen)
  - [Informationen](#-informationen)
  - [Aussehen](#-aussehen)
  - [App Stabilität](#️-app-stabilität)
  - [Auto-Kill Einstellungen](#-auto-kill-einstellungen)
  - [Erweiterte Tools](#-erweiterte-tools)
    - [Hintergrundbeschränkungen](#hintergrundbeschränkungen)
    - [Planung der Beschränkungen](#planung-der-beschränkungen)
    - [Schlafmodus](#schlafmodus)
  - [Über](#ℹ️-über)
- [Statistiken & Protokolle](#-statistiken--protokolle)
- [Geschützte Apps](#geschützte-apps)
- [FAQ](#faq)

---

## Was ist ReAppzuku?

**ReAppzuku** ist ein Dienstprogramm zur Diagnose und Verwaltung von Hintergrundprozessen. Es bietet eine breite Auswahl an Einschränkungsszenarien für jede App.

`Warum brauche ich ReAppzuku, wenn modernes Android die App-Kontrolle doch selbst gut beherrscht?` — Ja, das tut es, aber nicht perfekt. Die OS-Entwickler verbessern und modernisieren aktiv die Systemmechanismen für die Prozessverwaltung. Gleichzeitig gibt es zahlreiche Schlupflöcher, die es Apps ermöglichen, im Hintergrund aktiv zu bleiben. Diese reichen von harmlosen Empfängern bis hin zu aggressiven Alarms, Wakelocks und anderen Aufbewahrungsmechanismen. Letztendlich verhindern sie, dass Geräte in den Tiefschlafmodus wechseln, überlasten CPU/RAM und verbrauchen gerne Batteriestrom.

---

## Voraussetzungen
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

| Voraussetzung | Beschreibung |
| :--- | :--- |
| **Android** | 7.0 oder höher. Hintergrundbeschränkungen nur auf Android 11+ verfügbar |
| **Root** oder **Shizuku** | Eines von beiden wird benötigt |

### Root vs. Shizuku

- **Root** — Bevorzugter Modus, wird automatisch verwendet, falls verfügbar
- **Shizuku** — Root-freie Alternative. Wird aus dem Play Store installiert, erfordert eine Ersteinrichtung über ADB oder den MIUI/HyperOS-Entwicklermodus

> [!NOTE]
> Der aktuelle Betriebsmodus wird immer in **Einstellungen → Informationen → Betriebsmodus** angezeigt.

---

## Einrichtung für den Hintergrundbetrieb
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

Damit ReAppzuku zuverlässig läuft, ohne vom System beendet zu werden, müssen die Berechtigungen richtig konfiguriert werden. Die Schritte hängen von Ihrer Firmware ab.

---

### Batterieoptimierung (alle Firmwares)

Wichtigster Schritt. Wenn dies nicht deaktiviert wird, wird das System ReAppzuku regelmäßig beenden.

**Einstellungen → Apps → ReAppzuku → Akku → Uneingeschränkt**

Oder über den Systemdialog:

**Einstellungen → Akku → Batterieoptimierung → Alle Apps → ReAppzuku → Nicht optimieren**

---

### In den Letzten Apps anheften (alle Firmwares)

Öffnen Sie die Letzten Apps (Quadrat-Taste oder Wisch vom unteren Bildschirmrand), suchen Sie ReAppzuku und tippen Sie auf das **Schloss-Symbol** 🔒. Dadurch wird verhindert, dass die App beim Löschen der Letzten Apps entladen wird.

---

### MIUI / HyperOS (Xiaomi, Redmi, POCO)

<details>
<summary>Anweisungen erweitern</summary>

**Autostart:**

Einstellungen → Apps → Apps verwalten → ReAppzuku → Autostart → Aktivieren

**Hintergrundaktivität:**

Einstellungen → Apps → Apps verwalten → ReAppzuku → Energiesparmodus → Keine Einschränkungen

**In Letzten Apps sperren:**

Letzte Apps → ReAppzuku-Karte lange drücken → Schloss-Symbol antippen

**Zusätzlich (MIUI 12+):**

Einstellungen → Apps → Apps verwalten → ReAppzuku → Andere Berechtigungen → Im Hintergrund ausführen → Erlauben

</details>

---

### One UI (Samsung)

<details>
<summary>Anweisungen erweitern</summary>

**Hintergrundaktivität erlauben:**

Einstellungen → Gerätewartung → Akku → Nutzung im Hintergrund einschränken → Schlafende Apps → Stellen Sie sicher, dass ReAppzuku nicht aufgeführt ist

**Adaptiven Akku deaktivieren:**

Einstellungen → Gerätewartung → Akku → Weitere Akku-Einstellungen → Adaptiver Akku → Aus (optional, wenn weiterhin Probleme auftreten)

**Autostart:**

Einstellungen → Apps → ReAppzuku → Akku → Uneingeschränkt

</details>

---

### ColorOS / OxygenOS (OPPO, OnePlus, Realme)

<details>
<summary>Anweisungen erweitern</summary>

**Autostart:**

Einstellungen → App-Verwaltung → ReAppzuku → Autostart → Aktivieren

**Hintergrundaktivität:**

Einstellungen → App-Verwaltung → ReAppzuku → Energiesparmodus → Nicht einschränken

**Zusätzlich:**

Einstellungen → Akku → Batterieoptimierung → ReAppzuku → Nicht optimieren

</details>

---

### Flyme (Meizu)

<details>
<summary>Anweisungen erweitern</summary>

**Autostart:**

Einstellungen → Berechtigungen → Autostart → ReAppzuku → Aktivieren

**Hintergrundaktivität:**

Einstellungen → Berechtigungen → Hintergrundausführung → ReAppzuku → Aktivieren

**App-Sicherheit:**

Sicherheitscenter → Berechtigungsverwaltung → ReAppzuku → alle Berechtigungen aktivieren

</details>

---

### OriginOS / Funtouch OS (Vivo)

<details>
<summary>Anweisungen erweitern</summary>

**Autostart:**

Einstellungen → Apps → Apps verwalten → ReAppzuku → Berechtigungen → Autostart → Aktivieren

**Hintergrundaktivität:**

Einstellungen → Apps → Apps verwalten → ReAppzuku → Energieverbrauch → Hohe Hintergrundleistung

</details>

---

### MagicOS (Honor)

<details>
<summary>Anweisungen erweitern</summary>

**Autostart:**

Einstellungen → Apps → App-Start → ReAppzuku → Manuell → Autostart, Hintergrundaktivität

**Akku:**

Einstellungen → Akku → App-Start → ReAppzuku → Nicht einschränken

</details>

---

### So überprüfen Sie, ob alles richtig eingerichtet ist

Nach der Einrichtung:
1. Aktivieren Sie den **Hintergrunddienst** in den ReAppzuku-Einstellungen
2. Sperren Sie den Bildschirm für 10–15 Minuten
3. Entsperren Sie das Gerät und öffnen Sie ReAppzuku — der Dienst sollte noch aktiv sein
4. Wenn der Dienst gestoppt wurde — wiederholen Sie die Schritte für Ihre Firmware

> [!TIP]
> Gerätespezifische Anleitungen: [dontkillmyapp.com](https://dontkillmyapp.com)

---

## Erste Schritte
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

> [!CAUTION]
> Dieser Abschnitt dient nur zu Informationszwecken und ist keine Bedienungsanleitung für die Verwendung von ReAppzuku. Konfigurieren Sie alle Funktionen selbst, bewusst und mit Sorgfalt, und denken Sie daran, dass eine falsche Konfiguration einer Funktion den normalen Betrieb der Ziel-Apps beeinträchtigen kann.

Auf den ersten Blick mag ReAppzuku wie ein komplexes Werkzeug mit einer Vielzahl von Einstellungen aussehen. In Wirklichkeit ist es gar nicht so schwer, sich zurechtzufinden.

**Ersteinrichtung**\
Erteilen Sie Root- oder Shizuku-Berechtigungen. Entfernen Sie unbedingt alle Systemeinschränkungen für die App (Batterieoptimierung, Autostart-Sperre, Sperre in Letzten Apps usw.).\
Starten Sie ReAppzuku, gehen Sie zu den Einstellungen und aktivieren Sie den **Vordergrunddienst** — diese Einstellung startet die Hintergrundprozesse der App: App-Überwachung, Statistikerfassung usw.

**Statistiken sammeln**\
Aktivieren Sie nicht sofort Auto-Kill oder konfigurieren Sie andere Einschränkungen. Geben Sie der App 1–2 Tage Zeit, um Statistiken zu sammeln — dies hilft, genauer zu bestimmen, welche Apps das Gerät tatsächlich im Hintergrund belasten.

**Daten analysieren**\
Sobald Daten gesammelt wurden, gehen Sie zum Abschnitt **"Statistiken & Protokolle"** und studieren Sie die Verbrauchsdiagramme (Akku, CPU, RAM). Sie können dies auch mit den Batteriestatistiken in den Einstellungen Ihres Telefons abgleichen. Die Diagrammlegende zeigt, welche Apps die meisten Ressourcen verbrauchen.

**Auto-Kill konfigurieren**\
Es wird empfohlen, mit dem **Blacklist-Modus** zu beginnen — das ist sicherer, da er nur explizit ausgewählte Apps betrifft und nicht alle. Für das periodische Auto-Kill-Intervall können Sie mit 1 Minute beginnen und es später an Ihre Bedürfnisse anpassen.\
Nachdem Auto-Kill 1–2 Stunden gelaufen ist, gehen Sie zum Abschnitt "Statistiken & Protokolle" und öffnen Sie das Protokoll **Top Offenders**. Dort sehen Sie, welche Apps am häufigsten beendet wurden und welche davon sofort neu gestartet wurden.\
Apps, die mehr als 3 Mal neu starten, sind gute Kandidaten für Hintergrundbeschränkungen. Sie können mit dem Einschränkungstyp "Sanft" beginnen.

**Feinabstimmung**\
Für eine tiefere Analyse verwenden Sie die Schaltfläche **App-Trigger** auf dem Hauptbildschirm — sie zeigt genau, was die App verwendet, um im Hintergrund zu bleiben. Abhängig vom Ergebnis können Sie andere Hintergrundbeschränkungstypen anwenden. Weitere Details dazu finden Sie im entsprechenden Abschnitt des Leitfadens.

> [!TIP]
> Auto-Kill beseitigt die Tatsache, dass die App im Hintergrund läuft, aber nicht den Grund, warum sie immer wieder zurückkommt. Für die vollständige Kontrolle wird empfohlen, auch Hintergrundbeschränkungen und den Schlafmodus zu konfigurieren.

---

## Manuelle Steuerung
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**Schnelleinstellungen (Kacheln)**\
Zum Benachrichtigungsfeld hinzugefügt:

| Kachel | Aktion |
| :--- | :--- |
| **App beenden** | Beendet die aktuelle Vordergrund-App |
| **Hintergrund-Apps beenden** | Führt Auto-Kill mit Ihren Whitelist-/Blacklist-Einstellungen aus |

**Widget**\
Widget für den Startbildschirm — zeigt die Auto-Kill-Statistiken der letzten 12 Stunden und die aktuelle RAM-Auslastung an.

**Verknüpfung (Shortcut)**\
Statische Verknüpfung durch langes Drücken des App-Symbols — beendet die aktuelle Vordergrund-App.

---

## Hauptbildschirm
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

Der Hauptbildschirm zeigt alle aktiven Hintergrund-Apps mit Echtzeit-RAM- und CPU-Auslastung. Der obere Bereich zeigt Gesamtstatistiken: Anzahl der aktiven Apps und aktuelle RAM-Auslastung.

### Symbolleiste
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

Drei Schaltflächen in der Symbolleiste:

- 🔍 **Suche** — Filtert die Liste nach App-Namen oder Paket
- 🔽 **Sortieren** — Konfiguriert die Anzeigereihenfolge
- ☑️ **Alle auswählen** — Wählt alle Apps für einen Beendigungsvorgang mit einem Tipp aus

**Sortieren**

Die Liste kann sortiert werden nach:
- **Standard** — Benutzer-Apps zuerst, dann System-Apps
- **RAM-Nutzung: Hoch → Niedrig / Niedrig → Hoch**
- **CPU-Last: Hoch → Niedrig / Niedrig → Hoch**
- **Name A → Z** / **Name Z → A** — alphabetisch

Sie können auch die Anzeige von System- und persistenten Apps umschalten.

**Scannen**
Führt einen Scan der aktuellen Systemlast durch alle aktiven Apps in der Liste durch. Lastkategorien:
- CPU-Bindung
- Netzwerk-Bindung
- Vordergrunddienst-Bindung
- Gerät aufwecken (verhindert Schlafmodus)
- Sensor-Bindung
- GPS-Bindung

> [!NOTE]
> Der Scan funktioniert nicht bei persistenten und geschützten Apps, selbst wenn sie in der Liste der aktiven Apps angezeigt werden.

> [!TIP]
> Bedenken Sie, dass je mehr aktive Apps angezeigt werden (z.B. wenn die Anzeige von System-Apps aktiviert ist), desto länger dauert der Scan.

### App-Aktionen

Das Antippen einer App in der Liste öffnet ein Schnellaktionsmenü:

- **App-Info** — Öffnet die standardmäßige System-App-Info
- **App-Trigger** — Detaillierte Analyse der Ursachen für Hintergrundaktivität (siehe unten)
- **Deinstallieren** — Entfernt die App vom Gerät (bei System-Apps nicht verfügbar)
- **Hinzufügen zu...** — Fügt die App schnell zu einer der folgenden Listen hinzu:
  - Whitelist
  - Blacklist
  - Ausgeblendete Apps
  - Hintergrundbeschränkung (Sanft)

### App-Trigger
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

Trigger sind ein tiefgreifendes Diagnosewerkzeug, das die **tatsächlichen Gründe** für die Hintergrundaktivität einer App auf Systemebene analysiert. Statt Vermutungen — präzise technische Fakten: Was hält die App im Speicher, wie oft wacht sie auf und ob sie gerade aktive Netzwerkverbindungen hat.

Analysiert **63 unabhängige Faktoren (43 Hauptfaktoren und 20 zusätzliche, abhängig von der Android-Version)** über Systembefehle in Echtzeit.

---

**App-Status (aktiv / Hintergrund / zwischengespeichert)**\
Bestimmt durch die Prozesspriorität im Linux-Kernel in Kombination mit der Erkennung aktiver Dienste. Dies ist der gleiche Wert, den Android verwendet, um zu entscheiden, welche Prozesse bei Speichermangel beendet werden sollen.

- **Aktiv** — App befindet sich im Vordergrund oder hält Systemressourcen (Dienst, Alarm usw.)
- **Hintergrund • aktiver Dienst** — Läuft im Hintergrund mit einem aktiven Vordergrunddienst.
- **Hintergrund** — Läuft still, aber das System hält es für notwendig.
- **Zwischengespeichert • hält Dienst** — Prozess ist im Cache, hält aber einen aktiven Dienst am Laufen.
- **Zwischengespeichert • kürzlich verwendet** — Prozess ist im Cache, wurde kürzlich verwendet.
- **Zwischengespeichert • inaktiv** — Prozess ist am Leben, aber Android ist bereit, ihn jederzeit zu beenden.

---

**Aggressionswert**\
Wird auf einer 100-Punkte-Skala basierend auf den Triggern bewertet.
- Aktive Trigger: + **6 Punkte** jeder.
- Kann App jederzeit aufwecken: + **5 Punkte** jeder.
- Andere Trigger: **0–4 Punkte** je nach Wichtigkeit. Einige dienen nur zur Information und beeinflussen den Wert nicht.

> [!TIP]
> Was Sie basierend auf dem Aggressionswert tun können:
> - 0–40 — Das System kann dies selbst regeln. Kein dringender Bedarf an Einschränkungen.
> - 41–65 — Mittleres Niveau. Auto-Kill oder der Typ "Sanft" der Hintergrundbeschränkungen könnte ausreichen.
> - 66+ — Idealer Kandidat für Auto-Kill, harte oder manuelle Hintergrundbeschränkungen oder den Schlafmodus.

> [!CAUTION]
> Dieser Hinweis dient nur zu Informationszwecken und sollte nicht als Empfehlung verstanden werden. Entscheiden Sie, ob Sie Einschränkungen für eine App anwenden, basierend auf Faktoren wie:
> - dem Verhalten der App.
> - ihren Triggern und dem Aggressionswert.
> - dem aktuellen Status, der vom System zugewiesen wurde.
> - der Ressourcennutzung des Geräts (Akku, RAM, CPU).

---

#### Triggertypen:

**Tatsächlich**

Die App verbraucht **gerade jetzt** Ressourcen.

- **Vordergrunddienst**.
Die App hat einen Hintergrunddienst mit einer dauerhaften Benachrichtigung gestartet. Die zuverlässigste Methode, um nicht beendet zu werden — Android wird solche Prozesse nicht anfassen, solange die Benachrichtigung sichtbar ist. Zeigt den Diensttyp an: Medienwiedergabe, Standort, Telefonanruf, verbundenes Gerät usw.

- **FG-Benachrichtigungskanal**.
Ergänzt die Informationen zum Vordergrunddienst: Zeigt die Wichtigkeit des Benachrichtigungskanals. Die Wichtigkeit URGENT oder HIGH wird als Pop-up-Banner angezeigt — für das System extrem schwer zu unterdrücken, was einen Zwangsstopp nahezu unmöglich macht.

- **Haftender Dienst (Sticky Service)**.
Dienst, der als `START_STICKY` deklariert wurde — Android startet ihn nach dem Beenden automatisch neu. Die App kann ohne Deaktivierung nicht dauerhaft gestoppt werden.

- **Durch Bindungen gehalten**.
Ein oder mehrere Prozesse halten eine aktive Bindung an den Dienst dieser App. Solange die Bindung besteht, kann Android den Prozess nicht beenden. Google Play Services (GMS) ist ein häufiger Übeltäter — hält Push-Verbindungen und Kontosynchronisations-Bindungen.

- **WakeLock**.
Die App hat das System explizit aufgefordert, "wach zu bleiben". `PARTIAL_WAKE_LOCK` — CPU läuft bei ausgeschaltetem Bildschirm; `FULL_WAKE_LOCK` — Bildschirm bleibt ebenfalls an. Zeigt das Lock-Tag, den Typ und die Haltedauer. Entlädt den Akku direkt, solange es gehalten wird.

- **Netzwerkaktivität**.
Die App hat aktive Hintergrund-Netzwerkaktivität. Offene TCP-Verbindungen deuten auf einen laufenden Datenaustausch hin — typisch für Messenger, Push-Clients und Echtzeit-Synchronisations-Apps. Es wird nur Verkehr über 10 KB gezählt, zusammen mit `ESTABLISHED`-Verbindungen.

- **Sensoren**.
Die App fragt aktiv Hardwaresensoren ab: Beschleunigungsmesser, Gyroskop, Barometer, GPS, Herzfrequenzmesser und andere. Die kontinuierliche Nutzung von Sensoren entlädt den Akku auch bei ausgeschaltetem Bildschirm. Zeigt Sensornamen und Abfragerate, falls verfügbar.

- **Standort**.
Die App fordert Standortdaten an. Zeigt Genauigkeitsstufe (HIGH_ACCURACY, BALANCED, LOW_POWER), ob im Hintergrund oder Vordergrund und das minimale Aktualisierungsintervall. Hintergrund-Standort mit hoher Genauigkeit ist am ressourcenintensivsten.

- **Audio-Fokus**.
Die App hält den Audio-Fokus — exklusiv (GAIN) oder vorübergehend (duck/transient). Der Prozess bleibt am Leben, bis der Fokus freigegeben wird. Zeigt den Stream-Typ: MUSIC, VOICE_CALL, ALARM usw.

- **Media Session**.
Die App hat eine aktive `MediaSession`. Zeigt den Wiedergabestatus (PLAYING, PAUSED, BUFFERING usw.) und das Session-Tag. Eine nicht geschlossene, pausierte Session ist ein häufiger Grund, warum Medien-Apps im Speicher bleiben.

- **BLE-Scan**.
Die App führt einen Bluetooth-Low-Energy-Scan durch. Der BLE-Scan erwirbt intern einen WakeLock und hält den Prozess im Hintergrund am Laufen. Der `LOW_LATENCY`-Modus ist am stromhungrigsten.

- **GATT-Verbindung**.
Die App hat eine aktive Bluetooth-GATT-Verbindung zu einem Peripheriegerät. Die Verbindung wird vom System aufrechterhalten und hält den Prozess für ihre Dauer am Leben.

- **AppOps**.
AppOps-Operationen, die auf aktuelle App-Aktivität hinweisen:
  - **WAKE_LOCK** — Die App hat über AppOps einen WakeLock erworben — die CPU wurde in ihrem Namen wach gehalten.
  - **ACTIVITY_RECOGNITION** — Die App verwendet die Activity Recognition API und erhält regelmäßig Bewegungsaktualisierungen im Hintergrund (Gehen, Laufen, im Fahrzeug usw.).

<details>
<summary>Trigger für Android 15+</summary>

- **FGS-Zeitüberschreitung überschritten**.
Android 15: Ein Dienst vom Typ `dataSync` oder `mediaProcessing` hat das 6-Stunden-Limit überschritten. Das System hätte `onTimeout()` auslösen und ihn stoppen sollen.

- **FGS fast an Zeitüberschreitung**.
Android 15: Dem Dienst vom Typ `dataSync` / `mediaProcessing` bleiben weniger als 30 Minuten des 6-Stunden-Limits.

</details>

<details>
<summary>Trigger für Android 13 und niedriger</summary>

- **WakeLock (WorkSource-Zuschreibung)**.
Android 10–13: Ein WakeLock wird von einem Systemprozess gehalten, aber über WorkSource dieser App zugeschrieben. Die App ist der eigentliche Auslöser des Aufwachens, auch wenn das Lock formal vom System gehalten wird.

- **Kernel-WakeLock**.
Die App hält einen WakeLock auf Kernel-Ebene (`/sys/power/wake_lock`). Extrem selten — deutet auf einen nicht standardmäßigen Treiber oder eine Systemkomponente hin.

- **ACCESS_BACKGROUND_LOCATION**.
Android 11–13: Die App hat die Berechtigung, Standortdaten jederzeit aus dem Hintergrund zu empfangen, auch wenn sie nicht aktiv genutzt wird. Erfordert eine separate Benutzerzustimmung.

</details>

---

**Kann jederzeit aufwachen**\
Das System kann die App **jederzeit starten oder fortsetzen**, ohne dass der Benutzer etwas tut.

- **Alarme**.
Analysiert aktive `AlarmManager`-Alarme. Aufweck-Alarme (`RTC_WAKEUP`) holen das Gerät auch bei ausgeschaltetem Bildschirm aus dem Schlaf. Ein Intervall unter 2 Minuten hat hohe Schwere. `AllowWhileIdle`-Alarme werden sogar im Doze-Modus ausgelöst. Zeigt Alarm-Tags, Intervalle und die Zeit bis zum nächsten Auslösen.

- **Jobs / WorkManager**.
Die App hat Jobs in `JobScheduler` registriert. WorkManager-Aufgaben, Synchronisations-Jobs und periodische Operationen werden hier registriert und wecken die App nach Plan. Zeigt Job-Einschränkungen (Netzwerktyp, Ladevorgang erforderlich, Leerlaufmodus) und Stoppgründe aus der letzten Zeit.

- **PendingIntent**.
Die App hält registrierte `PendingIntent`s. Das System oder andere Apps können diese jederzeit aktivieren — über Benachrichtigung, AlarmManager oder Systemereignis — und so den Prozess starten. Zeigt Aufschlüsselung nach Typ: Aktivität, Dienst, Broadcast.

- **Übermäßige Aufweckungen**.
Gesamtzahl der Geräteaufweckungen, die diese App seit dem letzten Laden verursacht hat. Hohe Zahlen deuten auf aggressive Hintergrundaktivität hin, die den Tiefschlaf der CPU verhindert. Aufgeschlüsselt nach Alarmen, Jobs, GCM/FCM und Broadcasts.

- **Content Observer**.
Die App hat `ContentObserver`s für Content-URIs registriert (Kontakte, Medien, Einstellungen, Kalender usw.). Jede Änderung an diesen URIs weckt die App, um den Callback zu liefern.

- **Push-Benachrichtigungen (FCM)**.
Die App ist für Firebase Cloud Messaging (FCM) registriert. Google Play Services kann sie jederzeit aufwecken, wenn eine Push-Nachricht eintrifft, unabhängig von den Batterieoptimierungseinstellungen.

- **Dynamische Empfänger**.
Die App hat zur Laufzeit `BroadcastReceiver`s dynamisch registriert. Im Gegensatz zu statischen Manifest-Empfängern sind sie aktiv, solange der Prozess läuft, und reagieren in Echtzeit auf Systemereignisse.

- **AppOps**.
AppOps-Operationen, die Hintergrundausführungsrechte gewähren:
  - **RUN_IN_BACKGROUND** — Die System-Akku-Richtlinie erlaubt dieser App explizit die Ausführung im Hintergrund. Wird bei ausgeschaltetem Bildschirm nicht ausgesetzt.
  - **RUN_ANY_IN_BACKGROUND** — Die App ist vollständig von der Batterieoptimierung ausgenommen — uneingeschränkte Hintergrundausführung ohne Systembeschränkungen.
  - **USE_FULL_SCREEN_INTENT** — Berechtigung, Benachrichtigungen über dem Sperrbildschirm anzuzeigen. Android 14+: nur für Alarm- und Anruf-Apps erlaubt. Das Vorhandensein bei Drittanbieter-Apps ist eine Anomalie.
  - **RUN_USER_INITIATED_JOBS** — Berechtigung zur Ausführung langer, vom Benutzer initiierter Aufgaben. Kann bei gesperrtem Bildschirm ausgeführt werden.
  - **USER_INTERACTION** — Die App hat kürzlich ein explizites Signal für Benutzerinteraktion erhalten, das einen Hintergrundstart ausgelöst haben könnte.

<details>
<summary>Trigger für Android 14+</summary>

- **Jobs (sysfs Fallback)**.
Android 14+: Job-Status über `cmd jobscheduler get-job-state` abgerufen, wenn die primäre Methode (`dumpsys jobscheduler`) nicht verfügbar ist. Zeigt Status: läuft, anstehend oder gestoppt.

</details>

<details>
<summary>Trigger für Android 13 und niedriger</summary>

- **SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM**.
Android 12–13: Die App hat die Berechtigung für exakte Alarme, die zu einer bestimmten Zeit unabhängig von Doze-Modus und Energiesparen ausgelöst werden. `USE_EXACT_ALARM` ist ein umfassenderes Recht, das nur Wecker- und Kalender-Apps gewährt wird.

</details>

---

**Andere Trigger**\
Passive Faktoren, die das Hintergrundverhalten beeinflussen, aber keine aktuelle Aktivität direkt anzeigen.

- **Kettenstart (Chain Launch)**.
Identifiziert, wer diesen Prozess gestartet hat und wie. Direkter Aufruf — eine andere App hat ihn explizit über Dienst oder Aktivität gestartet. Broadcast — gestartet durch einen Broadcast einer Drittanbieter-App. Zeigt Absendername und auslösende Aktion.

- **Broadcast-Empfänger**.
Listet alle Systemereignisse auf, für die sich die App im Manifest registriert hat: Netzwerkänderungen, Ladegerätverbindung, Zeitzonenänderungen, Bildschirm ein/aus und andere. Subskriptionen für `BOOT` und `CONNECTIVITY` werden als potenziell aggressiv markiert.

- **Autostart beim Booten**.
Die App ist für System-Boot-Ereignisse registriert. `BOOT_COMPLETED` — startet nach der Speicherfreigabe. `LOCKED_BOOT_COMPLETED` — startet vor dem Erscheinen des Sperrbildschirms (vor PIN-/Passworteingabe) — besonders aggressiver Autostart.

- **App Standby Bucket**.
Die Prioritätsstufe der App im System: `ACTIVE` → `WORKING_SET` → `FREQUENT` → `RARE` → `RESTRICTED` → `NEVER`. Höherer Status = weniger Hintergrundbeschränkungen. `RESTRICTED` und `NEVER` bedeuten, dass das System die App bereits gedrosselt hat. Zeigt Bucket-Verlauf, falls verfügbar.

- **Doze-Ausnahme (Doze Exempt)**.
Die App befindet sich auf der Doze-Whitelist. Solche Apps schlafen nicht mit dem Gerät und behalten uneingeschränkten Netzwerk- und Alarmzugriff jederzeit. Herstellereinträge können vom Benutzer nicht widerrufen werden.

- **Akkunutzungsverlauf**.
Statistiken seit dem letzten Akku-Reset: WakeLock-Haltedauern, Alarm-Aufweckungen, Job- und Sync-Starts. Ergänzt die aktuelle Momentaufnahme mit längerfristigen Daten.

- **Broadcast-Effizienz**.
Zeigt, wie viele Broadcasts an die App gesendet wurden und wie viele einen Kaltstart erforderten. Ein hoher Prozentsatz bedeutet, dass das System sie regelmäßig beendet und neu startet.

- **Mehrere Prozesse**.
Die App läuft in mehr als einem OS-Prozess. Unterprozesse (`:sync`, `:remote`, `:push` usw.) können unabhängig voneinander am Leben bleiben und sterben möglicherweise nicht, wenn der Hauptprozess stoppt.

- **Bedienungshilfe-Dienst (Accessibility Service)**.
Die App ist als aktiver Bedienungshilfe-Dienst registriert. Das System hält ihn bei Aktivierung jederzeit am Laufen, unabhängig von der Batterieoptimierung.

- **Eingabemethode (IME)**.
Die App ist die aktuell ausgewählte Eingabemethode (Tastatur). Das System hält die aktive IME solange am Leben, wie sie ausgewählt ist.

- **Geräteadministrator**.
Die App ist ein aktiver Geräteadministrator, Gerätebesitzer oder Profilbesitzer. Hat erhöhte Privilegien — das System schützt sie vor einem Zwangsstopp durch die standardmäßigen Akku-Einschränkungsmechanismen.

- **Sync-Adapter**.
Die App hat einen Sync-Adapter im System registriert. Android startet ihn regelmäßig, um Kontodaten zu synchronisieren, auch wenn die App nicht läuft.

- **Hintergrundstart**.
Die App war kürzlich aktiv, aber nicht im Vordergrund — ein Zeichen für einen versteckten Hintergrundstart, der durch einen Alarm, Job, Push oder Kettenstart ausgelöst wurde. Erkannt durch Vergleich von `lastTimeUsed` und `lastTimeForeground` aus `dumpsys usagestats`.

- **AppOps**
  - **START_FOREGROUND (blockiert)** — Das System hat das Recht zum Starten eines Vordergrunddienstes blockiert. Die App versucht, im Hintergrund zu arbeiten, wird aber eingeschränkt.
  - **MANAGE_MEDIA** — Verwaltet Mediensitzungen anderer Anwendungen. Verknüpft mit dem FGS-Typ `mediaProcessing` unter Android 15.

- **ContentProvider**.
Die App hat einen oder mehrere ContentProvider deklariert. Andere Apps oder das System können sie direkt über eine URI abfragen — Android startet den Prozess automatisch bei einer eingehenden Anfrage, auch wenn er nicht lief. Zeigt die Authority-Adressen der registrierten Anbieter.

- **WakeLocks-Verlauf**.
Zeigt den Verlauf der letzten 5 von der App gehaltenen **WAKELOCK**s. Wenn die App einen WakeLock zu lange hält, ist das ein schlechtes Zeichen.

<details>
<summary>Trigger für Android 14+</summary>

- **Kettenstart (BAL-Privileg)**.
Android 14+: Die App hat ein `BackgroundStartPrivilege`-Token erhalten, um aus dem Hintergrund zu starten. Wird normalerweise vom System für hochprioritäres FCM, exakte Alarme oder PendingIntent von einer sichtbaren App gewährt.

- **Autostart beim Booten (FGS-Einschränkung)**.
Android 14+: Ein `BOOT_COMPLETED`-Empfänger kann keinen FGS vom Typ MICROPHONE oder PHONE_CALL starten. Die App versucht, diese Einschränkung beim Booten zu umgehen.

- **Doze-Ausnahme (Fallback)**.
Android 14+: Ausnahme von der Batterieoptimierung erkannt über `cmd appops get RUN_ANY_IN_BACKGROUND=allow`. Die App kann im Hintergrund ohne Doze-/App-Standby-Einschränkungen laufen.

- **StandbyBucket eingeschränkte Effekte**.
Android 14+: System bestätigt RESTRICTED-Bucket über appops. Jobs und Alarme werden blockiert — die App kann sich nicht unabhängig starten.

</details>

<details>
<summary>Trigger für Android 13 und niedriger</summary>

- **Kettenstart (BAL blockiert)**.
Android 13 und niedriger: Das System hat den Versuch blockiert, eine Aktivität oder FGS aus dem Hintergrund ohne gültige Ausnahme zu starten. Die App versuchte zu starten, wurde aber abgelehnt.

- **Prozess eingefroren**.
Android 11–13: Der Prozess wird vom System über den cgroup-Freezer eingefroren — die Ausführung wird pausiert, aber nicht beendet. Taut bei Zugriff automatisch wieder auf.

- **FGS-Start blockiert**.
Android 12–13: Versuch, einen Vordergrunddienst aus dem Hintergrund ohne erlaubte Ausnahme zu starten. Der Dienst wurde nicht gestartet.

- **Netzwerk blockiert (Datensparmodus)**.
Android 10–13: Der Benutzer hat den Datensparmodus aktiviert oder den Hintergrund-Netzwerkzugriff eingeschränkt. Die App kann im Hintergrund kein mobiles Datenvolumen nutzen.

- **Hintergrund-Netzwerk erlaubt (Datensparmodus)**.
Android 10–13: Die App ist in den Datenspar-Einstellungen auf die Whitelist gesetzt — uneingeschränkter Netzwerkzugriff im Hintergrund.

- **BT-Berechtigungen (BLUETOOTH_SCAN / BLUETOOTH_CONNECT)**.
Android 12–13: Die App hat Berechtigungen für Bluetooth-Scanning und/oder -Verbindung. Kann beim Empfang eines Broadcasts mit dem Scannen beginnen.

- **Dynamische Empfänger (exported=true)**.
Android 13: Ein dynamisch registrierter Empfänger mit `exported=true` ist für andere Apps zugänglich und kann Broadcasts von jedem Absender empfangen.

- **Doze-Status Fallback**.
Android 11–13: Das Gerät befindet sich im Deep Doze oder Light Doze. Wakelocks, Netzwerk, Jobs und Alarme (außer ALLOW_WHILE_IDLE) werden für Apps ohne Doze-Ausnahme blockiert.

</details>

> [!TIP]
> Für Root-Benutzer: [Blocker](https://github.com/lihenggui/blocker) lässt sich sehr gut mit ReAppzuku kombinieren. Zusammen bieten sie Ihnen eine neue Ebene der App-Kontrolle.

---

## Einstellungen

### 🔵 Informationen
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**ReAppzuku-Zugriffsmodus**\
Zeigt den aktuellen Zugriffsmodus: **Root**, **Shizuku** oder **Kein Zugriff**. Schreibgeschützt.

**Hilfe**\
Link zu diesem FAQ.

---

### 🎨 Aussehen
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**App-Design**\
Wählen Sie ein Design: Systemstandard, Hell, Dunkel oder AMOLED.

**Akzentfarbe**\
Wählen Sie eine Akzentfarbe: Indigo, Karmesinrot, Waldgrün, Bernstein und andere Farbtöne.

**Benachrichtigungen**\
Konfigurieren Sie das Benachrichtigungsverhalten. Kritische Benachrichtigungen umfassen den Status des Hintergrunddienstes und Berechtigungsfehler.

---

### ⚙️ App Stabilität
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**Hintergrunddienst**\
Hauptschalter für die Automatisierung. Startet den persistenten ReAppzuku-Hintergrundprozess. Erforderlich für die meisten Funktionen der App, einschließlich der Erfassung von Statistiken.

---

### 🎯 Auto-Kill Einstellungen
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**Periodisches Auto-Kill**\
Beendet Apps automatisch in einem festgelegten Intervall, während der Hintergrunddienst läuft.

**Auto-Kill-Intervall:**

| Intervall | Beschreibung |
| :--- | :--- |
| 10 Sekunden | Maximal aggressive Bereinigung |
| **18 Sekunden** | Standard |
| 30 Sekunden | Moderate Bereinigung |
| 1 Minute | Leichte Bereinigung |
| 5 Minuten | Minimale Eingriffe |

**Beenden bei Bildschirm aus**\
Führt das Beenden sofort aus, wenn der Bildschirm gesperrt wird. Nützlich, um jedes Mal aufzuräumen, wenn Sie Ihr Telefon weglegen.

**Beenden bei RAM-Auslastung**\
Zusätzliche Bedingung — Beenden wird **nur** ausgelöst, **wenn** die RAM-Auslastung den ausgewählten Schwellenwert überschreitet. Gilt sowohl für das periodische Beenden als auch für das Beenden bei Bildschirm aus.

| Schwellenwert | Beschreibung |
| :--- | :--- |
| 75% | Frühe Bereinigung |
| **80%** | Standard |
| 85–95% | Bereinigung nur, wenn der Speicher tatsächlich knapp ist |
| 100% | Nur in kritischen Situationen |

**Auto-Kill-Typ**\
Nur relevant, wenn ReAppzuku mit Ihrer Firmware in Konflikt gerät. Wenn Sie ungewöhnliches Verhalten bei anderen Apps bemerken, versuchen Sie, auf `am kill` umzuschalten.

**Auto-Kill-Modus**\
Bestimmt, **welche** Apps von Auto-Kill anvisiert werden.

- **🛡️ Whitelist** — Beendet alle Hintergrund-Apps **außer** denen auf der Whitelist. Verwenden Sie dies für maximale Bereinigung.
> [!WARNING]
> Bei Verwendung der Whitelist wird dringend empfohlen, die meisten Systemanwendungen hinzuzufügen, um Geräteprobleme zu vermeiden

- **🎯 Blacklist (Standard)** — Beendet **nur** Apps auf der Blacklist. Verwenden Sie dies, um bestimmte Apps zu stoppen, ohne alles andere anzurühren.

**Whitelist / Blacklist**\
App-Liste für den ausgewählten Modus. Eine der beiden Listen wird je nach Modus angezeigt.

**Erweiterte Bedingungen**\
Erweitern Sie Auto-Kill um zusätzliche Auslöser — für Fälle, in denen der reguläre Zeitplan nicht ausreicht.

- **Hardware-Ereignisse**.
Auto-Kill wird automatisch bei ausgewählten Ereignissen gestartet: Kopfhörer oder USB verbinden/trennen, Ladezustandsänderung, WiFi, mobiles Netzwerk, Bluetooth, GPS oder Hotspot. Nach dem Ereignis wird eine Pause von 10 Sekunden eingehalten — so haben parasitäre Apps Zeit zu starten und werden dann bereinigt.

- **App-Start**.
Auto-Kill wird genau dann ausgelöst, wenn ausgewählte Ziel-Apps geöffnet werden — nützlich auf Budget-Geräten, um RAM und CPU freizugeben, bevor schwere Spiele oder Programme gestartet werden. Die Ziel-Apps selbst werden nicht beendet.
  - **Cache leeren**. Leert zusätzlich den Cache aller Apps, mit Ausnahme von geschützten, persistenten und anderen Ziel-Apps.
> [!IMPORTANT]
> Die Funktion **App-Start** benötigt eine spezielle Berechtigung in den "Bedienungshilfen"-Einstellungen. Diese Funktion kann den Akkuverbrauch von ReAppzuku selbst leicht erhöhen.

**Auto-Kill-Voreinstellungen**\
Speichern Sie Ihren eigenen Satz von Auto-Kill-Einstellungen, der automatisch zu einer bestimmten Tageszeit aktiviert wird und die aktuellen Einstellungen für die Dauer seines aktiven Fensters ersetzt. Wenn das Fenster endet, werden die ursprünglichen Einstellungen automatisch wiederhergestellt.
**2 Voreinstellungen** sind verfügbar. Jede kann unabhängig konfiguriert werden: eigener Name, eigener aktiver Zeitbereich, eigene Auto-Kill-Regeln, eigene App-Listen und eigene Zusatzszenarien.
> [!WARNING]
> Während ihrer Aktivität ignorieren Voreinstellungen die Immunität, die Apps durch die Planung der Beschränkungen gewährt wird. Dies geschieht, um Verwirrung bei den Einstellungen zu vermeiden.

- **Voreinstellung aktivieren**.
Der Hauptschalter. Wenn deaktiviert, wird die Voreinstellung **nicht** planmäßig aktiviert, selbst wenn ihr Zeitfenster beginnt. Wenn die Voreinstellung derzeit aktiv ist und dieser Schalter ausgeschaltet wird, wird sie sofort deaktiviert und die ursprünglichen Einstellungen werden wiederhergestellt.

- **Name der Voreinstellung**.
Ein benutzerdefinierter Name, bis zu 30 Zeichen. Wird im Auswahldialog für Voreinstellungen in den Haupt-Einstellungen angezeigt. Wenn die Voreinstellung derzeit aktiv ist, erscheint ein **"Aktiv"**-Abzeichen neben ihrem Namen.

- **Aktive Zeit**.
Ein "Von — Bis"-Bereich, der im Zeitformat des Geräts (12/24-Stunden) angezeigt wird. Bereiche, die Mitternacht überschreiten, werden unterstützt (z.B. 22:00 – 06:00).
> [!WARNING]
> Die beiden Voreinstellungen dürfen sich in ihrer aktiven Zeit nicht überschneiden. Wenn Sie versuchen, eine Voreinstellung mit einem überschneidenden Bereich zu speichern, wird eine Warnung mit dem Zeitbereich der kollidierenden Voreinstellung angezeigt — passen Sie die Zeit einer der Voreinstellungen an, um das Problem zu beheben.

- **Quelle der App-Liste**
Wählen Sie zwischen:
  - **Aktuelle Whitelist / Blacklist verwenden** — Die Voreinstellung verwendet immer die aktuelle Whitelist/Blacklist aus den Haupt-Einstellungen zum Zeitpunkt ihrer Aktivierung.
  - **Eigene Liste der Voreinstellung verwenden** — Die Voreinstellung hat eine eigene unabhängige Whitelist/Blacklist, die separat bearbeitet wird und von Änderungen an den Haupt-Einstellungen nicht betroffen ist.

- **Auto-Kill-Verwaltung und Erweiterte Bedingungen**.
Ein Standardblock für Auto-Kill-Einstellungen, identisch mit den regulären App-Einstellungen. Alle diese Einstellungen werden unter [Auto-Kill-Einstellungen](#auto-kill-einstellungen) beschrieben.

- **Voreinstellung speichern**.
Übernimmt alle Änderungen: Speichert die Einstellungen, plant die Aktivierungs-/Deaktivierungs-Alarme neu und aktiviert oder deaktiviert die Voreinstellung sofort, falls erforderlich (wenn die Änderungen das aktuelle Zeitfenster betreffen).

- **JSON-Datei importieren/exportieren**.
Speichert die Voreinstellung in einer JSON-Datei oder stellt sie aus einer Sicherungsdatei wieder her. Um die Änderungen zu übernehmen, klicken Sie auf die Schaltfläche "Speichern".

- **Voreinstellung zurücksetzen**.
Setzt alle aktuellen Einstellungen auf dem Bildschirm auf ihre Standardwerte zurück (übernommen aus den Haupt-Einstellungen der App). **Die Änderungen werden erst übernommen**, wenn Sie auf "Speichern" drücken — Sie können den Bildschirm einfach ohne Speichern verlassen, und der Reset hat keinen Einfluss auf die bereits gespeicherte Voreinstellung.

**RAM-Kill-Verknüpfung**\
Fügt eine kleine 1x1-Desktop-Verknüpfung hinzu, die die RAM-Auslastung in Echtzeit in Prozent und GB anzeigt.\
Ein Tippen auf die Verknüpfung löst sofort Auto-Kill basierend auf den aktuellen Einstellungen aus und leert den RAM.

> [!TIP]
> Der RAM wird trotzdem geleert, unabhängig davon, ob Apps während Auto-Kill geschlossen wurden oder nicht. Zum Leeren des RAMs wird der Befehl `am send-trim-memory` verwendet. Nur die Whitelist und persistente Anwendungen sind davon nicht betroffen.

---

### 🔧 Erweiterte Tools

#### Hintergrundbeschränkungen
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

> [!WARNING]
> Verfügbar ab **Android 11+**

Verwendet die `appops` von Android, um **einer App die Ausführung im Hintergrund auf OS-Ebene zu blockieren**. Tiefergehend als ein normales Beenden.

| | Normales Beenden | Hintergrundbeschränkungen |
| :--- | :--- | :--- |
| Funktionsweise | Erzwingt das Stoppen des Prozesses | Verhindert, dass Android den Prozess im Hintergrund startet |
| Kann neu starten | ✅ Ja | ❌ Nein |
| Bleibt nach Neustart erhalten | ❌ Nein | ✅ Ja |
| Erfordert Android 11+ | ❌ Nein | ✅ Ja |

**Arten von Beschränkungen:**
- **Sanft** (RUN_ANY_IN_BACKGROUND ignorieren)\
Blockiert den Autostart auf einem strengeren Niveau als die Standard-Aktivitätseinstellungen.\
**So funktioniert's**: Wenn Sie die App öffnen und wechseln — läuft sie weiter (solange sie in den Letzten Apps ist). Aber von selbst (über Nacht oder im Hintergrund) wird sie nicht aufwachen, bis Sie sie öffnen.

- **Mittel**\
Schränkt einige Hintergrundaktivitäten ein.
**So funktioniert's:**\
Blockiert Dienststarts, Job-Scheduler und Alarme. Die App funktioniert normal, solange sie geöffnet ist, wechselt aber in den Standby, sobald Sie sie verlassen (minimieren).\
**Verwendete Befehle:**\
`RUN_ANY_IN_BACKGROUND ignore`\
`RUN_IN_BACKGROUND ignore`\
`SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS ignore`\
`GET_USAGE_STATS ignore`\
`ACCESS_NOTIFICATIONS ignore`\
`SYSTEM_EXEMPT_FROM_SUSPENSION ignore`\
`Standby Bucket: Rare`

- **Hart**\
Blockiert jegliche Hintergrundaktivität.\
**So funktioniert's:**\
Sobald die App minimiert oder verlassen wird — beendet das System sie sofort. Die App kann sich nicht ohne direkte Benutzerinteraktion im Speicher halten (selbst wenn sie in den Letzten Apps sichtbar ist). Verwenden Sie die harte Einschränkung mit Vorsicht, da sie der App möglicherweise vollständig Hintergrundoperationen (Datei-Downloads, Medienwiedergabe, lang laufende interne Aufgaben) entzieht.\
**Verwendete Befehle:**\
`RUN_ANY_IN_BACKGROUND ignore`\
`RUN_IN_BACKGROUND ignore`\
`START_FOREGROUND ignore`\
`SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS ignore`\
`GET_USAGE_STATS ignore`\
`WAKE_LOCK ignore`\
`SCHEDULE_EXACT_ALARM ignore`\
`INTERACT_ACROSS_PROFILES ignore`\
`ACCESS_NOTIFICATIONS ignore`\
`SYSTEM_EXEMPT_FROM_SUSPENSION ignore`\
`RUN_USER_INITIATED_JOBS ignore`\
`Entfernung aus der Batterieoptimierungs-Whitelist`\
`Standby Bucket: Restricted`

- **Manuell**\
Sie wählen, welche Einschränkungen angewendet werden sollen.\
**So funktioniert's**: ReAppzuku wendet nur die von Ihnen ausgewählten Einschränkungen an.

> [!IMPORTANT]
> Der App Standby Bucket wird bei Benutzerinteraktion mit der Ziel-App zurückgesetzt. Das System stellt ihn nicht immer wieder her. ReAppzuku stellt den App-Bucket beim nächsten Prüfzyklus der Beschränkungsintegrität automatisch wieder her.

**Verfügbare Einschränkungen:**
<details>
<summary>Android 11+</summary>

- **RUN_ANY_IN_BACKGROUND**\
Verhindert, dass die App Hintergrundprozesse oder -dienste ohne explizite Benutzerinteraktion startet. Primäre und umfassendste Einschränkung — wird im **Sanft**-Modus verwendet.

- **RUN_IN_BACKGROUND**\
Zielgerichtete Einschränkung der Hintergrundausführung. Blockiert Dienststarts über `startService()`, wenn die App im Hintergrund ist.

- **START_FOREGROUND**\
Verhindert, dass die App einen Dienst in den Vordergrund befördert (dauerhafte Benachrichtigung). Ohne dies kann die App keine Benachrichtigung "läuft im Hintergrund" anzeigen oder den Prozess am Leben halten.

- **GET_USAGE_STATS**
Schränkt den Zugriff der App auf den Gerätenutzungsverlauf und Statistiken anderer Anwendungen ein (welche Apps gestartet wurden, wie lange sie genutzt wurden und allgemeine Aktivitätshistorie).

- **WAKE_LOCK**\
Verhindert, dass die App die CPU bei ausgeschaltetem Bildschirm aktiv hält. Ohne WakeLock kann das System die CPU in den Schlaf versetzen und Hintergrundoperationen stoppen.

- **INTERACT_ACROSS_PROFILES**\
Verhindert, dass die App mit anderen Arbeitsprofilen interagiert. Hauptsächlich relevant auf Unternehmensgeräten.

</details>

<details>
<summary>Android 12+</summary>

- **SCHEDULE_EXACT_ALARM**
Verhindert, dass die App exakte Alarme über `AlarmManager.setExact()` und ähnliche Methoden plant. Diese Einschränkung blockiert die Planung des Alarms selbst, nicht nur die Fähigkeit, das Gerät aufzuwecken.

- **ACCESS_NOTIFICATIONS**
Schränkt den Zugriff der App auf den Benachrichtigungs-Listener-Dienst ein. Diese Verhinderung stoppt die App daran, Benachrichtigungen von anderen Anwendungen zu lesen, abzufangen oder mit ihnen zu interagieren.

</details>

<details>
<summary>Android 14+</summary>

- **SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS**
Verhindert, dass die App System-Energieeinschränkungen (wie Doze-Modus oder App Standby) umgeht. Normalerweise erlaubt diese Berechtigung System- und kritischen Apps, uneingeschränkt im Hintergrund zu laufen.

- **SYSTEM_EXEMPT_FROM_SUSPENSION**
Die App verliert ihre Immunität gegenüber der Prozessaussetzung — das System kann den Hintergrundprozess der App aggressiver als üblich einfrieren.

- **RUN_USER_INITIATED_JOBS**
Verhindert, dass die App vom Benutzer initiierte Jobs mit erhöhter Priorität ausführt — Aufgaben, die vom Benutzer gestartet wurden (Downloads, Exporte usw.), werden als reguläre Hintergrundaufgaben unter standardmäßigen Systemeinschränkungen ausgeführt.

</details>

- **Standby Bucket: Rare**\
Vom System als selten genutzt markiert. Blockiert die App auf Systemebene:
  - Hintergrundnetzwerk. Netzwerk nur während seltener Systemwartungsfenster verfügbar.
  - JobScheduler. Reguläre Jobs und Expedited Jobs sind auf 10 Minuten pro Tag begrenzt.
  - AlarmManager. Ungefähre Alarme werden verschoben. Limit — 1 Auslösung pro Stunde.
  - Push (FCM). Das Kontingent für High-Priority-Push wird reduziert. Überschüssige Pushs werden verzögert.

- **Standby Bucket: Restricted**\
Vom System als lange ungenutzt oder als anomale App markiert, die übermäßig CPU und Akku verbraucht hat. Beinhaltet alle Rare-Einschränkungen, setzt sie jedoch strenger durch. Zusätzliche Einschränkungen auf Systemebene:
  - Ladeausnahme aufgehoben. Wenn das Gerät angeschlossen ist, werden die Einschränkungen für alle Buckets (einschließlich Rare) vollständig aufgehoben. Für Restricted bleiben die JobScheduler-Startlimits jedoch auch während des Ladevorgangs aktiv.
  - Job-Häufigkeit strikt begrenzt. Begrenzt die Planungsgranularität strikt — der App ist es erlaubt, genau 1 Mal pro Tag einen Hintergrundjob zu starten.
  - Boot-Verhalten. Ab Android 13 blockiert das System bei einer App im Restricted-Bucket vollständig die Zustellung von `BOOT_COMPLETED`- und `LOCKED_BOOT_COMPLETED`-Broadcasts. Die App kann beim OS-Start nicht starten, bis der Benutzer sie manuell öffnet.
  - Zwangsbeendigung aktiver Dienste. Wenn eine laufende App vom System in den Restricted-Bucket verschoben wird (z.B. aufgrund erkannter abnormaler Energieverbrauch), entfernt und beendet das OS automatisch alle ihre aktiven Vordergrunddienste.
  - Netzwerkzugriff während Wartungsfenstern. Während des Doze-Modus öffnet das System periodisch Wartungsfenster. Apps im Restricted-Bucket wird der Netzwerkzugriff auch während dieser Systemfenster verweigert.
  - Expedited Jobs Limit gekürzt. Das Limit für Expedited Jobs wird halbiert — auf 5 Minuten pro Tag.

**Vergleich der Beschränkungstypen**

| Beschränkung | Sanft | Mittel | Hart | Manuell |
| :--- | :---: | :---: | :---: | :---: |
| RUN_ANY_IN_BACKGROUND | ✓ | ✓ | ✓ | optional |
| RUN_IN_BACKGROUND | — | ✓ | ✓ | optional |
| START_FOREGROUND | — | — | ✓ | optional |
| SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS | — | ✓ | ✓ | optional |
| GET_USAGE_STATS | — | ✓ | ✓ | optional |
| WAKE_LOCK | — | — | ✓ | optional |
| SCHEDULE_EXACT_ALARM | — | — | ✓ | optional |
| INTERACT_ACROSS_PROFILES | — | — | ✓ | optional |
| ACCESS_NOTIFICATIONS | — | ✓ | ✓ | optional |
| SYSTEM_EXEMPT_FROM_SUSPENSION | — | ✓ | ✓ | optional |
| RUN_USER_INITIATED_JOBS | — | — | ✓ | optional |
| Standby Bucket | — | Rare | Restricted | optional |

**Listen-Status**:
- **In ReAppzuku gespeichert** — gespeichert, aber Systemstatus unbekannt (ungenügende Berechtigungen)
- **In ReAppzuku gespeichert, aber nicht angewendet** — gespeichert, aber Android hat die Einschränkung nicht angewendet
- **Eingeschränkt, nicht von ReAppzuku** — von Android oder einer anderen App eingeschränkt

**Watchdog für Hintergrundbeschränkungen**\
Eine automatisierte ReAppzuku-Funktion, die regelmäßig die Integrität der Hintergrundbeschränkungen überprüft. Wenn das System Einschränkungen zurücksetzt, stellt WatchDog sie automatisch wieder her.\
Für **Sanft und Mittel** (und Manuell, wenn die gewählten Einschränkungen Sanft/Mittel entsprechen) — werden die Beschränkungen nur wiederhergestellt, wenn die App nicht aktiv auf dem Bildschirm ist und keinen `IMPORTANCE_FOREGROUND_SERVICE` hält.\
In allen anderen Fällen werden die Beschränkungen nur wiederhergestellt, wenn die App nicht aktiv auf dem Bildschirm (nicht in Verwendung) ist.

**Hintergrundbeschränkungen erneut anwenden**\
Wendet alle gespeicherten Beschränkungen manuell erneut an. Nach einem Neustart geschieht dies **automatisch**, wenn der Hintergrunddienst startet.

---

#### Planung der Beschränkungen
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

Planen Sie, wann Beschränkungen für bestimmte Apps aufgehoben und wiederhergestellt werden sollen.
> [!IMPORTANT]
> Es werden nur Apps mit einer aktiven **Hintergrundbeschränkung** (Sanft / Mittel / Hart / Manuell) angezeigt.
> Apps mit geplantem Eintrag zeigen das 🕐-Symbol mit der geplanten Zeit.

Tippen Sie auf eine App, um die Planungskonfiguration zu öffnen:

**Schützen vor**\
Wählen Sie, von welchen Beschränkungen die App vorübergehend ausgenommen werden soll.

**Zeitfenster**\
Legen Sie eine Startzeit (Beschränkungen aufgehoben) und eine Endzeit (Beschränkungen wiederhergestellt) fest.
Die App wird zwangsgestoppt, bevor die Beschränkungen wiederhergestellt werden.

**Bucket setzen: Active**
Wenn Sie Beschränkungen von einer App entfernen, wird ihr App Standby Bucket auf Active gesetzt. Dies ermöglicht es der App, ihre Dienste selbst zu starten.

**Bei Aktivierung**\
Aktion, die ausgeführt wird, wenn die Beschränkungen aufgehoben werden:
- **Keine** — keine zusätzliche Aktion.
- **Komponente starten** — öffnet die Komponentenauswahl der App (Aktivität, Dienst, Empfänger usw.).

> [!NOTE]
> Die Anzahl der geplanten Einträge ist auf 15 Apps begrenzt, um ReAppzuku selbst zu schützen.

> [!IMPORTANT]
> Der Scheduler schützt Apps nur vor dem **vorübergehenden** Einfrieren.

---

#### Schlafmodus
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**Friert** ausgewählte Apps vollständig **ein**, wenn das Gerät im Leerlauf ist. Im Gegensatz zu Hintergrundbeschränkungen — die App kann sich einfach nicht starten, sie wird vom System vollständig deaktiviert.
Kann eine App auch **dauerhaft** direkt im App-Listendialog einfrieren.

Für jede App (Vorübergehend oder Dauerhaft) können Sie einen Einfrier-Befehl wählen:
- **pm disable** — Die App wird vom System vollständig deaktiviert, das Symbol kann auf dem Startbildschirm verschwinden/verschoben werden. Zuverlässigstes Einfrieren, die App kann nicht starten.
- **pm suspend** — Die App wird ausgeblendet und blockiert, ohne deaktiviert zu werden, das Symbol bleibt an Ort und Stelle. Etwas weniger zuverlässiges Einfrieren, die App wird ausgesetzt, kann aber möglicherweise noch etwas Hintergrundaktivität haben.

> [!IMPORTANT]
> Für System-Apps ist nur der Befehl **pm suspend** verfügbar

> [!CAUTION] Seien Sie vorsichtig bei der Einstellung des Schlafmodus für System-Apps.\
> ReAppzuku schützt die meisten kritischen System-Apps (wie com.android.systemui) vor Manipulation, garantiert jedoch keine 100%ige Sicherheit.\
> Bedenken Sie, dass das gedankenlose Einfrieren von System-Apps zu Bootschleifen führen kann.

So funktioniert das **vorübergehende** Einfrieren:\
1. Bildschirm aus → Timer startet
2. Timer abgelaufen → ausgewählte Apps werden mit dem gewählten Befehl eingefroren
3. Bildschirm wird ein- und entsperrt → Apps werden automatisch aufgetaut

> [!NOTE]
> Wenn die Ziel-App auf dem Startbildschirm war, kann ihr Symbol nach Verwendung des `pm disable`-Befehls verschwinden/verschoben werden. Dies ist das eigene Verhalten von Android. Bei `pm suspend` bleibt das Symbol an Ort und Stelle.

**Schlafmodus-App-Liste**\
Wählen Sie Apps aus, die im Schlafmodus eingefroren werden sollen, und wählen Sie für jede den Einfrier-Befehl (pm suspend/pm disable).

**Einfrier-Timer**\
Leerlaufzeit, nach der das Einfrieren ausgelöst wird: von **5 bis 60 Minuten** (Standard 60 Minuten).

**Watchdog für den Schlafmodus**\
Automatische Funktion von ReAppzuku, die regelmäßig die Integrität des Schlafmodus-Einfrierens überprüft und, wenn das System einige Apps auftaut, sie mit dem für sie gewählten Befehl wieder einfriert.\
Funktioniert nur für den "Dauerhaften" Einfriertyp.

---

**Cache für alle Apps leeren**\
Führt `pm trim-caches` aus — leert den Cache aller Apps auf einmal.

**Ausgeblendete Apps**\
Apps hier erscheinen nicht auf dem Hauptbildschirm und werden von Auto-Kill niemals berührt. Nützlich für Dienstprozesse, die Sie nicht sehen müssen.

**Sicherung & Wiederherstellung**\
Exportieren und importieren Sie alle Einstellungen als JSON. Umfasst Whitelist, Blacklist, ausgeblendete Apps, Hintergrundbeschränkungen, Schlafmodus und alle Automatisierungseinstellungen.

---

### ℹ️ Über
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**Quellcode**\
Link zum GitHub-Repository.

**Nach Updates suchen**\
Sucht manuell auf GitHub nach einer neuen Version und zeigt sie an, falls vorhanden.
Die automatische Update-Prüfung erfolgt alle 12 Stunden.

**Telegram**\
Sie können dem ReAppzuku-Entwickler in Telegram schreiben.

**Besonderer Dank**\
Eine Ehrenliste von Benutzern, die zur Entwicklung von ReAppZuku beigetragen haben.

**Debug**\
Aktivieren/deaktivieren Sie Debug-Protokolle.\
Zum Speichern von Protokollen verwenden Sie:
- aShell (für Shizuku)
- Qute Terminal Emulator (für Root)

Oder Sie können jeden anderen Terminal-Emulator verwenden, der für Sie bequem ist.\
Um Protokolle in der Konsole auszugeben, verwenden Sie: `logcat -s ReAppzukuDebug`

**Debug-Menü**\
Menü zum Aktivieren/Deaktivieren der erforderlichen Protokollkategorien.

---

### 📊 Statistiken & Protokolle
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**ReAppzuku-Verbrauch**\
Der obere Bereich des Bildschirms zeigt den **eigenen Ressourcenverbrauch von ReAppzuku** — RAM, CPU und Akku — damit Sie dessen Auswirkungen auf das Gerät einschätzen können.

**Ressourcennutzungsdiagramme**\
Interaktive Diagramme der RAM-, CPU- und Akkunutzung über die verfolgten Apps. Wechseln Sie mit den **Pfeilen** zwischen den Diagrammtypen.

| Zeitraum | Beschreibung |
| :--- | :--- |
| 2 Stunden | Letzte 2 Stunden |
| 6 Stunden | Letzte 6 Stunden |
| 12 Stunden | Letzte 12 Stunden |
| 24 Stunden | Letzte 24 Stunden |

> [!TIP]
> Tippen Sie auf eine **App in der Diagrammlegende**, um ihr **persönliches Aktivitätsdiagramm** zu öffnen.

**Auto-Kill-Protokoll**\
Zeigt die Aktivität der letzten **12 Stunden**: Auto-Kill-Zählung, Neustarts, freigegebener RAM und Zeit des letzten Ereignisses pro App.

> [!TIP]
> Apps, die mehr als 3 Mal neu starten, sind gute Kandidaten für Hintergrundbeschränkungen.

**Top Offenders (Hauptverursacher)**\
Ordnet Apps nach einem kombinierten Wert (Beendigungen + Neustarts + RAM-Nutzung). Filtern nach: 12 Stunden / 24 Stunden / 7 Tage / Gesamtzeit.

> [!NOTE]
> Der Wert zeigt, wie aggressiv die App in die Hintergrundverwaltung eingreift.\
>
> `Wert = Beendigungen × 1 + Neustarts × 2 + freigegebener RAM × 0,01`
>
> • Beendigung (+1) — Die App wurde zwangsgestoppt.\
> • Neustart (+2) — Die App wurde nach dem Stopp neu gestartet; doppelt so hoch bewertet, da es aktiven Widerstand darstellt.\
> • RAM — Jede 100 MB freigegebener Speicher fügen +1 Punkt hinzu; normalerweise ein kleiner Beitrag.

> [!IMPORTANT]
> Freigegebener RAM wird nur gezählt, wenn die App im nächsten Auto-Kill-Zyklus nicht mehr läuft. Wenn sie neu startet, beansprucht sie denselben RAM zurück — der Nettogewinn beträgt 0 %.

**Protokoll der Hintergrundbeschränkungen**\
Detailliertes Protokoll der Hintergrundbeschränkungsoperationen. Wird im Cache gespeichert, maximal 200 Einträge.

| Status | Bedeutung |
| :--- | :--- |
| `Gesendet` | Befehl erfolgreich ausgeführt (möglicherweise nicht vom System angewendet) |
| `Angewendet` | Beschränkung vom System bestätigt (100 % Ergebnis) |
| `NICHT ANGEWENDET` | Befehl ausgeführt, aber das System hat die Änderung nicht übernommen |
| `FEHLER` | Befehl mit einem Fehler fehlgeschlagen |
| `Übersprungen` | Operation nicht durchgeführt (keine Berechtigungen, Android < 11, usw.) |
| `Verifizierung nicht verfügbar` | Der tatsächliche Status konnte vom System nicht abgefragt werden |
| `Von der Whitelist entfernt` | App aus den Batterieoptimierungs-Ausnahmen entfernt |
| `Zur Whitelist hinzugefügt` | App zu den Batterieoptimierungs-Ausnahmen hinzugefügt |

> [!TIP]
> Durch Tippen auf einen Eintrag im Protokoll der Hintergrundbeschränkungen werden die Protokolldetails geöffnet. Dort können Sie sehen, welche AppOps nicht angewendet oder zurückgesetzt wurden. Sie können auch prüfen, ob sich der App Standby Bucket geändert hat.

**Schlafmodus-Protokoll**\
Protokolliert Datum und Uhrzeit des Einfrierens/Auftauens für Ziel-Apps.

**Scheduler-Protokoll**\
Enthält Aufzeichnungen der Aktivitäten der Planung der Beschränkungen. Jeder Eintrag zeigt:
- Datum und Uhrzeit, wann Beschränkungen aufgehoben/wiederhergestellt wurden.
- Wie erfolgreich die Beschränkungen wiederhergestellt wurden (OK / TEILWEISE / FEHLGESCHLAGEN).
- Art des angewendeten Zwangsstopps (basierend auf Auto-Kill-Einstellungen).
- Welche App-Komponente ausgeführt wurde, als die Beschränkung aufgehoben wurde.

---

## Geschützte Apps
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

Diese Apps werden **niemals** von Auto-Kill oder anderen Einschränkungen beeinflusst, unabhängig von den Einstellungen:

**Android-Kern & Google**
- Google Play Services und Google Services Framework
- System-UI
- Android-Einstellungen
- Telefon / Wähler, Kontakte, SMS-Dienst, Telefonie-Server
- Bluetooth
- Externer Speicher und Medienmodul
- Paketinstaller und Berechtigungscontroller (AOSP und Google-Varianten)
- Gboard (Google-Tastatur)
- ADB/Shell-Dienst
- Android Keychain (TLS/VPN/Wi-Fi)
- Einstellungen, Telefonie- und SMS/MMS-Anbieter
- NFC
- Netzwerk-Stack, Tethering-Stack, DNS-Resolver, VPN-Dialoge

**Shizuku**
- Shizuku (beide Varianten: `rikka.shizuku.common` und `moe.shizuku.privileged.api`)

**Root-Manager**
- Magisk
- KernelSU
- KernelSU Next
- APatch
- SukiSU / SukiSU Ultra

**Herstellersystem-Apps**
| Hersteller | Geschützte Apps |
| :--- | :--- |
| **Xiaomi / MIUI / HyperOS** | Sicherheitscenter, Startbildschirm, Hintergrundbild, Kamera, Systemschutz, Kerndienste, PowerKeeper |
| **Samsung (One UI)** | Gerätewartung, Geräteschutz, One UI Home, Telefonoberfläche, Telefonie-Server |
| **Oppo / Realme / OnePlus (ColorOS)** | Telefonmanager, System-Startbildschirm, Smart Assistant |
| **Vivo / iQOO (Funtouch / OriginOS)** | iManager, Vivo-Startbildschirm |
| **Huawei / Honor (EMUI / MagicOS)** | Systemoptimierer, Huawei Home, Honor System Manager |

**Dynamisch ermittelt**
- Aktuelle Tastatur (wird zur Laufzeit automatisch erkannt)
- Aktueller Startbildschirm (wird zur Laufzeit automatisch erkannt)

---

## FAQ
↩️ [Inhaltsverzeichnis](#inhaltsverzeichnis)

**❓ Eine App startet sofort nach dem Beenden neu — was soll ich tun?**

Fügen Sie sie zu den **Hintergrundbeschränkungen** hinzu — dies verhindert, dass Android sie im Hintergrund auf OS-Ebene neu startet.

**❓ Hintergrundbeschränkungen gehen nach einem Neustart verloren**

Aktivieren Sie den **Hintergrunddienst** — dieser stellt alle gespeicherten Beschränkungen nach dem Neustart automatisch wieder her.

**❓ Welchen Modus soll ich wählen — Whitelist oder Blacklist?**

Whitelist — stoppt alles außer dem, was wichtig ist. Blacklist — stoppt nur bestimmte Apps und lässt alles andere in Ruhe.

**❓ Ist der Hintergrunddienst für das manuelle Beenden erforderlich?**

Nein. Das manuelle Beenden vom Hauptbildschirm, den Schnelleinstellungen, dem Widget und der Verknüpfung funktioniert alle ohne Hintergrunddienst.

**❓ Ist es sicher, System-Apps zu stoppen?**

Nein. Das Stoppen oder Einschränken von System-Apps kann zu Instabilität, Einfrieren, Benachrichtigungsverlust und Bootschleifen führen. ReAppzuku warnt Sie, bevor Sie System-Apps beeinflussen.

**❓ Schlafmodus vs. Hintergrundbeschränkungen — was ist der Unterschied?**

Hintergrundbeschränkungen verhindern, dass die App im Hintergrund **startet**, aber sie bleibt installiert und sichtbar. Der Schlafmodus **friert** sie vollständig auf Systemebene ein — als wäre sie deaktiviert — bis der Bildschirm entsperrt wird.

**❓ Shizuku funktioniert nach einem Neustart nicht mehr**

Shizuku muss nach jedem Neustart reaktiviert werden (es sei denn, der kabellose ADB-Modus wird verwendet). Öffnen Sie Shizuku und starten Sie den Dienst neu.

**❓ Eine App kann einfach nicht beendet werden — was soll ich tun?**

Öffnen Sie das App-Menü und wählen Sie **Trigger**. Es wird genau anzeigen, was den Prozess am Leben hält: Vordergrunddienst, WakeLock, haftender Dienst oder Bindung von einer anderen App. Abhängig vom Auslöser — wenden Sie **Hintergrundbeschränkungen** an (sanft, hart oder manuell).

**❓ Schlafmodus vs. Harte Einschränkung — was ist der Unterschied?**

Beide schränken die Hintergrundaktivität aggressiv ein, aber auf unterschiedliche Weise. Der Schlafmodus **friert** die App ein, wenn der Bildschirm aus ist, und taut sie beim Entsperren auf — folgt dem Bildschirmplan. Die harte Einschränkung ist **immer aktiv**: Die App kann im Hintergrund nicht überleben, selbst wenn der Bildschirm an ist und Sie gewechselt haben. Für das nächtliche Einfrieren — Schlafmodus. Für chronisch aggressive Apps — Harte Einschränkung.

**❓ Warum sollte der Kill-Typ von force-stop auf am kill geändert werden?**

`am force-stop` ist ein harter Stopp — beendet alle Prozesse und löscht den App-Zustand. `am kill` ist weicher — beendet nur Hintergrundprozesse, ohne den Vordergrund zu berühren. Wechseln Sie nur, wenn Sie Probleme bei anderen Apps oder Firmware-Konflikten feststellen — auf einigen Geräten ist `force-stop` zu aggressiv.
