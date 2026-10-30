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
| `/game pause` | Admin | Suspendre la partie ou la cérémonie et le vote |
| `/game save` | Admin | Sauvegarder immédiatement la session |
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

Les caisses sont sauvegardées à chaque modification dans `plugins/ilesFlottantes/balances.properties`. Une erreur de sauvegarde annule la transaction en mémoire. Un fichier invalide désactive le plugin plutôt que de remettre les caisses à zéro. Les caisses démarrent à zéro ; un administrateur les initialise avec `/set`. Les affectations et statistiques sont enregistrées dans `session.json` et restaurées au démarrage.

## Vérification en jeu

Après installation du JAR et redémarrage du serveur : affecter deux joueurs à deux équipes, créditer une caisse avec `/set`, puis effectuer `/pay` depuis un joueur non opérateur. Vérifier `/balance` des deux côtés, le refus de `/set` pour le non-opérateur, les suggestions après connexion/déconnexion et les noms d’équipes composés. Les tests automatisés d’économie sont exécutés par `mvn test`.

## Partie et interfaces

`/start` est remplacée par `/game start`, sans alias conservé. Sans durée configurée, le chrono compte depuis zéro jusqu’à l’arrêt manuel. Avant le départ, `/game time 120` fixe une durée de 120 minutes et affiche `02:00:00` ; le temps restant diminue uniquement pendant la partie active. À zéro, la partie se termine automatiquement, avec vérification chaque seconde. `/game time 0` retire la limite ; les durées acceptées vont de 0 à 525600 minutes. La durée ne se modifie pas après le départ.

`/game start` reprend une pause sans redistribuer les casques. Après `/game stop`, il faut `/game reset` pour rejouer. Le reset revient au chrono libre, vide les équipes (joueurs hors ligne compris), efface les kills/morts et sauvegarde toutes les caisses à zéro. Si cette sauvegarde échoue, la partie reste intacte. Les inventaires et les constructions ne sont pas effacés.

Le panneau de droite affiche l’état, le temps, l’équipe, les kills, les morts et la caisse partagée. Le TAB affiche ces statistiques par joueur avec le nom coloré et un en-tête de partie. Les pseudos au-dessus des joueurs utilisent la couleur vanilla la plus proche de celle de leur équipe. Les données sont actualisées chaque seconde et les affichages précédents sont restaurés à la désactivation du plugin.

Les kills et morts ne comptent que pendant la partie active pour les membres d’équipes. En pause, leurs déplacements, interactions, inventaires, dégâts, faim et actions courantes sont bloqués ; les paiements sont refusés en pause et après la fin. Le monde (mobs, redstone, etc.) continue de tourner. Les administrateurs peuvent toujours utiliser les commandes de gestion.

L’état de partie, le chrono, les statistiques et les affectations sont persistants. Une partie active est restaurée en pause ; le temps passé serveur éteint ne compte pas.

Pour vérifier en solo : se mettre dans une équipe, utiliser `/game start`, observer le panneau et le TAB, puis tester pause/reprise/stop/reset. Pour le compte à rebours, utiliser `/game time 1` après reset et vérifier l’arrêt à zéro. Vérifier aussi que `/start` n’est plus fournie par le plugin et qu’un non-opérateur ne peut pas gérer la partie.

## Zones, spawns et raids

L’équipe `Commun` est réservée aux builds du spawn et des défis. Elle refuse toute affectation de joueur et n’a pas besoin de spawn de partie. Elle peut posséder plusieurs zones distinctes, comme toutes les équipes. Son nom canonique est défini dans `messages.yml` ; le garder stable après création des zones.

Exemple de configuration d’une île rouge (commandes administratives) :

```text
/zone pos1 -100 40 -100
/zone pos2 -20 180 -20
/zone create ile_rouge Rouge
/spawn set Rouge
/spawn info Rouge
```

Les coordonnées des coins sont des blocs absolus dans le monde du joueur. Sans coordonnées, `/zone pos1` et `/zone pos2` sélectionnent sa position actuelle. Les deux coins doivent être dans le même monde ; leurs bornes sont inclusives et l’ordre n’a pas d’importance. Prévoir assez de hauteur pour les constructions ! Les zones ne peuvent pas se chevaucher, même pour la même équipe. Les identifiants utilisent des minuscules, chiffres, tirets ou underscores (40 caractères maximum).

Pour `/spawn set Rouge`, se placer sur le point de départ souhaité, dans une zone de cette équipe, sur un bloc solide non dangereux, avec les pieds et la tête dégagés. Position et orientation sont enregistrées. Les noms composés fonctionnent sans guillemets en fin de commande : `/zone create ile_bleue Bleu clair`, `/spawn set Bleu clair`.

