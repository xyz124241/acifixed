# ACI 1.4.3 – Paper / Minecraft 26.2

## Voraussetzungen
- Paper 26.2
- Java 25
- Maven 3.9+

## Plugin bauen
Im Projektordner `Quellcode` ausführen:
```bash
mvn -B -ntp clean package
```
Die fertige Datei liegt danach unter `target/ACI-1.4.3.jar`.
Der GitHub-Actions-Workflow baut sie bei Push/Tag automatisch. Ein Tag `v1.4.3` veröffentlicht JAR und Resourcepack als Release-Anhänge.

## Installation
1. Server stoppen und `plugins/ACI/` sichern.
2. Alle alten ACI-JARs aus `plugins/` entfernen (nur eine ACI-JAR laden).
3. Die neu gebaute `ACI-1.4.3.jar` nach `plugins/` kopieren.
4. Server starten. Beim ersten Upgrade wird `items.yml` gesichert und die eingebauten alten Kristalle werden durch exakt 38 neue Kristalle ersetzt. Andere Custom-Items bleiben erhalten.
5. Alte Kristall-Stacks im Inventar werden beim Join aktualisiert. Falls ein alter Kristall noch das alte Modell zeigt, ihn neu mit `/aci give <id>` ausgeben.

## Ingame-Settings
- `/aci` öffnen, einen Eintrag anklicken und im Item-Editor den Komparator **Attributes, cooldown & other settings** auswählen.
- Cooldown in Sekunden: `0` löst einmal beim Ablegen in die Nebenhand aus; Werte größer `0` lösen die eingestellte Fähigkeit während des Tragens in der Nebenhand erneut nach Ablauf aus.
- `Auto offhand` ein-/ausschalten.
- Fähigkeit im Chat hinzufügen: `RESISTANCE 4 10` (Effekt, Stufe, Sekunden). Shift-Klick entfernt den letzten Effekt.
- Attribut im Chat hinzufügen: `MAX_HEALTH 4 ADD_NUMBER ANY` (Attribut, Wert, Operation, Slotgruppe). Shift-Klick entfernt den letzten Modifier.
- Enchantment hinzufügen: `sharpness 5`. Shift-Klick entfernt die letzte Verzauberung.
- Shulker-Reihen durch Klicken von 1 bis 6 einstellen; Auto-Pickup kann umgeschaltet werden. Wenn das Spielerinventar voll ist, wird der Rest gedroppt.
- Passive Effekte bleiben im separaten Effekte-Menü; Effekt-Auswahl nutzt passende Item-Icons.

## Crafting
- Custom-Items sind als Zutaten für andere ACI-Rezepte verwendbar.
- ACI blockiert Vanilla-Crafting nicht; Custom-Items zählen in Vanilla-Rezepten als ihr Basismaterial (z. B. ein Custom-Enderauge als `ENDER_EYE`).

## 38 Kristalle / Resourcepack
- Genau 38 einzelne Kristall-Items mit separaten PNG-Texturen.
- Jede Farbe hat einen MiniMessage-Gradientnamen.
- Minecraft 26.2 Resourcepack-Format: `88.0`.
- Die Texturen verwenden das aktuelle Item-Model-System (`assets/aci/items/` + `assets/aci/models/item/`) statt der alten `CustomModelData`-Overrides.
- Für manuelle Installation das **Resourcepack-ZIP direkt** in `%appdata%\\.minecraft\\resourcepacks` legen und unter Optionen → Ressourcenpakete aktivieren. Nicht das komplette Quellcode-/Installations-ZIP in den Minecraft-Ordner legen.

## Automatisches Resourcepack beim Join
Der interne HTTP-Host ist standardmäßig aktiviert. Die Port- und Adresswerte werden aus `plugins/ACI/config.yml` gelesen. Beispiel:
```yaml
resource-pack:
  send-on-join: true
  required: false
  host:
    enabled: true
    port: 25708
    public-address: "neptunsmp.de"
```
Der Port muss beim Hoster als TCP-Port freigegeben sein. Bei einem externen GitHub-Release-Link `resource-pack.url` und den SHA-1-Wert `resource-pack.sha1` eintragen.

## GitHub Release erstellen
1. Den Inhalt des Projektordners `Quellcode` in den Stamm deines GitHub-Repositories hochladen.
2. Den Branch `main` pushen. GitHub Actions baut dann eine JAR als Workflow-Artefakt.
3. Einen neuen Versions-Tag erstellen und pushen.
4. Im GitHub-Release erscheinen `ACI-1.4.3.jar` und `ACI-Crystals-ResourcePack-26.2-v1.4.3.zip` als Assets.

## Teststatus / bekannte Grenzen
- Die Quellcode-Anpassungen, die Pack-Struktur und alle 38 Textur-/Modelldateien wurden lokal geprüft.
- Eine JAR konnte in dieser Arbeitsumgebung nicht kompiliert werden: Hier ist nur Java 21 installiert, Paper 26.2 benötigt Java 25. Der GitHub-Actions-Workflow ist deshalb auf Java 25 eingestellt und soll den endgültigen Kompiliertest ausführen.
- Vor dem Austausch auf dem Live-Server bitte `plugins/ACI/` sichern und den Build zuerst auf einer Serverkopie testen.


## Custom-Shulker improvements (1.4.3)
- Rename ACI shulkers in an anvil; the custom name survives placing and breaking.
- Dye ACI shulkers with the normal Minecraft dyeing recipe; the color survives placing and breaking.
- The dropped shulker lore lists its stored contents instead of relying on the vanilla empty-container tooltip.
- Custom recipe ingredients match by ACI item ID, so shulkers remain usable after being renamed, dyed, filled, placed and broken.
- Inventory refreshes preserve shulker color, name and stored contents.
