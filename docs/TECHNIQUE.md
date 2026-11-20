# Fonctionnement technique

## Détection des sauts

`JumpDetector` (Kotlin pur, sans dépendance Android) traite chaque mesure de l'accéléromètre (~100 Hz) :

1. **Norme du vecteur** `√(x² + y² + z²)` : le résultat ne dépend pas de l'orientation du téléphone.
2. **Gravité retirée** par un filtre passe-bas très lent : il reste la composante due au mouvement.
3. **Lissage** rapide pour gommer le bruit.
4. **Détection de pic** à chaque réception de saut, avec :
   - un seuil qui s'adapte à l'intensité moyenne de la personne ;
   - une hystérésis (le signal doit redescendre avant qu'un nouveau saut compte) ;
   - une période réfractaire de 230 ms (au plus ~4 sauts/s) ;
   - le rejet des pics aberrants (chute du téléphone, choc).

La sensibilité (Douce 0,8 · Normale 1,0 · Vive 1,3) divise le seuil.

`JumpDetectorTest` vérifie le comptage sur des signaux simulés de 78 à 210 sauts/min, de faible à
forte intensité (erreur < 3 %), et l'absence de faux positifs au repos.

## Calories

Méthode MET du *Compendium of Physical Activities* : 8,8 (< 100 sauts/min), 11,8 (100–120) ou
12,3 (> 120), × 3,5 × poids / 200 par minute. Seules les phases où tu sautes vraiment comptent.

## Comptage écran éteint

Au lancement d'une séance, `JumpTrackingService` passe en **service de premier plan de type `health`** :

- la permission `HIGH_SAMPLING_RATE_SENSORS` (accordée à l'installation) remplit le prérequis du
  type `health` : aucune demande d'autorisation supplémentaire ;
- un **wake lock partiel** garde le processeur éveillé pour que l'accéléromètre continue d'envoyer ses mesures ;
- la **notification** affiche sauts, temps, kcal et rythme, avec les boutons Pause et Terminer ;
- le moteur de séance (`WorkoutEngine`) vit au niveau du processus, pas de l'écran ;
- **vibrations** : départ, tous les 100 sauts, changement effort/repos en Tabata, fin de séance.

Seule l'autorisation des notifications (Android 13+) est demandée. Si elle est refusée, le
comptage fonctionne quand même, sans compteur visible écran verrouillé.

## Material 3 Expressive

`MaterialExpressiveTheme` + `MotionScheme.expressive()` (toutes les animations à ressort en viennent),
`MaterialShapes` + `Morph` (formes qui se transforment et pulsent à chaque saut),
`HorizontalFloatingToolbar`, `LoadingIndicator` / `ContainedLoadingIndicator`,
`CircularWavyProgressIndicator` / `LinearWavyProgressIndicator`, `ToggleButton` connectés,
boutons dont la forme change à l'appui, `HorizontalMultiBrowseCarousel`.

## Architecture

```
app/src/main/java/app/hopla/
├── sensor/     JumpDetector (pur Kotlin, testé) + JumpSensor (Flow sur SensorManager)
├── workout/    WorkoutEngine (séance, niveau processus) + JumpTrackingService (premier plan)
├── domain/     Calories (MET), Stats (jour, semaine, série)
├── data/       DataStore (réglages + séances), Supabase.kt (auth, social, synchro), mode démo
└── ui/         theme/, components/, auth/, home/, workout/, friends/, profile/, nav/
supabase/       migration SQL (testée sur Postgres 16) + modèle d'e-mail
```

## Versions

AGP 9.5.1 (Kotlin intégré), Kotlin 2.4.0, Compose BOM 2026.11.01 (Material 3 1.5.0),
supabase-kt 3.8.0, Ktor 3.5.1, Gradle 9.6.0, minSdk 26, compileSdk 37.1.
