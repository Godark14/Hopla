# Changelog

Toutes les évolutions notables de Hopla sont listées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/)
et le projet respecte le [versionnage sémantique](https://semver.org/lang/fr/).

## [Non publié]

### Prévu
- Version iOS (Kotlin Multiplatform + Compose Multiplatform, navigation Liquid Glass)
- Défis en direct entre amis (Supabase Realtime)
- Calibration de la détection au premier lancement

## [1.0.0] - 2026-11-20

Première version publique. 🎉

### Ajouté

**Détection des sauts**
- Comptage automatique des sauts avec l'accéléromètre (~100 mesures/s), quelle que soit
  l'orientation du téléphone : poche, brassard ou main
- Seuil qui s'adapte à l'intensité de chacun, avec trois sensibilités (Douce, Normale, Vive)
- Calories estimées selon le rythme (méthode MET) et le poids

**Séances**
- Quatre modes : Libre, Chrono, Objectif de sauts, Tabata (effort, repos et tours réglables)
- Décompte 3-2-1, pause, rythme en direct (sauts/min) et rythme max
- Comptage écran verrouillé, avec le compteur dans la notification et les boutons Pause / Terminer
- Vibrations au départ, tous les 100 sauts, aux changements effort/repos et à la fin
- Bandeau « séance en cours » pour revenir au compteur depuis n'importe quel écran
- Bilan de fin de séance, et notification de bilan si la séance se termine écran éteint

**Statistiques**
- Objectif du jour, série de jours consécutifs, record
- Histogramme de la semaine comparée à la semaine précédente, calories et minutes

**Social (Supabase)**
- Connexion sans mot de passe par code envoyé par e-mail
- Profil public : prénom, @pseudo, avatar dans une forme unique
- Suivre ses amis, recherche par nom ou @pseudo
- Classement de la semaine avec podium, fiche de chaque athlète, « Défi 1 min »
- Séances enregistrées d'abord sur le téléphone puis synchronisées : rien n'est perdu hors ligne
- Mode démo avec amis simulés quand aucune clé Supabase n'est configurée

**Design (Material 3 Expressive)**
- Formes qui se transforment et pulsent à chaque saut, animations à ressort
- Barre d'outils flottante, indicateurs de chargement et de progression ondulés, boutons qui
  changent de forme à l'appui
- Quatre palettes (Braise, Lagon, Acide, Prune) plus les couleurs du fond d'écran (Android 12+)
- Quatre thèmes de titres : Bolide, Affiche, Arcade, Élastique
- Mode clair, sombre ou automatique

### Configuration requise
- Android 8.0 (API 26) ou plus récent, avec accéléromètre
- Comptage écran éteint : Android 8.0+, notification visible à partir d'Android 13 si autorisée

### Problèmes connus
- Sur certains appareils (Xiaomi, Huawei, Oppo…), l'optimisation de batterie peut couper le
  comptage écran éteint : désactive-la pour Hopla dans les réglages du téléphone.
- Les données des athlètes en mode démo sont fictives.

[Non publié]: https://github.com/Godark14/hopla/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Godark14/hopla/releases/tag/v1.0.0
