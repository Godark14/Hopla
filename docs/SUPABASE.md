# Configurer Supabase

Sans clés, Hopla tourne en **mode démo** (amis simulés). Pour le vrai partage entre amis :

## 1. Créer le projet

Sur [supabase.com](https://supabase.com), crée un projet (le plan gratuit suffit). Choisis la région la plus
proche de tes utilisateurs, par exemple Paris (eu-west-3) : elle ne pourra plus être changée.

## 2. Créer la base

**SQL Editor** → colle et exécute [`supabase/migrations/20261120000000_hopla.sql`](../supabase/migrations/20261120000000_hopla.sql).
Avec la CLI : `supabase link` puis `supabase db push`.

Le script crée les tables, les autorisations d'accès (`GRANT`, obligatoires sur les projets créés
depuis mai 2026), les règles de sécurité (RLS) et la fonction de classement `athletes_overview`.
Il peut être relancé sans risque.

## 3. Configurer les e-mails

Les e-mails de connexion doivent contenir un **code à 6 chiffres**, pas un lien.

1. **Authentication → Emails → SMTP Settings** : active un SMTP personnel. Sur les nouveaux projets
   gratuits, les modèles d'e-mail ne sont modifiables qu'avec un SMTP perso, et l'envoi intégré est
   limité à 2 e-mails/heure, uniquement vers les membres de ton organisation.
   - Pour démarrer sans nom de domaine, un compte Gmail avec un
     [mot de passe d'application](https://myaccount.google.com/apppasswords) fonctionne :
     hôte `smtp.gmail.com`, port `465`.
2. **Authentication → Emails → Templates** : dans **Magic Link** *et* **Confirm signup**, remplace
   le contenu par [`supabase/templates/code.html`](../supabase/templates/code.html) (il utilise `{{ .Token }}`).
3. **Authentication → Rate Limits** : augmente la limite d'e-mails par heure (par exemple 30).

## 4. Brancher l'app

**Project Settings → API Keys** : copie l'URL et la clé *publishable* dans `local.properties`
(modèle : [`local.properties.example`](../local.properties.example)) :

```properties
supabase.url=https://xxxx.supabase.co
supabase.key=sb_publishable_...
```

La clé *publishable* est faite pour être embarquée dans l'app. Ne mets **jamais** la clé
`secret` / `service_role` dans l'app ni dans le dépôt.

## Ce qui est stocké

| Table | Contenu | Lecture | Écriture |
|---|---|---|---|
| `profiles` | prénom, @pseudo unique, forme et couleur d'avatar | utilisateurs connectés | soi uniquement |
| `sessions` | date, durée, sauts, kcal, mode, rythme max | utilisateurs connectés | soi uniquement |
| `follows` | qui suit qui | utilisateurs connectés | soi uniquement |

Le poids reste sur le téléphone. Les séances sont d'abord écrites en local puis envoyées
(upsert idempotent sur l'id) : hors ligne, rien n'est perdu. Sur un nouvel appareil, les 90 derniers
jours sont récupérés.

## Dépannage

| Message dans l'app | Cause probable |
|---|---|
| « Accès refusé par Supabase (42501) » | Migration SQL pas exécutée, ou ancienne version sans `GRANT` |
| « Code invalide ou expiré » | Code trop ancien, ou modèle d'e-mail encore en mode lien |
| Aucun e-mail reçu | SMTP non configuré, limite d'envoi atteinte, ou e-mail dans les spams |

Le détail des erreurs est visible dans Logcat avec le filtre `Hopla`.
