# 0004 — Sauvegarde Android désactivée

- **Statut** : accepté
- **Date** : 2026-11-03
- **Références** : `SPEC.md` §3 (Vie privée), `AGENTS.md` §9

## Contexte

`AGENTS.md` §9 demande de configurer `android:allowBackup` pour exclure les données sensibles.
Les données de Lutrin sont locales : récents (URI et vignettes), positions de lecture,
préférences, et plus tard l'historique des actions des assistants. La sauvegarde Android
dans le cloud enverrait ces données hors de l'appareil, contre la promesse « aucune donnée
ne quitte l'appareil ». De plus, les permissions d'accès aux URI (SAF) ne survivent pas à une
restauration : des récents restaurés seraient inutilisables.

## Options envisagées

- **A — Tout désactiver** : `allowBackup="false"` et règles d'exclusion totales.
- **B — Sauvegarder seulement les préférences** : utile pour l'utilisateur, mais données
  envoyées au service de sauvegarde de Google (ou du constructeur).

## Décision

**A.** `android:allowBackup="false"`, plus `dataExtractionRules` (Android 12+, sauvegarde
cloud **et** transfert entre appareils) et `fullBackupContent` (Android ≤ 11) qui excluent tout.

## Conséquences

- Aucune donnée de Lutrin dans une sauvegarde ; après réinstallation, réglages par défaut.
- Si un besoin d'export des réglages apparaît, il passera par un fichier choisi par
  l'utilisateur (SAF), jamais par la sauvegarde système.