| Commande | Fonction |
| --- | --- |
| `/zone create defi_jump Commun` | Créer une zone commune à partir des deux coins sélectionnés |
| `/zone list` ou `/zone info ile_rouge` | Consulter propriétaires, coordonnées et protection |
| `/zone unlock ile_rouge 120` | Ouvrir la zone à tous pendant 120 secondes |
| `/zone unlock ile_rouge` | Ouvrir sans limite jusqu’à un verrouillage manuel |
| `/zone lock ile_rouge` | Refermer immédiatement |
| `/zone delete ile_rouge` | Supprimer la protection et rendre cet espace libre |
| `/spawn tp Rouge` | Tester une téléportation au spawn de l’équipe |
| `/spawn info Rouge` | Vérifier le point enregistré et sa validité actuelle |

Les ouvertures temporisées durent de 1 à 86400 secondes réelles, y compris pendant une pause de partie. Une boss bar globale affiche les zones vulnérables et leur durée ; à l’expiration, elles sont de nouveau protégées. Le reset referme toutes les zones mais conserve leur géométrie et les spawns. Au redémarrage, toutes les zones sont verrouillées par défaut. `/game stop` ne ferme pas une ouverture administrative en cours ; utiliser `/zone lock` ou `/game reset`.

Seuls les membres propriétaires et les opérateurs peuvent modifier une zone verrouillée. Les opérateurs contournent ces contrôles directs ; la permission d’administration seule n’accorde pas ce contournement. En dehors des zones, la construction est libre. Les déplacements, le PvP et l’utilisation des portes/boutons restent possibles ; les coffres et autres interactions modificatrices sont protégés. Les explosions, incendies, mobs destructeurs, fluides entrants, éponges, pistons et transferts par entonnoirs sont également contrôlés. Les mécanismes fonctionnent au sein d’une même zone ; ils ne peuvent pas modifier une autre zone verrouillée, même avec le même propriétaire. Les protections d’environnement ne connaissent pas l’identité d’un opérateur : déverrouiller la zone pour tester des destructions indirectes.

Le premier `/game start` contrôle **uniquement les équipes ayant des membres**, y compris les membres hors ligne. Le départ est refusé si un spawn manque, si le monde est indisponible, si le point n’est plus dans une zone de son équipe ou si le sol/espace est devenu dangereux. Les joueurs en ligne sont téléportés avant le lancement du chrono et la distribution des casques. En cas de refus de téléportation par un autre plugin, le départ est annulé et le retour aux anciennes positions est tenté. Une reprise après pause ne téléporte pas les joueurs.

Les membres absents au lancement rejoignent leur spawn à leur première connexion pendant la partie. Les réapparitions utilisent aussi le spawn d’équipe tant que la partie est active ou suspendue, s’il est encore valide ; sinon, le comportement normal de Minecraft est conservé. Ces règles ne suppriment pas les inventaires.

Zones et spawns sont sauvegardés à chaque modification dans `plugins/ilesFlottantes/territories.properties`. Une sauvegarde ratée annule la modification. Un fichier invalide désactive le plugin et produit un message de diagnostic ; corriger ce fichier avant d’ouvrir le serveur aux joueurs. La session complète est sauvegardée séparément dans le même dossier.

### Vérification en jeu

Configurer une zone et son spawn, puis vérifier le refus de `/game start` avec une autre équipe participante sans spawn. Tester ensuite la téléportation de départ, la reprise sans téléportation et le spawn de réapparition. Pour tester la protection, utiliser un compte **non opérateur**, successivement membre propriétaire, membre adverse et sans équipe : les opérateurs ont volontairement tous les droits. Vérifier aussi un TNT, un seau et un piston à la frontière, les coffres, l’ouverture de 10 secondes avec sa boss bar et la fermeture après reset/redémarrage.




## Points finaux et vote des builds

L’arrêt manuel (`/game stop`) et la fin du compte à rebours déclenchent une seule cérémonie. Les équipes participantes, leurs caisses, leurs morts et leurs membres sont figés à cet instant ; `Commun` et les équipes vides sont exclues. Les modifications d’équipe et `/set` sont bloquées jusqu’au reset.

Chaque catégorie vaut **un point** : équipe la plus riche, premier Ender Dragon tué, premier Warden tué, total de morts le plus faible, premier Wither tué, puis meilleur build. Les boss sont crédités au joueur reconnu comme tueur par Minecraft, uniquement pendant la partie active. Un boss mort sans joueur crédité ne donne aucun point. Les boss suivants de la même espèce ne donnent pas de point supplémentaire. Une mort annulée par un autre plugin est ignorée.

Les catégories défilent avec titres et sons, puis les joueurs passent en **spectateur** pour visiter les îles pendant **30 minutes**. Une boss bar affiche le temps restant et le nombre de votes. Les joueurs rejoignant pendant cette visite passent aussi en spectateur, mais seuls les membres d’équipes présents dans l’effectif figé sont électeurs. Les membres hors ligne restent électeurs : le délai de 30 minutes ou l’intervention d’un admin évite toute attente indéfinie.

