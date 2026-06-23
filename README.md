# Alzheimer Villagers - Minecraft NeoForge Mod 1.21.1

Ce mod Minecraft NeoForge (conçu pour Minecraft 1.21.1 et Java 23) connecte les villageois à un grand modèle de langage (LLM) hébergé localement ou à distance (Ollama, LM Studio, etc.). Il introduit des mécaniques uniques de jeu de rôle basées sur la mémoire, l'humeur et le commerce dynamique.

---

## 🌟 Fonctionnalités Clés

### 1. Dialogue Interactif et Naturel
*   **Activation** : Faites un clic droit sur un villageois avec la **main vide** pour démarrer une session de conversation. L'interface de commerce vanilla est alors désactivée pour laisser place au dialogue.
*   **Discussion dans le Chat** : Écrivez directement vos messages dans le chat du jeu. Vos messages sont interceptés et envoyés de manière asynchrone au LLM (pour éviter de geler ou faire laguer le serveur).
*   **Personnalité selon le Métier** : Le prompt système du villageois s'adapte automatiquement à son métier vanilla (les fermiers parlent de leurs récoltes, les bibliothécaires sont plus intellectuels mais distraits, les villageois sans emploi ou idiots ont des réponses loufoques).
*   **Distance de Sécurité** : Si vous vous éloignez de plus de 8 blocs du villageois, la conversation s'arrête automatiquement. Vous pouvez également taper `exit` ou `bye` pour terminer la discussion.

### 2. Mémoire Glissante & Syndrome d'Alzheimer (Perte de Mémoire)
*   **Données NBT persistantes** : L'historique de discussion est stocké directement sur chaque villageois via le système de **Data Attachments** de NeoForge, ce qui permet de le sauvegarder lors de l'arrêt du monde.
*   **Sliding Window (Fenêtre Glissante)** : Pour éviter de surcharger le contexte du modèle et de dépasser les limites de tokens, le mod garde uniquement un nombre limité de messages récents (par défaut 8 messages). Les messages plus anciens sont supprimés.
*   **Prompt de Confusion Temporaire** : Lorsqu'un ancien message est effacé de la mémoire du villageois, celui-ci subit une perte de mémoire.
    *   Un message s'affiche dans le chat pour le joueur : `[Lore] <Nom du villageois> vous regarde d'un air vide. Il semble avoir oublié d'anciennes parties de votre conversation...`
    *   Pendant les **2 prochains tours**, un prompt système additionnel force le villageois à exprimer une confusion passagère ou un léger mal de crâne dans ses réponses, simulant ainsi un épisode d'Alzheimer de manière narrative.

### 3. Connaissance Dynamique des Échanges (Trades)
*   Le mod récupère dynamiquement les offres d'échange actives du villageois.
*   Ces échanges (les achats et les ventes) sont injectés directement dans le prompt système du LLM sous la forme :
    `Player gives: 20 Wheat -> You give: 1 Emerald`
*   Cela permet au LLM de savoir exactement ce qu'il a en inventaire et de négocier intelligemment avec vous !

### 4. Humeur et Pénalités Commerciales
*   **Analyse de Sentiment** : Le modèle est configuré pour renvoyer des tags de sentiment à la fin de ses réponses (`[ANGRY]`, `[FRIENDLY]`, ou `[NEUTRAL]`).
*   **Indice d'Agacement (Annoyance)** :
    *   Si vous insultez ou énervez le villageois, le LLM renvoie le tag `[ANGRY]`, ce qui augmente son score d'agacement.
    *   Si vous **frappez physiquement** le villageois, son agacement augmente de 40 points instantanément et il crie de douleur dans le chat.
    *   Être aimable avec lui (`[FRIENDLY]`) réduit son agacement.
*   **Inflation et Hausse des Prix** : Plus l'agacement d'un villageois est élevé, plus ses prix grimpent en flèche ! Ses offres d'échange vanilla subissent une hausse de prix proportionnelle à son agacement.

---

## 🛠️ Prérequis et Configuration

### Prérequis
*   **Java 23 JDK** installé et configuré.
*   Un serveur LLM local en cours d'exécution. Par exemple :
    *   **Ollama** : avec un modèle comme `llama3`, `mistral` ou `qwen2.5`.
    *   **LM Studio** : configuré avec un serveur d'API compatible OpenAI.

### Configuration du Mod
Lors du premier lancement du jeu, un fichier de configuration est généré à cet emplacement :
`run/config/alzheimer_villagers-common.toml`

Vous pouvez modifier les options suivantes :
```toml
# URL de l'API de chat du LLM (Ollama ou LM Studio)
llmUrl = "http://localhost:11434/api/chat"

# Nom du modèle à interroger
modelName = "llama3"

# Nombre maximum de messages gardés en mémoire avant la perte de mémoire
maxMemorySize = 8

# Facteur multiplicateur pour la pénalité de prix (augmentation = agacement * facteur)
pricePenaltyFactor = 0.15
```

---

## 🚀 Compiler et Tester localement

### Compiler le projet
Pour compiler le code source de manière classique :
```powershell
.\gradlew compileJava
```

### Lancer le client de test
Pour démarrer directement Minecraft avec le mod installé pour tester votre installation :
```powershell
.\gradlew runClient
```

### Lancer et déboguer avec IntelliJ IDEA
1. Générez les configurations de projet pour IntelliJ en lançant la commande :
   ```powershell
   .\gradlew IntelliJIDEA
   ```
2. Ouvrez le dossier du projet dans IntelliJ.
3. Sélectionnez la configuration d'exécution **`runClient`** en haut à droite.
4. Cliquez sur **Debug** (l'insecte vert) pour lancer le jeu avec la possibilité de placer des points d'arrêt dans votre code.

---

## 📂 Structure Technique du Projet

*   `src/main/java/com/kylian/alzheimer/`
    *   `AlzheimerVillagersMod.java` : Initialisation du mod, enregistrement de la configuration et des Attachments de données NBT.
    *   `config/ModConfig.java` : Déclaration et chargement des paramètres personnalisables du mod.
    *   `data/`
        *   `ChatMessage.java` : Représentation d'un message dans l'historique (rôle user/assistant, contenu).
        *   `VillagerChatData.java` : Gestionnaire de l'historique d'un villageois, sérialisation NBT, logique de la fenêtre glissante (sliding window) et déclenchement de la confusion Alzheimer.
    *   `event/ModEvents.java` : Événements NeoForge gérant l'interaction (clic droit), le chat en jeu, les dégâts physiques subis, les cycles de ticks serveurs et la mise à jour des prix.
    *   `llm/LlmClient.java` : Client HTTP asynchrone utilisant Gson pour requêter l'API du LLM sans bloquer le thread principal du jeu.
    *   `manager/ChatSessionManager.java` : Gestion des sessions actives (qui parle à quel villageois).
