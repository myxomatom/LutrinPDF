# 0006 — Identifiant d'application et paquets

- **Statut** : accepté
- **Date** : 2026-11-03
- **Références** : `SPEC.md` §7 (F-Droid, Google Play)

## Contexte

L'`applicationId` est définitif une fois l'application publiée sur F-Droid ou Google Play :
le changer revient à publier une autre application. Il doit être unique et correspondre à
un domaine que le projet contrôle.

## Options envisagées

- **`io.github.myxomatom.lutrin`** : domaine GitHub Pages du propriétaire du dépôt, convention
  courante sur F-Droid pour les projets hébergés sur GitHub.
- Un domaine propre (`org.lutrin…`) : nécessite d'acheter et de garder le domaine.

## Décision

`applicationId = io.github.myxomatom.lutrin` ; les paquets Kotlin commencent par
`io.github.myxomatom.lutrin` (`…core.ui`, `…feature.reader`, …). Les builds de debug ont le
suffixe `.debug` pour cohabiter avec la version publiée.

## Conséquences

- Si le projet change d'hébergeur ou acquiert un domaine, l'identifiant **ne change pas**.
- À reconsidérer **uniquement avant** la première publication (jalon v1.0).