- `/vote Bleu clair` : un vote définitif par UUID et par partie, sans permission spéciale. Impossible de voter pour sa propre équipe, une équipe vide ou `Commun`. Les suggestions proposent uniquement les choix valides. Une reconnexion ne redonne pas de vote.
- `/game vote end` : clôture anticipée du scrutin par un administrateur.
- `/points` : classement des points déjà attribués, consultable par tous pendant et après la cérémonie.

Le scrutin se ferme automatiquement lorsque tous les électeurs ont voté ou lorsque les 30 minutes sont écoulées. Zéro vote ne donne aucun point de build. Avec une seule équipe, personne ne peut voter pour une autre équipe : l’attente est automatiquement sautée. En cas d’égalité dans une catégorie, chaque équipe à égalité gagne un point ; une égalité finale annonce plusieurs vainqueurs.

Après l’attribution du build, un décompte **3, 2, 1** révèle le ou les vainqueurs. Les totaux restent visibles dans le scoreboard et via `/points`. Les pseudos restent colorés selon l’équipe, au-dessus des joueurs et dans le TAB (les règles d’affichage propres au mode spectateur sont celles du client Minecraft).

`/game reset` annule la cérémonie et le scrutin, efface points, premiers boss et votes, puis effectue le reset habituel. Le mode de jeu, les capacités de vol et la position d’avant visite sont restaurés ; les inventaires ne sont pas modifiés. Les spectateurs restent en visite après la révélation jusqu’au reset. À la déconnexion et à la désactivation du plugin, leur mode précédent est également restauré.

Les résultats, votes, électeurs, temps de vote restant et retours de visite sont persistants. Une cérémonie interrompue attend `/game start` pour reprendre ; les points déjà attribués ne sont pas doublés. Pour vérifier en jeu : utiliser deux équipes participantes, terminer une partie, vérifier le passage en spectateur, le refus du vote pour soi-même et du second vote, puis tester `/game vote end` et `/game reset` pendant les différentes phases.


## Événement sur plusieurs jours

Pour fermer le serveur entre deux sessions :

1. `/game pause` suspend la partie ou la cérémonie, y compris le temps de vote.
2. `/game save` force une sauvegarde (facultatif : les changements sont déjà sauvegardés automatiquement).
3. Utiliser la commande serveur `/stop` pour éteindre proprement Paper.
4. Au prochain démarrage, contrôler les interfaces puis lancer `/game start` pour reprendre.

**`/game stop` termine la compétition et déclenche les résultats.** Pour une interruption jusqu’au lendemain, utiliser `/game pause`, puis `/stop`. Même sans pause manuelle, l’arrêt propre sauvegarde les compteurs suspendus. Au redémarrage, aucune durée de jeu ou de vote n’est consommée pendant l’absence. La reprise ne redistribue pas les équipements et ne retéléporte pas les participants déjà arrivés.

Les données sont dans `plugins/ilesFlottantes/`, et non dans les fichiers de la map :

- `session.json` : chrono, durée, équipes des joueurs, kills/morts, premiers boss, points, votes, électeurs, progression de cérémonie, téléportations de départ encore attendues et état de retour des spectateurs.
- `session.json.bak` : version précédente vérifiée de la session.
- `balances.properties` : caisses des équipes, sauvegardées à chaque transaction.
- `territories.properties` : zones et points de spawn, sauvegardés à chaque modification.

La session est écrite après chaque mutation suivie et chaque seconde pour les compteurs, avec fichier temporaire, synchronisation disque, remplacement atomique et contrôle d’intégrité. Un crash peut perdre la fraction de chrono depuis le dernier point de reprise ; ce mécanisme ne garantit pas une perte nulle en cas de panne matérielle. Les inventaires, entités et blocs restent sauvegardés par Paper selon son propre cycle : ils ne forment pas une transaction commune avec le plugin. Sauvegarder **les mondes et le dossier du plugin ensemble** pour déplacer l’événement ou créer une sauvegarde complète.

Une erreur d’écriture suspend la partie et la cérémonie et avertit les opérateurs ; après rétablissement du stockage, la reprise reste manuelle. Un fichier de session invalide désactive le plugin sans écraser les données ni revenir silencieusement à une ancienne session. Conserver les fichiers principal et `.bak` pour diagnostic. Un marqueur `reset.pending` permet d’achever au démarrage un reset interrompu, sans laisser une session ancienne avec des caisses remises à zéro. `/game reset` conserve les zones et les spawns. Toutes les zones sont reverrouillées au redémarrage, même si un raid était ouvert.

Vérification serveur recommandée avant l’événement : commencer un compte à rebours, modifier les équipes et caisses, enregistrer une mort, mettre en pause puis redémarrer. Vérifier que les valeurs sont conservées et le chrono figé, puis reprendre. Refaire le test pendant le vote : mode spectateur, vote déjà effectué toujours interdit, temps restant conservé, puis reset et restauration du mode de jeu initial.
