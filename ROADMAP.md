# Skin Totems – Roadmap

## ✅ 1.0 – Skin-Totem
Flaches Totem im Stil des Custom-Totem-Generators, automatisch mit dem Skin des Halters.

## ✅ 1.1 – Plushie
Statt des flachen Totems hält jeder Spieler ein kleines 3D-Plushie von sich selbst.

- Echtes Blockmodell aus Quadern, gemappt wie das Spielermodell → jeder Skin passt automatisch,
  inklusive zweiter Ebene (Hut, Jacke, Ärmel, Hose) und dünner Alex-Arme.
- Chibi-Proportionen: großer Kopf, kleiner Körper, Stummelarme, sitzend mit Beinen nach vorne.
- Form steht als JSON in `assets/skintotems/plushie/plushie.json` → per Texture Pack ersetzbar.
- Größe/Haltung kommen aus `assets/skintotems/models/item/plushie.json` (normale
  `display`-Transforms) → per Texture Pack anpassbar.
- Haltung in erster/dritter Person mit Fabis Plushie Hand-Editor eingestellt (Web-Vorschau mit
  derselben Transform-Rechnung wie das Spiel); Inventar-Icon 35 % kleiner.

## ✅ 1.2 – Eigenes Einstellungsmenü
- Screen über Mod Menu oder Tastenkürzel (standardmäßig nicht belegt), Live-Vorschau des eigenen
  Spielers mit Totems in beiden Händen + Inventar-Icon.
- Stil Totem / Plushie; wo anzeigen (andere Spieler, eigene Hand, Inventar, Totem-Animation);
  Totem: Hut-Ebene, Rahmen; Plushie: zweite Ebene, Größe 50–150 %.

## 📋 Nächste Ideen für Feineinstellungen
- Plushie: Kopfgröße, Pose (sitzend/stehend).
- Nur eigener Skin vs. Skin des jeweiligen Halters.

## 💡 Später / Ideen
- **Cosmetics für das Plushie**, z. B. Hüte (Partyhut, Krone, Weihnachtsmütze …), auswählbar im
  Einstellungsmenü. Wahrscheinlich als zusätzliche Modell-Teile/Texturen in den Assets, damit
  Texture Packs eigene Cosmetics liefern können.
