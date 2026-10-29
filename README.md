# Îles Flottantes

Plugin de l’événement Minecraft, compilé contre l’API Paper 26.3 build 227 fournie dans `libs/`. Utiliser un JDK 25 pour compiler cette API : `mvn package`. Le JAR est produit dans `target/`.

## Commandes

| Commande | Accès | Fonction |
| --- | --- | --- |
| `/team` ou `/team list` | Admin | Équipes colorées, effectifs et caisses actuels |
| `/team info Bleu clair` | Admin | Effectif, caisse et membres en ligne |
| `/team join Alice Bleu clair` | Admin | Affecter un joueur en ligne à une équipe |
| `/team leave Alice` | Admin | Retirer un joueur en ligne de son équipe |
| `/game start` | Admin | Démarrer ou reprendre la partie |
| `/game pause` | Admin | Suspendre le chrono et les actions des participants |
| `/game stop` | Admin | Terminer la partie jusqu’au prochain reset |
| `/game reset` | Admin | Remettre chrono, membres, statistiques et caisses à zéro |
| `/game time 120` | Admin | Préparer un compte à rebours de deux heures |
| `/game time 0` | Admin | Revenir au chrono libre avant le départ |
| `/set "Bleu clair" 100.50` | Admin | Définir une caisse (zéro autorisé) |
| `/pay Bob 12.50` | Tout joueur | Transférer de sa caisse d’équipe vers celle de Bob |
| `/balance` | Tout joueur | Consulter sa caisse d’équipe |

La permission `ilesflottantes.admin` est accordée aux opérateurs par défaut. `/pay` et `/balance` n’exigent aucune permission. Les deux joueurs d’un paiement doivent être en ligne et appartenir à des équipes différentes. Tout membre peut dépenser la caisse de son équipe.

Les arbres Brigadier affichent les arguments pendant la saisie. Les suggestions de joueurs et d’équipes sont recalculées à chaque demande ; les couleurs des équipes apparaissent dans les infobulles des suggestions et dans la liste. Les noms composés sont acceptés sans guillemets pour `/team join` et `/team info` ; pour `/set`, utiliser les guillemets proposés par la complétion. Les virgules et points sont acceptés pour les montants, avec deux décimales maximum.

## Organisation

- `commandes` : adaptation des commandes Paper et présentation.
- `modeles/teams` : équipes et membres.
- `modeles/economy` : règles économiques indépendantes de Bukkit, calculs exacts en `BigDecimal`.
- `persistence` : implémentation de `BalanceRepository`, sauvegarde atomique des caisses.
- `utils/MessageManager` : textes centralisés dans `src/main/resources/messages.yml`, avec valeurs par défaut intégrées pour les anciennes installations.

Les caisses sont sauvegardées à chaque modification dans `plugins/ilesFlottantes/balances.properties`. Une erreur de sauvegarde annule la transaction en mémoire. Un fichier invalide désactive le plugin plutôt que de remettre les caisses à zéro. Les caisses démarrent à zéro ; un administrateur les initialise avec `/set`. La persistance des affectations d’équipe reste à implémenter : comme auparavant, il faut réaffecter les joueurs après un redémarrage.

## Vérification en jeu

Après installation du JAR et redémarrage du serveur : affecter deux joueurs à deux équipes, créditer une caisse avec `/set`, puis effectuer `/pay` depuis un joueur non opérateur. Vérifier `/balance` des deux côtés, le refus de `/set` pour le non-opérateur, les suggestions après connexion/déconnexion et les noms d’équipes composés. Les tests automatisés d’économie sont exécutés par `mvn test`.

## Partie et interfaces

`/start` est remplacée par `/game start`, sans alias conservé. Sans durée configurée, le chrono compte depuis zéro jusqu’à l’arrêt manuel. Avant le départ, `/game time 120` fixe une durée de 120 minutes et affiche `02:00:00` ; le temps restant diminue uniquement pendant la partie active. À zéro, la partie se termine automatiquement, avec vérification chaque seconde. `/game time 0` retire la limite ; les durées acceptées vont de 0 à 525600 minutes. La durée ne se modifie pas après le départ.

`/game start` reprend une pause sans redistribuer les casques. Après `/game stop`, il faut `/game reset` pour rejouer. Le reset revient au chrono libre, vide les équipes (joueurs hors ligne compris), efface les kills/morts et sauvegarde toutes les caisses à zéro. Si cette sauvegarde échoue, la partie reste intacte. Les inventaires et les constructions ne sont pas effacés.

Le panneau de droite affiche l’état, le temps, l’équipe, les kills, les morts et la caisse partagée. Le TAB affiche ces statistiques par joueur avec le nom coloré et un en-tête de partie. Les pseudos au-dessus des joueurs utilisent la couleur vanilla la plus proche de celle de leur équipe. Les données sont actualisées chaque seconde et les affichages précédents sont restaurés à la désactivation du plugin.

Les kills et morts ne comptent que pendant la partie active pour les membres d’équipes. En pause, leurs déplacements, interactions, inventaires, dégâts, faim et actions courantes sont bloqués ; les paiements sont refusés en pause et après la fin. Le monde (mobs, redstone, etc.) continue de tourner. Les administrateurs peuvent toujours utiliser les commandes de gestion.

L’état de partie, le chrono, les statistiques et les affectations restent en mémoire : un redémarrage du serveur revient à une partie en attente. Les caisses restent persistantes. Une reprise de partie complète après redémarrage nécessitera une sauvegarde supplémentaire.

Pour vérifier en solo : se mettre dans une équipe, utiliser `/game start`, observer le panneau et le TAB, puis tester pause/reprise/stop/reset. Pour le compte à rebours, utiliser `/game time 1` après reset et vérifier l’arrêt à zéro. Vérifier aussi que `/start` n’est plus fournie par le plugin et qu’un non-opérateur ne peut pas gérer la partie.

Les spawns, protections d’îles, îles centrales, périodes de vulnérabilité et boss bars restent les prochaines étapes.
